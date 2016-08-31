package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.init.Initializer;
import jenkins.model.Jenkins;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

import static hudson.init.InitMilestone.JOB_LOADED;

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
        SplunkLogService.getInstance().send(splunkFormatter.getEvent(record), "logger://" + record.getLoggerName());
    }

    @Override
    public void flush() {
        String stats = SplunkLogService.getInstance().getStats();
        SplunkLogService.getInstance().send(stats, "logger://com.splunk.splunkjenkins");
    }

    @Override
    public void close() throws SecurityException {

    }

    private class LogFilter implements Filter {
        //logger may trigger recursive call, need skip them
        private final String[] skipLoggerNames = {"com.splunk.splunkjenkins", "jenkins.InitReactorRunner",
                "org.apache.http"};

        @Override
        public boolean isLoggable(LogRecord record) {
            if (!SplunkJenkinsInstallation.loaded) {
                return false;
            }
            String logger = record.getSourceClassName();
            if (logger == null) {
                return false;
            }
            for (int i = 0; i < skipLoggerNames.length; i++) {
                String skipPrefix = skipLoggerNames[i];
                if (logger.startsWith(skipPrefix)) {
                    return false;
                }
            }
            return true;
        }
    }

    private class LogEventFormatter extends Formatter {
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
                event.put("throwable", sw.toString());
            }
            return event;
        }
    }

    @Initializer(after = JOB_LOADED)
    public static void forwardJdkLog() {
        ClassLoader cl = Jenkins.getInstance().getPluginManager().uberClassLoader;
        JdkSplunkLogHandler instance;
        try {
            instance = (JdkSplunkLogHandler) cl.loadClass(JdkSplunkLogHandler.class.getName()).newInstance();
            Logger.getLogger("").addHandler(instance);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
