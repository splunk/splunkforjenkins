package com.splunk.splunkjenkins.model;

import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(FormattedJsonAdapter.class)
public class JenkinsJsonConfig {
    String value;

    public JenkinsJsonConfig(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
