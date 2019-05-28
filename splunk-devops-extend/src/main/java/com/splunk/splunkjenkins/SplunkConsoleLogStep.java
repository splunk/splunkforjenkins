package com.splunk.splunkjenkins;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Set;

public class SplunkConsoleLogStep extends Step {
    @DataBoundConstructor
    public SplunkConsoleLogStep() {
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ConsoleLogExecutionImpl(context);
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getFunctionName() {
            return "sendSplunkConsoleLog";
        }

        /**
         * {@inheritDoc}
         */
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Send console log Splunk";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }


    public static class ConsoleLogExecutionImpl extends StepExecution {
        public ConsoleLogExecutionImpl(StepContext context) {
            super(context);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean start() throws Exception {
            if (!SplunkJenkinsInstallation.get().isEnabled()) {
                getContext().onSuccess("splunk-devops plugin is not enabled");
                return true;
            }
            //refer to WithContextStep implementation
            StepContext context = getContext();
            Run run = context.get(Run.class);
            ConsoleLogFilter filter = BodyInvoker.mergeConsoleLogFilters(context.get(ConsoleLogFilter.class), new TeeConsoleLogFilter(run));
            context.newBodyInvoker().withContext(filter).withCallback(BodyExecutionCallback.wrap(context)).start();
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
            getContext().onFailure(cause);
        }
    }
}
