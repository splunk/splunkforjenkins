package com.splunk.splunkjenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import static hudson.Util.fixNull;

/**
 * Send Message to splunk
 */
@Deprecated
public class SplunkMessageStep extends Step {
    //required fields
    String scriptText;
    //reuse global script
    boolean globalScriptEnabled = true;

    @DataBoundConstructor
    public SplunkMessageStep(boolean globalScriptEnabled, String scriptText) {
        this.globalScriptEnabled = globalScriptEnabled;
        this.scriptText = fixNull(scriptText);
    }

    public SplunkMessageStep() {
        this.globalScriptEnabled = true;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SplunkLogFileStepExecution(context, this);
    }

    public String getScriptText() {
        if (scriptText != null) {
            return scriptText;
        } else {
            return "";
        }
    }

    public void setScriptText(String scriptText) {
        this.scriptText = scriptText;
    }

    public boolean isGlobalScriptEnabled() {
        return globalScriptEnabled;
    }

    public void setGlobalScriptEnabled(boolean globalScriptEnabled) {
        this.globalScriptEnabled = globalScriptEnabled;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(SplunkLogFileStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "sendSplunkScript";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Send data to Splunk via DSL script(Deprecated)";
        }
    }

    public static class SplunkLogFileStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 774937291218385173L;
        private transient FilePath workspace;
        private transient Run build;
        private transient TaskListener listener;
        private transient EnvVars envVars;
        private transient SplunkMessageStep step;

        public SplunkLogFileStepExecution(StepContext context, SplunkMessageStep step) throws Exception {
            super(context);
            this.step = step;
            listener = context.get(TaskListener.class);
            workspace = context.get(FilePath.class);
            build = context.get(Run.class);
            envVars = context.get(EnvVars.class);
        }

        @Override
        protected Void run() throws Exception {
            throw new UnsupportedOperationException("Please configure the script on global configure page, " +
                    "custom script at step level is disabled for SECURITY-496, to prevent arbitrary groovy code execution");
        }
    }
}
