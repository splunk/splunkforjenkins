package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.tasks.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.UUID;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.SplunkConfigUtil.INDEX_NAME;
import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static org.junit.Assert.*;

public class SplunkArchiveFileTest {
    public static Object result = null;
    private static final Logger logger = Logger.getLogger(SplunkArchiveFileTest.class.getName());
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

    public String buildWithScript(String groovyScript) throws Exception {
        Label label = r.jenkins.getLabel("filetest");
        r.createOnlineSlave(label);
        FreeStyleProject project = r.createFreeStyleProject("verify_archive" + UUID.randomUUID());
        CaptureEnvironmentBuilder captureEnvironment = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(captureEnvironment);
        project.getBuildersList().add(new Shell("ps -ef >process_list.txt"));
        project.setAssignedLabel(label);
        SplunkJenkinsInstallation.get().setScriptContent(groovyScript);
        SplunkJenkinsInstallation.get().updateCache();
        long start_time = System.currentTimeMillis();
        FreeStyleBuild build = r.buildAndAssertSuccess(project);
        String buildUrl = build.getUrl();
        int expected = 5;
        String query = "search index=" + INDEX_NAME
                + " source=\"" + buildUrl + "process_list.txt\"";
        logger.info(query);
        verifySplunkSearchResult(query, start_time, expected);
        return build.getUrl();
    }

    @Test
    public void testUploadFromSlave() {
        String script = "println \"uploading files\"\n" +
                "archive(\"*.txt\",\"\",true,\"0\")";
        try {
            buildWithScript(script);
        } catch (Exception e) {
            e.printStackTrace();
            fail("upload failed");
        }
    }

    @Test
    public void testUploadFromMaster() throws Exception {
        result = null;
        String script = "println \"uploading files\"\n" +
                "def sentCount=archive(\"*.txt\",\"\",false,\"10MB\");" +
                "com.splunk.splunkjenkins.SplunkArchiveFileTest.result=sentCount;" +
                "println \"send \"+sentCount";
        buildWithScript(script);
        assertNotNull("archive file completed", result);
    }
}
