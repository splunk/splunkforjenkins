package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.util.ByteArrayOutputStream2;
import org.jenkinsci.remoting.RoleChecker;

import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.splunk.splunkjenkins.utils.EventType.FILE;

public class LogFileCallable implements FilePath.FileCallable<Integer> {
    private final int WAIT_MINUTES = 5;
    private final String baseName;
    private final String buildUrl;
    private final Map configs;

    public LogFileCallable(String baseName, String buildUrl, Map configs) {
        this.baseName = baseName;
        this.configs = configs;
        this.buildUrl = buildUrl;
    }


    public Integer send(String fileName, InputStream input) throws IOException, InterruptedException {
        String sourceName = fileName;
        if (sourceName.startsWith(baseName)) {
            sourceName = sourceName.substring(baseName.length() + 1);
        }
        sourceName = buildUrl + sourceName;
        ByteArrayOutputStream2 logText = new ByteArrayOutputStream2();
        Integer count = 0;
        int c;
        while ((c = input.read()) >= 0) {
            logText.write(c);
            if (c == '\n') {
                if (logText.size() > SplunkJenkinsInstallation.get().maxEventsBatchSize) {
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
        //init splunk config in slave, can not reference Jenkins.getInstance(), Xtream
        SplunkJenkinsInstallation config = new SplunkJenkinsInstallation(false);
        config.token = (String) configs.get("token");
        config.useSSL = Boolean.valueOf("" + configs.get("useSSL"));
        config.rawEventEnabled = Boolean.valueOf("" + configs.get("rawEventEnabled"));
        config.metaDataConfig = (String) configs.get("metaDataConfig");
        config.host = (String) configs.get("host");
        config.port = Integer.parseInt("" + configs.get("port"));
        config.enabled = true;
        SplunkJenkinsInstallation.setConfig(config);
        InputStream input = new FileInputStream(f);
        try {
            int count = send(f.getAbsolutePath(), input);
            long expireTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(WAIT_MINUTES);
            while (SplunkLogService.getInstance().getQueueSize() > 0 && System.currentTimeMillis() < expireTime) {
                Thread.sleep(500);
            }
            return count;
        } finally {
            input.close();
        }
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException {

    }
}
