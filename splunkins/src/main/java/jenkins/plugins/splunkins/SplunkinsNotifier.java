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
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by djenkins on 6/18/15.
 */
public class SplunkinsNotifier extends Notifier {
    public boolean collectBuildLog;
    public String filesToSend;

    private final static Logger LOGGER = Logger.getLogger(SplunkinsNotifier.class.getName());

    @DataBoundConstructor
    public SplunkinsNotifier(String filesToSend ){
        this.filesToSend = filesToSend;
    }

    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        PrintStream buildLogStream = listener.getLogger();  // used for printing to the build log
        EnvVars envVars = getBuildEnvVars(build, listener); // Get environment variables
        String buildLog;

        SplunkinsInstallation.Descriptor descriptor = SplunkinsInstallation.getSplunkinsDescriptor();

        // Get the httpinput name
        String httpinputName;
        if (descriptor.source == null || descriptor.source.isEmpty()){
            httpinputName = envVars.get("JOB_NAME") + "_" + envVars.get("BUILD_NUMBER");
        } else {
            httpinputName = descriptor.source;
        }

        // Create the Splunk instance connector
        SplunkConnector connector = new SplunkConnector(descriptor.host, descriptor.port, descriptor.username, descriptor.password, descriptor.scheme);
        String token = null;
        ServiceArgs hostInfo = null;
        try {
            token = connector.createHttpinput(httpinputName);
            hostInfo = connector.getSplunkHostInfo();
        } catch (Exception e) {
            e.printStackTrace();
        }

        HashMap<String, String> userInputs = new HashMap<>();
        userInputs.put("user_httpinput_token", token);

        Dictionary metadata = new Hashtable();
        metadata.put(HttpInputsEventSender.MetadataIndexTag, descriptor.indexName);
        metadata.put(HttpInputsEventSender.MetadataSourceTag, "");
        metadata.put(HttpInputsEventSender.MetadataSourceTypeTag, "");

        // Discover xml files to collect
        FilePath[] xmlFiles = collectXmlFiles(this.filesToSend, build, buildLogStream);

        ArrayList<ArrayList> toSplunkList = new ArrayList<>();

        // Read and parse xml files
        for (FilePath xmlFile : xmlFiles){
            try {
                XmlParser parser = new XmlParser();
                ArrayList<JSONObject> testRun = parser.xmlParser(xmlFile.readToString());
                // Add envVars to each testcase
                for (JSONObject testcase : testRun){
                    Set keys = envVars.keySet();
                    for (Object key : keys){
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

        return true;
    }

    // Returns the build log as a list of strings.
    public String getBuildLog(AbstractBuild<?, ?> build){
        List<String> log = new ArrayList<String>();
        try {
            log = build.getLog(Integer.MAX_VALUE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return log.toString();
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
    public FilePath[] collectXmlFiles(String filenamesExpression, AbstractBuild<?, ?> build, PrintStream buildLogStream){
        FilePath[] xmlFiles = null;
        String buildLogMsg;
        FilePath workspacePath = build.getWorkspace();   // collect junit xml file
        try {
            xmlFiles = workspacePath.list(filenamesExpression);
            LOGGER.info("xmlFiles collected: "+xmlFiles.toString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        assert xmlFiles != null;
        if (xmlFiles.length == 0){
            buildLogMsg = "Splunkins cannot find any files matching the expression: "+filenamesExpression+"\n";
        }else{
            ArrayList<String> filenames = new ArrayList<>();
            for(FilePath file : xmlFiles){
                filenames.add(file.getName());
            }
            buildLogMsg = "Splunkins collected these files to send to Splunk: "+filenames.toString()+"\n";
        }
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
