package com.splunk.splunkjenkins;


import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.EnvVars;
import hudson.model.*;
import hudson.model.listeners.RunListener;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import org.apache.commons.lang.StringUtils;

import java.util.*;

import static com.splunk.splunkjenkins.Constants.JOB_RESULT;
import static com.splunk.splunkjenkins.model.EventType.BUILD_EVENT;
import static com.splunk.splunkjenkins.utils.LogEventHelper.SEPARATOR;

@SuppressWarnings("unused")
@Extension
public class LoggingRunListener extends RunListener<Run> {
    UserActionDSL postJobAction = new UserActionDSL();

    @Override
    public void onStarted(Run run, TaskListener listener) {
        Map event = getCommonBuildInfo(run);
        event.put("type", "started");
        SplunkLogService.getInstance().send(event, BUILD_EVENT);
    }

    private String getUpStreamUrl(Run run) {
        StringBuilder buf = new StringBuilder(100);
        for (CauseAction action : run.getActions(CauseAction.class)) {
            Cause.UpstreamCause upstreamCause = action.findCause(Cause.UpstreamCause.class);
            if (upstreamCause != null) {
                return upstreamCause.getUpstreamUrl() + upstreamCause.getUpstreamBuild() + "/";
            }
        }
        return "";
    }

    private String getBuildCauses(Run run) {
        StringBuilder buf = new StringBuilder(100);
        for (CauseAction action : run.getActions(CauseAction.class)) {
            for (Cause cause : action.getCauses()) {
                //append all causes
                if (buf.length() > 0) buf.append(", ");
                buf.append(cause.getShortDescription());
            }
        }
        return buf.toString();
    }

    private Map getScmInfo(AbstractBuild build) {
        Map event = new HashMap();
        if (build.getProject().getScm() != null) {
            SCM scm = build.getProject().getScm();
            try {
                EnvVars envVars = build.getEnvironment(TaskListener.NULL);
                String className = scm.getClass().getName();
                //not support GIT_URL_N or SVN_URL_n
                // scm can be found at https://wiki.jenkins-ci.org/display/JENKINS/Plugins
                if (className.equals("hudson.plugins.git.GitSCM")) {
                    event.put("scm", "git");
                    event.put("scm_url", getScmURL(envVars, "GIT_URL"));
                    event.put("branch", envVars.get("GIT_BRANCH"));
                    event.put("revision", envVars.get("GIT_COMMIT"));
                } else if (className.equals("hudson.scm.SubversionSCM")) {
                    event.put("scm", "svn");
                    event.put("scm_url", getScmURL(envVars, "SVN_URL"));
                    event.put("revision", envVars.get("SVN_REVISION"));
                } else if (className.equals("org.jenkinsci.plugins.p4.PerforceScm")) {
                    event.put("scm", "p4");
                    event.put("p4_client", envVars.get("P4_CLIENT"));
                    event.put("revision", envVars.get("P4_CHANGELIST"));
                } else if (className.equals("hudson.scm.NullSCM")) {
                    event.put("scm", "");
                } else {
                    event.put("scm", className);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return event;
    }

    private String getScmURL(EnvVars envVars, String prefix) {
        String value = envVars.get(prefix);
        if (value == null) {
            List<String> urls = new ArrayList();
            //just probe max 10 url
            for (int i = 0; i < 10; i++) {
                String probe_url = envVars.get(prefix + "_" + i);
                if (probe_url != null) {
                    urls.add(probe_url);
                } else {
                    break;
                }
            }
            if (!urls.isEmpty()) {
                value = StringUtils.join(urls, ",");
            }
        }
        return value;
    }

    /**
     * @param run
     * @return Build event which are common both to start/complete event
     * should not reference some fields only available after build such as result or duration
     */
    private Map<String, Object> getCommonBuildInfo(Run run) {
        Map event = new HashMap();
        event.put(Constants.TAG, Constants.JOB_EVENT_TAG_NAME);
        event.put("build_number", run.getNumber());
        event.put("trigger_by", getBuildCauses(run));
        float queueTime = (run.getStartTimeInMillis() - run.getTimeInMillis()) / 1000;
        event.put("queue_time", queueTime);
        event.put(Constants.BUILD_ID, run.getUrl());
        event.put("upstream", getUpStreamUrl(run));
        if (!(run instanceof AbstractBuild)) {
            event.put("message", "unknown build type" + run.getClass().getName());
            return event;
        }
        AbstractBuild build = (AbstractBuild) run;
        String jenkinsNode = (StringUtils.isEmpty(build.getBuiltOnStr()) ? Constants.MASTER : build.getBuiltOnStr());
        event.put("node", jenkinsNode);
        event.put("job_name", build.getProject().getUrl());
        event.put("job_started_at", build.getTimestampString2());
        if (build.getProject() instanceof Describable) {
            String jobType = ((Describable) build.getProject()).getDescriptor().getDisplayName();
            event.put("job_type", jobType);
        }
        return event;
    }

    @Override
    public void onCompleted(Run run, @Nonnull TaskListener listener) {
        Map event = getCommonBuildInfo(run);
        if (run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) run;
            postJobAction.perform(build, listener);
            event.put(JOB_RESULT, build.getResult().toString());
            event.put("job_duration", build.getDuration() / 1000);
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
            Map testSummary = new HashMap();
            //check test summary
            if (build.getProject().getPublishersList().get(JUnitResultArchiver.class) != null) {
                TestResultAction resultAction = build.getAction(TestResultAction.class);
                if (resultAction != null && resultAction.getResult() != null) {
                    TestResult testResult = resultAction.getResult();
                    testSummary.put("fail", testResult.getFailCount());
                    testSummary.put("pass", testResult.getPassCount());
                    testSummary.put("skip", testResult.getSkipCount());
                    testSummary.put("duration", testResult.getDuration());
                }
            }
            event.put("type", "completed");
            if (!testSummary.isEmpty()) {
                event.put("test_summary", testSummary);
            }
            if (!changelog.isEmpty()) {
                event.put("changelog", changelog);
            }
            event.putAll(getScmInfo(build));
        }
        SplunkLogService.getInstance().send(event, BUILD_EVENT);
    }
}
