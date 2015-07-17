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
import jenkins.plugins.splunkins.SplunkLogging.HttpInputsEventInfo;
import jenkins.plugins.splunkins.SplunkLogging.HttpInputsEventSender;
import jenkins.plugins.splunkins.SplunkLogging.SplunkConnector;
import jenkins.plugins.splunkins.SplunkLogging.XmlParser;

import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Created by djenkins on 6/18/15.
 */
public class SplunkinsNotifier extends Notifier {
    public boolean collectBuildLog;
    public boolean collectEnvVars;
    public String testArtifactFilename;
    public EnvVars envVars;
    private static String host;
    private static String port;
    private static String scheme;

    private final static Logger LOGGER = Logger.getLogger(SplunkinsNotifier.class.getName());

    @DataBoundConstructor
    public SplunkinsNotifier(boolean collectBuildLog, boolean collectEnvVars, String testArtifactFilename, EnvVars envVars){
        this.collectBuildLog = collectBuildLog;
        this.collectEnvVars = collectEnvVars;
        this.testArtifactFilename = testArtifactFilename;
        this.envVars = envVars;
    }

    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
	@Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        PrintStream buildLogStream = listener.getLogger();
        String artifactContents = null;

        if (this.collectEnvVars) {
            String log = getBuildLog(build);
            LOGGER.info(log);
        }
        if (this.collectEnvVars){
            envVars = getBuildEnvVars(build, listener);
            LOGGER.info(envVars.toString());
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
      

        if (!this.testArtifactFilename.equals("")) {
            artifactContents = readTestArtifact(testArtifactFilename, build, buildLogStream);
            //splunk_Logger.info("XML report:\n" + artifactContents);
        }
        

        XmlParser parser = new XmlParser();
        ArrayList<JSONObject> jsonList= parser.xmlParser(artifactContents);
        
        if (jsonList.size() > 0){
        	  HttpInputsEventSender sender = new HttpInputsEventSender(
                      scheme + "://"+ host + ":8088", token, 0, 0, 0, 5, "sequential", dictionary);
        	  
              sender.disableCertificateValidation();
              
              for (int i=0 ;i< jsonList.size() ; i++){
            	  sender.send("INFO", jsonList.get(i).toString());
              }
              
              sender.close();
        }
        
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

    // Reads test artifact text files and returns their contents. Logs errors to both the Jenkins build log and the
    // Jenkins internal logging.
    public String readTestArtifact(String artifactName, AbstractBuild<?, ?> build, PrintStream buildLogStream){
        String report = "";
        FilePath workspacePath = build.getWorkspace();   // collect junit xml file
        FilePath fullReportPath = new FilePath(workspacePath, artifactName);
        try {
            report = fullReportPath.readToString();  // Attempt to read test artifact
        } catch(FileNotFoundException e ){           // If the test artifact file is not found...
            String noSuchFileMsg = "Build: "+build.getFullDisplayName()+", Splunkins Error: "+e.getMessage();
            LOGGER.warning(noSuchFileMsg);           // Write to Jenkins log
            try {
                // Attempt to write to build's console log
                String buildConsoleError = "Splunkins cannot find JUnit XML Report:" + e.getMessage() + "\n";
                buildLogStream.write(buildConsoleError.getBytes());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            buildLogStream.flush();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        assert report != null;
        return report;
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

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return Messages.DisplayName();
        }
    }
}
