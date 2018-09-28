package com.splunk.splunkjenkins;

import com.cloudbees.workflow.rest.external.*;
import com.splunk.splunkjenkins.model.LoggingJobExtractor;
import hudson.Extension;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StageChunkFinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Extension
public class PipelineRunSupport extends LoggingJobExtractor<WorkflowRun> {
    @Override
    public Map<String, Object> extract(WorkflowRun workflowRun, boolean jobCompleted) {
        Map<String, Object> info = new HashMap<String, Object>();
        if (jobCompleted) {
            FlowExecution execution = workflowRun.getExecution();
            if (execution != null) {
                ChunkVisitor visitor = new ChunkVisitor(workflowRun);
                ForkScanner.visitSimpleChunks(execution.getCurrentHeads(), visitor, new StageChunkFinder());
                Collection<StageNodeExt> nodes = visitor.getStages();
                if (!nodes.isEmpty()) {
                    List<Map> stages = new ArrayList<Map>(nodes.size());
                    for (StageNodeExt stageNodeExt : nodes) {
                        Map<String, Object> stage = flowNodeToMap(stageNodeExt);
                        List<Map<String, Object>> children = new ArrayList<>();
                        for (FlowNodeExt childNode : stageNodeExt.getStageFlowNodes()) {
                            children.add(flowNodeToMap(childNode));
                        }
                        stage.put("children", children);
                        stages.add(stage);
                    }
                    info.put("stages", stages);
                }
            }
        }
        return info;
    }

    /**
     * @param node FlowNodeExt
     * @return a map contains basic info
     */
    private Map<String, Object> flowNodeToMap(FlowNodeExt node) {
        Map<String, Object> result = new HashMap();
        ErrorExt error = node.getError();
        result.put("name", node.getName());
        result.put("id", node.getId());
        result.put("status", toResult(node.getStatus()));
        result.put("duration", node.getDurationMillis() / 1000f);
        result.put("pause_duration", node.getPauseDurationMillis() / 1000f);
        result.put("start_time", node.getStartTimeMillis() / 1000);
        if (error != null) {
            result.put("error", error.getMessage());
            result.put("error_type", error.getType());
        }
        return result;
    }

    /**
     * @param status
     * @return String compatible with hudson.model.Result
     */
    private String toResult(StatusExt status) {
        if (status == null) {
            return "UNKNOWN";
        }
        switch (status) {
            case FAILED:
                return Result.FAILURE.toString();
            case NOT_EXECUTED:
                return Result.NOT_BUILT.toString();
            default:
                return status.toString();
        }
    }
}
