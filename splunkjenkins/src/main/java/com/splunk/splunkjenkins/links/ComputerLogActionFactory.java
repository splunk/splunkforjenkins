package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
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
        return Collections.singleton(new LinkSplunkAction("agent",
                "form.host=" + SplunkJenkinsInstallation.get().getMetadataHost() + "&form.agent_name=" + target.getName(),
                "Splunk"));
    }
}
