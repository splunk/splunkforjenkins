package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.model.EventType;
import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.splunk.splunkjenkins.Constants.*;
import static com.splunk.splunkjenkins.utils.LogEventHelper.*;
import static hudson.Util.getHostName;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;


@Restricted(NoExternalUse.class)
@Extension
public class SplunkJenkinsInstallation extends GlobalConfiguration {
    private static transient boolean loadCompleted = false;
    private transient static final Logger LOG = Logger.getLogger(SplunkJenkinsInstallation.class.getName());
    public transient static final int MIN_BUFFER_SIZE = 2048;
    private transient static final int MAX_BUFFER_SIZE = 1 << 21;

    private transient volatile static SplunkJenkinsInstallation cachedConfig;
    private transient static final Pattern uuidPattern = Pattern.compile("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}", CASE_INSENSITIVE);
    // Defaults plugin global config values
    private boolean enabled = false;
    private String host;
    private String token;
    private boolean useSSL = true;
    private Integer port = 8088;
    //for console log default cache size for 256KB
    private long maxEventsBatchSize = 1 << 18;
    private long retriesOnError = 3;
    private boolean rawEventEnabled = true;
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
            super.load();
            //load default metadata
            try (InputStream metaInput = this.getClass().getClassLoader().getResourceAsStream("metadata.properties")) {
                metaDataProperties.load(metaInput);
            } catch (IOException e) {
                //ignore
            }
            this.updateCache();
        }
    }

    public SplunkJenkinsInstallation() {
        this(true);
    }

    public static SplunkJenkinsInstallation get() {
        if (cachedConfig != null) {
            return cachedConfig;
        } else {
            return GlobalConfiguration.all().get(SplunkJenkinsInstallation.class);
        }
    }

    /**
     * @return true if the plugin had been setup by Jenkins (constructor had been called)
     */
    public static boolean isLoadCompleted() {
        return loadCompleted && Jenkins.getInstance() != null;
    }

    /**
     * mark this plugin as initiated
     * @param completed mark the init as initiate completed
     */
    public static void markComplete(boolean completed) {
        loadCompleted = completed;
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
        updateCache();
        save();
        return true;
    }

    /*
     * Form validation methods
     */
    public FormValidation doCheckHost(@QueryParameter("value") String hostName) {
        if (StringUtils.isBlank(hostName)) {
            return FormValidation.warning(Messages.PleaseProvideHost());
        } else if ((hostName.endsWith("cloud.splunk.com") || hostName.endsWith("splunkcloud.com")
                || hostName.endsWith("splunktrial.com")) &&
                !(hostName.startsWith("input-") || hostName.startsWith("http-inputs-"))) {
            return FormValidation.warning(Messages.CloudHostPrefix(hostName));
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
        config.enabled = true;
        config.updateCache();
        if (!config.isValid()) {
            return FormValidation.error("Invalid config, please check Hostname or Token");
        }
        return verifyHttpInput(config);
    }

    public FormValidation doCheckScriptContent(@QueryParameter String value) {
        return validateGroovyScript(value);
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
            if (metaDataProperties == null) {
                metaDataProperties = new Properties();
            }
            if (metaDataConfig != null) {
                metaDataProperties.load(new StringReader(metaDataConfig));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Invalid Splunk host " + host, e);
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

    public boolean isEventDisabled(EventType eventType) {
        return !isValid() || "false".equals(metaDataProperties.getProperty(eventType.getKey("enabled")));
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
        if (isEmpty(splunkAppUrl) && isNotEmpty(host)) {
            return "http://" + host + ":8000/en-US/app/splunk_app_jenkins/";
        }
        return splunkAppUrl;
    }

    public String getAppUrlOrHelp() {
        String url = getSplunkAppUrl();
        if (isEmpty(url)) {
            return "/plugin/splunk-devops/help-splunkAppUrl.html?";
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

    public String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "yourhostname";
        }
    }
}
