package com.splunk.splunkjenkins;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;

/**
 * audit config and job changes
 *
 * @TODO send xml file to splunk?
 */

//@Extension
public class SplunkConfigListener extends
        SaveableListener {
    @Override
    public void onChange(Saveable o, XmlFile file) {
        super.onChange(o, file);
    }
}
