package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.LogConsumer;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.model.Computer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.*;


public class JdkSplunkLogHandler extends Handler {
    private Level filterLevel = Level.parse(System.getProperty(JdkSplunkLogHandler.class.getName() + ".level", "INFO"));
    private LogEventFormatter splunkFormatter;

    public JdkSplunkLogHandler() {
        this.splunkFormatter = new LogEventFormatter();
        setFilter(new LogFilter());
        //prevent log flood
        if (filterLevel.intValue() < Level.INFO.intValue()) {
            filterLevel = Level.INFO;
        }
        setLevel(filterLevel);
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        Map logEvent = splunkFormatter.getEvent(record);
        if (logEvent == null || logEvent.isEmpty()) {
            return;
        }
        SplunkLogService.getInstance().send(logEvent, "logger://" + record.getLoggerName());
    }

    @Override
    public void flush() {
        String stats = SplunkLogService.getInstance().getStats();
        SplunkLogService.getInstance().send(stats, "logger://com.splunk.splunkjenkins");
    }

    @Override
    public void close() throws SecurityException {

    }

    private static class LogFilter implements Filter {
        //logger may trigger recursive call, need skip them
        private final String[] skipLoggerNames = {
                SplunkLogService.class.getName(), LogConsumer.class.getName(),
                "jenkins.InitReactorRunner", "org.apache.http", "hudson.node_monitors", "hudson.Extension"};

        @Override
        public boolean isLoggable(LogRecord record) {
            if (! SplunkJenkinsInstallation.isLoadCompleted()) {
                return false;
            }
            String logSource = record.getSourceClassName();
            String loggerName = record.getLoggerName();
            if (logSource == null || loggerName == null) {
                return false;
            }
            for (int i = 0; i < skipLoggerNames.length; i++) {
                String skipPrefix = skipLoggerNames[i];
                if (logSource.startsWith(skipPrefix) || loggerName.startsWith(skipPrefix)) {
                    return false;
                }
            }
            if (record.getThrown() != null) {
                StackTraceElement[] cause = record.getThrown().getStackTrace();
                for (StackTraceElement element : cause) {
                    if (element.getClassName().equals(JdkSplunkLogHandler.class.getName())) {
                        SplunkLogService.LOG.log(Level.SEVERE, "discard recursive log\n{0}", record.getMessage());
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static class LogEventFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return formatMessage(record);
        }

        public Map getEvent(LogRecord record) {
            Map event = new HashMap<>();
            event.put("level", record.getLevel().getName());
            //event.put("level_int", record.getLevel().intValue());
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
            if (record.getLevel().intValue() > Level.INFO.intValue() && record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                String logStackTrace = sw.toString();
                event.put("log_thrown", logStackTrace);
            }
            return event;
        }
    }

    public static final class LogHolder {
        /**
         * This field is used on each slave node to record log records on the slave.
         */
        static final JdkSplunkLogHandler LOG_HANDLER = new JdkSplunkLogHandler();

        public static void getSlaveLog(Computer computer) {
            if (computer == null || computer instanceof Jenkins.MasterComputer) {
                return;
            }
            try {
                List<LogRecord> records = computer.getLogRecords();
                for (LogRecord record : records) {
                    LOG_HANDLER.publish(record);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
