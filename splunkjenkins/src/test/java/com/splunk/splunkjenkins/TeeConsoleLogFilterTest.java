package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static com.splunk.splunkjenkins.SplunkConfigUtil.waitForSplunkSearchResult;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TeeConsoleLogFilterTest {
    private static final Logger LOG = Logger.getLogger(TeeConsoleLogFilterTest.class.getName());
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
    }

    @After
    public void tearDown() {
        SplunkLogService.getInstance().stopWorker();
        SplunkLogService.getInstance().releaseConnection();
    }

    @Test
    public void decorateLogger() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("console_" + UUID.randomUUID());
        CaptureEnvironmentBuilder captureEnvironment = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(captureEnvironment);
        p.getBuildersList().add(new Shell("echo $PATH;echo $$"));
        long eventCount = SplunkLogService.getInstance().getSentCount();
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        long timeToWait = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        while (eventCount >= SplunkLogService.getInstance().getSentCount() && (p.getLastBuild() == null || p.getLastBuild().isBuilding())) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() > timeToWait) {
                LOG.fine("queue size:" + SplunkLogService.getInstance().getQueueSize());
                fail("can not send event in time");
            }
        }
        String query = "index=" + SplunkConfigUtil.INDEX_NAME + " source=" + b.getUrl() + "console";
        int expected = 5;
        int count = waitForSplunkSearchResult(query, b.getTimeInMillis(), expected);
        assertEquals(expected, count);
    }
}
