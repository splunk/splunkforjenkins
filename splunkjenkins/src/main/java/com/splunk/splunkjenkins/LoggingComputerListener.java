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

import static com.splunk.splunkjenkins.Constants.TAG;
import static com.splunk.splunkjenkins.utils.EventType.QUEUE_INFO;
import static com.splunk.splunkjenkins.utils.LogEventHelper.SEPARATOR;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getQueueInfo;

@SuppressWarnings("unused")
@Extension
public class LoggingComputerListener extends ComputerListener {
    private static final String TAG_SUFFIX = SEPARATOR + TAG + "=slave";

    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        String message = getQueueInfo() + SEPARATOR + "action=online" + SEPARATOR + "item=" + c.getName() + TAG_SUFFIX;
        SplunkLogService.getInstance().send(message, QUEUE_INFO);
    }

    @Override
    public void onOffline(@Nonnull Computer c, @CheckForNull OfflineCause cause) {
        String message = getQueueInfo() + SEPARATOR + "action=offline" + SEPARATOR + "item=" + c.getName();
        if (cause != null) {
            message = message + SEPARATOR + "cause=" + cause.toString();
        }
        SplunkLogService.getInstance().send(message, QUEUE_INFO);
    }

    @Override
    public void onTemporarilyOnline(Computer c) {
        String message = getQueueInfo() + SEPARATOR + "action=temp_offline" + SEPARATOR + "item=" + c.getName() + TAG_SUFFIX;
        SplunkLogService.getInstance().send(message, QUEUE_INFO);
    }
}
