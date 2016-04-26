package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;

public class EventRecord {
    long time;
    int retryCount;
    Object message;

    public EventRecord(Object message) {
        this.retryCount = 0;
        this.message = message;
        this.time = System.currentTimeMillis();
    }

    public void increase() {
        this.retryCount++;
    }

    public boolean discard() {
        return retryCount > SplunkJenkinsInstallation.getSplunkDescriptor().retriesOnError;
    }

    public double getTime() {
        return time;
    }

    public Object getMessage() {
        return message;
    }
}
