package jenkins.plugins.splunkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Logger;

/**
 * Created by djenkins on 6/18/15.
 */
public class SplunkinsNotifier extends Notifier {
    public int maxLines;
    public boolean failbuild;
    private final static Logger LOGGER = Logger.getLogger(SplunkinsNotifier.class.getName());

    @DataBoundConstructor
    public SplunkinsNotifier(int maxLines, boolean failBuild){
        this.maxLines = maxLines;
        this.failBuild = failBuild;
    }


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        PrintStream errorPrintStream = listener.getLogger();
        log = build.getLog(Integer.MAX_VALUE);
        LOGGER.info(log);

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
