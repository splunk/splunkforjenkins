package com.splunk.splunkjenkins;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class SplunkLogServiceTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    public SplunkLogServiceTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of update method, of class SplunkLogService.
     */
    @Test
    public void testUpdate() throws IOException, InterruptedException {
        System.out.println("testUpdate");
        SplunkConfigUtil configUtil = new SplunkConfigUtil();
        boolean valid = configUtil.setupSender();
        Assert.assertTrue("config is valid", valid);
        int eventNumber = 20000;
        long batchId = System.currentTimeMillis();
        System.out.println("Batch ID:" + batchId);
        long initNumber = SplunkLogService.getInstance().getSentCount();
        for (int i = 0; i < eventNumber; i++) {
            Map data = new HashMap();
            data.put("id", UUID.randomUUID());
            data.put("batch", batchId);
            data.put("number", i);
            SplunkLogService.getInstance().send(data);
        }
        //give some time to send,max wait time is 1 minute
        long timeToWait = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
        while (SplunkLogService.getInstance().getSentCount() < (eventNumber + initNumber)) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() > timeToWait) {
                fail("can not send events in time, sent out " + (SplunkLogService.getInstance().getSentCount() - initNumber));
            }
        }
        System.out.println("index=main |spath batch |search batch=" + batchId);
    }

    /**
     * Test of getScript method, of class SplunkLogService.
     */
    //@Test
    public void testGetScript() {
        System.out.println("getScript");
        String expResult = "";
        String result = SplunkLogService.config.getScript();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

}
