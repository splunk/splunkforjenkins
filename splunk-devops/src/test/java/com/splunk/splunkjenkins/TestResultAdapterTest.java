package com.splunk.splunkjenkins;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;

public class TestResultAdapterTest extends BaseTest {
    @LocalData
    @Test
    public void verifyTestNG() throws ExecutionException, InterruptedException, IOException {
        String query = "ExampleIntegrationTest| spath | search \"testsuite.testcase{}.classname\"=ExampleIntegrationTest";
        FreeStyleProject project = (FreeStyleProject) j.getInstance().getItem("testng_job1");
        String jobName = UUID.randomUUID().toString();
        project.renameTo(jobName);
        long startTime = System.currentTimeMillis();
        Run run = project.scheduleBuild2(0).get();
        verifySplunkSearchResult(query, startTime, 1);
        //verify test_summary.total number
        String buildUrl = run.getUrl();
        query = "type=completed test_summary build_url=\"" + buildUrl + "\"| spath | search test_summary.total=2";
        verifySplunkSearchResult(query, startTime, 1);
    }

}
