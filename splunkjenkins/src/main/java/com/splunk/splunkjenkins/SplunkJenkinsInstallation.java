package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.listeners.LoggingConfigListener;
import com.splunk.splunkjenkins.model.EventType;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.listeners.SaveableListener;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.splunk.splunkjenkins.Constants.JSON_ENDPOINT;
import static com.splunk.splunkjenkins.Constants.RAW_ENDPOINT;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getPostJobSample;
import static com.splunk.splunkjenkins.utils.LogEventHelper.nonEmpty;
import static com.splunk.splunkjenkins.utils.LogEventHelper.verifyHttpInput;
import static hudson.Util.getHostName;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.apache.commons.lang.StringUtils.isEmpty;

@Restricted(NoExternalUse.class)
@Extension
public class SplunkJenkinsInstallation extends GlobalConfiguration {
    transient static boolean loaded = false;
    private transient static final Logger LOG = Logger.getLogger(SplunkJenkinsInstallation.class.getName());
    public transient static final int MIN_BUFFER_SIZE = 2048;
    private transient static final int MAX_BUFFER_SIZE = 1 << 21;

    private transient static SplunkJenkinsInstallation cachedConfig;
    private transient static final Pattern uuidPattern = Pattern.compile("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}", CASE_INSENSITIVE);
    // Defaults plugin global config values:
    private boolean enabled = false;
    private String host;
    private String token;
    private boolean useSSL = true;
    private Integer port = 8088;
    //for console log default cache size for 256KB
    private long maxEventsBatchSize = 1 << 18;
    private long retriesOnError = 3;
    private boolean rawEventEnabled = false;
    //groovy script path
    private String scriptPath;
    private String metaDataConfig;
    //groovy content if file path not set
    private String scriptContent;
    //the app-jenkins link
    private String splunkAppUrl;
    public transient Properties metaDataProperties = new Properties();
    //cached values, will not be saved to disk!
    private transient String jsonUrl;
    private transient String rawUrl;
    private transient File scriptFile;
    private transient long scriptTimestamp;
    private transient String postActionScript;

