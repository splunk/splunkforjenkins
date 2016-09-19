package com.splunk.splunkjenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.utils.LogEventHelper.parseFileSize;
import static com.splunk.splunkjenkins.utils.LogEventHelper.sendFiles;

@SuppressWarnings("unused")
public class SplunkArtifactNotifier extends Notifier {
    /**
     * {@link org.apache.tools.ant.types.FileSet} "includes" string, like "foo/bar/*.xml"
     */
    private final String includeFiles;
    private final String excludeFiles;
    private final boolean publishFromSlave;
    private final boolean skipGlobalSplunkArchive;
    private final String sizeLimit;

    @DataBoundConstructor
    public SplunkArtifactNotifier(String includeFiles, String excludeFiles, boolean publishFromSlave,
                                  boolean skipGlobalSplunkArchive, String sizeLimit) {
        this.includeFiles = includeFiles;
        this.excludeFiles = excludeFiles;
        this.publishFromSlave = publishFromSlave;
        this.skipGlobalSplunkArchive = skipGlobalSplunkArchive;
        this.sizeLimit=sizeLimit;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        Map<String, String> envVars = new HashMap<>();
        try {
            envVars = build.getEnvironment(listener);
        } catch (Exception ex) {
            listener.getLogger().println("failed to get env");
        }
        long maxFileSize=parseFileSize(sizeLimit);
        listener.getLogger().println("sending files at job level, includes:" + includeFiles + " excludes:" + excludeFiles);
        int eventCount = sendFiles(build, envVars, listener, includeFiles, excludeFiles, publishFromSlave, maxFileSize);
        Logger.getLogger(this.getClass().getName()).log(Level.FINE,"sent "+eventCount+" events with file size limit "+maxFileSize);
        //do not mark build as failed even archive file failed
        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return Messages.SplunArtifactArchive();
        }
    }

    @Override
    public String toString() {
        return "SplunkArtifactNotifier{" +
                "includeFiles='" + includeFiles + '\'' +
                ", excludeFiles='" + excludeFiles + '\'' +
                ", publishFromSlave=" + publishFromSlave +
                ", skipGlobalSplunkArchive=" + skipGlobalSplunkArchive +
                ", sizeLimit='" + sizeLimit + '\'' +
                '}';
    }

    public String getIncludeFiles() {
        return includeFiles;
    }

    public String getExcludeFiles() {
        return excludeFiles;
    }

    public boolean isPublishFromSlave() {
        return publishFromSlave;
    }

    public boolean isSkipGlobalSplunkArchive() {
        return skipGlobalSplunkArchive;
    }

    public String getSizeLimit() {
        return sizeLimit;
    }
}
