package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.LogEventHelper;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

@Extension
public class BuildActionFactory extends TransientActionFactory<AbstractBuild> {
    @Override
    public Class<AbstractBuild> type() {
        return AbstractBuild.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull AbstractBuild target) {
        String query = new LogEventHelper.UrlQueryBuilder()
                .putIfAbsent("build_analysis_jenkinsmaster", SplunkJenkinsInstallation.get().getMetadataHost())
                .putIfAbsent("build_analysis_job", target.getUrl())
                .putIfAbsent("build_analysis_build", target.getNumber() + "")
                .build();
        return Collections.singleton(new LinkSplunkAction("build_analysis", query, "Splunk"));
    }
}
