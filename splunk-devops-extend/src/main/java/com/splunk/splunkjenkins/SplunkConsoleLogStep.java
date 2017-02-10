package com.splunk.splunkjenkins;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class SplunkConsoleLogStep extends Step {
    @DataBoundConstructor
    public SplunkConsoleLogStep() {
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ConsoleLogExecutionImpl(context);
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(ConsoleLogExecutionImpl.class);
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
