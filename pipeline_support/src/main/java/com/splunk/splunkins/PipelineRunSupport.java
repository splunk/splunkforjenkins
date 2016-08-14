package com.splunk.splunkins;

import com.cloudbees.workflow.rest.external.ErrorExt;
import com.cloudbees.workflow.rest.external.RunExt;
import com.cloudbees.workflow.rest.external.StageNodeExt;
import com.splunk.splunkjenkins.LoggingJobExtractor;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Extension
public class PipelineRunSupport extends LoggingJobExtractor<WorkflowRun> {
    @Override
    public Map<String, Object> extract(WorkflowRun workflowRun, boolean jobCompleted) {
        Map<String, Object> info = new HashMap<String, Object>();
        if (jobCompleted) {
            RunExt runExt = RunExt.createOld(workflowRun);
            List<StageNodeExt> nodes = runExt.getStages();
            if (!nodes.isEmpty()) {
                List<Map> stages = new ArrayList<Map>(nodes.size());
                for (StageNodeExt stageNodeExt : nodes) {
                    Map<String, Object> stage = new HashMap();
                    ErrorExt error = stageNodeExt.getError();
                    stage.put("name", stageNodeExt.getName());
                    stage.put("id", stageNodeExt.getId());
                    stage.put("status", stageNodeExt.getStatus().toString());
                    stage.put("error", error);
                    stage.put("duration", stageNodeExt.getDurationMillis() / 1000f);
                    stage.put("pause_duration", stageNodeExt.getPauseDurationMillis() / 1000f);
                    stage.put("start_time", stageNodeExt.getStartTimeMillis() / 1000);
                    if (error != null) {
                        stage.put("error", error.getMessage());
                    }
                    stages.add(stage);
                }
                info.put("stages", stages);
            }
        }
        return info;
    }
}
