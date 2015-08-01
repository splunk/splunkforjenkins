package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.SplunkLogging.Constants;
import com.splunk.splunkjenkins.SplunkLogging.HttpInputsEventSender;
import com.splunk.splunkjenkins.SplunkLogging.SplunkConnector;
import com.splunk.splunkjenkins.SplunkLogging.XmlParser;
import com.splunk.splunkjenkins.Messages;
import com.splunk.ServiceArgs;
import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by djenkins on 6/18/15.
 */
public class SplunkJenkinsNotifier extends Notifier{
    public String filesToSend;
    public String token;
    public ServiceArgs hostInfo;
    
    private final static Logger LOGGER = Logger.getLogger(SplunkJenkinsNotifier.class.getName());
    private String logLevel;
    @DataBoundConstructor
    public SplunkJenkinsNotifier(String filesToSend){
        this.filesToSend = filesToSend;
    }


    /**
     * This is the main driver of the plugin's flow
     */
    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
        final PrintStream buildLogStream = listener.getLogger();  // used for printing to the build log
        final EnvVars envVars = getBuildEnvVars(build, listener); // Get environment variables

        SplunkJenkinsInstallation.Descriptor descriptor = SplunkJenkinsInstallation.getSplunkDescriptor();

        // Set the httpinput name to the hostname
        String httpinputName = descriptor.sourceName;

        // Create the Splunk instance connector
        SplunkConnector connector = new SplunkConnector(descriptor.host, descriptor.managementPort, descriptor.username, descriptor.password, descriptor.scheme, buildLogStream);

        try {
            token = connector.createHttpinput(httpinputName);            
            hostInfo = connector.getSplunkHostInfo();
        } catch (Exception e) {
            logException(e, buildLogStream);      
        }

        HashMap<String, String> userInputs = new HashMap<>();
        userInputs.put("user_httpinput_token", token);

        // Set httpinput metadata
        Dictionary metadata = new Hashtable();
        metadata.put(HttpInputsEventSender.MetadataIndexTag, descriptor.indexName);
        metadata.put(HttpInputsEventSender.MetadataSourceTag, descriptor.sourceName);
        metadata.put(HttpInputsEventSender.MetadataSourceTypeTag, descriptor.sourceTypeName);

        // Discover xml files to collect
        FilePath[] xmlFiles = null;
        try {
             xmlFiles = collectXmlFiles(filesToSend, build, buildLogStream, envVars);
        } catch (IOException | InterruptedException e1) {
            logException(e1, buildLogStream);
        }

        ArrayList<ArrayList> toSplunkList = new ArrayList<>();
        // Read and parse xml files
        if (null != xmlFiles) {
            for (FilePath xmlFile : xmlFiles) {
                try {
                    toSplunkList.add(createDataForSplunk(xmlFile.readToString(), envVars, buildLogStream));                   
                } catch (InterruptedException e) {
                    logException(e, buildLogStream);
                }
            }
            logLevel = Constants.INFO;
        }else{
            toSplunkList.add(createDataForSplunk(String.format(Constants.errorXML.toString(), envVars.get("aggregate_report_name"), envVars.get("aggregate_report_name"),envVars.get("generic_job_name"), envVars.get("BUILD_NUMBER")), envVars, buildLogStream));
            logLevel = Constants.CRITICAL;
        }
        
        // Setup connection for sending to build data to Splunk
        
