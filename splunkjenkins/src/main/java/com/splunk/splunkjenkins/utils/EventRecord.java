package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;

import java.util.Locale;

public class EventRecord {
    long time;
    int retryCount;
    Object message;
    boolean raw;
    byte[] data;

    public EventRecord(Object message) {
        this.retryCount = 0;
        this.time = System.currentTimeMillis();
        if (message instanceof byte[]) {
            this.raw = true;
            this.data = (byte[]) message;
            this.message = "[rawdata]";
        } else {
            this.message = message;
        }
    }

    public void increase() {
        this.retryCount++;
    }

    public boolean discard() {
        return retryCount > SplunkJenkinsInstallation.get().getMaxRetries();
    }

    public long getTime() {
        return time;
    }

    public Object getMessage() {
        return message;
    }

    public boolean isRaw() {
        return raw;
    }

    /**
     * get raw data
     *
     * @return
     */
    public byte[] getData() {
        return data;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getTimestamp() {
        return String.format(Locale.US, "%.3f", time / 1000d);
    }
}
