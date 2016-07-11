package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
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

import static com.splunk.splunkjenkins.utils.EventType.FILE;

public class LogFileCallable implements FilePath.FileCallable<Integer> {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(LogFileCallable.class.getName());
    private final int WAIT_MINUTES = 5;
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
                    LOG.log(Level.FINE, "uploading from slave:" + path.getName());
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
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "failed to archive files", e);
            } catch (InterruptedException e) {
                LOG.log(Level.SEVERE, "interrupted while archiving file", e);
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
        ByteArrayOutputStream2 logText = new ByteArrayOutputStream2();
        long totalSize=0;
        Integer count = 0;
        int c;
        while ((c = input.read()) >= 0) {
            totalSize++;
            if(maxFileSize!=0 && totalSize>maxFileSize){
                logText.reset();
                logText.write(("file truncated to size:"+maxFileSize).getBytes());
                EventRecord warningRecord=new EventRecord(sourceName+" too large", EventType.GENERIC_TEXT);
                warningRecord.setSource("large_file");
                SplunkLogService.getInstance().send(warningRecord);
                break;
            }
            logText.write(c);
            if (c == '\n') {
                if (logText.size() > SplunkJenkinsInstallation.get().getMaxEventsBatchSize()) {
                    flushLog(sourceName, logText);
                    count++;
                }
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
        enabledSplunkConfig = true;
    }

    private void flushLog(String source, ByteArrayOutputStream out) {
        EventRecord record = new EventRecord(out.toString(), FILE);
        record.setSource(source);
        SplunkLogService.getInstance().enqueue(record);
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
