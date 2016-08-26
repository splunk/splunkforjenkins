package com.splunk.splunkjenkins;

import com.google.common.io.NullOutputStream;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.slaves.ComputerListener;
import hudson.slaves.OfflineCause;
import org.apache.commons.io.IOUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.splunk.splunkjenkins.model.EventType.QUEUE_INFO;
import static com.splunk.splunkjenkins.model.EventType.SLAVE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getMasterStats;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getSlaveStats;

@SuppressWarnings("unused")
@Extension
public class LoggingComputerListener extends ComputerListener {
    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        Map event = getComputerEvent(c, null);
        event.put("type", "online");
        SplunkLogService.getInstance().send(event, QUEUE_INFO);
    }

    private Set<String> getLabels(Set<LabelAtom> labels) {
        Set<String> set = new HashSet<>();
        for (LabelAtom label : labels) {
            set.add(label.toString());
        }
        return set;
    }

    @Override
    public void onOffline(@Nonnull Computer c, @CheckForNull OfflineCause cause) {
        try {
            sendSlaveLog(c);
        } catch (Exception e) {
            //just ignore
        }
        Map event = getComputerEvent(c, cause);
        event.put("type", "offline");
        SplunkLogService.getInstance().send(event, QUEUE_INFO);
    }

    private Map getComputerEvent(Computer c, OfflineCause cause) {
        Map event = getMasterStats();
        event.put(Constants.TAG, Constants.QUEUE_TAG_NAME);
        event.put("num_executors", c.getNumExecutors());
        event.put("node_name", getSlaveName(c));
        Node node = c.getNode();
        if (node != null) {
            event.put("label", node.getLabelString());
        }
        if (cause != null) {
            event.put("cause", cause.toString());
        }
        return event;
    }

    @Override
    public void onTemporarilyOffline(Computer c, OfflineCause cause) {
        Map event = getComputerEvent(c, cause);
        event.put("type", "temp_offline");
        SplunkLogService.getInstance().send(event, QUEUE_INFO);
        SplunkLogService.getInstance().send(getSlaveStats().values(), SLAVE_INFO);
    }

    @Override
    public void onLaunchFailure(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
        sendSlaveLog(c);
    }

    private String getSlaveName(Computer computer) {
        String name = computer.getName();
        if ("".equals(name)) {
            return Constants.MASTER;
        } else {
            return name;
        }
    }

    private void sendSlaveLog(Computer c) throws IOException {
        // for each new launch (or launch attempt) after being disconnected, this log file is rotated.
        // just send whole log
        File slaveLog = c.getLogFile();
        if (!slaveLog.exists()) {
            return;
        }
        OutputStream out = new TeeConsoleLogFilter.TeeOutputStream(new NullOutputStream(), c.getUrl() + "log");
        try (FileInputStream in = new FileInputStream(slaveLog)) {
            IOUtils.copy(in, out);
        } finally {
            out.close();
        }
    }
}
