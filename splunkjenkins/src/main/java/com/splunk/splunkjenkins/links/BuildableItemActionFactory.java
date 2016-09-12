package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.LogEventHelper;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildableItem;
import jenkins.model.TransientActionFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings("unused")
@Extension
public class BuildableItemActionFactory extends TransientActionFactory<BuildableItem> {
    @Override
    public Class<BuildableItem> type() {
        return BuildableItem.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull BuildableItem target) {
        String query = new LogEventHelper.UrlQueryBuilder()
                .putIfAbsent("build_analysis_jenkinsmaster", SplunkJenkinsInstallation.get().getMetadataHost())
                .putIfAbsent("build_analysis_job", target.getUrl()).build();
        return Collections.singleton(new LinkSplunkAction("build_analysis", query, "Splunk"));
    }
}
