package com.splunk.splunkjenkins;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.FileAppender;
import com.splunk.logging.RemoteAppender;

import static com.splunk.splunkjenkins.SplunkLogService.APPENDER_NAME;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import org.slf4j.LoggerFactory;

public class SplunkLogServiceTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    String host;
    String token;

    public SplunkLogServiceTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        host = System.getProperty("splunk_host", "127.0.0.1");
        token = System.getProperty("splunk_token", "8F8C75E8-ACF1-4BE2-A726-D38A0B79096E");
        System.out.println("use mvn -Dsplunk_token=xx -Dsplunk_host=yy to overwrite default settings");
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
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertTrue("logback logger", SplunkLogService.getLogger() instanceof ch.qos.logback.classic.Logger);
        SplunkJenkinsInstallation.Descriptor config = new SplunkJenkinsInstallation.Descriptor();
        config.sourceName = "jenkins";
        config.sourceTypeName = "jenkins";
        config.indexName = "main";
        config.host = host;
        config.scheme = "https";
        config.httpInputToken = token;
        config.sendMode = "sequential";
        config.delay = 300;
        config.sourceName = "debug";
        config.indexName = "main";
        boolean valid = SplunkLogService.update(config);
        Assert.assertTrue("config is valid", valid);
        int eventNumber = 10;
        for (int i = 0; i < eventNumber; i++) {
            SplunkLogService.getLogger().info("hello world:" + UUID.randomUUID());
        }

        ch.qos.logback.classic.Logger lbkLogger = (ch.qos.logback.classic.Logger) SplunkLogService.getLogger();
        RemoteAppender appender = (RemoteAppender) lbkLogger.getAppender(APPENDER_NAME);
        appender.flush();
        //give some time to send,max wait time is 2 minute
        long timeToWait = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        while (appender.getSentCount() + appender.getErrorCount() < eventNumber) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() > timeToWait) {
                fail("can not send event in time");
            }
        }
        Assert.assertEquals(eventNumber, appender.getSentCount());
    }

    /**
     * Test of getScript method, of class SplunkLogService.
     */
    //@Test
    public void testGetScript() {
        System.out.println("getScript");
        String expResult = "";
        String result = SplunkLogService.getScript();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

}
