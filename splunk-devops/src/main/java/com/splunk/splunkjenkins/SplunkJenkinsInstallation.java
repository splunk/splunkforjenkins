package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.model.EventType;
import com.splunk.splunkjenkins.model.MetaDataConfigItem;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.splunk.splunkjenkins.Constants.*;
import static com.splunk.splunkjenkins.utils.LogEventHelper.*;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getDefaultDslScript;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;


@Restricted(NoExternalUse.class)
@Extension
public class SplunkJenkinsInstallation extends GlobalConfiguration {
    private static transient boolean loadCompleted = false;
    private transient static final Logger LOG = Logger.getLogger(SplunkJenkinsInstallation.class.getName());
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
    private String metadataHost;
    private String metadataSource;
    private String ignoredJobs;

    //below are all transient properties
    public transient Properties metaDataProperties = new Properties();
    //cached values, will not be saved to disk!
    private transient String jsonUrl;
    private transient String rawUrl;
    private transient File scriptFile;
    private transient long scriptTimestamp;
    private transient String postActionScript;
    private transient Set<MetaDataConfigItem> metadataItemSet = new HashSet<>();
    private transient String defaultMetaData;
    private transient Pattern ignoredJobPattern;

    public SplunkJenkinsInstallation(boolean useConfigFile) {
        if (useConfigFile) {
            super.load();
            migrate();
            //load default metadata
            try (InputStream metaInput = this.getClass().getClassLoader().getResourceAsStream("metadata.properties")) {
                defaultMetaData = IOUtils.toString(metaInput);
            } catch (IOException e) {
                //ignore
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
            if (Jenkins.getInstance() != null) {
                return GlobalConfiguration.all().get(SplunkJenkinsInstallation.class);
            } else {
                //jenkins is in shutdown own phase
                throw new IllegalStateException("Jenkins has not been started, or was already shut down");
            }
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
     *
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
        this.metadataItemSet = null; // otherwise bindJSON will never clear it once set
        boolean previousState = this.enabled;
        req.bindJSON(this, formData);
        if (this.metadataItemSet == null) {
            this.metaDataConfig = "";
        }
        //handle choice
        if ("file".equals(formData.get("commandsOrFileInSplunkins"))) {
            this.scriptContent = null;
        } else {
            this.scriptPath = null;
        }
        updateCache();
        save();
        if (previousState && !this.enabled) {
            //switch from enable to disable
            SplunkLogService.getInstance().stopWorker();
            SplunkLogService.getInstance().releaseConnection();
        }
        return true;
    }

    /*
     * Form validation methods
     */
    public FormValidation doCheckHost(@QueryParameter("value") String hostName) {
        if (StringUtils.isBlank(hostName)) {
            return FormValidation.warning(Messages.PleaseProvideHost());
        } else if (hostName.startsWith("http://") || hostName.startsWith("https://")) {
            try {
                URI uri = new URI(hostName);
                String domain = uri.getHost();
                return FormValidation.warning(Messages.HostNameSchemaWarning(domain));
            } catch (URISyntaxException e) {
                return FormValidation.warning(Messages.HostNameInvalid());
            }
        } else if ((hostName.endsWith("cloud.splunk.com") || hostName.endsWith("splunkcloud.com")
                || hostName.endsWith("splunktrial.com")) &&
                !(hostName.startsWith("input-") || hostName.startsWith("http-inputs-"))) {
            return FormValidation.warning(Messages.CloudHostPrefix(hostName));
        } else if (hostName.contains(",")) {
            return FormValidation.warning(Messages.HostNameListWarning());
        } else {
            return FormValidation.ok();
        }
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
            return FormValidation.error(Messages.InvalidHostOrToken());
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

    public FormValidation doCheckIgnoredJobs(@QueryParameter String value) {
        try {
            Pattern.compile(value);
        } catch (PatternSyntaxException ex) {
            return FormValidation.errorWithMarkup(Messages.InvalidPattern());
        }
        return FormValidation.ok();
    }

    ////////END OF FORM VALIDATION/////////
    protected void updateCache() {
        if (!this.enabled) {
            //nothing to do if not enabled
            return;
        }
        if (scriptPath != null) {
            scriptFile = new File(scriptPath);
        } else if (nonEmpty(scriptContent)) {
            // During startup, hudson.model.User.current() calls User.load which will load other plugins, will throw error:
            // Tried proxying com.splunk.splunkjenkins.SplunkJenkinsInstallation to support a circular dependency, but it is not an interface.
            // Use Jenkins.getAuthentication() will by pass the issue
            Authentication auth = Jenkins.getAuthentication();
            String userName;
            if (auth != null) {
                userName = auth.getName();
            } else {
                userName = Jenkins.ANONYMOUS.getName();
            }
            ApprovalContext context = ApprovalContext.create().withUser(userName).withKey(this.getClass().getName());
            //check approval saving pending for approval
            ScriptApproval.get().configuring(scriptContent, GroovyLanguage.get(), context);
            postActionScript = scriptContent;
        } else {
            postActionScript = null;
        }
        if (StringUtils.isEmpty(ignoredJobs)) {
            ignoredJobPattern = null;
        } else {
            try {
                ignoredJobPattern = Pattern.compile(ignoredJobs);
            } catch (PatternSyntaxException ex) {
                LOG.log(Level.SEVERE, "invalid ignore job pattern {0}, error: {1}", new Object[]{
                        ignoredJobs, ex.getDescription()});
            }
        }
        try {
            String scheme = useSSL ? "https" : "http";
            jsonUrl = new URI(scheme, null, host, port, JSON_ENDPOINT, null, null).toString();
            rawUrl = new URI(scheme, null, host, port, RAW_ENDPOINT, null, null).toString();
            //discard previous metadata cache and load new one
            metaDataProperties = new Properties();
            String combinedMetaData = Util.fixNull(defaultMetaData) + "\n" + Util.fixNull(metaDataConfig);
            if (!isEmpty(combinedMetaData)) {
                metaDataProperties.load(new StringReader(combinedMetaData));
            }
            if (isNotEmpty(metadataSource)) {
                metaDataProperties.put("source", metadataSource);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "update cache failed, splunk host:" + host, e);
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
    public boolean canPostRaw(EventType eventType) {
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

    public boolean isJobIgnored(String jobUrl) {
        if (ignoredJobPattern != null) {
            return ignoredJobPattern.matcher(jobUrl).find();
        } else {
            return false;
        }
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
        map.put("metaDataConfig", Util.fixNull(defaultMetaData) + Util.fixNull(metaDataConfig));
        map.put("retriesOnError", retriesOnError);
        map.put("metadataHost", metadataHost);
        map.put("metadataSource", metadataSource);
        return map;
    }

    public String getScriptOrDefault() {
        if (scriptContent == null && scriptPath == null) {
            //when user clear the text on UI, it will be set to empty string
            //so use null check will not overwrite user settings
            return getDefaultDslScript();
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

    private String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "jenkins";
        }
    }

    public Set<MetaDataConfigItem> getMetadataItemSet() {
        return metadataItemSet;
    }

    public String getMetadataHost() {
        if (metadataHost != null) {
            return metadataHost;
        } else {
            //backwards compatible
            if (metaDataProperties != null && metaDataProperties.containsKey("host")) {
                return metaDataProperties.getProperty("host");
            } else {
                String url = JenkinsLocationConfiguration.get().getUrl();
                if (url != null && !url.startsWith("http://localhost")) {
                    try {
                        return (new URL(url)).getHost();
                    } catch (MalformedURLException e) {
                        //do not care,just ignore
                    }
                }
                return getLocalHostName();
            }
        }
    }

    public void setMetadataHost(String metadataHost) {
        this.metadataHost = metadataHost;
    }

    public void setMetadataItemSet(Set<MetaDataConfigItem> metadataItemSet) {
        this.metadataItemSet = metadataItemSet;
        this.metaDataConfig = MetaDataConfigItem.toString(metadataItemSet);
    }

    public String getMetadataSource() {
        if (metadataSource != null) {
            return metadataSource;
        } else if (metaDataProperties != null && metaDataProperties.containsKey("source")) {
            return metaDataProperties.getProperty("source");
        } else {
            return "";
        }
    }

    public String getMetadataSource(String suffix) {
        return getMetadataSource() + JENKINS_SOURCE_SEP + suffix;
    }

    public void setMetadataSource(String metadataSource) {
        this.metadataSource = metadataSource;
    }

    private void migrate() {
        if (this.scriptContent != null) {
            String hash = DigestUtils.md5Hex(this.scriptContent);
            if (SCRIPT_TEXT_MD5_HASH.contains(hash)) { //previous versions' script hash, update to use new version
                this.scriptContent = getDefaultDslScript();
                ScriptApproval.get().preapprove(this.scriptContent, GroovyLanguage.get());
                // provided by the plugin itself, the namespace was already migrated from old settings
            }
        }
        this.metadataItemSet = MetaDataConfigItem.loadProps(this.metaDataConfig);
    }

    public String getIgnoredJobs() {
        return ignoredJobs;
    }

    public void setIgnoredJobs(String ignoredJobs) {
        this.ignoredJobs = ignoredJobs;
    }
}
