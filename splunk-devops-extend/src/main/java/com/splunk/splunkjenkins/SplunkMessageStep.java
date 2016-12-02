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

import javax.annotation.Nonnull;
import javax.inject.Inject;

import static hudson.Util.fixNull;

/**
 * Send Message to splunk
 */
public class SplunkMessageStep extends AbstractStepImpl {
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
            return "Send data to Splunk via DSL script";
        }
    }

    public static class SplunkLogFileStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 774937291218385173L;
        @StepContextParameter
        private transient FilePath workspace;
        @StepContextParameter
        private transient Run build;
        @StepContextParameter
        private transient TaskListener listener;
        @StepContextParameter
        private transient EnvVars envVars;
        @Inject
        private transient SplunkMessageStep step;

        @Override
        protected Void run() throws Exception {
            UserActionDSL scriptJobAction = new UserActionDSL();
            String dslScript;
            if (step.globalScriptEnabled) {
                dslScript = SplunkJenkinsInstallation.get().getScript() + "\n" + step.getScriptText();
            } else {
                dslScript = step.getScriptText();
            }
            scriptJobAction.perform(build, listener, dslScript);
            return null;
        }
    }
}
