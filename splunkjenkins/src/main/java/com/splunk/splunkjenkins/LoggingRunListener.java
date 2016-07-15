package com.splunk.splunkjenkins;


import com.google.common.collect.ImmutableMap;
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
import static com.splunk.splunkjenkins.utils.EventType.BUILD_EVENT;
import static com.splunk.splunkjenkins.utils.LogEventHelper.SEPARATOR;

@SuppressWarnings("unused")
@Extension
public class LoggingRunListener extends RunListener<Run> {
    UserActionDSL postJobAction = new UserActionDSL();

    @Override
    public void onStarted(Run run, TaskListener listener) {

        Map event = ImmutableMap.builder()
                .put(Constants.TAG, "job_event")
                .put(Constants.BUILD_ID, run.getUrl())
                .put("trigger_by", getBuildCause(run))
                .put("build_event", "started").build();
        SplunkLogService.getInstance().send(event, BUILD_EVENT);
    }

    private String getBuildCause(Run run) {
        StringBuilder buf = new StringBuilder(100);
        for (CauseAction action : run.getActions(CauseAction.class)) {
            for (Cause cause : action.getCauses()) {
                if (cause instanceof Cause.UpstreamCause) {
                    //find nearest upstream
                    Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;
                    //upstream url is project url, build is build number
                    return upstreamCause.getUpstreamUrl() + "/" + upstreamCause.getUpstreamBuild() + "/";
                }
                //manual triggered
                if (buf.length() > 0) buf.append(", ");
                buf.append(cause.getShortDescription());
            }
        }
        if (buf.length() == 0) {
            buf.append("system");
        }
        return buf.toString();
    }

    private Map getScmInfo(AbstractBuild build) {
        Map event =new HashMap();
        if (build.getProject().getScm() != null) {
            SCM scm = build.getProject().getScm();
            try {
                EnvVars envVars = build.getEnvironment(TaskListener.NULL);
                String className = scm.getClass().getName();
                //not support GIT_URL_N or SVN_URL_n
                // scm can be found at https://wiki.jenkins-ci.org/display/JENKINS/Plugins
                if (className.equals("hudson.plugins.git.GitSCM")) {
                    event.put("scm", "git");
                    event.put("scm_url", getScmURL(envVars,"GIT_URL"));
                    event.put("branch", envVars.get("GIT_BRANCH"));
                    event.put("revision", envVars.get("GIT_COMMIT"));
                } else if (className.equals("hudson.scm.SubversionSCM")) {
                    event.put("scm", "svn");
                    event.put("scm_url", getScmURL(envVars,"SVN_URL"));
                    event.put("revision", envVars.get("SVN_REVISION"));
                } else if (className.equals("org.jenkinsci.plugins.p4.PerforceScm")) {
                    event.put("scm", "svn");
                    event.put("p4_client", envVars.get("P4_CLIENT"));
                    event.put("revision", envVars.get("P4_CHANGELIST"));
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

    @Override
    public void onCompleted(Run run, @Nonnull TaskListener listener) {
        if (!(run instanceof AbstractBuild)) {
            return;
        }
        AbstractBuild build = (AbstractBuild) run;
        long queueTime = run.getStartTimeInMillis() - run.getTimeInMillis();
        String jenkinsNode = (build.getBuiltOn() == null ? "unknown" : build.getBuiltOn().getDisplayName());
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
        Map testSummay = new HashMap();
        //check test summary
        if (build.getProject().getPublishersList().get(JUnitResultArchiver.class) != null) {
            TestResultAction resultAction = build.getAction(TestResultAction.class);
            if (resultAction != null && resultAction.getResult() != null) {
                TestResult testResult = resultAction.getResult();
                testSummay.put("fail", testResult.getFailCount());
                testSummay.put("pass", testResult.getPassCount());
                testSummay.put("skip", testResult.getSkipCount());
                testSummay.put("duration", testResult.getDuration());
            }
        }
        ImmutableMap.Builder builder = ImmutableMap.builder()
                .put(Constants.TAG, "job_event")
                .put(Constants.BUILD_ID, run.getUrl())
                .put("job_name", build.getProject().getUrl())
                .put("build_number", run.getNumber())
                .put("trigger_by", getBuildCause(run))
                .put(JOB_RESULT, build.getResult().toString())
                .put("job_started_at", build.getTimestampString2())
                .put("job_duration", build.getDuration())
                .put("queue_time", queueTime)
                .put("node", jenkinsNode);
        if (!testSummay.isEmpty()) {
            builder.put("testsummay", testSummay);
        }
        if (!changelog.isEmpty()) {
            builder.put("changelog", changelog);
        }
        builder.putAll(getScmInfo(build));
        Map event = builder.build();
        SplunkLogService.getInstance().send(event, BUILD_EVENT);
        postJobAction.perform(build, listener);
    }
}
