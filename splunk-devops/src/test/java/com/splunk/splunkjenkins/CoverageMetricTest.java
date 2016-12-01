package com.splunk.splunkjenkins;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.UUID;

import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static org.junit.Assert.*;

public class CoverageMetricTest extends BaseTest {
    @LocalData
    @Test
    public void getCoberturaReport() throws Exception {
        String jobName = "Cobertura";
        getCoverageReport(jobName, 50);
    }

    @LocalData
    @Test
    public void getCloverReport() throws Exception {
        String jobName = "Clover";
        getCoverageReport(jobName, 50);
    }

    public void getCoverageReport(String jobName, int methodPercentage) throws Exception {
        FreeStyleProject project = (FreeStyleProject) j.getInstance().getItem(jobName);
        String newName= UUID.randomUUID().toString();
        project.renameTo(newName);
        long startTime = System.currentTimeMillis();
        AbstractBuild build = project.scheduleBuild2(0).get();
        String query = "build_url=\"" + build.getUrl() + "\" \"coverage.methods\" >= " + methodPercentage;
        verifySplunkSearchResult(query, startTime, 1);
    }
}