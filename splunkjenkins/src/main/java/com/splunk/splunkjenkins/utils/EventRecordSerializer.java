package com.splunk.splunkjenkins.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.SplunkLogService;

import java.lang.reflect.Type;
import java.util.Locale;

public class EventRecordSerializer implements JsonSerializer<EventRecord> {
    @Override
    public JsonElement serialize(EventRecord src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject eventJson = (JsonObject) context.serialize(SplunkLogService.config.eventMetaData);
        eventJson.addProperty("time", String.format(Locale.US, "%.3f", src.getTime() / 1000d));
        eventJson.add("event", context.serialize(src.getMessage()));
        return eventJson;
    }
}
