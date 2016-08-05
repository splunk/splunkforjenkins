package com.splunk.splunkjenkins;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.splunk.splunkjenkins.model.EventType;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import org.junit.*;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static com.splunk.splunkjenkins.SplunkConfigUtil.waitForSplunkSearchResult;
import static org.junit.Assert.*;

import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public class SplunkLogServiceTest {
    private static final Logger LOG = Logger.getLogger(SplunkLogServiceTest.class.getName());
    private static final int BATCH_COUNT = 1000;
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void setUp() {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
    }

    @After
    public void tearDown() {
        SplunkLogService.getInstance().stopWorker();
        SplunkLogService.getInstance().releaseConnection();
    }

    /**
     * Test of update method, of class SplunkLogService.
     */
    @Test
    public void testLogServiceSendMethod() throws IOException, InterruptedException {
        LOG.info("running test SplunkLogServiceTest testLogServiceSendMethod");
        assertTrue("config should be valid", SplunkJenkinsInstallation.get().isValid());
        String line = "127.0.0.1 - admin \"GET /en-US/ HTTP/1.1\"";
        boolean queuedGenericMessage = SplunkLogService.getInstance().send(line, EventType.GENERIC_TEXT);
        assertTrue("should put message in queue", queuedGenericMessage);
        long timestamp = System.currentTimeMillis();
        String query = "index=" + SplunkConfigUtil.INDEX_NAME + " |spath batch|where batch=" + timestamp;
        LOG.info(query);
        long initNumber = SplunkLogService.getInstance().getSentCount();
        for (int i = 0; i < BATCH_COUNT; i++) {
            Map data = new HashMap();
            data.put("id", UUID.randomUUID().toString());
            data.put("batch", timestamp);
            data.put("number", i);
            boolean queued = SplunkLogService.getInstance().send(data);
            assertTrue("should put the message to queue", queued);
        }
        //give some time to send,max wait time is 3 minute
        long timeToWait = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3);
        while (SplunkLogService.getInstance().getSentCount() < (BATCH_COUNT + initNumber)) {
            Thread.sleep(1000);
            long queueSize = SplunkLogService.getInstance().getQueueSize();
            long sentCount = SplunkLogService.getInstance().getSentCount();
            long remaining = BATCH_COUNT + initNumber - sentCount;
            LOG.fine("queue size:" + queueSize + " sent:" + sentCount);
            if (System.currentTimeMillis() > timeToWait) {
                fail("can not send events in time, remaining " + remaining);
            }
        }
        int expected = BATCH_COUNT;
        int eventCount = waitForSplunkSearchResult(query, timestamp, expected);
        assertEquals(expected, eventCount);
    }
}
