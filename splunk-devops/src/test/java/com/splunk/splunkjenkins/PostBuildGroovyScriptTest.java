package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.model.JunitTestCaseGroup;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.junit.JUnitResultArchiver;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.SplunkConfigUtil.checkTokenAvailable;
import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PostBuildGroovyScriptTest {
    private static final Logger LOG = Logger.getLogger(PostBuildGroovyScriptTest.class.getName());
    //test cases are stored in zip file named using project name
    private final String simple_project = "simple_tests";
    private final int simple_project_cases = 9;
    private final String large_project = "large_tests";
    private final int large_project_cases = 95358;
    public static Map buildEvent = null;
    public static List<JunitTestCaseGroup> suites = null;
    @Rule
    public JenkinsRule j = new JenkinsRule();
    private FreeStyleProject project;
    private FreeStyleBuild build;

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeTrue(checkTokenAvailable());
        LOG.log(Level.INFO, "jenkins home {0}", j.getInstance().getRootDir());
        SplunkJenkinsInstallation.get().updateCache();
    }

    @After
    public void tearDown() {
        SplunkLogService.getInstance().stopWorker();
        SplunkLogService.getInstance().releaseConnection();
    }

    /**
     * We have a PostBuildGroovyScriptTest.zip contains a junit result file
     */
    @LocalData
    @Test
    public void postBuildJunitResult() throws Exception {
        String groovyScript = "def metadata = [:]\n" +
                "metadata[\"product\"]=\"splunk\"\n" +
                "def result = [\n" +
                "        \"build_url\": build.url,\n" +
                "        \"metadata\" : metadata,\n" +
                "        \"testsuite\": getJunitReport(10)\n" +
                "]\n" +
                "com.splunk.splunkjenkins.PostBuildGroovyScriptTest.buildEvent=result";
        createJob(simple_project, groovyScript);
        assertNotNull("groovy script should set the item", buildEvent);
        LOG.info("event is " + buildEvent);
        List<JunitTestCaseGroup> suites = (List<JunitTestCaseGroup>) buildEvent.get("testsuite");
        assertEquals(1, suites.size());
        assertEquals(simple_project_cases, suites.get(0).getTotal());
        Assert.assertTrue("product is splunk", "splunk".equals(((Map) buildEvent.get("metadata")).get("product")));
    }

    @LocalData
    @Test
    public void paginationResult() throws Exception {
        String report_id = UUID.randomUUID().toString();
        int pageSize = 49;
        String groovyScript = "def report_id=\"" + report_id + "\"\n" +
                "def suites=getJunitReport(" + pageSize + ")\n" +
                "com.splunk.splunkjenkins.PostBuildGroovyScriptTest.suites=suites\n" +
                "suites.each{ report->\n" +
                "\tsend( [\"report_id\":report_id, \"report\":report] );\n" +
                "}";
        createJob(large_project, groovyScript);
        int remained = (large_project_cases % pageSize == 0) ? 0 : 1;
        int pageCount = large_project_cases / pageSize + remained;
        assertEquals(pageCount, suites.size());
        int total = 0;
        for (JunitTestCaseGroup group : suites) {
            total += group.getTotal();
        }
        assertEquals(large_project_cases, total);
        //check splunk event
        String query = "index=" + SplunkConfigUtil.INDEX_NAME + " |spath report_id |search report_id=" + report_id;
        verifySplunkSearchResult(query, build.getTimeInMillis(), pageCount);
    }

    private void createJob(String projectName, String groovyScript) throws IOException, ExecutionException, InterruptedException {
        SplunkJenkinsInstallation.get().setScriptContent(groovyScript);
        SplunkJenkinsInstallation.get().updateCache();
        project = j.createFreeStyleProject(projectName);
        addJUnitResultArchiver(project);
        build = project.scheduleBuild2(0).get();
    }

    private void addJUnitResultArchiver(FreeStyleProject project) {
        JUnitResultArchiver archiver = new JUnitResultArchiver("*.xml");
        project.getPublishersList().add(archiver);
        project.getBuildersList().add(new TouchBuilder());
    }
}
