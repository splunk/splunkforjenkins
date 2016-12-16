package com.splunk.splunkjenkins;

import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.PeriodicWork;
import hudson.model.TaskListener;
import org.junit.Test;
import org.jvnet.hudson.test.TouchBuilder;

import java.util.UUID;

import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static org.junit.Assert.*;

public class HealthMonitorTest extends BaseTest {


    @Test
    public void execute() throws Exception {
        HealthMonitor monitor = PeriodicWork.all().get(HealthMonitor.class);
        FreeStyleProject p = j.createFreeStyleProject("test-sleep" + UUID.randomUUID());
        p.setAssignedLabel(Label.get("no-such-node"));
        p.getBuildersList().add(new TouchBuilder());
        long startTime = System.currentTimeMillis();
        p.scheduleBuild2(0);
        monitor.lastAccessTime=0;
        monitor.execute(TaskListener.NULL);
        verifySplunkSearchResult("event_tag=queue", startTime, 1);
        verifySplunkSearchResult("event_tag=slave", startTime, 1);
    }

    @Test
    public void getRecurrencePeriod() throws Exception {
        long userDefault = 45000;
        long period = PeriodicWork.all().get(HealthMonitor.class).getRecurrencePeriod();
        assertEquals(userDefault, period);
    }

}