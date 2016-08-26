package com.splunk.splunkjenkins.model;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import org.apache.commons.lang.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

public class JdkSplunkLogHandler extends Handler {
    private static final Pattern CAPTURE_PATTERN = Pattern.compile("^(hudson|jenkins)");
    private String packageName = "com.splunk.splunkjenkins";
    private LogEventFormatter formatter = new LogEventFormatter();

    @Override
    public void publish(LogRecord record) {
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
        SplunkLogService.getInstance().send(stats, "logger://" + packageName);
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
}
