package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.LogEventHelper;
import hudson.init.Initializer;
import jenkins.util.Timer;

import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.JOB_LOADED;

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("LG_LOST_LOGGER_DUE_TO_WEAK_REFERENCE")
public class LoggingInitStep {
    private final static String rootLoggerName = "";

    @Initializer(after = JOB_LOADED)
    public static void setupSplunkJenkins() {
        Timer.get().schedule(new Runnable() {
            @Override
            public void run() {
                registerHandler();
            }
        }, 3, TimeUnit.MINUTES);
    }

    protected static void registerHandler() {
        Handler[] handlers = Logger.getLogger(rootLoggerName).getHandlers();
        for (Handler handler : handlers) {
            if (handler instanceof JdkSplunkLogHandler) {
                // already registered
                return;
            }
        }
        //only log warning message for HealthMonitor which runs every 20s
        Logger.getLogger(HealthMonitor.class.getName()).setLevel(Level.WARNING);
        Logger.getLogger(rootLoggerName).addHandler(JdkSplunkLogHandler.LogHolder.LOG_HANDLER);
        //init plugin
        SplunkJenkinsInstallation.get().updateCache();
        SplunkJenkinsInstallation.markComplete(true);
        Logger.getLogger(LoggingInitStep.class.getName()).info("plugin splunk-devops version " + LogEventHelper.getBuildVersion() + " loaded");
    }

}
