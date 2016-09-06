package com.splunk.splunkjenkins;

import hudson.init.Initializer;
import hudson.util.PluginServletFilter;

import javax.servlet.ServletException;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.JOB_LOADED;

public class LoggingInitStep {

    @Initializer(after = JOB_LOADED)
    public static void forwardJdkLog() {
        Logger.getLogger("").addHandler(JdkSplunkLogHandler.LogHolder.LOG_HANDLER);
        try {
            PluginServletFilter.addFilter(new WebAuditFilter());
        } catch (ServletException e) {
            e.printStackTrace();
        }
    }

}
