package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.LogEventHelper;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.TransientComputerActionFactory;
import jnr.ffi.annotations.Encoding;

import java.util.Collections;
import java.util.Collection;

//TODO add agent page and update link
@Extension
public class ComputerLogActionFactory extends TransientComputerActionFactory {
    @Override
    public Collection<? extends Action> createFor(Computer target) {
        String query = new LogEventHelper.UrlQueryBuilder()
                .putIfAbsent("master", SplunkJenkinsInstallation.get().getMetadataHost())
                .putIfAbsent("slave", target.getName())
                .build();
        return Collections.singleton(new LinkSplunkAction("jenkins_slave", query, "Splunk"));
    }
}
