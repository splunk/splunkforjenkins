package com.splunk.splunkjenkins;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.FileAppender;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.tasks.Shell;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.fail;

public class TeeConsoleLogFilterTest {

    TeeConsoleLogFilter filter = new TeeConsoleLogFilter();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    public SplunkConfigUtil configUtil = new SplunkConfigUtil();

    @Before
    public void setUp() throws Exception {
        configUtil.setupSender();
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
        p.scheduleBuild(0);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        long timeToWait = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        while (eventCount >= SplunkLogService.getInstance().getSentCount() && (p.getLastBuild() == null || p.getLastBuild().isBuilding())) {
            Thread.sleep(1000); //give some time to start build
            if (System.currentTimeMillis() > timeToWait) {
                fail("can not send event in time");
            }
        }
        System.out.println(p.getLastBuild().isBuilding());

    }
}
