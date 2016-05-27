package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import jenkins.model.GlobalConfiguration;

import java.util.HashMap;
import java.util.Map;

import static com.splunk.splunkjenkins.Constants.TAG;
import static com.splunk.splunkjenkins.utils.EventType.XML_CONFIG;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getUserName;

import java.util.regex.Pattern;

/**
 * audit config and job changes
 * <p>
 * send xml file to splunk
 */

@Extension
public class SplunkConfigListener extends SaveableListener {
    private static final Pattern IGNORED = Pattern.compile("(queue|nodeMonitors|UpdateCenter|global-build-stats|nodes)\\.xml$", Pattern.CASE_INSENSITIVE);

    @Override
    public void onChange(Saveable o, XmlFile file) {
        super.onChange(o, file);
        //when config splunkins plugin will also trigger the listener, but it hasn't been configure yet
        //use getDynamic will not trigger loading
        SplunkJenkinsInstallation globalConfig = (SplunkJenkinsInstallation) GlobalConfiguration.all().getDynamic(SplunkJenkinsInstallation.class.getName());

        if (globalConfig == null || (!globalConfig.isMonitoringConfig()) || IGNORED.matcher(file.getFile().getName()).find()) {
            return;
        }
        Map logInfo = new HashMap<>();
        logInfo.put(TAG, "xmlconfig");
        logInfo.put("file", file.getFile().getAbsolutePath());
        logInfo.put("user", getUserName());
        logInfo.put("config", o);
        SplunkLogService.getInstance().send(logInfo,XML_CONFIG);
    }
}
