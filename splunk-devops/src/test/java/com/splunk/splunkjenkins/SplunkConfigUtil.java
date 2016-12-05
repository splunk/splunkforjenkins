package com.splunk.splunkjenkins;

import com.google.common.collect.ImmutableMap;
import shaded.splk.com.google.gson.Gson;
import com.splunk.*;
import com.splunk.splunkjenkins.model.*;
import com.splunk.splunkjenkins.model.EventType;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class SplunkConfigUtil {
    public static final String INDEX_NAME = System.getProperty("splunk-index", "plugin_sandbox");
    private static final Logger LOG = Logger.getLogger(SplunkLogServiceTest.class.getName());
    private static final String COLLECTOR_NAME = "splunkins_unittest";
    private static final String ENDPOINT = "/servicesNS/admin/search/data/inputs/http/";
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
        serviceArgs.setPort(Integer.parseInt(System.getProperty("splunk-port", "8089")));
        // Create a Service instance and log in with the argument map
        splunkService = Service.connect(serviceArgs);
        //create index if not exists

        //splunk service used URLConnection but not set timeout
        System.setProperty("sun.net.client.defaultReadTimeout", "90000");
        System.setProperty("sun.net.client.defaultConnectTimeout", "90000");
        return splunkService;

    }

    public static synchronized boolean checkTokenAvailable() {
        SplunkJenkinsInstallation.markComplete(false);
        String password = System.getProperty("splunk-passwd");
        if (password == null) {
            System.err.println("please use mvn -Dsplunk-password=AdminPassword -Dsplunk-host=ip-address to run the test\n" +
                    "and you can also put this to global vm settings in IDE");
            return false;
        }
        String token = System.getProperty("splunk-token");
        String host = System.getProperty("splunk-host", "127.0.0.1");
        if (token == null) {
            Service service = getSplunkServiceInstance();
            try {
                Index myIndex = service.getIndexes().get(INDEX_NAME);
                if (myIndex == null) {
                    service.getIndexes().create(INDEX_NAME);
                }
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
                System.setProperty("splunk-token", token);
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "failed to get token", e);
            }
        }
        return setupSender(host, token);
    }

    public static boolean setupSender(String host, String token) {
        LOG.info("host:" + host + " token:" + token);
        SplunkJenkinsInstallation config = SplunkJenkinsInstallation.get();
        String metadataConfig = "";
        try (InputStream input = SplunkConfigUtil.class.getClassLoader().getResourceAsStream("splunk_metadata.properties")) {
            metadataConfig = IOUtils.toString(input);
            metadataConfig += "\nindex=" + INDEX_NAME + "\n";
            for (EventType type : EventType.values()) {
                metadataConfig = metadataConfig.concat(type.getKey("index") + "=" + INDEX_NAME + "\n");
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "failed to read meta config", e);
        }
        config.setHost(host);
        config.setPort(8088);
        config.setUseSSL(true);
        config.setToken(token);
        config.setRawEventEnabled(false);
        config.setEnabled(true);
        config.setScriptPath(null);
        config.setScriptContent(null);
        config.setMetaDataConfig(metadataConfig);
        config.updateCache();
        LOG.fine("update splunkjenkins config");
        config.save();
        boolean isValid = config.isValid();
        LOG.fine("splunk httpinput collector config is valid ?" + isValid);
        SplunkJenkinsInstallation.markComplete(true);
        return isValid;
    }

    public static void verifySplunkSearchResult(String query, long startTime, int minNumber) throws InterruptedException {
        JobArgs jobargs = new JobArgs();
        jobargs.setExecutionMode(JobArgs.ExecutionMode.BLOCKING);
        jobargs.put("earliest_time", startTime / 1000);
        if (!query.contains("index=")) {
            query = "index=" + SplunkConfigUtil.INDEX_NAME + " " + query;
        }
        if (!query.startsWith("search")) {
            query = "search " + query;
        }
        query = query + "|stats count| where count>=" + minNumber;
        LOG.info("running query:\n" + query);
        int eventCount = 0;
        for (int i = 0; i < 20; i++) {
            com.splunk.Job job = SplunkConfigUtil.getSplunkServiceInstance().getJobs().create(query, jobargs);
            eventCount = job.getEventCount();
            if (eventCount == 0) {
                LOG.fine("remaining:" + SplunkLogService.getInstance().getQueueSize());
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                }
            } else {
                break;
            }
        }
        assertTrue("event not reached using:" + query, eventCount > 0);
    }
}
