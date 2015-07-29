package jenkins.plugins.splunkins;

import com.splunk.ServiceArgs;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.plugins.splunkins.SplunkLogging.Constants;
import jenkins.plugins.splunkins.SplunkLogging.HttpInputsEventSender;
import jenkins.plugins.splunkins.SplunkLogging.SplunkConnector;
import jenkins.plugins.splunkins.SplunkLogging.XmlParser;

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
public class SplunkinsNotifier extends Notifier{
    public String filesToSend;
    public String token;
    public ServiceArgs hostInfo;
    
    private final static Logger LOGGER = Logger.getLogger(SplunkinsNotifier.class.getName());

    @DataBoundConstructor
    public SplunkinsNotifier(String filesToSend){
        this.filesToSend = filesToSend;
    }


    /**
     * This is the main driver of the plugin's flow
     */
    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
        final PrintStream buildLogStream = listener.getLogger();  // used for printing to the build log
        final EnvVars envVars = getBuildEnvVars(build, listener); // Get environment variables

        SplunkinsInstallation.Descriptor descriptor = SplunkinsInstallation.getSplunkinsDescriptor();

        // Set the httpinput name to the hostname
        String httpinputName = InetAddress.getLocalHost().getHostName();

        // Create the Splunk instance connector
        SplunkConnector connector = new SplunkConnector(descriptor.host, descriptor.port, descriptor.username, descriptor.password, descriptor.scheme, buildLogStream);

        try {
            token = connector.createHttpinput(httpinputName);            
            hostInfo = connector.getSplunkHostInfo();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            buildLogStream.write(exceptionAsString.getBytes());
        }

        HashMap<String, String> userInputs = new HashMap<>();
        userInputs.put("user_httpinput_token", token);

        // Set httpinput metadata
        Dictionary metadata = new Hashtable();
        metadata.put(HttpInputsEventSender.MetadataIndexTag, descriptor.indexName);
        metadata.put(HttpInputsEventSender.MetadataSourceTag, "");
        metadata.put(HttpInputsEventSender.MetadataSourceTypeTag, "");

        // Discover xml files to collect
        FilePath[] xmlFiles = new FilePath[0];
        try {
            xmlFiles = collectXmlFiles(filesToSend, build, buildLogStream);
        } catch (IOException | InterruptedException e1) {
            e1.printStackTrace();
        }

        ArrayList<ArrayList> toSplunkList = new ArrayList<>();

        // Read and parse xml files
        for (FilePath xmlFile : xmlFiles) {
            try {
                XmlParser parser = new XmlParser();
                ArrayList<JSONObject> testRun = parser.xmlParser(xmlFile.readToString());
                // Add envVars to each testcase
                for (JSONObject testcase : testRun) {
                    Set keys = envVars.keySet();
                    for (Object key : keys) {
                        try {
                            testcase.append(key.toString(), envVars.get(key));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
                toSplunkList.add(testRun);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Setup connection for sending to build data to Splunk
        
        if (null != hostInfo && null != token && null != descriptor) {
            if ((!("").equalsIgnoreCase(hostInfo.scheme) && null != hostInfo.scheme) && (!("").equalsIgnoreCase(hostInfo.host) && null != hostInfo.host)){
                if(!("").equalsIgnoreCase(descriptor.sendMode) && null != descriptor.sendMode ){
                        HttpInputsEventSender sender = new HttpInputsEventSender(hostInfo.scheme + "://" + hostInfo.host + ":" +
                                Constants.HTTPINPUTPORT, token, descriptor.delay, descriptor.maxEventsBatchCount,
                                descriptor.maxEventsBatchSize, descriptor.retriesOnError, descriptor.sendMode, metadata);

                        sender.disableCertificateValidation();
        
                        // Send data to splunk
                        for (ArrayList<JSONObject> toSplunkFile : toSplunkList) {
                            for (JSONObject json : toSplunkFile){
                                sender.send("INFO", json.toString());
                            }
                        }

                        sender.close();
                    }else{
                        LOGGER.info("Value of sendMode is: " + descriptor.sendMode);
                    }
                }else{
                    LOGGER.info("Value of hostInfo Details is: " + hostInfo.scheme  + "://" + hostInfo.host + ":" + Constants.HTTPINPUTPORT);
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
    public FilePath[] collectXmlFiles(String filenamesExpression, AbstractBuild<?, ?> build, PrintStream buildLogStream) throws IOException, InterruptedException{
        FilePath[] xmlFiles = null;
        String buildLogMsg;
        FilePath workspacePath = build.getWorkspace();   // collect junit xml file
        if (workspacePath.isRemote()){
            LOGGER.info("Collecting files on remote Jenkins slave...");
        }else{
            LOGGER.info("Collecting files on local Jenkins Master...");
        }
        try {
            xmlFiles = workspacePath.list(filenamesExpression);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        assert xmlFiles != null;
        if (xmlFiles.length == 0){
            buildLogMsg = "Splunkins cannot find any files in " + workspacePath.toString() + " matching the expression: " + filenamesExpression+"\n";
        }else{
            ArrayList<String> filenames = new ArrayList<>();
            for(FilePath file : xmlFiles){
                filenames.add(file.getName());
            }
            buildLogMsg = "Splunkins collected these files to send to Splunk: "+filenames.toString()+"\n";
        }
        LOGGER.info(buildLogMsg);
        // Attempt to write to build's console log
        try {
            buildLogStream.write(buildLogMsg.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
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
}
