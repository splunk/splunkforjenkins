package com.splunk.splunkjenkins.utils;

import com.google.common.base.Predicate;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.TeeConsoleLogFilter;
import com.splunk.splunkjenkins.listeners.LoggingRunListener;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.util.IOUtils;
import hudson.util.NullStream;
import hudson.util.RunList;
import jenkins.model.Jenkins;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import static com.splunk.splunkjenkins.model.EventType.CONSOLE_LOG;

public class BuildInfoArchiver {
    Set<String> processedJob = Collections.newSetFromMap(
            new WeakHashMap<String, Boolean>());
    LoggingRunListener runListener = RunListener.all().get(LoggingRunListener.class);


    /**
     * Send existing build
     *
     * @param startTime the start time window of build
     * @param endTime   the end time window of build
     * @return total number of builds whose result or log was resent
     */
    public int run(long startTime, long endTime) {
        List<TopLevelItem> topItems = Jenkins.getInstance().getItems();
        Predicate<Run> predicate = new BuildTimePredict(startTime, endTime);
        int count = 0;
        for (TopLevelItem topLevelItem : topItems) {
            count += run(topLevelItem, predicate);
        }
        return count;
    }

    /**
     * @param jobName   the job name, e.g. /folder/jobname or /job/folder/job/jobname
     * @param startTime the start time window of build
     * @param endTime   the end time window of build
     * @return total number of builds whose result or log was resent
     */
    public int run(String jobName, long startTime, long endTime) {
        Item item = normalizeJob(jobName);
        return run(item, new BuildTimePredict(startTime, endTime));
    }

    /**
     * @param jobName   the job name, e.g. /folder/jobname or /job/folder/job/jobname
     * @param predicate function to check whether build apply
     * @return total number of builds whose result or log was resent
     */
    public int run(String jobName, Predicate<Run> predicate) {
        Item item = normalizeJob(jobName);
        return run(item, predicate);
    }

    /**
     * @param jobName
     * @return normalized job name, replaced job URL /job/ with / if necessary
     */
    private Item normalizeJob(String jobName) {
        Item item = Jenkins.getInstance().getItem(jobName, (ItemGroup) null);
        if (item != null) {
            return item;
        }
        if (!jobName.startsWith("/")) {
            jobName = "/" + jobName;
        }
        String jobPath = jobName.replace("/job/", "/");
        return Jenkins.getInstance().getItem(jobPath, (ItemGroup) null);
    }

    /**
     * Send existing build
     *
     * @param item      Jenkins job item
     * @param predicate function to check whether build apply
     * @return total number of builds whose result or log was resent
     */
    public int run(Item item, Predicate<Run> predicate) {
        if (item == null) {
            return 0;
        }
        int count = 0;
        if (item instanceof ItemGroup) {
            ItemGroup group = (ItemGroup) item;
            for (Object subItem : group.getItems()) {
                if (subItem instanceof Item) {
                    count = count + run((Item) subItem, predicate);
                }
            }
        } else if (item instanceof Project) {
            Project project = (Project) item;
            RunList<Run> runList = project.getBuilds();
            for (Run run : runList) {
                if (processedJob.contains(run.getUrl())) {
                    continue;
                } else if (run.isBuilding() || run.getResult() == null) {
                    continue;
                }
                processedJob.add(run.getUrl());
                //check whether the build is in the time range
                if (predicate.apply(run)) {
                    count++;
                    //resend build event
                    runListener.onCompleted(run, TaskListener.NULL);
                    if (SplunkJenkinsInstallation.get().isEventDisabled(CONSOLE_LOG)) {
                        continue;
                    }
                    //resend console logs, but with current timestamp
                    try (InputStream input = run.getLogInputStream()) {
                        TeeConsoleLogFilter.TeeOutputStream outputStream =
                                new TeeConsoleLogFilter.TeeOutputStream(new NullStream(), true, run.getUrl() + "console");
                        IOUtils.copy(input, outputStream);
                        outputStream.flush();
                        outputStream.close();
                    } catch (IOException e) {
                        //just ignore
                    }
                }
            }
        }
        return count;
    }

    public static class BuildTimePredict implements Predicate<Run> {
        long startTime, endTime;

        public BuildTimePredict(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        @Override
        public boolean apply(@Nullable Run run) {
            long jobTimestamp = run.getStartTimeInMillis() + run.getDuration();
            //check whether the build is in the time range
            if (jobTimestamp >= startTime && jobTimestamp < endTime) {
                return true;
            }
            return false;
        }
    }

    public static class BuildIdPredict implements Predicate<Run> {
        int startId, endId;

        public BuildIdPredict(int startId, int endId) {
            this.startId = startId;
            this.endId = endId;
        }

        @Override
        public boolean apply(@Nullable Run run) {
            //check whether the build is in the time range
            if (run.getNumber() >= startId && run.getNumber() < endId) {
                return true;
            }
            return false;
        }
    }
}
