package com.splunk.splunkjenkins.utils;

import shaded.splk.com.google.gson.JsonElement;
import shaded.splk.com.google.gson.JsonSerializationContext;
import shaded.splk.com.google.gson.JsonSerializer;
import com.splunk.splunkjenkins.model.CoverageMetricAdapter;

import java.lang.reflect.Type;

public class CoverageDetailJsonSerializer implements JsonSerializer<CoverageMetricAdapter.CoverageDetail> {
    @Override
    public JsonElement serialize(CoverageMetricAdapter.CoverageDetail coverageDetail, Type type,
                                 JsonSerializationContext jsonSerializationContext) {
        return jsonSerializationContext.serialize(coverageDetail.getReport());
    }
}