    public SplunkJenkinsInstallation(boolean useConfigFile) {
        if (useConfigFile) {
            XmlFile file = getConfigFile();
            if (file.exists()) {
                try {
                    String xmlText = file.asString();
                    if (xmlText.contains("com.splunk.splunkjenkins.SplunkJenkinsInstallation_-Descriptor")) {
                        //migration from previous version because file format changed
                        SplunkJenkinsInstallation.Descriptor desc = (SplunkJenkinsInstallation.Descriptor) file.read();
                        this.host = desc.host;
                        this.port = Integer.parseInt(desc.httpInputPort);
                        this.token = desc.httpInputToken;
                        this.useSSL = "https".equalsIgnoreCase(desc.scheme);
                        this.enabled = true;
                        this.metaDataConfig = "source=" + desc.sourceName + "\n"
                                + "console_log.source=" + desc.sourceName + ":console" + "\n"
                                + "host=" + getHostName();
                        if (nonEmpty(desc.indexName)) {
                            this.metaDataConfig = this.metaDataConfig + "\nindex=" + desc.indexName;
                        }
                        if (nonEmpty(desc.sourceTypeName)) {
                            this.metaDataConfig = this.metaDataConfig + "\nsourcetype=" + desc.sourceTypeName;
                        }
                    } else {
                        file.getXStream().fromXML(xmlText, this);
                    }
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "failed to read " + getId() + ".xml", ex);
                }
                this.updateCache();
                this.updateConfigListener();
                loaded = true;
            }
        }
    }

    public SplunkJenkinsInstallation() {
        this(true);
    }

    public static SplunkJenkinsInstallation get() {
        if (cachedConfig != null) {
            return cachedConfig;
        } else {
            LOG.fine("init SplunkJenkinsInstallation on master");
            cachedConfig = GlobalConfiguration.all().get(SplunkJenkinsInstallation.class);
            return cachedConfig;
        }
    }

    /**
     * Note: this method is meant to be called on slave only!
     *
     * @param config the SplunkJenkinsInstallation to be used on Slave
     */
    public static void initOnSlave(SplunkJenkinsInstallation config) {
        SplunkJenkinsInstallation.cachedConfig = config;
        config.updateCache();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        req.bindJSON(this, formData);
        //handle choice
        if ("file".equals(formData.get("commandsOrFileInSplunkins"))) {
            this.scriptContent = null;
        } else {
            this.scriptPath = null;
        }
        this.cachedConfig = null;
        updateCache();
        updateConfigListener();
        save();
        return true;
    }

    /**
     * toggle enable/disable for config listener
     */
    private void updateConfigListener() {
        LoggingConfigListener configListener = SaveableListener.all().get(LoggingConfigListener.class);
        if (configListener != null) {
            boolean enabled = this.enabled
                    && "true".equals(metaDataProperties.getProperty("jenkins_config.monitoring"));
            configListener.setEnabled(enabled);
        }
    }

    /*
     * Form validation methods
     */
    public FormValidation doCheckHost(@QueryParameter("value") String value) {
        if (StringUtils.isBlank(value)) {
            return FormValidation.warning(Messages.PleaseProvideHost());
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckToken(@QueryParameter("value") String value) {
        //check GUID format such as 18654C68-B28B-4450-9CF0-6E7645CA60CA
        if (StringUtils.isBlank(value) || !uuidPattern.matcher(value).find()) {
            return FormValidation.warning(Messages.InvalidToken());
        }

        return FormValidation.ok();
    }

    public FormValidation doTestHttpInput(@QueryParameter String host, @QueryParameter int port,
                                          @QueryParameter String token, @QueryParameter boolean useSSL,
                                          @QueryParameter String metaDataConfig) {
        //create new instance to avoid pollution global config
        SplunkJenkinsInstallation config = new SplunkJenkinsInstallation(false);
        config.host = host;
        config.port = port;
        config.token = token;
        config.useSSL = useSSL;
        config.metaDataConfig = metaDataConfig;
        config.updateCache();
        return verifyHttpInput(config);
    }

    public FormValidation doCheckScriptContent(@QueryParameter String value) {
        try {
            new GroovyShell(Jenkins.getActiveInstance().pluginManager.uberClassLoader).parse(value);
        } catch (MultipleCompilationErrorsException e) {
            return FormValidation.error(e.getMessage());
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckMaxEventsBatchSize(@QueryParameter int value) {
        if (value < MIN_BUFFER_SIZE || value > MAX_BUFFER_SIZE) {
            return FormValidation.error(String.format("please consider a value between %d and %d", MIN_BUFFER_SIZE, MAX_BUFFER_SIZE));
        }
        return FormValidation.ok();
    }

    ////////END OF FORM VALIDATION/////////
    protected void updateCache() {
        if (scriptPath != null) {
            scriptFile = new File(scriptPath);
        } else if (nonEmpty(scriptContent)) {
            postActionScript = scriptContent;
        } else {
            postActionScript = null;
        }
        try {
            String scheme = useSSL ? "https" : "http";
            jsonUrl = new URI(scheme, null, host, port, JSON_ENDPOINT, null, null).toString();
            rawUrl = new URI(scheme, null, host, port, RAW_ENDPOINT, null, null).toString();
            metaDataProperties = new Properties();
            if (metaDataConfig != null) {
                metaDataProperties.load(new StringReader(metaDataConfig));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "invalid Splunk url ", e);
        }
    }

    /**
     * Reload script content from file if modified
     */
    private void refreshScriptText() {
        if (scriptFile == null) {
            return;
        }
        try {
            if (!scriptFile.canRead()) {
                postActionScript = null;
            } else {
                scriptTimestamp = scriptFile.lastModified();
                postActionScript = IOUtils.toString(scriptFile.toURI());
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "can not read file " + scriptFile, e);
            //file was removed from jenkins, just ignore
        }
    }

    /**
     * check if configured correctly
     *
     * @return true setup is completed
     */
    public boolean isValid() {
        return enabled && host != null && token != null
                && jsonUrl != null && rawUrl != null;
    }

    /**
     * get cached script contents
     *
     * @return script content
     */
    public String getScript() {
        if (scriptPath != null && scriptFile.lastModified() > scriptTimestamp) {
            refreshScriptText();
        }
        return this.postActionScript;
    }

    public boolean isRawEventEnabled() {
        return rawEventEnabled;
    }

    /**
     * Check whether we can optimize sending process, e.g. if we need to send 1000 lines for one job console log,
     * and we can specify host,source,sourcetype,index only once in query parameter if raw event is supported,
     * instead of sending 1000 times in request body
     *
     * @param eventType does this type of text need to be logged to splunk line by line
     * @return true if HEC supports specify metadata in url query parameter
     */
    public boolean isMetaDataInURLSupported(EventType eventType) {
        return rawEventEnabled && eventType.needSplit();
    }

    public String getToken() {
        return token;
    }

    public long getMaxRetries() {
        return retriesOnError;
    }

    /**
     * @param keyName such as host,source,index
     * @return the configured metadata
     */
    public String getMetaData(String keyName) {
        return metaDataProperties.getProperty(keyName);
    }

    public String getJsonUrl() {
        return jsonUrl;
    }

    public String getRawUrl() {
        return rawUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isUseSSL() {
        return useSSL;
    }

    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }

    public long getMaxEventsBatchSize() {
        return maxEventsBatchSize;
    }

    public void setMaxEventsBatchSize(long maxEventsBatchSize) {
        if (maxEventsBatchSize > MIN_BUFFER_SIZE) {
            this.maxEventsBatchSize = maxEventsBatchSize;
        } else {
            this.maxEventsBatchSize = MIN_BUFFER_SIZE;
        }
    }

    public void setRawEventEnabled(boolean rawEventEnabled) {
        this.rawEventEnabled = rawEventEnabled;
    }

    public String getMetaDataConfig() {
        return metaDataConfig;
    }

    public void setMetaDataConfig(String metaDataConfig) {
        this.metaDataConfig = metaDataConfig;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public long getRetriesOnError() {
        return retriesOnError;
    }

    public void setRetriesOnError(long retriesOnError) {
        this.retriesOnError = retriesOnError;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public String getScriptContent() {
        return scriptContent;
    }

    public void setScriptContent(String scriptContent) {
        this.scriptContent = scriptContent;
    }

    public Map toMap() {
        HashMap map = new HashMap();
        map.put("token", this.token);
        map.put("rawEventEnabled", this.rawEventEnabled);
        map.put("maxEventsBatchSize", this.maxEventsBatchSize);
        map.put("host", host);
        map.put("port", port);
        map.put("useSSL", useSSL);
        map.put("metaDataConfig", metaDataConfig);
        map.put("retriesOnError", retriesOnError);
        return map;
    }

    /**
     * retaining backward compatibility, before v5.0.1
     */
    protected static class Descriptor {
        protected String host, httpInputToken, httpInputPort, indexName, scheme, sourceName, sourceTypeName;
    }

    public String getScriptOrDefault() {
        if (scriptContent == null && scriptPath == null) {
            //when user clear the text on UI, it will be set to empty string
            //so use null check will not overwrite user settings
            return getPostJobSample();
        } else {
            return scriptContent;
        }
    }

    public String getSplunkAppUrl() {
        if (splunkAppUrl == null && host != null) {
            return "http://" + host + ":8000/en-US/app/splunk_app_jenkins/";
        }
        return splunkAppUrl;
    }

    public String getAppUrlOrHelp() {
        String url = getSplunkAppUrl();
        if (isEmpty(url)) {
            return "/plugin/splunkjenkins/help-splunkAppUrl.html?";
        }
        return url;
    }

    public void setSplunkAppUrl(String splunkAppUrl) {
        if (!isEmpty(splunkAppUrl) && !splunkAppUrl.endsWith("/")) {
            splunkAppUrl += "/";
        }
        this.splunkAppUrl = splunkAppUrl;
    }

    public String getMetadataHost() {
        if (metaDataProperties != null && metaDataProperties.containsKey("host")) {
            return (String) metaDataProperties.get("host");
        }
        return getHostName();
    }
}
