package com.splunk.splunkjenkins;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;

import java.util.Map;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static org.junit.Assert.assertNotNull;

public class PostBuildGroovyScriptTest {
    private FreeStyleProject project;
    @Rule
    public JenkinsRule j = new JenkinsRule();
    public static Map buildEvent = null;

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
        String groovyScript = "def metadata = [:]\n" +
                "metadata[\"product\"]=\"splunk\"\n" +
                "def result = [\n" +
                "        \"build_url\": build.url,\n" +
                "        \"metadata\" : metadata,\n" +
                "        \"testsuite\": getJunitReport()\n" +
                "]\n" +
                "com.splunk.splunkjenkins.PostBuildGroovyScriptTest.buildEvent=result";
        SplunkJenkinsInstallation.get().scriptContent = groovyScript;
        SplunkJenkinsInstallation.get().updateCache();
        //create a job
        project = j.createFreeStyleProject("simple");
        project.getBuildersList().add(new TouchBuilder());
    }

    /**
     * We have a PostBuildGroovyScriptTest.zip contains a junit result file
     */
    @Test
    public void postBuildJunitResult() throws Exception {
        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        assertNotNull("groovy script should set the item", buildEvent);
        System.out.printf("event is " + buildEvent);
        Assert.assertTrue("product is splunk", "splunk".equals(((Map) buildEvent.get("metadata")).get("product")));
    }
}
