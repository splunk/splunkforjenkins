package com.splunk.splunkjenkins;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.UUID;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;

import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static org.junit.Assert.*;

public class SplunkinsDslVariableTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
    }

    @Test
    public void testDslScript() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        String eventSource = "test_dsl_var_" + UUID.randomUUID().toString();
        p.setDefinition(new CpsFlowDefinition("node { splunkins.send('hello', '" + eventSource + "') }"));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse(b1.isBuilding());
        assertTrue(b1.getDuration() > 0);
        String query = "index=" + SplunkConfigUtil.INDEX_NAME + " source=" + eventSource;
        int expected = 1;
        verifySplunkSearchResult(query, b1.getTimeInMillis(), expected);
    }
}