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
                "jenkins.InitReactorRunner", "org.apache.http", "hudson.node_monitors"};

        @Override
        public boolean isLoggable(LogRecord record) {
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
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                String logStackTrace = sw.toString();
                /* Nested call, may happen when jenkins failed to load some plugin
                    at java.util.logging.Logger.log(Logger.java:830)
                    at hudson.ExtensionFinder$GuiceFinder$FaultTolerantScope$1.error(ExtensionFinder.java:440)
                    at hudson.ExtensionFinder$GuiceFinder$FaultTolerantScope$1.get(ExtensionFinder.java:429)
                    at com.google.inject.internal.InternalFactoryToProviderAdapter.get(InternalFactoryToProviderAdapter.java:41)
                    at com.google.inject.internal.InjectorImpl$3$1.call(InjectorImpl.java:1005)
                ....
                    at hudson.DescriptorExtensionList.load(DescriptorExtensionList.java:185)
                    at hudson.ExtensionList.ensureLoaded(ExtensionList.java:287)
                    at hudson.ExtensionList.iterator(ExtensionList.java:156)
                    at hudson.ExtensionList.get(ExtensionList.java:147)
                    at com.splunk.splunkjenkins.SplunkJenkinsInstallation.get(SplunkJenkinsInstallation.java:93)
                    at com.splunk.splunkjenkins.utils.SplunkLogService.enqueue(SplunkLogService.java:137)
                    at com.splunk.splunkjenkins.utils.SplunkLogService.send(SplunkLogService.java:133)
                    at com.splunk.splunkjenkins.utils.SplunkLogService.send(SplunkLogService.java:103)
                    at com.splunk.splunkjenkins.JdkSplunkLogHandler.publish(JdkSplunkLogHandler.java:36)
                    at java.util.logging.Logger.log(Logger.java:738)
                    at java.util.logging.Logger.doLog(Logger.java:765)
                    at java.util.logging.Logger.log(Logger.java:830)
                    at hudson.ExtensionFinder$GuiceFinder$FaultTolerantScope$1.error(ExtensionFinder.java:440)
                **/
                if (logStackTrace.contains("com.splunk.splunkjenkins.utils.SplunkLogService.enqueue")) {
                    SplunkLogService.LOG.log(Level.SEVERE, "discard recursive log\n{0}", logStackTrace);
                    return null;
                }
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
