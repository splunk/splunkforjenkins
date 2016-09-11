package com.splunk.splunkjenkins.links;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

@Extension
public class ProjectActionFactory extends TransientActionFactory<AbstractProject> {
    @Override
    public Class<AbstractProject> type() {
        return AbstractProject.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull AbstractProject target) {
        return Collections.singleton(new LinkSplunkAction("build_analysis",
                "job=" + Util.encode(target.getUrl()), "Splunk"));
    }
}
