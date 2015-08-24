package com.splunk.splunkjenkins.SplunkLogging;

import jenkins.model.Jenkins;

import java.io.File;

public class Constants {
    public static final String TESTCASE = "testcase";
    public static final String TESTSUITE = "testsuite";
    public static final String ENVVARS = "environmentVariables";
    public static final String buildURL = "BUILD_URL";
    public static final String SEVERITY = "severity";

    public static final String httpInputTokenEndpointPath = "/services/data/inputs/http";
    public static final String httpInputCreateEndpoint = "/servicesNS/admin/search/data/inputs/http/http";

    public static final String pluginPath = Jenkins.getInstance().getPluginManager().getPlugin("splunkjenkins").baseResourceURL.getPath();
    public static final String xsdPath  = pluginPath + File.separator + "junit.xsd";
    public static final String W3C_XML_SCHEMA_NS_URI = "http://www.w3.org/2001/XMLSchema";
    
    public static String errorXML = "<?xml version=\"1.0\" encoding=\"utf-8\"?><testsuite errors='' failures='' name='' skips='' tests='' time='' ><testcase classname='' name='' time=''><error message='%s not found.'>The job  %s has not generated the %s. Please check</error></testcase></testsuite>";
    public static final String INFO = "INFO";
    public static final String CRITICAL = "CRITICAL";
}
