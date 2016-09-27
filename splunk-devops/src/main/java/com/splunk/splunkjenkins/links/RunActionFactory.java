package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.LogEventHelper;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.TransientActionFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings("unused")
@Extension
public class RunActionFactory extends TransientActionFactory<Run> {
    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull Run target) {
        Job job = target.getParent();
        File junitFile = new File(target.getRootDir(), "junitResult.xml");
        if (junitFile.exists() || job.getClass().getName().startsWith("hudson.maven.")) {
            String query = new LogEventHelper.UrlQueryBuilder()
                    .putIfAbsent("host", SplunkJenkinsInstallation.get().getMetadataHost())
                    .putIfAbsent("job", job.getUrl())
                    .putIfAbsent("build", target.getNumber() + "")
                    .build();
            return Collections.singleton(new LinkSplunkAction("test_analysis", query, "Splunk"));
        }
        String query = new LogEventHelper.UrlQueryBuilder()
                .putIfAbsent("build_analysis_jenkinsmaster", SplunkJenkinsInstallation.get().getMetadataHost())
                .putIfAbsent("build_analysis_job", job.getUrl())
                .putIfAbsent("build_analysis_build", target.getNumber() + "")
                .build();
        return Collections.singleton(new LinkSplunkAction("build_analysis", query, "Splunk"));
    }
}
