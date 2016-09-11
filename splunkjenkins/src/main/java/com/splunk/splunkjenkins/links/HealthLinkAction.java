package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import hudson.Extension;
import hudson.model.ManagementLink;

import static com.splunk.splunkjenkins.Messages.SplunkIconName;

@Extension
public class HealthLinkAction extends ManagementLink {
    @Override
    public String getIconFileName() {
        return SplunkIconName();
    }

    @Override
    public String getDisplayName() {
        return "Jenkins Health";
    }

    @Override
    public String getUrlName() {
        SplunkJenkinsInstallation instance = SplunkJenkinsInstallation.get();
        return instance.getAppUrlOrHelp() + "jenkins_health?form.hostname=" + instance.getMetadataHost();
    }
}
