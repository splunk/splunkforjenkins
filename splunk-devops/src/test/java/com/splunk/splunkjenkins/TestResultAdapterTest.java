package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.BaseTest;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.WithPlugin;

import java.util.concurrent.ExecutionException;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getPostJobSample;

public class TestResultAdapterTest extends BaseTest {
    @LocalData
    @Test
    public void verifyTestNG() throws ExecutionException, InterruptedException {
        String query = "ExampleIntegrationTest| spath | search \"testsuite.testcase{}.classname\"=ExampleIntegrationTest";
        FreeStyleProject project = (FreeStyleProject) j.getInstance().getItem("testng_job1");
        long startTime = System.currentTimeMillis();
        AbstractBuild build = project.scheduleBuild2(0).get();
        verifySplunkSearchResult(query, startTime, 1);
    }

}
