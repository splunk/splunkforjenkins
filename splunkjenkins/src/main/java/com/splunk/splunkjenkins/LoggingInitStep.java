package com.splunk.splunkjenkins;

import hudson.init.Initializer;

import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.JOB_LOADED;

public class LoggingInitStep {

    @Initializer(after = JOB_LOADED)
    public static void forwardJdkLog() {
        //only log warning message for HealthMonitor which runs every 20s
        Logger.getLogger(HealthMonitor.class.getName()).setLevel(Level.WARNING);
        Logger.getLogger("").addHandler(JdkSplunkLogHandler.LogHolder.LOG_HANDLER);
    }

}
