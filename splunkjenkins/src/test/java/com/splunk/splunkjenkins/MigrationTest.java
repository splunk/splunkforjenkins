package com.splunk.splunkjenkins;

import hudson.util.XStream2;
import org.junit.Assert;
import org.junit.Test;

public class MigrationTest {

    @Test
    public void testMigration() {
        String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<com.splunk.splunkjenkins.SplunkJenkinsInstallation_-Descriptor plugin=\"splunkjenkins@0.5.0\">\n" +
                "  <installations class=\"com.splunk.splunkjenkins.SplunkJenkinsInstallation-array\"/>\n" +
                "  <globalConfigTitle>Splunk-Jenkins: Splunk Server Configuration</globalConfigTitle>\n" +
                "  <host>localhost</host>\n" +
                "  <scheme>https</scheme>\n" +
                "  <httpInputToken>FAAC5EB2-D313-4C76-9965-379B76DA72CE</httpInputToken>\n" +
                "  <httpInputPort>8088</httpInputPort>\n" +
                "  <maxEventsBatchCount>3</maxEventsBatchCount>" +
                "</com.splunk.splunkjenkins.SplunkJenkinsInstallation_-Descriptor>";
        XStream2 xStream2 = new XStream2();
        SplunkJenkinsInstallation.Descriptor desc = (SplunkJenkinsInstallation.Descriptor) xStream2.fromXML(xml);
        Assert.assertEquals("FAAC5EB2-D313-4C76-9965-379B76DA72CE", desc.httpInputToken);
    }
}
