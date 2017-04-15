package com.splunk.splunkjenkins;

import com.google.common.collect.ImmutableMap;
import com.splunk.*;
import com.splunk.splunkjenkins.model.EventType;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import org.apache.commons.io.IOUtils;
import shaded.splk.com.google.gson.Gson;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public class SplunkConfigUtil {
    public static final String INDEX_NAME = System.getProperty("splunk-index", "plugin_sandbox");
    private static final Logger LOG = Logger.getLogger(SplunkLogServiceTest.class.getName());
    private static final String COLLECTOR_NAME = "splunkins_unittest";
    private static final String ENDPOINT = "/servicesNS/admin/search/data/inputs/http/";
    private static Gson gson = new Gson();
    private static Service splunkService;

    public static synchronized Service getSplunkServiceInstance() throws IOException {
        if (splunkService != null) {
            return splunkService;
        }
        Properties properties = new Properties();
        File configFile = new File(System.getProperty("user.home"), ".splunkrc");
        if (configFile.exists()) {
            properties.load(new FileReader(configFile));
        }
        Properties sysProperties = System.getProperties();
        properties.putAll(sysProperties);
        if (!properties.containsKey("password")) {
            System.err.println("please use mvn -Dpassword=AdminPassword -Dhost=ip-address to run the test\n" +
                    "and you can also put this to global vm settings in IDE");
            throw new IOException("splunk admin password is needed");
        }
        String host = properties.getProperty("host", "127.0.0.1");
        ServiceArgs serviceArgs = new ServiceArgs();
        serviceArgs.setSSLSecurityProtocol(SSLSecurityProtocol.TLSv1);
        serviceArgs.setUsername(properties.getProperty("username", "admin"));
        serviceArgs.setPassword(properties.getProperty("password", "changeme"));
        serviceArgs.setHost(host);
        serviceArgs.setPort(Integer.parseInt(properties.getProperty("port", "8089")));
        //splunk service used URLConnection but not set timeout
        System.setProperty("sun.net.client.defaultReadTimeout", "90000");
        System.setProperty("sun.net.client.defaultConnectTimeout", "90000");
        // Create a Service instance and log in with the argument map
        splunkService = Service.connect(serviceArgs);
        return splunkService;

    }

    public static synchronized boolean checkTokenAvailable() {
        SplunkJenkinsInstallation.markComplete(false);
        Service service = null;
        try {
            service = getSplunkServiceInstance();
        } catch (IOException e) {
            return false;
        }
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
            String token = result.getFirst("token");
            return setupSender(service.getHost(), service.getPort(), token);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean setupSender(String host, int port, String token) {
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

    public static void verifySplunkSearchResult(String query, int minNumber) {
        long fiveMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
        verifySplunkSearchResult(query, fiveMinutesAgo, minNumber);
    }

    public static void verifySplunkSearchResult(String query, long startTime, int minNumber) {
        JobArgs jobargs = new JobArgs();
        jobargs.setExecutionMode(JobArgs.ExecutionMode.BLOCKING);
        jobargs.put("earliest_time", startTime / 1000);
        jobargs.put("latest_time", 60 + System.currentTimeMillis() / 1000);
        if (!query.contains("index=")) {
            query = "index=" + SplunkConfigUtil.INDEX_NAME + " " + query;
        }
        if (!query.startsWith("search")) {
            query = "search splunk_server=local " + query;
        }
        query = query + "|stats count| where count>=" + minNumber;
        LOG.info("running query:\n" + query);
        int eventCount = 0;
        for (int i = 0; i < 20; i++) {
            try {
                Job job = SplunkConfigUtil.getSplunkServiceInstance().getJobs().create(query, jobargs);
                eventCount = job.getEventCount();
                if (eventCount == 0) {
                    Thread.sleep(10000);
                } else {
                    break;
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException("execute query " + query + " failed");
            }
        }
        assertTrue("event not reached using:" + query, eventCount > 0);
    }
}
