package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;

import static com.splunk.splunkjenkins.Constants.TAG;
import static com.splunk.splunkjenkins.utils.EventType.QUEUE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.SEPARATOR;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getQueueInfo;

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
    private static final String TAG_SUFFIX = SEPARATOR + TAG + "=queue";

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        String name = getTaskName(wi.task);
        String message = getQueueInfo() + SEPARATOR + "action=enqueue" + SEPARATOR + "item=" + name + TAG_SUFFIX;
        SplunkLogService.getInstance().send(message, QUEUE_INFO);
    }

    @Override
    public void onLeft(Queue.LeftItem li) {
        String name = getTaskName(li.task);
        String message = getQueueInfo() + SEPARATOR + "action=dequeue" + SEPARATOR + "item=" + name + TAG_SUFFIX;
        SplunkLogService.getInstance().send(message, QUEUE_INFO);
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
