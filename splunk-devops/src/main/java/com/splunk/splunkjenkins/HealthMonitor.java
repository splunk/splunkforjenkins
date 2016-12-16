package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.*;
import hudson.model.Queue;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.splunk.splunkjenkins.model.EventType.QUEUE_INFO;
import static com.splunk.splunkjenkins.model.EventType.SLAVE_INFO;
import static com.splunk.splunkjenkins.Constants.NODE_NAME;
import static com.splunk.splunkjenkins.Constants.SLAVE_TAG_NAME;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getMasterStats;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getSlaveStats;

@Extension
public class HealthMonitor extends AsyncPeriodicWork {
    //make sure no less than 2 minutes, default is 8 minutes
    private long slaveUpdatePeriod = TimeUnit.MINUTES.toMillis(Math.max(2, Long.getLong("com.splunk.splunkjenkins.slaveMonitorMinutes", 8)));
    private long period = TimeUnit.SECONDS.toMillis(Math.max(20, Long.getLong("com.splunk.splunkjenkins.queueMonitorSeconds", 45)));

    private Set<String> slaveNames = new HashSet<>();
    //use protected to allow tweak it in testcase
    protected long lastAccessTime = System.currentTimeMillis();

    public HealthMonitor() {
        super("Splunk data monitor");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        sendPendingQueue();
        if (System.currentTimeMillis() - lastAccessTime < slaveUpdatePeriod) {
            return;
        }
        lastAccessTime = System.currentTimeMillis();
        sendSlaveUpdate();
        listener.getLogger().println("execute sendSlaveUpdate");
    }

    private void sendSlaveUpdate() {
        Map<String, Map<String, Object>> slaveStats = getSlaveStats();
        Set<String> aliveSlaves = slaveStats.keySet();
        //send event one by one instead of list to aid search
        SplunkLogService.getInstance().sendBatch(slaveStats.values(), SLAVE_INFO);
        List<Map> removedSlavs = new ArrayList<>();
        for (String slaveName : slaveNames) {
            if (!aliveSlaves.contains(slaveName)) {
                Map event = new HashMap();
                event.put(Constants.TAG, SLAVE_TAG_NAME);
                event.put(NODE_NAME, slaveName);
                event.put("status", "removed");
                removedSlavs.add(event);
            }
        }
        SplunkLogService.getInstance().sendBatch(removedSlavs, SLAVE_INFO);
        //replace slave names, at one time should only one thread is running, so modify slaveNames is safe without lock
        slaveNames = aliveSlaves;
        //update master stats
        Map masterEvent = getMasterStats();
        masterEvent.put("item", name);
        masterEvent.put(Constants.TAG, Constants.QUEUE_TAG_NAME);
        SplunkLogService.getInstance().send(masterEvent, QUEUE_INFO);
    }

    private void sendPendingQueue() {
        //send queue items
        Queue.Item[] items = Jenkins.getInstance().getQueue().getItems();
        List<Map> queue = new ArrayList<>(items.length);
        for (int i = 0; i < items.length; i++) {
            Queue.Item item = items[i];
            Map queueItem = new HashMap();
            queueItem.put("queue_id", item.getId());
            queueItem.put("queue_time", (System.currentTimeMillis() - item.getInQueueSince()) / 1000f);
            queueItem.put("stuck", item.isStuck());
            queueItem.put("block_reason", item.getWhy());
            queueItem.put("task", item.task.getUrl());
            queueItem.put("concurrent_build", item.task.isConcurrentBuild());
            queueItem.put(Constants.TAG, Constants.QUEUE_WAITING_ITEM_NAME);
            queue.add(queueItem);
        }
        SplunkLogService.getInstance().sendBatch(queue, QUEUE_INFO);
    }

    @Override
    public long getRecurrencePeriod() {
        return period;
    }
}
