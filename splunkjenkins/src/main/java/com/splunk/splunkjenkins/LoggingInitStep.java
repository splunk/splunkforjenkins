package com.splunk.splunkjenkins;

import hudson.init.Initializer;

import java.util.logging.Logger;

import static hudson.init.InitMilestone.JOB_LOADED;

public class LoggingInitStep {

    @Initializer(after = JOB_LOADED)
    public static void forwardJdkLog() {
        Logger.getLogger("").addHandler(JdkSplunkLogHandler.LogHolder.LOG_HANDLER);
    }

}
