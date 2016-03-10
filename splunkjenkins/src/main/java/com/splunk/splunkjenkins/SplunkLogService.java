package com.splunk.splunkjenkins;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.splunk.logging.RemoteAppender;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;

import org.slf4j.helpers.Util;

public class SplunkLogService {

    public final static String APPENDER_NAME = "jenkins2slunk";
    private final static InstanceHolder instanceHolder = new InstanceHolder();
    private final static Gson gson = new GsonBuilder().serializeNulls().create();

    protected static org.slf4j.Logger getLogger() {
        return instanceHolder.logger;
    }

    protected static void flush() {
        ch.qos.logback.classic.Logger lbkLogger = (ch.qos.logback.classic.Logger) getLogger();
        RemoteAppender appender = (RemoteAppender) lbkLogger.getAppender(APPENDER_NAME);
        appender.flush();
    }

    public static void send(Object obj) {
        String message = gson.toJson(obj);
        getLogger().info(message);
    }

    public static boolean update(SplunkJenkinsInstallation.Descriptor config) {
        boolean isValid=instanceHolder.updateSettings(config);
        Util.report("splunk config is valid:"+isValid);
        return isValid;
    }

    protected static String getScript() {
        return instanceHolder.postActionScript;
    }

    /**
     * @return InstanceHolder
     */
    protected static InstanceHolder getInstance() {
        return instanceHolder;
    }

    public static class InstanceHolder {

        private LoggerContext defaultLoggerContext = new LoggerContext();
        Logger logger = LoggerFactory.getLogger(SplunkLogService.class.getName());
        private String postActionScript;

        private void initLoggerContext() {
            if (!(logger instanceof ch.qos.logback.classic.Logger)) {
                try {
                    new ContextInitializer(defaultLoggerContext).autoConfig();
                    logger = defaultLoggerContext.getLogger(SplunkLogService.class.getName());
                } catch (JoranException je) {
                    Util.report("Failed to auto configure default logger context", je);
                }
            }
        }

        public synchronized boolean updateSettings(SplunkJenkinsInstallation.Descriptor config) {
            String urlStr = config.scheme + "://" + config.host + ":" + config.httpInputPort;
            //validate config
            try {
                URL url = new URL(urlStr);
            } catch (MalformedURLException ex) {
                LOG.log(Level.SEVERE, "invalid splunk http input config " + urlStr, ex);
                //not a valid url, no action
                return false;
            }
            if (config.scriptPath != null) {
                try {
                    postActionScript = IOUtils.toString(new URL(config.scriptPath));
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "can not read file " + config.scriptPath);
                    //file was removed from jenkins, just ignore
                }
            } else {
                postActionScript = null;
            }
            initLoggerContext();
            ch.qos.logback.classic.Logger lbkLogger = (ch.qos.logback.classic.Logger) logger;
            RemoteAppender appender = (RemoteAppender) lbkLogger.getAppender(APPENDER_NAME);
            appender.setRetry(config.retriesOnError);
            appender.updateIndex(config.indexName, config.sourceName, config.sourceTypeName);
            return appender.updateSender(urlStr, config.httpInputToken, config.delay, config.maxEventsBatchCount,
                    config.maxEventsBatchSize, config.sendMode);
        }

        private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(InstanceHolder.class.getName());
    }
}
