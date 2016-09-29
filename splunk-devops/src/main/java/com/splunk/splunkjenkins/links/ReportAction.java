package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.Messages;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import hudson.Extension;
import hudson.model.RootAction;

@SuppressWarnings("unused")
@Extension
public class ReportAction implements RootAction {
    @Override
    public String getIconFileName() {
        return Messages.SplunkIconName();
    }

    @Override
    public String getDisplayName() {
        return "Splunk";
    }

    @Override
    public String getUrlName() {
        SplunkJenkinsInstallation instance = SplunkJenkinsInstallation.get();
        return instance.getAppUrlOrHelp() + "overview?overview_jenkinsmaster=" + instance.getMetadataHost();
    }
}
