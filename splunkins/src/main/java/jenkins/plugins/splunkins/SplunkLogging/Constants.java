package jenkins.plugins.splunkins.SplunkLogging;

import jenkins.model.Jenkins;

import java.io.File;

public class Constants {
    public static final String TESTCASE = "testcase";
    public static final String TESTSUITE = "testsuite";
    public static final String ENVVARS = "environmentVariables";
    
    public static String HTTPINPUTPORT = "8088";

    public static final String httpInputTokenEndpointPath = "/services/data/inputs/http";
    public static final String httpInputCreateEndpoint = "/servicesNS/admin/search/data/inputs/http/http";

    public static final String pluginPath = Jenkins.getInstance().getPluginManager().getPlugin("splunkins").baseResourceURL.getPath();
    public static final String xsdPath  = pluginPath + File.separator + "junit.xsd";
    public static final String W3C_XML_SCHEMA_NS_URI = "http://www.w3.org/2001/XMLSchema";
    
    public static String errorXML = "<?xml version=\"1.0\" encoding=\"utf-8\"?><testsuite errors='The trigger %s has not generated the test-result.xml. Please check trigger %s, job %s, job_build_no %s for issues' failures='' name='' skips='' tests='' time='' ></testsuite>";
    public static final String INFO = "INFO";
    public static final String CRITICAL = "CRITICAL";
}
