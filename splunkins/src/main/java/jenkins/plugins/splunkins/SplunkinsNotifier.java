package jenkins.plugins.splunkins;

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
    public boolean collectEnvVars;
    public String filesToSend;
    public EnvVars envVars;
    private static String host;
    private static String scheme;

    private final static Logger LOGGER = Logger.getLogger(SplunkinsNotifier.class.getName());

    @DataBoundConstructor
    public SplunkinsNotifier(boolean collectBuildLog, boolean collectEnvVars, String filesToSend, EnvVars envVars){
        this.collectBuildLog = collectBuildLog;
        this.collectEnvVars = collectEnvVars;
        this.filesToSend = filesToSend;
        this.envVars = envVars;
    }

    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        PrintStream buildLogStream = listener.getLogger();
        String buildLog;

        if (this.collectEnvVars) {
            buildLog = getBuildLog(build);
        }
        if (this.collectEnvVars){
            envVars = getBuildEnvVars(build, listener);
        }

        String httpinputName = envVars.get("JOB_NAME") + "_" + envVars.get("BUILD_NUMBER");
        String token = null;
        try {
            token = SplunkConnector.createHttpinput(httpinputName);
            host = SplunkConnector.getSplunkHostInfo().host;
            scheme = SplunkConnector.getSplunkHostInfo().scheme;
        } catch (Exception e) {
            e.printStackTrace();
        }

        HashMap<String, String> userInputs = new HashMap<String, String>();
        userInputs.put("user_httpinput_token", token);


        Dictionary dictionary = new Hashtable();
        dictionary.put(HttpInputsEventSender.MetadataIndexTag, "main");
        dictionary.put(HttpInputsEventSender.MetadataSourceTag, "");
        dictionary.put(HttpInputsEventSender.MetadataSourceTypeTag, "");


//            artifactContents = readTestArtifact(testArtifactFilename, build,
//                    buildLogStream);
//            // splunk_Logger.info("XML report:\n" + artifactContents);
//
//            XmlParser parser = new XmlParser();
//            ArrayList<JSONObject> jsonList = parser.xmlParser(artifactContents,
//                    envVars);
//
//            if (jsonList.size() > 0) {
//                HttpInputsEventSender sender = new HttpInputsEventSender(scheme
//                        + "://" + host + ":" + Constants.HTTPINPUTPORT, token,
//                        0, 0, 0, 5, "sequential", dictionary);
//
//                sender.disableCertificateValidation();
//
//                for (int i = 0; i < jsonList.size(); i++) {
//                    sender.send("INFO", jsonList.get(i).toString());
//                }
//
//                sender.close();
//            }
//        }

        userInputs.put("user_httpinput_token", token);

        // Discover xml files to collect
        FilePath[] xmlFiles = collectXmlFiles(this.filesToSend, build, buildLogStream);

        // Read and parse xml files
//        for (FilePath xml : xmlFiles){
//            XmlParser parser = new XmlParser();
//            try {
//                JSONObject json = parser.xmlParser(xml.readToString());
//            } catch (IOException | InterruptedException e) {
//                e.printStackTrace();
//            }
//        }

        // Combine json objects

        // Send json data to splunk

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
