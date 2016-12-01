package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.BaseTest;
import hudson.model.Label;
import hudson.model.Slave;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static org.junit.Assert.*;

public class LogEventHelperTest extends BaseTest {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(LogEventHelper.class.getName());

    @Test
    public void parseFileSize() throws Exception {
        long oneMB = 1024 * 1024;
        long twoKB = 2 * 1024;
        assertEquals(oneMB, LogEventHelper.parseFileSize("1MB"));
        assertEquals(512 * 1024, LogEventHelper.parseFileSize("0.5MB"));
        assertEquals(twoKB, LogEventHelper.parseFileSize("2KB"));
        assertEquals(123535, LogEventHelper.parseFileSize("123535"));
        assertEquals(0, LogEventHelper.parseFileSize("12s"));
    }

    @Test
    public void testSlaveStats() throws Exception {
        Slave slave = j.createOnlineSlave();
        Map<String, Map<String, Object>> slaveStats = LogEventHelper.getSlaveStats();
        assertTrue("should not be empty", !slaveStats.isEmpty());
        String slaveName = slave.getNodeName();
        String monitorName = "ClockMonitor";
        long timeToWait = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
        boolean hasMonitorData = false;
        while (System.currentTimeMillis() < timeToWait) {
            slaveStats = LogEventHelper.getSlaveStats();
            LOG.log(Level.FINER, slaveStats.toString());
            if (slaveStats.containsKey(slaveName)) {
                hasMonitorData = slaveStats.get(slaveName).containsKey(monitorName);

            }
            if (hasMonitorData) {
                break;
            }
        }
        assertTrue(hasMonitorData);
    }
}