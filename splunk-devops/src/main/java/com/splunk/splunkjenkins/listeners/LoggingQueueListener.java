package com.splunk.splunkjenkins.listeners;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.splunk.splunkjenkins.Constants;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;

import java.util.Map;

import static com.splunk.splunkjenkins.model.EventType.QUEUE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getMasterStats;

/**
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
 */
@SuppressWarnings("unused")
@Extension
public class LoggingQueueListener extends QueueListener {
    private final static Cache<Long, Float> cache = CacheBuilder.newBuilder()
            .maximumSize(3000).build();

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(QUEUE_INFO)) {
            return;
        }
        String name = getTaskName(wi.task);
        if (SplunkJenkinsInstallation.get().isJobIgnored(name)) {
            return;
        }
        Map event = getMasterStats();
        event.put("item", name);
        event.put(Constants.TAG, Constants.QUEUE_TAG_NAME);
        event.put("type", "enqueue");
        SplunkLogService.getInstance().send(event, QUEUE_INFO);
    }

    @Override
    public void onLeft(Queue.LeftItem li) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(QUEUE_INFO)) {
            return;
        }
        String name = getTaskName(li.task);
        if (SplunkJenkinsInstallation.get().isJobIgnored(name)) {
            return;
        }
        float queueTime = (System.currentTimeMillis() - li.getInQueueSince()) / 1000f;
        cache.put(li.getId(), queueTime);
        Map event = getMasterStats();
        event.put("item", name);
        event.put(Constants.TAG, Constants.QUEUE_TAG_NAME);
        event.put("queue_id", li.getId());
        event.put("queue_time", queueTime);
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

    public static Float getQueueTime(Long Id) {
        return cache.getIfPresent(Id);
    }

    public static void expire(Long Id) {
        cache.invalidate(Id);
    }

}
