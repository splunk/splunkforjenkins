package com.splunk.splunkjenkins;

public class SplunkConfigUtil {

    public boolean setupSender(String host, String token) {
        SplunkJenkinsInstallation.Descriptor config = new SplunkJenkinsInstallation.Descriptor();
        config.sourceName = "jenkins";
        config.sourceTypeName = "_json";
        config.indexName = "main";
        config.host = host;
        config.scheme = "https";
        config.httpInputToken = token;
        config.sendMode = "parallel";
        config.delay = 300;
        config.sourceName = "debug";
        config.indexName = "main";
        SplunkLogService.updateCache(config);
        return !SplunkLogService.config.isInvalid();
    }

    public boolean setupSender() {
        String host = System.getProperty("splunk.host", "127.0.0.1");
        String token = System.getProperty("splunk.token", "215D1911-C019-41BE-9980-09E260A24D65");
        System.out.println("use mvn -Dsplunk.token=xx -Dsplunk.host=yy to overwrite default settings");
        return setupSender(host, token);
    }
}
