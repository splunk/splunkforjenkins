package com.splunk.splunkjenkins.model;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class FormattedJsonAdapter extends TypeAdapter<JenkinsJsonConfig> {

    @Override
    public void write(JsonWriter jsonWriter, JenkinsJsonConfig jenkinsJsonConfig) throws IOException {
        jsonWriter.jsonValue(jenkinsJsonConfig.getValue());
    }

    @Override
    public JenkinsJsonConfig read(JsonReader jsonReader) throws IOException {
        throw new IOException("not implemented");
    }
}