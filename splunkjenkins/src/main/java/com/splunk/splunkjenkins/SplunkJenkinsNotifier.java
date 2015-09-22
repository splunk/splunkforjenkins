package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.SplunkLogging.Constants;
import com.splunk.splunkjenkins.SplunkLogging.HttpInputsEventSender;
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
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by djenkins on 6/18/15.
 */
public class SplunkJenkinsNotifier extends Notifier{
    public String filesToSend;
    public String filesToAppend;
    public String token;

    private final static Logger LOGGER = Logger.getLogger(SplunkJenkinsNotifier.class.getName());
    private String logLevel;
    @DataBoundConstructor
    public SplunkJenkinsNotifier(String filesToSend, String filesToAppend){
        this.filesToSend = filesToSend;
        this.filesToAppend = filesToAppend;
    }


    /**
     * This is the main driver of the plugin's flow
     */
    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
        final PrintStream buildLogStream = listener.getLogger();  // used for printing to the build log
        final EnvVars envVars = getBuildEnvVars(build, listener); // Get environment variables

        SplunkJenkinsInstallation.Descriptor descriptor = SplunkJenkinsInstallation.getSplunkDescriptor();

        // Create the Splunk instance connector

        token = descriptor.httpInputToken;

        HashMap<String, String> userInputs = new HashMap<>();
        userInputs.put("user_httpinput_token", token);

        // Set httpinput metadata
        Dictionary metadata = new Hashtable();
        metadata.put(HttpInputsEventSender.MetadataIndexTag, descriptor.indexName);
        metadata.put(HttpInputsEventSender.MetadataSourceTag, descriptor.sourceName);
        metadata.put(HttpInputsEventSender.MetadataSourceTypeTag, descriptor.sourceTypeName);

        // Discover xml files to collect
        FilePath[] allFiles = null;
        try {
            allFiles = collectFiles(filesToSend, build, buildLogStream, envVars);
        } catch (IOException | InterruptedException e1) {
            logException(e1, buildLogStream);
        }

        ArrayList<FilePath> fileForSplunk = filesToAppend(filesToAppend, build, buildLogStream, envVars);
        ArrayList<JSONObject> toSplunkList = new ArrayList<>();
        // Read and parse xml files
        try {
            String metadataJSON = null;
            if (!fileForSplunk.isEmpty()) {
                metadataJSON = fileForSplunk.get(0).readToString();
            }

            if (allFiles != null) { // If there's xml files collected,
                for (FilePath xmlFile : allFiles) {  // create a separate event for each xml file.
                    toSplunkList.add(createDataForSplunk(xmlFile.readToString(), metadataJSON, buildLogStream, null, null));
                }
            } else { // Otherwise, send event with an error
                toSplunkList.add(createDataForSplunk(null, metadataJSON, buildLogStream, "file-not-found", String.format("%s not found", filesToSend)));
            }
        } catch (InterruptedException e) {
            logException(e, buildLogStream);
        }
        
        // Setup connection for sending to build data to Splunk
        if (null != token && null != descriptor) {
            if ((!("").equalsIgnoreCase(descriptor.scheme) && null != descriptor.scheme) && (!("").equalsIgnoreCase(descriptor.host) && null != descriptor.host)){
                if(!("").equalsIgnoreCase(descriptor.sendMode) && null != descriptor.sendMode ){
                        HttpInputsEventSender sender = new HttpInputsEventSender(descriptor.scheme + "://" + descriptor.host + ":" +
                                descriptor.httpInputPort, token, descriptor.delay, descriptor.maxEventsBatchCount,
                                descriptor.maxEventsBatchSize, descriptor.retriesOnError, descriptor.sendMode, metadata);

                        sender.disableCertificateValidation();
        
                        // Send data to splunk
                        for (JSONObject splunkEvent : toSplunkList) {
                                sender.send("", splunkEvent.toString());
                        }

                        sender.close();
                    }else{
                        LOGGER.info("Value of sendMode is: " + descriptor.sendMode);
                    }
                }else{
                    LOGGER.info("Value of hostInfo Details is: " + descriptor.scheme  + "://" + descriptor.host + ":" + descriptor.httpInputPort);
                }
        }else{
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
    public FilePath[] collectFiles(String filenamesExpression, AbstractBuild<?, ?> build, PrintStream buildLogStream, EnvVars envVars) throws IOException, InterruptedException{
        FilePath[] files = null;
        String buildLogMsg;
        String expandedFilePath = Util.replaceMacro(filenamesExpression, envVars);
        FilePath workspacePath = build.getWorkspace();   // collect junit xml file
        if (workspacePath.isRemote()){
            LOGGER.info("Collecting files on remote Jenkins slave...");
        }else{
            LOGGER.info("Collecting files on local Jenkins Master...");
        }
        try {
            files = workspacePath.list(expandedFilePath);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        assert files != null;
        if (files.length == 0){
            buildLogMsg = "Splunk cannot find any files in " + workspacePath.toString() + " matching the expression: " + filenamesExpression+"\n";
            files = null;
        }else{
            ArrayList<String> filenames = new ArrayList<>();
            for(FilePath file : files){
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
        return files;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
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
        public String configBuildFileToAppend = Messages.ConfigBuildFileToAppend();

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
    
    private JSONObject createDataForSplunk(String xmlFileData, String metadata, PrintStream buildLogStream, String errorType, String errorMsg) throws IOException {
        try {
            
            JSONObject splunkEvent = new JSONObject();
            JSONObject jsonEvent = new JSONObject();

            if (null != xmlFileData){
                XmlParser parser = new XmlParser();
                jsonEvent = parser.xmlParser(xmlFileData);
            }

            splunkEvent.put(Constants.TESTSUITE, jsonEvent);

            if (null != metadata && !("").equalsIgnoreCase(metadata)){
                JSONObject metadataValues = new JSONObject(metadata.toString());
                splunkEvent.put(Constants.METADATA, metadataValues);
            }

            JSONObject buildError = null;
            if(errorType != null){
                buildError = new JSONObject();
                buildError.put("type", errorType);
                buildError.put("message", errorMsg);
            }
            splunkEvent.put(Constants.ERROR, buildError);

            return splunkEvent;
        } catch (JSONException e) {
            logException(e, buildLogStream);
        }
        return null;
    }
    
    private ArrayList<FilePath> filesToAppend(String filesToAppend, AbstractBuild<?, ?> build, PrintStream buildLogStream, EnvVars envVars){
        FilePath[] files = null;
        String buildLogMsg;
        ArrayList<FilePath> filesToAppendList= new ArrayList<FilePath>();
        String expandedFilePath = Util.replaceMacro(filesToAppend, envVars);
        FilePath workspacePath = build.getWorkspace();   // collect metadata file to append
        
        try {
            files = workspacePath.list(expandedFilePath);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        assert files != null;
        if (files.length == 0){
            buildLogMsg = "Splunk cannot find any files in " + workspacePath.toString() + " matching the expression: " + filesToAppend+"\n";
            files = null;
        }else{
            ArrayList<String> filenames = new ArrayList<>();
            for(FilePath file : files){
                filenames.add(file.getName());
                filesToAppendList.add(file);
            }
            buildLogMsg = Messages.DisplayName()+" collected these files to append to Splunk Events: "+filenames.toString()+"\n";
        }
        LOGGER.info(buildLogMsg);
        // Attempt to write to build's console log
        try {
            buildLogStream.write(buildLogMsg.getBytes());
        } catch (IOException e) {
            try {
                logException(e, buildLogStream);
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }
        return filesToAppendList;
        
    }
}
