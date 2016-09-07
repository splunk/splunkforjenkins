package com.splunk.splunkjenkins.listeners;

import com.splunk.splunkjenkins.Constants;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;

import java.util.Map;

import static com.splunk.splunkjenkins.model.EventType.QUEUE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getMasterStats;

/**
 *
 * <pre>{@code from jenkins javadoc
 *  (enter) --> waitingList --+--> blockedProjects
 *                            |        ^
 *                            |        |
 *                            |        v
 *                            +--> buildables ---> pending ---> left
 *                                     ^              |
 *                                     |              |
 *                                     +---(rarely)---+
 *
 * }</pre>
 *
 */
@SuppressWarnings("unused")
@Extension
public class LoggingQueueListener extends QueueListener {

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        String name = getTaskName(wi.task);
        Map event = getMasterStats();
        event.put("item", name);
        event.put(Constants.TAG,Constants.QUEUE_TAG_NAME);
        event.put("type", "enqueue");
        SplunkLogService.getInstance().send(event, QUEUE_INFO);
    }

    @Override
    public void onLeft(Queue.LeftItem li) {
        String name = getTaskName(li.task);
        Map event = getMasterStats();
        event.put("item", name);
        event.put(Constants.TAG,Constants.QUEUE_TAG_NAME);
        event.put("type", "dequeue");
        SplunkLogService.getInstance().send(event, QUEUE_INFO);
    }

    /**
     * queue task only have project name, don't have build number
     *
     * @param task Queue task
     * @return task name
     */
    public String getTaskName(Queue.Task task) {
        if (task == null) {
            return "n/a";
        } else {
            return task.getUrl();
        }

    }

}
