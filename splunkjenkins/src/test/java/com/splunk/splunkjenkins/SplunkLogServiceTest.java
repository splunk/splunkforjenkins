package com.splunk.splunkjenkins;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.splunk.splunkjenkins.utils.EventType;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import org.junit.*;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static org.junit.Assert.*;

import org.jvnet.hudson.test.JenkinsRule;

public class SplunkLogServiceTest {
    private static final Logger LOG = Logger.getLogger(SplunkLogServiceTest.class.getName());
    private static final int BATCH_COUNT = 1000;
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable(r.getInstance()));
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
    public void testSend() throws IOException, InterruptedException {
        assertTrue("config is valid",SplunkJenkinsInstallation.get().isValid());
        String line = "127.0.0.1 - admin \"GET /en-US/ HTTP/1.1\"";
        boolean queuedGenericMessage=SplunkLogService.getInstance().send(line, EventType.GENERIC_TEXT);
        assertTrue("should put message in queue", queuedGenericMessage);
        long batchId = System.currentTimeMillis();
        LOG.info("index=" + SplunkConfigUtil.INDEX_NAME + " |spath batch |search batch=" + batchId);
        long initNumber = SplunkLogService.getInstance().getSentCount();
        for (int i = 0; i < BATCH_COUNT; i++) {
            Map data = new HashMap();
            data.put("id", UUID.randomUUID().toString());
            data.put("batch", batchId);
            data.put("number", i);
            boolean queued = SplunkLogService.getInstance().send(data);
            assertTrue("should put the message to queue", queued);
        }
        //give some time to send,max wait time is 2 minute
        long timeToWait = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
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

    }
}
