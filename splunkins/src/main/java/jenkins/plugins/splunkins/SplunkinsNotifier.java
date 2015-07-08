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
import java.io.IOException;
import java.io.PrintStream;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;
import java.util.logging.Logger;

/**
 * Created by djenkins on 6/18/15.
 */
public class SplunkinsNotifier extends Notifier {
    public int maxLines;
    public boolean failBuild;
    public String junitReport;
    private final static Logger LOGGER = Logger.getLogger(SplunkinsNotifier.class.getName());

    @DataBoundConstructor
    public SplunkinsNotifier(int maxLines, boolean failBuild, String junitReport){
        this.maxLines = maxLines;
        this.failBuild = failBuild;
        this.junitReport = junitReport;
    }


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        PrintStream errorPrintStream = listener.getLogger();
        List<String> log = null;
        try {
            log = build.getLog(Integer.MAX_VALUE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        EnvVars envVars = new EnvVars();
        try {
            envVars = build.getEnvironment(listener);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        FilePath workspacePath = build.getWorkspace(); // collect junit xml file

        LOGGER.info(log.toString());
        LOGGER.info(envVars.toString());
        LOGGER.info("junitReport Path:" + this.junitReport.toString());
        LOGGER.info("workspace path:" + workspacePath);

        if (!this.junitReport.equals("")){
            FilePath fullReportPath = new FilePath(workspacePath, this.junitReport.toString());
            String report = null;
            try {
                report = fullReportPath.readToString();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            LOGGER.info("fullReportPath:" + fullReportPath);
            LOGGER.info(report);
        }

        return !(failBuild);
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
