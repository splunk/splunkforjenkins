package com.splunk.splunkjenkins;

import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;

import java.util.HashMap;
import java.util.Map;

import static com.splunk.splunkjenkins.Constants.CATEGORY;

/**
 * audit config and job changes
 * <p>
 * send xml file to splunk
 */

//@Extension
public class SplunkConfigListener extends
        SaveableListener {
    @Override
    public void onChange(Saveable o, XmlFile file) {
        super.onChange(o, file);
        Map logInfo = new HashMap<>();
        logInfo.put(CATEGORY, "config_file");
        logInfo.put("xmlfile", file.toString());
        SplunkLogService.getInstance().send(logInfo);
    }
}
