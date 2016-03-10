package com.splunk.splunkjenkins;


import com.google.common.collect.ImmutableMap;
import hudson.model.*;
import hudson.model.listeners.RunListener;

import javax.annotation.Nonnull;

import static com.splunk.logging.Constants.BUILD_ID;
import hudson.Extension;
import java.util.Map;

@SuppressWarnings("unused")
@Extension
public class LoggingRunListener extends RunListener<Run> {
    UserActionDSL postJobAction = new UserActionDSL();

    @Override
    public void onStarted(Run run, TaskListener listener) {
        Map event=ImmutableMap.of(BUILD_ID,run.getUrl(),
                "trigger_by",getBuildCause(run),
                "build_event","started");
        SplunkLogService.send(event);
    }

    private String getBuildCause(Run run) {
        StringBuilder buf = new StringBuilder(100);
        boolean blank = false;
        for (CauseAction action : run.getActions(CauseAction.class)) {
            for (Cause cause : action.getCauses()) {
                if (buf.length() > 0) buf.append(", ");
                buf.append(cause.getShortDescription());
            }
            blank = blank || true;
        }
        if (blank) {
            buf.append("unknown");
        }
        return buf.toString();
    }

    @Override
    public void onCompleted(Run run, @Nonnull TaskListener listener) {
        if (!(run instanceof AbstractBuild)) {
            return;
        }
        AbstractBuild build = (AbstractBuild) run;
        String jenkinsNode = (build.getBuiltOn() == null ? "unknown" : build.getBuiltOn().getDisplayName());
        //other messages no need to escape quote
        Map event=ImmutableMap.builder().put(BUILD_ID,run.getUrl())
                .put("trigger_by", getBuildCause(run))
                .put("job_result",build.getResult())
                .put("job_started_at",build.getTimestampString2())
                .put("job_duration",build.getDuration() + "ms")
                .put("node",jenkinsNode)
                .put("build_event","completed")
                .build();
        SplunkLogService.send(event);
        postJobAction.perform(build);
    }
}
