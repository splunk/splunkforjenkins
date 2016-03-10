package com.splunk.splunkjenkins;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.FileAppender;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.tasks.Shell;
import java.io.File;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.slf4j.LoggerFactory;

public class TeeConsoleLogFilterTest {

    TeeConsoleLogFilter filter = new TeeConsoleLogFilter();
    @Rule
    public JenkinsRule r = new JenkinsRule();
    File logFile;

    @Before
    public void setUp() throws Exception {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.splunk.splunkjenkins");
        rootLogger.setLevel(Level.DEBUG);
        FileAppender appender = new FileAppender();
        logFile = File.createTempFile("tee_output", "log");
        logFile.deleteOnExit();
        appender.setFile(logFile.getAbsolutePath());
    }

    @After
    public void tearDown() throws Exception {
        String logText = IOUtils.toString(logFile.toURI());
        System.out.println("logifle is " + logFile + "\nlogText is:\n" + logText);
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
        p.scheduleBuild(0);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        int count = 0;
        while (count < 20 && (p.getLastBuild() == null || p.getLastBuild().isBuilding())) {
            Thread.sleep(1000); //give some time to start build
            count++;
        }
        System.out.println(p.getLastBuild().isBuilding());

    }
}