        if (null != hostInfo && null != token && null != descriptor) {
            if ((!("").equalsIgnoreCase(hostInfo.scheme) && null != hostInfo.scheme) && (!("").equalsIgnoreCase(hostInfo.host) && null != hostInfo.host)){
                if(!("").equalsIgnoreCase(descriptor.sendMode) && null != descriptor.sendMode ){
                        HttpInputsEventSender sender = new HttpInputsEventSender(hostInfo.scheme + "://" + hostInfo.host + ":" +
                                descriptor.httpInputPort, token, descriptor.delay, descriptor.maxEventsBatchCount,
                                descriptor.maxEventsBatchSize, descriptor.retriesOnError, descriptor.sendMode, metadata);

                        sender.disableCertificateValidation();
        
                        // Send data to splunk
                        for (ArrayList<JSONObject> toSplunkFile : toSplunkList) {
                            for (JSONObject json : toSplunkFile){
                                sender.send(logLevel, json.toString());
                            }
                        }

                        sender.close();
                    }else{
                        LOGGER.info("Value of sendMode is: " + descriptor.sendMode);
                    }
                }else{
                    LOGGER.info("Value of hostInfo Details is: " + hostInfo.scheme  + "://" + hostInfo.host + ":" + descriptor.httpInputPort);
                }
        }else{
            LOGGER.info("Is hostInfo null: " + (hostInfo != null?false:true));
            LOGGER.info("Is token null: " + (token!= null?false:true));
            LOGGER.info("Is descriptor null: " + (descriptor != null?false:true));
        }

        return true;
    }

    // Returns environment variables for the build.
    public EnvVars getBuildEnvVars(AbstractBuild<?, ?> build, BuildListener listener){
        EnvVars envVars = null;
        try {
            envVars = build.getEnvironment(listener);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        assert envVars != null;
        return envVars;
    }

    // Collects all files based on ant-style filter string and returns them as an array of FilePath objects.
    // Logs errors to both the Jenkins build log and the Jenkins internal logging.
    public FilePath[] collectXmlFiles(String filenamesExpression, AbstractBuild<?, ?> build, PrintStream buildLogStream, EnvVars envVars) throws IOException, InterruptedException{
        FilePath[] xmlFiles = null;
        String buildLogMsg;
        String expandedFilePath = Util.replaceMacro(filenamesExpression, envVars);
        FilePath workspacePath = build.getWorkspace();   // collect junit xml file
        if (workspacePath.isRemote()){
            LOGGER.info("Collecting files on remote Jenkins slave...");
        }else{
            LOGGER.info("Collecting files on local Jenkins Master...");
        }
        try {
            xmlFiles = workspacePath.list(expandedFilePath);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        assert xmlFiles != null;
        if (xmlFiles.length == 0){
            buildLogMsg = "Splunk cannot find any files in " + workspacePath.toString() + " matching the expression: " + filenamesExpression+"\n";
            xmlFiles = null;
        }else{
            ArrayList<String> filenames = new ArrayList<>();
            for(FilePath file : xmlFiles){
                filenames.add(file.getName());
            }
            buildLogMsg = Messages.DisplayName()+" collected these files to send to Splunk: "+filenames.toString()+"\n";
        }
        LOGGER.info(buildLogMsg);
        // Attempt to write to build's console log
        try {
            buildLogStream.write(buildLogMsg.getBytes());
        } catch (IOException e) {
            logException(e, buildLogStream);
        }
        return xmlFiles;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public Descriptor getDescriptor() {
        return (Descriptor) super.getDescriptor();
    }

    @Extension
    public static class Descriptor extends BuildStepDescriptor<Publisher> {
        public String configBuildStepSendLog = Messages.ConfigBuildStepSendLog();
        public String configBuildStepSendEnvVars = Messages.ConfigBuildStepSendEnvVars();
        public String configBuildStepSendFiles = Messages.ConfigBuildStepSendFiles();

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return Messages.ConfigBuildStepTitle();
        }
    }
    
    private void logException(Exception e, PrintStream buildLogStream) throws IOException {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();
        buildLogStream.write(exceptionAsString.getBytes());
    }
    
    private ArrayList<JSONObject> createDataForSplunk(String xmlFileData, EnvVars envVars, PrintStream buildLogStream) throws IOException {
            try {
                XmlParser parser = new XmlParser();
                ArrayList<JSONObject> testRun = parser.xmlParser(xmlFileData);

                // Add envVars to each testcase
                for (JSONObject testcase : testRun) {
                    JSONObject envVarsJSON = new JSONObject();

                    Set keys = envVars.keySet();
                    for (Object key : keys) {
                        envVarsJSON.append(key.toString(), envVars.get(key));
                    }
                    testcase.append(Constants.ENVVARS, envVarsJSON);
                }
                return testRun;
            } catch (JSONException e) {
                logException(e, buildLogStream);
            }
            return null;
        
    }
}
