package com.splunk.splunkjenkins.links;

import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.TransientComputerActionFactory;

import java.util.Collections;
import java.util.Collection;

//TODO enable this
public class ComputerLogActionFactory extends TransientComputerActionFactory {
    @Override
    public Collection<? extends Action> createFor(Computer target) {
        return Collections.singleton(new LinkSplunkAction("agent", "name=" + target.getName(),
                "Splunk"));
    }
}
