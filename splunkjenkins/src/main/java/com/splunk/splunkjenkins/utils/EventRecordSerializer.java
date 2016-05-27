package com.splunk.splunkjenkins.utils;

import com.google.gson.*;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;

import java.lang.reflect.Type;
import java.util.logging.Logger;

public class EventRecordSerializer implements JsonSerializer<EventRecord> {
    private static final Logger LOG = Logger.getLogger(EventRecordSerializer.class.getName());

    @Override
    public JsonElement serialize(EventRecord src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject eventJson = new JsonObject();
        SplunkJenkinsInstallation config = SplunkJenkinsInstallation.get();
        if (!config.isMetaDataInURLSupported()) {
            //need append metadata into event json object
            eventJson = (JsonObject) context.serialize(src.getMetaData());
        }
        eventJson.add("event", context.serialize(src.getMessage()));
        eventJson.addProperty("time", src.getTimestamp());
        return eventJson;
    }

}
