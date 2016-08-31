package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.model.EventType;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.init.Initializer;
import hudson.util.HudsonIsLoading;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;
import java.util.regex.Pattern;

import static hudson.init.InitMilestone.JOB_LOADED;

public class JdkSplunkLogHandler extends Handler {
    private static final Pattern CAPTURE_PATTERN = Pattern.compile("^(hudson|jenkins)");
    private String packageName = "com.splunk.splunkjenkins";
    private LogEventFormatter formatter = new LogEventFormatter();

    @Override
    public void publish(LogRecord record) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null || !SplunkJenkinsInstallation.loaded) {
            return;
        }
        if (jenkins.servletContext.getAttribute("app") instanceof HudsonIsLoading) {
            //no-op since jenkins is still loading
            return;
        }
        String logger = record.getLoggerName();
        if (StringUtils.startsWith(logger, packageName)) {
            //avoid sending splunkjenkins JDK logs which will cause recursive call
            return;
        }
        if (record.getLevel().intValue() < Level.INFO.intValue() && !CAPTURE_PATTERN.matcher(logger).find()) {
            return;
        }
        SplunkLogService.getInstance().send(formatter.getEvent(record), EventType.CONSOLE_LOG, "logger://" + logger);
    }

    @Override
    public void flush() {
        String stats = SplunkLogService.getInstance().getStats();
        SplunkLogService.getInstance().send(stats, "logger://com.splunk.splunkjenkins");
    }

    @Override
    public void close() throws SecurityException {

    }

    public static class LogEventFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return formatMessage(record);
        }

        public Map getEvent(LogRecord record) {
            Map event = new HashMap<>();
            event.put("level", record.getLevel().getName());
            event.put("level_int", record.getLevel().intValue());
            event.put("message", formatMessage(record));
            String source;
            if (record.getSourceClassName() != null) {
                source = record.getSourceClassName();
                if (record.getSourceMethodName() != null) {
                    source += " " + record.getSourceMethodName();
                }
            } else {
                source = record.getLoggerName();
            }
            event.put("log_source", source);
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                event.put("throwable", sw.toString());
            }
            return event;
        }
    }

    @Initializer(after = JOB_LOADED)
    public static void forwardJdkLog() {
        try {
            //preload LogRecord to avoid class loading exception: java.lang.ClassCircularityError: java/util/logging/LogRecord
            Class.forName("java.util.logging.LogRecord");
            // capture jdk logging
            Logger.getLogger("").addHandler(new JdkSplunkLogHandler());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
