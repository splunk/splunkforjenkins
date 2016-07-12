package com.splunk.splunkjenkins;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.splunk.*;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class SplunkConfigUtil {
    public static final String INDEX_NAME = System.getProperty("splunk-index", "plugin_sandbox");
    private static final Logger LOG = Logger.getLogger(SplunkLogServiceTest.class.getName());
    private static final String COLLECTOR_NAME = "splunkins_unittest";
    private static final String ENDPOINT = "/servicesNS/admin/search/data/inputs/http/";
    public static String TOKEN = null;
    private static Gson gson = new Gson();

    public static synchronized boolean checkTokenAvailable(Jenkins jenkins) {
        boolean useAutoConfig = Boolean.getBoolean("splunk-token-setup");
        String token = System.getProperty("splunk-token");
        String host = System.getProperty("splunk-host", "127.0.0.1");
        ServiceArgs serviceArgs = new ServiceArgs();
        serviceArgs.setSSLSecurityProtocol(SSLSecurityProtocol.TLSv1);
        if (token == null && useAutoConfig) {
            serviceArgs.setUsername(System.getProperty("splunk-username", "admin"));
            serviceArgs.setPassword(System.getProperty("splunk-passwd", "changeme"));
            serviceArgs.setHost(host);
            serviceArgs.setPort(8089);
            // Create a Service instance and log in with the argument map
            Service service = Service.connect(serviceArgs);
            //create index if not exists
            try {
                //splunk service used URLConnection but not set timeout
                System.setProperty("sun.net.client.defaultReadTimeout", "90000");
                System.setProperty("sun.net.client.defaultConnectTimeout", "90000");
                service.getIndexes().create(INDEX_NAME);
            } catch (com.splunk.HttpException ex) {
                int statusCode = ex.getStatus();
                if (!(statusCode == 409 || statusCode == 201)) {
                    throw ex;
                }
            }
            //enable logging endpoint
            service.post(ENDPOINT +"http", ImmutableMap.of("disabled", (Object) "0"));
            ResponseMessage response;
            try {
                response = service.get(ENDPOINT  + COLLECTOR_NAME, ImmutableMap.of("output_mode", (Object) "json"));
            } catch (com.splunk.HttpException e) {
                if (e.getStatus() != 404) {
                    throw e;
                }
                //create token because it doesn't exist
                Map<String, Object> args = new HashMap<String, Object>();
                args.put("output_mode", "json");
                args.put("name", COLLECTOR_NAME);
                args.put("index",INDEX_NAME);
                args.put("indexes","main,"+INDEX_NAME);
                args.put("description", "test http event collector");
                response = service.post(ENDPOINT + "http", args);
                System.err.println(response);
                response = service.get(ENDPOINT  + COLLECTOR_NAME, ImmutableMap.of("output_mode", (Object) "json"));
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
        Properties properties = new Properties();
        try {
            properties.load(SplunkConfigUtil.class.getClassLoader().getResourceAsStream("splunk_metadata.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        properties.put("index", INDEX_NAME);
        config.setHost(host);
        config.setPort(8088);
        config.setUseSSL(true);
        config.setToken(token);
        config.setRawEventEnabled(false);
        config.setEnabled(true);
        config.metaDataProperties = properties;
        config.updateCache();
        config.save();
        return config.isValid();
    }
}
