package com.splunk.splunkjenkins;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import org.junit.*;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static org.junit.Assert.*;

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

    @Before
    public void setUp() {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
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
        String line = "127.0.0.1 - admin \"GET /en-US/ HTTP/1.1\"";
        SplunkLogService.getInstance().send(line.getBytes());
        int eventNumber = 1;
        long batchId = System.currentTimeMillis();
        System.err.println("index=main |spath batch |search batch=" + batchId);
        long initNumber = SplunkLogService.getInstance().getSentCount();
        for (int i = 0; i < eventNumber; i++) {
            Map data = new HashMap();
            data.put("id", UUID.randomUUID().toString());
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

    }
}
