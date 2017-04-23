package com.splunk.splunkjenkins.listeners;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.slaves.OfflineCause;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;

import static com.splunk.splunkjenkins.Constants.EVENT_CAUSED_BY;
import static com.splunk.splunkjenkins.model.EventType.SLAVE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getComputerStatus;

@SuppressWarnings("unused")
@Extension
public class LoggingComputerListener extends ComputerListener {
    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        updateStatus(c, "Online");
        listener.getLogger().flush();
    }

    @Override
    public void onOffline(@Nonnull Computer c, @CheckForNull OfflineCause cause) {
        updateStatus(c, "Offline");
    }

    @Override
    public void onTemporarilyOnline(Computer c) {
        updateStatus(c, "Temporarily Online");
    }

    @Override
    public void onTemporarilyOffline(Computer c, OfflineCause cause) {
        updateStatus(c, "Temporarily Offline");
    }

    @Override
    public void onLaunchFailure(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
        updateStatus(c, "Launch Failure");
        taskListener.getLogger().flush();
    }

    private void updateStatus(Computer c, String eventSource) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(SLAVE_INFO)) {
            return;
        }
        Map slaveInfo = getComputerStatus(c);
        slaveInfo.put(EVENT_CAUSED_BY, eventSource);
        SplunkLogService.getInstance().send(slaveInfo, SLAVE_INFO);
    }

}
