package com.splunk.splunkjenkins.listeners;


import com.splunk.splunkjenkins.Constants;
import com.splunk.splunkjenkins.model.CoverageMetricsAdapter;
import com.splunk.splunkjenkins.model.LoggingJobExtractor;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.UserActionDSL;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import com.splunk.splunkjenkins.utils.TestCaseResultUtils;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.scm.ChangeLogSet;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.Constants.*;
import static com.splunk.splunkjenkins.model.EventType.BUILD_EVENT;
import static com.splunk.splunkjenkins.utils.LogEventHelper.*;

@SuppressWarnings("unused")
@Extension
public class LoggingRunListener extends RunListener<Run> {
    private static final Logger LOG = Logger.getLogger(LoggingRunListener.class.getName());
    private final String NODE_NAME_KEY = "node";

    UserActionDSL postJobAction = new UserActionDSL();

    @Override
    public void onStarted(Run run, TaskListener listener) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(BUILD_EVENT) ||
                SplunkJenkinsInstallation.get().isJobIgnored(run.getUrl())) {
            return;
        }
        Map<String, Object> event = getCommonBuildInfo(run, false);
        event.put("type", "started");
        String sourceName = SplunkJenkinsInstallation.get().getMetadataSource() + JENKINS_SOURCE_SEP + JOB_EVENT_TAG_NAME;
        SplunkLogService.getInstance().send(event, BUILD_EVENT, sourceName);
        //audit the start action
        if (event.get(Constants.USER_NAME_KEY) != null) {
            logUserAction((String) event.get(Constants.USER_NAME_KEY), Messages.audit_start_job(event.get(Constants.BUILD_ID)));
        }
        updateSlaveInfoAsync((String) event.get(NODE_NAME_KEY));
    }

    @Override
    public void onCompleted(Run run, @Nonnull TaskListener listener) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(BUILD_EVENT) ||
                SplunkJenkinsInstallation.get().isJobIgnored(run.getUrl())) {
            return;
        }
        Map<String, Object> event = getCommonBuildInfo(run, true);
        event.put("type", "completed");
        float duration = getRunDuration(run);
        event.put("job_duration", duration);
        event.put(JOB_RESULT, run.getResult().toString());
        Map testSummary = TestCaseResultUtils.getSummary(run);
        if (!testSummary.isEmpty()) {
            event.put("test_summary", testSummary);
        }
        //get coverage summary
        Map coverage = CoverageMetricsAdapter.getMetrics(run);
        if (!coverage.isEmpty()) {
            event.put("coverage", coverage);
        }
        event.putAll(getScmInfo(run));
        if (run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) run;
            List<String> changelog = getChangeLog(build);

            if (!changelog.isEmpty()) {
                event.put("changelog", changelog);
            }
        }
        String sourceName = SplunkJenkinsInstallation.get().getMetadataSource(JOB_EVENT_TAG_NAME);
        SplunkLogService.getInstance().send(event, BUILD_EVENT, sourceName);
        //custom event processing dsl
        postJobAction.perform(run, listener, SplunkJenkinsInstallation.get().getScript());

        if (run.getExecutor() != null) {
            //JdkSplunkLogHandler.LogHolder.getSlaveLog(run.getExecutor().getOwner());
            updateSlaveInfoAsync((String) event.get(NODE_NAME_KEY));
        }
        //remove cached values
        LoggingQueueListener.expire(run.getQueueId());
        recordAbortAction(run);
    }

    /**
     * @param run Jenkins job Run
     * @return the upstream job url
     */
    private String getUpStreamUrl(Run run) {
        for (CauseAction action : run.getActions(CauseAction.class)) {
            Cause.UpstreamCause upstreamCause = action.findCause(Cause.UpstreamCause.class);
            if (upstreamCause != null) {
                return upstreamCause.getUpstreamUrl() + upstreamCause.getUpstreamBuild() + "/";
            }
        }
        return "";
    }

    /**
     * @param run Jenkins job run
     * @return causes separated by comma
     */
    private String getBuildCauses(Run run) {
        Set<String> causes = new LinkedHashSet<>();
        for (CauseAction action : run.getActions(CauseAction.class)) {
            for (Cause cause : action.getCauses()) {
                causes.add(cause.getShortDescription());
            }
        }
        for (InterruptedBuildAction action : run.getActions(InterruptedBuildAction.class)) {
            for (CauseOfInterruption cause : action.getCauses()) {
                causes.add(cause.getShortDescription());
            }
        }
        return StringUtils.join(causes, ", ");
    }

    /**
     * @param run Jenkins build run
     * @return Build event which are common both to start/complete event
     * should not reference some fields only available after build such as result or duration
     */
    private Map<String, Object> getCommonBuildInfo(Run run, boolean completed) {
        Map<String, Object> event = new HashMap();
        event.put(Constants.TAG, JOB_EVENT_TAG_NAME);
        event.put("build_number", run.getNumber());
        event.put("trigger_by", getBuildCauses(run));
        event.put(Constants.USER_NAME_KEY, getTriggerUserName(run));
        long queueId = run.getQueueId();
        Float queueTime = LoggingQueueListener.getQueueTime(queueId);
        event.put("queue_time", queueTime);
        event.put("queue_id", queueId);
        event.put(Constants.BUILD_ID, run.getUrl());
        event.put("upstream", getUpStreamUrl(run));
        event.put("job_started_at", run.getTimestampString2());
        event.put("job_name", run.getParent().getFullName());
        Map parameters = getBuildVariables(run);
        if (!parameters.isEmpty()) {
            event.put(BUILD_REPORT_ENV_TAG, parameters);
        }
        if (run.getParent() instanceof Describable) {
            String jobType = ((Describable) run.getParent()).getDescriptor().getDisplayName();
            event.put("job_type", jobType);
        }
        Executor executor = run.getExecutor();
        String nodeName = null;
        String label=null;
        if (executor != null && executor.getOwner().getNode()!=null) {
            label=executor.getOwner().getNode().getLabelString();
            nodeName = executor.getOwner().getName();
            if (StringUtils.isEmpty(nodeName)) {
                nodeName = Constants.MASTER;
            }
        }else if(run instanceof AbstractBuild){
            nodeName=((AbstractBuild) run).getBuiltOnStr();
        }
        event.put("label",label);
        event.put(NODE_NAME_KEY, nodeName);
        for (LoggingJobExtractor extendListener : LoggingJobExtractor.canApply(run)) {
            try {
                Map<String, Object> extend = extendListener.extract(run, completed);
                if (extend != null && !extend.isEmpty()) {
                    event.putAll(extend);
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "failed to extract job info", e);
            }
        }
        return event;
    }

    /**
     * Send audit information
     *
     * @param run Jenkins job run
     */
    private void recordAbortAction(Run run) {
        List<InterruptedBuildAction> actions = run.getActions(InterruptedBuildAction.class);
        for (InterruptedBuildAction action : actions) {
            List<CauseOfInterruption.UserInterruption> interrupts = Util.filter(action.getCauses(), CauseOfInterruption.UserInterruption.class);
            if (!interrupts.isEmpty()) { //contains at most one record
                User user = interrupts.get(0).getUser();
                if (user != null) {
                    logUserAction(user.getFullName(), Messages.audit_abort_job(run.getUrl()));
                    break;
                }
            }
        }

    }

    /**
     * @param build Jenkins job build
     * @return scm change log
     */
    private List<String> getChangeLog(AbstractBuild build) {
        //check changelog
        List<String> changelog = new ArrayList<>();
        if (build.hasChangeSetComputed()) {
            ChangeLogSet<? extends ChangeLogSet.Entry> changeset = build.getChangeSet();
            for (ChangeLogSet.Entry entry : changeset) {
                StringBuilder sbr = new StringBuilder();
                sbr.append(entry.getTimestamp());
                sbr.append(SEPARATOR).append("commit:").append(entry.getCommitId());
                sbr.append(SEPARATOR).append("author:").append(entry.getAuthor());
                sbr.append(SEPARATOR).append("message:").append(entry.getMsg());
                changelog.add(sbr.toString());
            }
        }
        return changelog;
    }
}
