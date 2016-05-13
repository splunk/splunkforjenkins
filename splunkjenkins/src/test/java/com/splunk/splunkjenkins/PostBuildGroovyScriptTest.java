package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;

import java.util.Map;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static org.junit.Assert.assertNotNull;

public class PostBuildGroovyScriptTest {
    private static final Logger LOG=Logger.getLogger(PostBuildGroovyScriptTest.class.getName());
    private FreeStyleProject project;
    @Rule
    public JenkinsRule j = new JenkinsRule();
    public static Map buildEvent = null;

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable(j.getInstance()));
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
    @After
    public void tearDown() {
        SplunkLogService.getInstance().stopWorker();
        SplunkLogService.getInstance().releaseConnection();
    }
    /**
     * We have a PostBuildGroovyScriptTest.zip contains a junit result file
     */
    @Test
    public void postBuildJunitResult() throws Exception {
        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        assertNotNull("groovy script should set the item", buildEvent);
        LOG.info("event is " + buildEvent);
        Assert.assertTrue("product is splunk", "splunk".equals(((Map) buildEvent.get("metadata")).get("product")));
    }
}
