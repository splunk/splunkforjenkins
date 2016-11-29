package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.TeeConsoleLogFilter;
import com.splunk.splunkjenkins.listeners.LoggingRunListener;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.util.IOUtils;
import hudson.util.NullStream;
import hudson.util.RunList;
import jenkins.model.Jenkins;

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

    /**
     * Send existing build
     *
     * @param startTime the start time window of build
     * @param endTime   the end time window of build
     * @return total number of builds whose result or log was resent
     */
    public int run(long startTime, long endTime) {
        List<TopLevelItem> topItems = Jenkins.getInstance().getItems();
        int count = 0;
        for (TopLevelItem topLevelItem : topItems) {
            count += run(topLevelItem, startTime, endTime);
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
        if (!jobName.startsWith("/")) {
            jobName = "/" + jobName;
        }
        String jobPath = jobName.replace("/job/", "/");
        Item item = Jenkins.getInstance().getItem(jobPath, (ItemGroup) null);
        return run(item, startTime, endTime);
    }

    /**
     * Send existing build
     *
     * @param item      Jenkins job item
     * @param startTime the start time window of build
     * @param endTime   the end time window of build
     * @return total number of builds whose result or log was resent
     */
    public int run(Item item, long startTime, long endTime) {
        if (item == null) {
            return 0;
        }
        int count = 0;
        if (item instanceof ItemGroup) {
            ItemGroup group = (ItemGroup) item;
            for (Object subItem : group.getItems()) {
                if (subItem instanceof Item) {
                    count = count + run(((Item) subItem).getFullName(), startTime, endTime);
                }
            }
        } else if (item instanceof Project) {
            Project project = (Project) item;
            RunList<Run> runList = project.getBuilds();
            LoggingRunListener runListener = RunListener.all().get(LoggingRunListener.class);
            for (Run run : runList) {
                if (processedJob.contains(run.getUrl())) {
                    continue;
                } else if (run.isBuilding() || run.getResult() == null) {
                    continue;
                }
                processedJob.add(run.getUrl());
                long jobTimestamp = run.getStartTimeInMillis() + run.getDuration();
                //check whether the build is in the time range
                if (jobTimestamp >= startTime && jobTimestamp < endTime) {
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
                } else if (run.getStartTimeInMillis() < startTime) {
                    //Job builds is ordered by start time(build id)
                    break;
                }
            }
        }
        return count;
    }
}
