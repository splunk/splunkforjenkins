package com.splunk.splunkjenkins;

import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
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

public class StageStepNodesTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();
    private String jobScript = "stage(\"unit-test\"){\n" +
            "    node(\"ci-1\"){\n" +
            "        echo \"hello\"\n" +
            "        echo \"hello world2\"\n" +
            "    }\n" +
            "    node(\"ci-2\"){\n" +
            "      echo \"hello\"\n" +
            "    }\n" +
            "}";

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
    }

    @Test
    public void testStageNodes() throws Exception {
        long startTime = System.currentTimeMillis();
        DumbSlave node = r.createOnlineSlave(new LabelAtom("ci-1"));
        DumbSlave node1 = r.createOnlineSlave(new LabelAtom("ci-2"));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(jobScript));
        WorkflowRun b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse(b1.isBuilding());
        r.assertLogContains("hello", b1);
        assertTrue(b1.getDuration() > 0);
        //check exec_node
        verifySplunkSearchResult("\"stages{}.children{}.exec_node\"=\"" + node.getNodeName() + "\"", startTime, 1);
        verifySplunkSearchResult("\"stages{}.children{}.exec_node\"=\"" + node1.getNodeName() + "\"", startTime, 1);
    }
}
