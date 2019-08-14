package com.splunk.splunkjenkins;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.tasks.Shell;
import org.junit.Test;

import java.util.UUID;

import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;

public class SplunkSendJsonFileTest extends BaseTest {

    private Run createBuild(String textContent, String fileName) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("publisher_test" + UUID.randomUUID());
        project.getBuildersList().add(new Shell("cat > " + fileName + " <<'EOF'\n" + textContent + "\nEOF"));
        SplunkArtifactNotifier splunkArtifactNotifier = new SplunkArtifactNotifier("*", null, false, false, "10MB");
        project.getPublishersList().add(splunkArtifactNotifier);
        Run build = project.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);
        return build;
    }


    @Test
    public void testSendJsonFile() throws Exception {
        String name = "client_" + UUID.randomUUID();
        String jsonText = "{\n\"name\":\n\"" + name + "\"\n}";
        String query = "index=" + SplunkConfigUtil.INDEX_NAME + " name=" + name;
        Run build = createBuild(jsonText, "user.json");
        verifySplunkSearchResult(query, build.getTimeInMillis(), 1);
    }

    @Test
    public void testSendTextFile() throws Exception {
        String name = "client_" + UUID.randomUUID();
        String jsonText = "line1: " + name + "\nline2: " + name;
        String query = "index=" + SplunkConfigUtil.INDEX_NAME + " " + name;
        Run build = createBuild(jsonText, "user.txt");
        verifySplunkSearchResult(query, build.getTimeInMillis(), 2);
    }
}
