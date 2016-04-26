package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SplunkConfig {
    private static Logger LOG = Logger.getLogger(SplunkConfig.class.getName());
    protected URI url;
    protected String authToken;
    protected EventMetaData eventMetaData;
    protected String postActionScript;

    public SplunkConfig(SplunkJenkinsInstallation.Descriptor descriptor) {
        String splunkLink = descriptor.scheme + "://" + descriptor.host + ":" + descriptor.httpInputPort
                + "/services/collector/event/1.0";
        try {
            this.url = new URI(splunkLink);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "invalid splunk url " + splunkLink);
        }
        this.authToken = "Splunk " + descriptor.httpInputToken;
        this.eventMetaData = new EventMetaData(descriptor.indexName, descriptor.sourceName,
                descriptor.sourceTypeName, descriptor.host);
        if (descriptor.scriptPath != null) {
            try {
                postActionScript = IOUtils.toString(new URL(descriptor.scriptPath));
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "can not read file " + descriptor.scriptPath);
                //file was removed from jenkins, just ignore
            }
        } else {
            postActionScript = null;
        }
    }

    public boolean isInvalid() {
        return url == null;
    }

    public String getScript() {
        return this.postActionScript;
    }
}
