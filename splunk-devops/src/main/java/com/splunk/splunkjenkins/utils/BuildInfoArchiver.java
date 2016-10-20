package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.listeners.LoggingRunListener;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.util.RunList;
import jenkins.model.Jenkins;

import java.util.List;

public class BuildInfoArchiver {
    /**
     * Send existing build
     *
     * @param startTime the start time window of build
     * @param endTime   the end time window of build
     */
    public void run(long startTime, long endTime) {
        List<TopLevelItem> topItems = Jenkins.getInstance().getItems();
        for (TopLevelItem topLevelItem : topItems) {
            run(topLevelItem, startTime, endTime);
        }
    }

    /**
     * @param jobName   the job name, e.g. folder/jobname or job/folder/job/jobname
     * @param startTime the start time window of build
     * @param endTime   the end time window of build
     */
    public void run(String jobName, long startTime, long endTime) {
        String jobPath = jobName.replace("/job/", "");
        Item item = Jenkins.getInstance().getItem(jobPath, (ItemGroup) null);
        run(item, startTime, endTime);
    }

    /**
     * Send existing build
     *
     * @param item      Jenkins job item
     * @param startTime the start time window of build
     * @param endTime   the end time window of build
     */
    public void run(Item item, long startTime, long endTime) {
        if (item == null) {
            return;
        }
        if (item instanceof ItemGroup) {
            ItemGroup group = (ItemGroup) item;
            for (Object subItem : group.getItems()) {
                if (subItem instanceof Item) {
                    run(((Item) subItem).getName(), startTime, endTime);
                }
            }
        } else if (item instanceof Project) {
            Project project = (Project) item;
            RunList<Run> runList = project.getBuilds();
            LoggingRunListener runListener = RunListener.all().get(LoggingRunListener.class);
            for (Run run : runList) {
                if (run.getStartTimeInMillis() > startTime && run.getStartTimeInMillis() + run.getDuration() < endTime) {
                    runListener.onCompleted(run, TaskListener.NULL);
                }
            }
        }
    }
}
