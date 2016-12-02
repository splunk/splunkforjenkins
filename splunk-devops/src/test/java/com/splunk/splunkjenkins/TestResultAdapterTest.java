package com.splunk.splunkjenkins;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.concurrent.ExecutionException;

import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;

public class TestResultAdapterTest extends BaseTest {
    @LocalData
    @Test
    public void verifyTestNG() throws ExecutionException, InterruptedException {
        String query = "ExampleIntegrationTest| spath | search \"testsuite.testcase{}.classname\"=ExampleIntegrationTest";
        FreeStyleProject project = (FreeStyleProject) j.getInstance().getItem("testng_job1");
        long startTime = System.currentTimeMillis();
        project.scheduleBuild2(0).get();
        verifySplunkSearchResult(query, startTime, 1);
    }

}
