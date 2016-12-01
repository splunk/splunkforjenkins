package com.splunk.splunkjenkins;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.tasks.Shell;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;

import java.util.UUID;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.SplunkConfigUtil.INDEX_NAME;
import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class SplunkArchiveFileTest extends BaseTest {
    public static Object result = null;
    private static final Logger logger = Logger.getLogger(SplunkArchiveFileTest.class.getName());

    public String buildWithScript(String groovyScript) throws Exception {
        Label label = j.jenkins.getLabel("filetest");
        j.createOnlineSlave(label);
        FreeStyleProject project = j.createFreeStyleProject("verify_archive" + UUID.randomUUID());
        CaptureEnvironmentBuilder captureEnvironment = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(captureEnvironment);
        project.getBuildersList().add(new Shell("ps -ef >process_list.txt"));
        project.setAssignedLabel(label);
        SplunkJenkinsInstallation.get().setScriptContent(groovyScript);
        SplunkJenkinsInstallation.get().updateCache();
        long start_time = System.currentTimeMillis();
        FreeStyleBuild build = j.buildAndAssertSuccess(project);
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
