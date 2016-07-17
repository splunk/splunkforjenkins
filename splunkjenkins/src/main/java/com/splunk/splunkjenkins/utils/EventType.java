package com.splunk.splunkjenkins.utils;

public enum EventType {
    BUILD_REPORT(false),
    BUILD_EVENT(false),
    QUEUE_INFO(false),
    XML_CONFIG(false),
    GENERIC_TEXT(false),
    CONSOLE_LOG(true),
    GENERIC_LINES(true),
    FILE(true),
    SLAVE_INFO(false);

    /**
     * whether the data need to be split by line breaker before send
     */
    private boolean needSplit;

    EventType(boolean needSplit) {
        this.needSplit = needSplit;
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
}
