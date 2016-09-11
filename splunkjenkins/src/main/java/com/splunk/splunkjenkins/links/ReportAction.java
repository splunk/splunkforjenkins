package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import hudson.Extension;
import hudson.model.RootAction;

import static com.splunk.splunkjenkins.Messages.SplunkIconName;

@Extension
public class ReportAction implements RootAction {
    @Override
    public String getIconFileName() {
        return SplunkIconName();
    }

    @Override
    public String getDisplayName() {
        return "Splunk";
    }

    @Override
    public String getUrlName() {
        SplunkJenkinsInstallation instance = SplunkJenkinsInstallation.get();
        return instance.getAppUrlOrHelp() + "overview?host=" + instance.getMetadataHost();
    }
}
