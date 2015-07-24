package test.jenkins.plugins.splunkins;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

/**
 * Created by djenkins on 7/23/15.
 */
public class SplunkinsNotifierTest extends JenkinsRule {

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testPerform() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo hello"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        System.out.println(build.getDisplayName()+" completed");

        // TODO: change this to use HtmlUnit
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("+ echo hello"));

    }

    @Test
    public void testGetBuildLog() throws Exception {
        assertTrue(true);

    }

    @Test
    public void testGetBuildEnvVars() throws Exception {

    }

    @Test
    public void testCollectXmlFiles() throws Exception {

    }

    @Test
    public void testGetRequiredMonitorService() throws Exception {

    }
}