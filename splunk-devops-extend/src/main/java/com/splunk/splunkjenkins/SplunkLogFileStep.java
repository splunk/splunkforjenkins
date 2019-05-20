package com.splunk.splunkjenkins;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.Set;

import static com.splunk.splunkjenkins.utils.LogEventHelper.parseFileSize;
import static com.splunk.splunkjenkins.utils.LogEventHelper.sendFiles;

/**
 * Send logs to splunk
 */
public class SplunkLogFileStep extends Step {
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

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SplunkLogFileStepExecution(context, this);
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
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class, FilePath.class, EnvVars.class);
        }

        @Override
        public String getFunctionName() {
            return "sendSplunkFile";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Send files to Splunk";
        }
    }

    public static class SplunkLogFileStepExecution extends SynchronousNonBlockingStepExecution<Void> {
        protected SplunkLogFileStepExecution(StepContext context, SplunkLogFileStep step) throws Exception {
            super(context);
            this.step = step;
        }

        private static final long serialVersionUID = 1152009261375345133L;
        private transient SplunkLogFileStep step;

        @Override
        protected Void run() throws Exception {
            if (!SplunkJenkinsInstallation.get().isEnabled()) {
                return null;
            }
            TaskListener listener = getContext().get(TaskListener.class);
            FilePath workspace = getContext().get(FilePath.class);
            Run build =  getContext().get(Run.class);
            EnvVars envVars =  getContext().get(EnvVars.class);
            sendFiles(build, workspace, envVars, listener,
                    step.includes, step.excludes, step.publishFromSlave, parseFileSize(step.sizeLimit));
            return null;
        }
    }
}
