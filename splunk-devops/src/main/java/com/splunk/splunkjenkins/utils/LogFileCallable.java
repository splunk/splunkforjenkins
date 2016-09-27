package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.model.EventRecord;
import com.splunk.splunkjenkins.model.EventType;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.util.ByteArrayOutputStream2;
import jenkins.model.Jenkins;
import org.apache.commons.beanutils.BeanUtils;
import org.jenkinsci.remoting.RoleChecker;

import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.splunk.splunkjenkins.SplunkJenkinsInstallation.MIN_BUFFER_SIZE;
import static com.splunk.splunkjenkins.model.EventType.FILE;

public class LogFileCallable implements FilePath.FileCallable<Integer> {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(LogFileCallable.class.getName());
    private static final int LOOKAHEAD_NEW_LINE=151;
    private static final String TIMEOUT_NAME = LogFileCallable.class.getName() + ".timeout";
    private final int WAIT_MINUTES = Integer.getInteger(TIMEOUT_NAME, 5);
    private final String baseName;
    private final String buildUrl;
    private final Map eventCollectorProperty;
    private final boolean sendFromSlave;
    private final long maxFileSize;
    private boolean enabledSplunkConfig = false;

    public LogFileCallable(String baseName, String buildUrl,
                           Map eventCollectorProperty, boolean sendFromSlave, long maxFileSize) {
        this.baseName = baseName;
        this.eventCollectorProperty = eventCollectorProperty;
        this.buildUrl = buildUrl;
        this.sendFromSlave = sendFromSlave;
        this.maxFileSize=maxFileSize;
    }

    public int sendFiles(FilePath[] paths) {
        int eventCount = 0;
        for (FilePath path : paths) {
            try {
                if (path.isDirectory()) {
                    continue;
                }
                if (sendFromSlave) {
                    LOG.log(Level.INFO, "uploading from slave:" + path.getName());
                    eventCount += path.act(this);
                    LOG.log(Level.FINE, "sent in " + eventCount + " batches");
                } else {
                    InputStream in = path.read();
                    try {
                        LOG.log(Level.FINE, "uploading from master:" + path.getName());
                        eventCount += send(path.getRemote(), in);
                    } finally {
                        in.close();
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "archive file failed", e);
            }
        }
        return eventCount;
    }

    public Integer send(String fileName, InputStream input) throws IOException, InterruptedException {
        //always use unix style path because windows slave maybe launched by ssh
        String sourceName = fileName.replace("\\","/");
        String ws_posix_path =baseName.replace("\\","/");
        if (sourceName.startsWith(ws_posix_path)) {
            sourceName = sourceName.substring(ws_posix_path.length() + 1);
        }
        sourceName = buildUrl + sourceName;
        ByteArrayOutputStream2 logText = new ByteArrayOutputStream2(MIN_BUFFER_SIZE);
        long totalSize=0;
        Integer count = 0;
        int c;
        while ((c = input.read()) >= 0) {
            totalSize++;
            if(maxFileSize!=0 && totalSize>maxFileSize){
                logText.reset();
                logText.write(("file truncated to size:"+maxFileSize).getBytes());
                SplunkLogService.getInstance().send(sourceName+" too large", "large_file");
                break;
            }
            logText.write(c);
            long throttleSize= SplunkJenkinsInstallation.get().getMaxEventsBatchSize();
            if(!SplunkJenkinsInstallation.get().isRawEventEnabled()){
                //if raw event is not supported, we need split the content line by line and append metadata to each line
                throttleSize=throttleSize/2;
            }
            if (c == '\n') {
                throttleSize = throttleSize - LOOKAHEAD_NEW_LINE;
            }
            if (logText.size() >= throttleSize ) {
                flushLog(sourceName, logText);
                count++;
            }

        }
        if (logText.size() > 0) {
            flushLog(sourceName, logText);
            count++;
        }
        return count;
    }

    private void initSplunkins() {
        if (enabledSplunkConfig) {
            return;
        }
        // Init SplunkJenkins global config in slave, can not reference Jenkins.getInstance(), Xtream
        // need built from map
        SplunkJenkinsInstallation config = new SplunkJenkinsInstallation(false);
        try {
            BeanUtils.populate(config, eventCollectorProperty);
            config.setEnabled(true);
            SplunkJenkinsInstallation.initOnSlave(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //only use one thread on slave
        SplunkLogService.getInstance().MAX_WORKER_COUNT=1;
        enabledSplunkConfig = true;
    }

    private void flushLog(String source, ByteArrayOutputStream out) {
        SplunkLogService.getInstance().send(out.toString(), FILE, source);
        out.reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        if (!enabledSplunkConfig && Jenkins.getInstance() == null) {
            //running on slave node, need init config
            initSplunkins();
        }
        InputStream input = new FileInputStream(f);
        try {
            long expireTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(WAIT_MINUTES);
            int count = send(f.getAbsolutePath(), input);
            while (SplunkLogService.getInstance().getQueueSize() > 0 && System.currentTimeMillis() < expireTime) {
                Thread.sleep(500);
            }
            if(System.currentTimeMillis()>expireTime){
                LOG.log(Level.SEVERE, "sending file timeout in "+WAIT_MINUTES+" minutes," +
                        " please adjust the value by passing -D"+TIMEOUT_NAME +"=minutes to slave jvm parameter");
            }
            SplunkLogService.getInstance().stopWorker();
            SplunkLogService.getInstance().releaseConnection();
            return count;
        } finally {
            input.close();
        }
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException {

    }
}
