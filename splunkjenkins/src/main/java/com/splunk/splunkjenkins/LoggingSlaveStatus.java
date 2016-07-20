package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.splunk.splunkjenkins.model.EventType.SLAVE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.NODE_NAME;
import static com.splunk.splunkjenkins.utils.LogEventHelper.SLAVE_TAG_NAME;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getSlaveStats;

@Extension
public class LoggingSlaveStatus extends AsyncPeriodicWork {
    //make sure no less than 3 minutes, default is 10 minutes
    public static long recurrencePeriod = TimeUnit.MINUTES.toMillis(Math.max(3, Long.getLong("com.splunk.splunkjenkins.slaveMonitorMinutes", 10)));
    private Set<String> slaveNames = new HashSet<>();

    public LoggingSlaveStatus() {
        super("Splunk Slave monitor");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("execute start");
        Map<String,Map<String,Object>> slaveStats = getSlaveStats();
        listener.getLogger().println("collected "+slaveStats.keySet().size()+" slaves, previous slaves count "+slaveNames.size());
        Set<String> aliveSlaves = slaveStats.keySet();
        //send event one by one instead of list to aid search
        for(Map slaveInfo: slaveStats.values()){
            SplunkLogService.getInstance().send(slaveInfo, SLAVE_INFO);
        }
        for (String slaveName : slaveNames) {
            if (!aliveSlaves.contains(slaveName)) {
                Map event = new HashMap();
                event.put("tag", SLAVE_TAG_NAME);
                event.put(NODE_NAME, slaveName);
                event.put("status", "removed");
                SplunkLogService.getInstance().send(event, SLAVE_INFO);
            }
        }
        //replace slave names, at one time should only one thread is running, so it is save
        slaveNames = aliveSlaves;
        listener.getLogger().println("execute completed");
    }

    @Override
    public long getRecurrencePeriod() {
        return recurrencePeriod;
    }
}
