package com.splunk.splunkjenkins;

import com.google.common.base.Objects;
import hudson.Extension;
import jenkins.model.OptionalJobProperty;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;

@SuppressWarnings("rawtypes")
public class SplunkPipelineJobProperty extends OptionalJobProperty<WorkflowJob> {
    Boolean enableDiagram;

    @DataBoundConstructor
    public SplunkPipelineJobProperty() {
    }

    @CheckForNull
    public Boolean getEnableDiagram() {
        return enableDiagram;
    }

    @DataBoundSetter
    public void setEnableDiagram(Boolean enableDiagram) {
        this.enableDiagram = enableDiagram;
    }

    public boolean isDiagramEnabled() {
        return Boolean.TRUE.equals(enableDiagram);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("enableDiagram", enableDiagram)
                .toString();
    }

    @Extension
    @Symbol("splunkinsJobOption")
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Opt in data sent to Splunk";
        }
    }
}
