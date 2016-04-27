package com.splunk.splunkjenkins;

import jenkins.model.GlobalConfiguration;

public class SplunkConfigUtil {
    public static boolean checkTokenAvailable() {
        String token = System.getProperty("splunk-token");
        if (token == null) {
            System.err.println("please use mvn -Dsplunk-token=eventcollctor-token -Dsplunk-host=ip-address to run the test\n" +
                    "and you can also add this to global vm settings in IDE");
            return false;
        }
        setupSender();
        return true;
    }

    public static boolean setupSender(String host, String token) {
        SplunkJenkinsInstallation config = SplunkJenkinsInstallation.get();
        if (config == null) {
            System.out.println("new config");
            config = new SplunkJenkinsInstallation();
            GlobalConfiguration.all().add(config);
        }
        config.sourceName = "jenkins";
        config.jsonType = "_json";
        config.indexName = "main";
        config.host = host;
        config.useSSL = true;
        config.token = token;
        config.sourceName = "unit_test";
        config.rawEventEnabled = false;
        config.updateCache();
        return config.isValid();
    }

    public static boolean setupSender() {
        String host = System.getProperty("splunk-host", "127.0.0.1");
        String token = System.getProperty("splunk-token");
        System.out.println("host:" + host + " token:" + token);
        return setupSender(host, token);
    }
}
