package com.splunk.splunkjenkins.model;

public enum EventType {
    BUILD_REPORT(false),
    BUILD_EVENT(false),
    QUEUE_INFO(false),
    JENKINS_CONFIG(false),
    CONSOLE_LOG(true),
    FILE(true),
    SLAVE_INFO(false),
    LOG(false),
    BATCH_JSON(false),
    JSON_FILE(true);

    /**
     * whether the data need to be split by line breaker before send
     */
    private boolean needSplit;

    EventType(boolean needSplit) {
        this.needSplit = needSplit;
    }

    /**
     * Need spit the content line by line if raw event not supported
     * Only applied for non-structural data, such as file and console text.
     * It doesn't applied for json data or xml data
     *
     * @return <code>true</code> if need spit the contents line by line if raw event not supported;
     * <code>false</code> otherwise.
     */
    public boolean needSplit() {
        return needSplit;
    }

    /**
     * @param suffix the config metadata, can be either index, source or sourcetype
     * @return return name.suffix
     */
    public String getKey(String suffix) {
        return this.name().toLowerCase() + "." + suffix;
    }
}
