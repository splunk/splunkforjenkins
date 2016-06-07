package com.splunk.splunkjenkins.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class LogEventHelperTest {

    @Test
    public void parseFileSize() throws Exception {
        long oneMB=1024*1024;
        long twoKB=2*1024;
        assertEquals(oneMB,LogEventHelper.parseFileSize("1MB"));
        assertEquals(512*1024,LogEventHelper.parseFileSize("0.5MB"));
        assertEquals(twoKB,LogEventHelper.parseFileSize("2KB"));
        assertEquals(123535,LogEventHelper.parseFileSize("123535"));
        assertEquals(0,LogEventHelper.parseFileSize("12s"));
    }
}