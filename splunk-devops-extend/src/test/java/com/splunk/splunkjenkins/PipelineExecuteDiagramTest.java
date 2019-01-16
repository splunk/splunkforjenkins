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

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PipelineExecuteDiagramTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();
    private String jobScript = "properties([splunkinsJobOption(enableDiagram: true)])\n" +
            "stage(\"unit-test\"){\n" +
            "    node{\n" +
            "        echo \"hello\"\n" +
            "        echo \"hello world2\"\n" +
            "    }\n" +
            "}";

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
    }

    @Test
    public void testExecuteDiagram() throws Exception {
        long startTime = System.currentTimeMillis();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "testExecuteDiagram");
        p.setDefinition(new CpsFlowDefinition(jobScript));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse(b1.isBuilding());
        r.assertLogContains("hello", b1);
        assertTrue(b1.getDuration() > 0);
        //check exec_node
        verifySplunkSearchResult("source=" + b1.getUrl() + "graphviz", startTime, 1);
    }
}
