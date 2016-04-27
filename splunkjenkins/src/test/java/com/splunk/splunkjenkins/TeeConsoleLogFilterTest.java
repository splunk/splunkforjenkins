package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.tasks.Shell;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TeeConsoleLogFilterTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
    }

    @Test
    public void decorateLogger() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        JDK jdk = new JDK("test", "/opt/jdk");
        r.jenkins.getJDKs().add(jdk);
        p.setJDK(jdk);
        CaptureEnvironmentBuilder captureEnvironment = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(captureEnvironment);
        p.getBuildersList().add(new Shell("echo effective PATH=$PATH"));
        long eventCount = SplunkLogService.getInstance().getSentCount();
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        long timeToWait = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        while (eventCount >= SplunkLogService.getInstance().getSentCount() && (p.getLastBuild() == null || p.getLastBuild().isBuilding())) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() > timeToWait) {
                fail("can not send event in time");
            }
        }

    }
}
