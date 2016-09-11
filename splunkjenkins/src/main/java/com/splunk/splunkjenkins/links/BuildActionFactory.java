package com.splunk.splunkjenkins.links;

import hudson.Extension;
import hudson.Util;
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
        return Collections.singleton(new LinkSplunkAction("build_analysis",
                "job=" + Util.encode(target.getProject().getUrl())
                        + "&build=" + target.getNumber(), "Splunk"));
    }
}
