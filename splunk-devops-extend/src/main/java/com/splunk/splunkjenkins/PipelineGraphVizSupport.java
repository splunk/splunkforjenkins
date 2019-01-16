package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.model.EventType;
import com.splunk.splunkjenkins.model.LoggingJobExtractor;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The getDot code is borrowed from https://plugins.jenkins.io/workflow-job
 */
@Extension
public class PipelineGraphVizSupport extends LoggingJobExtractor<WorkflowRun> {
    private static final String SUFFIX = "graphviz";
    private static final Logger LOGGER = Logger.getLogger(PipelineGraphVizSupport.class.getName());

    @Override
    public Map<String, Object> extract(WorkflowRun workflowRun, boolean completed) {
        if (!completed) {
            return Collections.EMPTY_MAP;
        }
        if (!SplunkJenkinsInstallation.get().isJobIgnored(workflowRun.getUrl())) {
            SplunkPipelineJobProperty jobProperty = workflowRun.getParent().getProperty(SplunkPipelineJobProperty.class);
            LOGGER.log(Level.FINE, "job {0}, property {1}", new Object[]{workflowRun.getUrl(), jobProperty});
            if (jobProperty != null && jobProperty.isDiagramEnabled()) {
                String dotStr = getDot(workflowRun);
                String source = workflowRun.getUrl() + SUFFIX;
                SplunkLogService.getInstance().send(dotStr, EventType.BUILD_EVENT, source);
            }
        }
        return Collections.EMPTY_MAP;
    }


    private String getDot(WorkflowRun run) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("digraph G {\n");
        FlowGraphWalker walker = new FlowGraphWalker(run.getExecution());
        for (FlowNode n : walker) {
            for (FlowNode p : n.getParents()) {
                buffer.append(String.format("%s -> %s%n", p.getId(), n.getId()));
            }

            if (n instanceof BlockStartNode) {
                buffer.append(String.format("%s [shape=trapezium]%n", n.getId()));
            } else if (n instanceof BlockEndNode) {
                BlockEndNode sn = (BlockEndNode) n;
                buffer.append(String.format("%s [shape=invtrapezium]%n", n.getId()));
                buffer.append(String.format("%s -> %s [style=dotted]%n", sn.getStartNode().getId(), n.getId()));
            }
            buffer.append(String.format("%s [label=\"%s: %s\"]%n", n.getId(), n.getId(), n.getDisplayName()));
        }

        buffer.append("}");
        return buffer.toString();
    }
}
