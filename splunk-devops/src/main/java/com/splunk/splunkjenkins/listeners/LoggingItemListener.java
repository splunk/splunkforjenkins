package com.splunk.splunkjenkins.listeners;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;

import java.io.File;

import static com.splunk.splunkjenkins.utils.LogEventHelper.getRelativeJenkinsHomePath;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getUserName;
import static com.splunk.splunkjenkins.utils.LogEventHelper.logUserAction;

@Extension
public class LoggingItemListener extends ItemListener {
    @Override
    public void onCreated(Item item) {
        logUserAction(getUserName(), Messages.audit_create_item(getConfigPath(item)));
    }

    @Override
    public void onCopied(Item src, Item item) {
        logUserAction(getUserName(), Messages.audit_cloned_item(getConfigPath(item), getConfigPath(src)));
    }

    @Override
    public void onDeleted(Item item) {
        logUserAction(getUserName(), Messages.audit_delete_item(getConfigPath(item)));
    }

    @Override
    public void onRenamed(Item item, String oldName, String newName) {
        //no-op, we use onLocationChanged
    }

    @Override
    public void onUpdated(Item item) {
        //prior to delete, makeDisabled was called and onUpdated is triggered
        logUserAction(getUserName(), Messages.audit_update_item(getConfigPath(item)));
    }

    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {
        logUserAction(getUserName(), Messages.audit_rename_item(oldFullName, newFullName));
    }

    private String getConfigPath(Item item) {
        if (item == null) {
            return "unknown";
        }
        return getRelativeJenkinsHomePath(item.getRootDir() + File.separator + "config.xml");
    }

    @Override
    public void onBeforeShutdown() {
        SplunkLogService.getInstance().stopWorker();
        SplunkLogService.getInstance().releaseConnection();
    }
}
