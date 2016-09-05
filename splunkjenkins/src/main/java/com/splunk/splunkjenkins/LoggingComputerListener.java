package com.splunk.splunkjenkins;

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

import static com.splunk.splunkjenkins.model.EventType.SLAVE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getComputerStatus;

@SuppressWarnings("unused")
@Extension
public class LoggingComputerListener extends ComputerListener {
    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        updateStatus(c);
    }

    @Override
    public void onOffline(@Nonnull Computer c, @CheckForNull OfflineCause cause) {
        updateStatus(c);
    }

    @Override
    public void onTemporarilyOnline(Computer c) {
        updateStatus(c);
    }

    @Override
    public void onTemporarilyOffline(Computer c, OfflineCause cause) {
        Map event = getComputerStatus(c);
        event.put("temp_offline", true);
        SplunkLogService.getInstance().send(event, SLAVE_INFO);
    }

    @Override
    public void onLaunchFailure(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
        updateStatus(c);
    }

    private void updateStatus(Computer c) {
        SplunkLogService.getInstance().send(getComputerStatus(c), SLAVE_INFO);
    }

}
