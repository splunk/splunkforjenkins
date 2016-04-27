package com.splunk.splunkjenkins.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EventRecordSerializer implements JsonSerializer<EventRecord> {
    @Override
    public JsonElement serialize(EventRecord src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject eventJson = new JsonObject();
        SplunkJenkinsInstallation config = SplunkJenkinsInstallation.get();
        // need add metadata into json object if query parameter is not supported.
        // if raw input is not supported, then metadata in query parameter is not supported neither
        if (!config.isRawEventEnabled()) {
            eventJson = (JsonObject) context.serialize(config.metaData);
        }
        if (src.isRaw()) {
            String rawSource = config.getSouceName("console");
            eventJson.addProperty("source", rawSource);
            eventJson.add("event", context.serialize(new String(src.getData())));
        } else {
            eventJson.add("event", context.serialize(src.getMessage()));

        }
        eventJson.addProperty("time", src.getTimestamp());
        return eventJson;
    }


}
