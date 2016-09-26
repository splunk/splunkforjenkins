package com.splunk.splunkjenkins.model;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.LogEventHelper;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.splunk.splunkjenkins.Constants.EVENT_SOURCE_TYPE;

public class EventRecord {
    private final static String METADATA_KEYS[] = {"index", "source", "host", EVENT_SOURCE_TYPE};
    private long time;
    private int retryCount;
    private Object message;
    private EventType eventType;
    private String source;

    public EventRecord(Object message, EventType eventType) {
        this.retryCount = 0;
        if (eventType == null) {
            this.eventType = EventType.GENERIC_TEXT;
        } else {
            this.eventType = eventType;
        }
        this.time = System.currentTimeMillis();
        this.message = message;
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

    public void setTime(long time) {
        this.time = time;
    }

    public Object getMessage() {
        return message;
    }

    public String getMessageString() {
        if (message instanceof byte[]) {
            return new String((byte[]) message);
        } else {
            return message.toString();
        }
    }

    /**
     * @return short message, to be showed in debug message
     */
    public String getShortDescr() {
        if (message == null) { //should not happen
            return "NULL message";
        }
        if (message instanceof String) {
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
            if (eventType.equals(EventType.GENERIC_TEXT)) {
                //use console log settings if not set
                metaDataBuilder.putIfAbsent(metaDataKey, config.getMetaData(EventType.CONSOLE_LOG.getKey(metaDataKey)));
            }
            metaDataBuilder.putIfAbsent(metaDataKey, config.getMetaData(metaDataKey));
        }
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
