package com.splunk.splunkjenkins.model;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.LogEventHelper;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.splunk.splunkjenkins.Constants.EVENT_SOURCE_TYPE;
import static com.splunk.splunkjenkins.model.EventType.CONSOLE_LOG;

public class EventRecord {
    private final static String METADATA_KEYS[] = {"index", "source", EVENT_SOURCE_TYPE};
    private long time;
    private int retryCount;
    private Object message;
    private EventType eventType;
    private String source;

    public EventRecord(Object message, EventType eventType) {
        this.retryCount = 0;
        if (eventType == null) {
            this.eventType = EventType.LOG;
        } else {
            this.eventType = eventType;
        }
        this.time = System.currentTimeMillis();
        if (message == null) {
            throw new RuntimeException("null message not allowed");
        }
        this.message = message;
    }

    public void increase() {
        this.retryCount++;
    }

    public boolean isDiscarded() {
        try {
            return retryCount > SplunkJenkinsInstallation.get().getMaxRetries();
        }catch (IllegalStateException ex){
            //jenkins server was shutdown
            return true;
        }
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Nonnull
    public Object getMessage() {
        return message;
    }

    public String getMessageString() {
        return message.toString();
    }

    private boolean isString() {
        return (message instanceof String);
    }

    /**
     * @return short message, to be showed in debug message
     */
    public String getShortDescr() {
        if (isString()) {
            return "{length:" + ((String) message).length() + " " + StringUtils.substring((String) message, 0, 160) + " ...}";
        } else {
            return "{raw data" + StringUtils.abbreviate("" + message, 160) + "}";
        }
    }

    public String getTimestamp() {
        return String.format(Locale.US, "%.3f", time / 1000d);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /**
     * @return the event type
     */
    @Nonnull
    public EventType getEventType() {
        return eventType;
    }

    /**
     * @param config the Splunk config which contains metadata information
     * @return metdata information for http event collector
     */
    private Map<String, String> getMetaData(SplunkJenkinsInstallation config) {
        LogEventHelper.UrlQueryBuilder metaDataBuilder = new LogEventHelper.UrlQueryBuilder();
        metaDataBuilder.putIfAbsent("source", source);
        for (String metaDataKey : METADATA_KEYS) {
            //individual config(EventType) have higher priority over default config
            metaDataBuilder.putIfAbsent(metaDataKey, config.getMetaData(eventType.getKey(metaDataKey)));
        }
        //default sourcetype for text
        if (isString()) {
            //just plain text, not complex object, prefer "text:jenkins" as sourcetype
            metaDataBuilder.putIfAbsent(EVENT_SOURCE_TYPE, config.getMetaData(EVENT_SOURCE_TYPE + "_text"));
        }
        //prefer console log's index
        if (eventType == EventType.LOG) {
            metaDataBuilder.putIfAbsent("index", config.getMetaData(CONSOLE_LOG.getKey("index")));
        }
        //default settings for complex object
        for (String metaDataKey : METADATA_KEYS) {
            metaDataBuilder.putIfAbsent(metaDataKey, config.getMetaData(metaDataKey));
        }
        metaDataBuilder.putIfAbsent("host", config.getMetadataHost());
        return metaDataBuilder.getQueryMap();
    }

    /**
     * @param config the Splunk config which contains metadata information
     * @return the http event collector endpoint
     */
    public String getRawEndpoint(SplunkJenkinsInstallation config) {
        Map queryMap = new HashMap();
        queryMap.putAll(getMetaData(config));
        return config.getRawUrl() + "?" + LogEventHelper.UrlQueryBuilder.toString(queryMap);
    }

    /**
     * @param config the Splunk config which contains metadata information
     * @return a Map object can be used for json serialization
     */
    public Map<String, Object> toMap(SplunkJenkinsInstallation config) {
        Map<String, Object> values = new HashMap<>();
        values.put("time", getTimestamp());
        values.put("event", message);
        Map<String, String> metaDataConfig = getMetaData(config);
        values.putAll(metaDataConfig);
        return values;
    }

    public boolean isFailed() {
        return retryCount > 0;
    }
}
