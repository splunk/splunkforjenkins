package com.splunk.splunkjenkins.utils;

import static com.splunk.splunkjenkins.utils.LogEventHelper.emptyToNull;

public class EventMetaData {
    private String index;
    private String source;
    private String sourcetype;
    private String host;

    public EventMetaData(String index, String source, String sourcetype, String host) {
        this.index = emptyToNull(index);
        this.source = emptyToNull(source);
        this.sourcetype = emptyToNull(sourcetype);
        this.host = emptyToNull(host);
    }

}
