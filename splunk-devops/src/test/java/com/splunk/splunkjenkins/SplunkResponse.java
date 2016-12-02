package com.splunk.splunkjenkins;

import java.util.List;
import java.util.Map;

public class SplunkResponse {
    List<EntryItem> entry;

    public String getFirst(String key) {
        return getItem(0, key);
    }

    public void setEntry(List<EntryItem> entry) {
        this.entry = entry;
    }

    public String getItem(int idx, String key) {
        return entry.get(idx).content.get(key).toString();
    }

    public static class EntryItem {
        Map content;
    }
}
