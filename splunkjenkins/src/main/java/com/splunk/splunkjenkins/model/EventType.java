package com.splunk.splunkjenkins.model;

public enum EventType {
    BUILD_REPORT(false),
    BUILD_EVENT(false),
    QUEUE_INFO(false),
    JENKINS_CONFIG(false),
    GENERIC_TEXT(false),
    CONSOLE_LOG(true, "generic_single_line"),
    FILE(true, "generic_single_line"),
    SLAVE_INFO(false);

    /**
     * whether the data need to be split by line breaker before send
     */
    private boolean needSplit;
    private String sourceType;

    EventType(boolean needSplit) {
        this.needSplit = needSplit;
        this.sourceType = "_json";
    }

    EventType(boolean needSplit, String sourceType) {
        this.needSplit = needSplit;
        this.sourceType = sourceType;
    }

    /**
     * need spit the content line by line if raw event not supported
     *
     * @return <code>true</code> if need spit the contents line by line if raw event not supported;
     * <code>false</code> otherwise.
     */
    public boolean needSplit() {
        return needSplit;
    }

    public String getSourceType() {
        return this.sourceType;
    }
}
