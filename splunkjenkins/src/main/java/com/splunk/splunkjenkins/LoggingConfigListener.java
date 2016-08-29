package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.model.JenkinsJsonConfig;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import com.splunk.splunkjenkins.utils.XstremJsonDriver;
import com.thoughtworks.xstream.XStream;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Describable;
import hudson.model.Job;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.XStream2;
import jenkins.model.Jenkins;

import java.util.HashMap;
import java.util.Map;

import static com.splunk.splunkjenkins.Constants.TAG;
import static com.splunk.splunkjenkins.model.EventType.JENKINS_CONFIG;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getUserName;

import java.util.regex.Pattern;

/**
 * record jenkins config and job changes
 * send config content to splunk
 */

@Extension
public class LoggingConfigListener extends SaveableListener {
    private static final Pattern IGNORED = Pattern.compile("(queue|nodeMonitors|UpdateCenter|global-build-stats|nodes|build)\\.xml$", Pattern.CASE_INSENSITIVE);
    private static final XStream xstream = new XStream2(new XstremJsonDriver());
    private boolean enabled = false;
    private int previousHash = 0;

    @Override
    public void onChange(Saveable saveable, XmlFile file) {
        String configPath = file.getFile().getAbsolutePath();
        String jenkinsHome = Jenkins.getInstance().getRootDir().getPath();
        if (saveable == null || !enabled || !SplunkJenkinsInstallation.loaded || IGNORED.matcher(configPath).find()) {
            return;
        }
        String user = getUserName();
        if ("SYSTEM".equals(user)) {
            return;
        }
        String config = xstream.toXML(saveable);
        int configHash = config.hashCode();
        if (previousHash == configHash) {
            //Save a job can trigger multiple SaveableListener, depends on jenkins versions
            // e.g. AbstractProject.submit may call setters which can trigger save()
            return;
        }
        previousHash = configHash;
        if (configPath.startsWith(jenkinsHome)) {
            configPath = configPath.substring(jenkinsHome.length() + 1);
        }
        if (saveable instanceof Job) {
            Job job = (Job) saveable;
            configPath = job.getUrl() + "config.xml";
        }
        String sourceName = "jenkins://" + configPath;
        Map logInfo = new HashMap<>();
        logInfo.put(TAG, "config_update");
        logInfo.put("config_source", sourceName);
        logInfo.put("user", user);
        if (saveable instanceof Describable) {
            Describable describable = (Describable) saveable;
            logInfo.put("id", describable.getDescriptor().getId());
            String displayName = describable.getDescriptor().getDisplayName();
            logInfo.put("displayName", displayName);
        }
        SplunkLogService.getInstance().send(logInfo, JENKINS_CONFIG, "config_audit");
        SplunkLogService.getInstance().send(new JenkinsJsonConfig(config), JENKINS_CONFIG, sourceName);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
