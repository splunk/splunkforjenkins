package com.splunk.splunkjenkins.links;

import com.splunk.splunkjenkins.Messages;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import hudson.Extension;
import hudson.model.ManagementLink;

@SuppressWarnings("unused")
@Extension
public class HealthLinkAction extends ManagementLink {
    @Override
    public String getIconFileName() {
        return Messages.SplunkIconName();
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
