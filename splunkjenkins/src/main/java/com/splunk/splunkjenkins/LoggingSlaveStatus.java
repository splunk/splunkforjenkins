package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Node;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.splunk.splunkjenkins.utils.EventType.QUEUE_INFO;
import static com.splunk.splunkjenkins.utils.EventType.SLAVE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getQueueInfo;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getSlaveStats;

@Extension
public class LoggingSlaveStatus extends AsyncPeriodicWork {
    public static long recurrencePeriod = Math.max(120000, Long.getLong("com.splunk.splunkjenkins.slaveStatusPeriod", TimeUnit.MINUTES.toMillis(10)));

    public LoggingSlaveStatus() {
        super("Splunk Slave monitor");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        SplunkLogService.getInstance().send(getQueueInfo(), QUEUE_INFO);
        List<Map> slaves = getSlaveStats();
        for (Map slaveInfo : slaves) {
            SplunkLogService.getInstance().send(slaveInfo, SLAVE_INFO);
        }
        //send whole slave list
        List<Node> nodes = Jenkins.getInstance().getNodes();
        List<String> names = new ArrayList();
        for (Node node : nodes) {
            //non master names
            if ("".equals(node.getNodeName())) {
                names.add(node.getNodeName());
            }
        }
        Map event = new HashMap();
        event.put("tag", "slave_list");
        event.put("names", name);
        SplunkLogService.getInstance().send(event, SLAVE_INFO);
    }

    @Override
    public long getRecurrencePeriod() {
        return recurrencePeriod;
    }
}
