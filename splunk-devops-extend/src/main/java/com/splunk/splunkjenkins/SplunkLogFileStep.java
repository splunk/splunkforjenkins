package com.splunk.splunkjenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import static com.splunk.splunkjenkins.utils.LogEventHelper.parseFileSize;
import static com.splunk.splunkjenkins.utils.LogEventHelper.sendFiles;

/**
 * Send logs to splunk
 */
public class SplunkLogFileStep extends AbstractStepImpl {
    //required fields
    String includes;

    @DataBoundSetter
    String sizeLimit;
    @DataBoundSetter
    String excludes;
    @DataBoundSetter
    boolean publishFromSlave;

    @DataBoundConstructor
    public SplunkLogFileStep(@Nonnull String includes) {
        this.includes = includes;
    }

    public String getIncludes() {
        return includes;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    public String getExcludes() {
        return excludes;
    }

    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    public boolean isPublishFromSlave() {
        return publishFromSlave;
    }

    public void setPublishFromSlave(boolean publishFromSlave) {
        this.publishFromSlave = publishFromSlave;
    }

    public String getSizeLimit() {
        return sizeLimit;
    }

    public void setSizeLimit(String sizeLimit) {
        this.sizeLimit = sizeLimit;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(SplunkLogFileStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "sendSplunkFile";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Send log files to Splunk";
        }
    }

    public static class SplunkLogFileStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1152009261375345133L;
        @StepContextParameter
        private transient FilePath workspace;
        @StepContextParameter
        private transient Run build;
        @StepContextParameter
        private transient TaskListener listener;
        @StepContextParameter
        private transient EnvVars envVars;
        @Inject
        private transient SplunkLogFileStep step;

        @Override
        protected Void run() throws Exception {
            sendFiles(build, workspace, envVars, listener,
                    step.includes, step.excludes, step.publishFromSlave, parseFileSize(step.sizeLimit));
            return null;
        }
    }
}
