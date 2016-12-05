package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.model.CoverageMetricsAdapter;
import shaded.splk.com.google.gson.JsonElement;
import shaded.splk.com.google.gson.JsonSerializationContext;
import shaded.splk.com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class CoverageDetailJsonSerializer implements JsonSerializer<CoverageMetricsAdapter.CoverageDetail> {
    @Override
    public JsonElement serialize(CoverageMetricsAdapter.CoverageDetail coverageDetail, Type type,
                                 JsonSerializationContext jsonSerializationContext) {
        return jsonSerializationContext.serialize(coverageDetail.getReport());
    }
}
