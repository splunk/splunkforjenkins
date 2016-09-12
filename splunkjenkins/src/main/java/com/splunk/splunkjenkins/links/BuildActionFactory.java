package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.LogEventHelper;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import jenkins.model.TransientActionFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings("unused")
@Extension
public class BuildActionFactory extends TransientActionFactory<Run> {
    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull Run target) {
        String query = new LogEventHelper.UrlQueryBuilder()
                .putIfAbsent("build_analysis_jenkinsmaster", SplunkJenkinsInstallation.get().getMetadataHost())
                .putIfAbsent("build_analysis_job", target.getUrl())
                .putIfAbsent("build_analysis_build", target.getNumber() + "")
                .build();
        return Collections.singleton(new LinkSplunkAction("build_analysis", query, "Splunk"));
    }
}
