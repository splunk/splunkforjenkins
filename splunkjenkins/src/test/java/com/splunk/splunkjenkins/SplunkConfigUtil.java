package com.splunk.splunkjenkins;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.splunk.*;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SplunkConfigUtil {
    public static final String INDEX_NAME = System.getProperty("splunk-index", "plugin_sandbox");
    private static final Logger LOG = Logger.getLogger(SplunkLogServiceTest.class.getName());
    private static final String COLLECTOR_NAME = "splunkins_unittest";
    private static final String ENDPOINT = "/servicesNS/admin/search/data/inputs/http/";
    public static String TOKEN = null;
    private static Gson gson = new Gson();
    private static Service splunkService;

    public static synchronized Service getSplunkServiceInstance() {
        if (splunkService != null) {
            return splunkService;
        }

        String host = System.getProperty("splunk-host", "127.0.0.1");
        ServiceArgs serviceArgs = new ServiceArgs();
        serviceArgs.setSSLSecurityProtocol(SSLSecurityProtocol.TLSv1);
        serviceArgs.setUsername(System.getProperty("splunk-username", "admin"));
        serviceArgs.setPassword(System.getProperty("splunk-passwd", "changeme"));
        serviceArgs.setHost(host);
        serviceArgs.setPort(8089);
        // Create a Service instance and log in with the argument map
        splunkService = Service.connect(serviceArgs);
        //create index if not exists

        //splunk service used URLConnection but not set timeout
        System.setProperty("sun.net.client.defaultReadTimeout", "90000");
        System.setProperty("sun.net.client.defaultConnectTimeout", "90000");
        return splunkService;

    }

    public static synchronized boolean checkTokenAvailable(Jenkins jenkins) {
        String token = System.getProperty("splunk-token");
        String host = "127.0.0.1";
        try {
            host = System.getProperty("splunk-host", InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            //ignore
        }
        boolean useAutoConfig = Boolean.getBoolean("splunk-token-setup");
        if (token == null && useAutoConfig) {
            Service service = getSplunkServiceInstance();
            try {
                service.getIndexes().create(INDEX_NAME);
            } catch (com.splunk.HttpException ex) {
                int statusCode = ex.getStatus();
                if (!(statusCode == 409 || statusCode == 201)) {
                    throw ex;
                }
            }
            //enable logging endpoint
            service.post(ENDPOINT + "http", ImmutableMap.of("disabled", (Object) "0"));
            ResponseMessage response;
            try {
                response = service.get(ENDPOINT + COLLECTOR_NAME, ImmutableMap.of("output_mode", (Object) "json"));
            } catch (com.splunk.HttpException e) {
                if (e.getStatus() != 404) {
                    throw e;
                }
                //create token because it doesn't exist
                Map<String, Object> args = new HashMap<String, Object>();
                args.put("output_mode", "json");
                args.put("name", COLLECTOR_NAME);
                args.put("index", INDEX_NAME);
                args.put("indexes", "main," + INDEX_NAME);
                args.put("description", "test http event collector");
                response = service.post(ENDPOINT + "http", args);
                System.err.println(response);
                response = service.get(ENDPOINT + COLLECTOR_NAME, ImmutableMap.of("output_mode", (Object) "json"));
            }
            try {
                String tokenMessage = IOUtils.toString(response.getContent());
                SplunkResponse result = gson.fromJson(tokenMessage, SplunkResponse.class);
                token = result.getFirst("token");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (token == null) {
            System.err.println("please use mvn -Dsplunk-token=eventcollctor-token -Dsplunk-host=ip-address to run the test\n" +
                    "and you can also putIfAbsent this to global vm settings in IDE");
            return false;
        }
        TOKEN = token;
        return setupSender(jenkins, host, token);
    }

    public static boolean setupSender(Jenkins jenkins, String host, String token) {
        LOG.info("host:" + host + " token:" + token);
        SplunkJenkinsInstallation config = jenkins.getExtensionList(GlobalConfiguration.class).get(SplunkJenkinsInstallation.class);
        if (config == null) {
            LOG.severe("empty config, create a new config");
            config = new SplunkJenkinsInstallation(false);
            jenkins.getExtensionList(GlobalConfiguration.class).add(0, config);
        }
        String metadataConfig = "";
        try (InputStream input = SplunkConfigUtil.class.getClassLoader().getResourceAsStream("splunk_metadata.properties")) {
            metadataConfig = IOUtils.toString(input);
            metadataConfig += "\nindex=" + INDEX_NAME;
        } catch (IOException e) {
            e.printStackTrace();
        }
        config.setHost(host);
        config.setPort(8088);
        config.setUseSSL(true);
        config.setToken(token);
        config.setRawEventEnabled(false);
        config.setEnabled(true);
        config.setMetaDataConfig(metadataConfig);
        config.updateCache();
        config.save();
        return config.isValid();
    }

    public static int waitForSplunkSearchResult(String query, long startTime, int expected) {
        JobArgs jobargs = new JobArgs();
        jobargs.setExecutionMode(JobArgs.ExecutionMode.BLOCKING);
        jobargs.put("earliest_time", startTime / 1000);
        if (!query.startsWith("search")) {
            query = "search " + query;
        }
        query = query + "|head " + expected;
        LOG.info("running query " + query);
        int eventCount = 0;
        for (int i = 0; i < 6; i++) {
            com.splunk.Job job = SplunkConfigUtil.getSplunkServiceInstance().getJobs().create(query, jobargs);
            eventCount = job.getEventCount();
            if (eventCount < expected) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        return eventCount;
    }
}
