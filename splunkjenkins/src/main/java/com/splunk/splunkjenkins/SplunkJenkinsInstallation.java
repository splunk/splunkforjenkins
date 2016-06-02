package com.splunk.splunkjenkins;

import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.XmlFile;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
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
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.splunk.splunkjenkins.Constants.JSON_ENDPOINT;
import static com.splunk.splunkjenkins.Constants.RAW_ENDPOINT;
import static com.splunk.splunkjenkins.utils.LogEventHelper.nonEmpty;
import static com.splunk.splunkjenkins.utils.LogEventHelper.verifyHttpInput;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

@Restricted(NoExternalUse.class)
@Extension
public class SplunkJenkinsInstallation extends GlobalConfiguration {
    private transient final static Logger LOGGER = Logger.getLogger(SplunkJenkinsInstallation.class.getName());
    private transient static SplunkJenkinsInstallation config;
    private transient final Pattern uuidPattern = Pattern.compile("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}", CASE_INSENSITIVE);
    // Defaults plugin global config values:
    public boolean enabled = false;
    public String host;
    public String token;
    public boolean useSSL = true;
    public Integer port = 8088;
    //for console log default cache size for 512KB
    public long maxEventsBatchSize = 512 * 1024 * 1024;
    public long retriesOnError = 3;
    public boolean rawEventEnabled = false;
    //groovy script path
    public String scriptPath;
    public String metaDataConfig;
    //groovy content if file path not set
    public String scriptContent;
    public transient Properties metaDataProperties;
    private boolean monitoringConfig = false;
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
                        //overwrite with newer version
                        this.save();
                    } else {
                        file.getXStream().fromXML(xmlText, this);
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "failed to read " + getId() + ".xml", ex);
                }
                this.updateCache();
            }
        }
    }

    public SplunkJenkinsInstallation() {
        this(true);
    }

    public static SplunkJenkinsInstallation get() {
        if (config == null) {
            config = GlobalConfiguration.all().get(SplunkJenkinsInstallation.class);
        }
        return config;
    }

    /*
     * Gets the jenkins's hostname
     */
    private static String getHostName() {
        String hostname = null;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e1) {
            e1.printStackTrace();
        }
        return hostname;
    }

    /**
     * Warnig: thie is method is meant be called on slave only!
     *
     * @param config
     */
    public static void setConfig(SplunkJenkinsInstallation config) {
        SplunkJenkinsInstallation.config = config;
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
            new GroovyShell().parse(value);
        } catch (MultipleCompilationErrorsException e) {
            return FormValidation.error(e.getMessage());
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
            LOGGER.log(Level.SEVERE, "invalid Splunk url ", e);
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
            LOGGER.log(Level.SEVERE, "can not read file " + scriptFile, e);
            //file was removed from jenkins, just ignore
        }
    }

    /**
     * check if configured correctly
     *
     * @return
     */
    public boolean isValid() {
        return enabled && host != null && token != null
                && jsonUrl != null && rawUrl != null;
    }

    /**
     * get cached script contents
     *
     * @return
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
     * if raw input is not supported, then metadata in URL query parameter is not supported neither
     *
     * @return
     */
    public boolean isMetaDataInURLSupported() {
        return rawEventEnabled;
    }

    public boolean isMonitoringConfig() {
        return monitoringConfig;
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

    public Map toMap() {
        HashMap map = new HashMap();
        map.put("token", this.token);
        map.put("rawEventEnabled", this.rawEventEnabled);
        map.put("host", host);
        map.put("port", port);
        map.put("useSSL", useSSL);
        map.put("metaDataConfig", metaDataConfig);
        return map;
    }

    /**
     * retaining backward compatibility, before v5.0.1
     */
    protected static class Descriptor {
        protected String host, httpInputToken, httpInputPort, indexName, scheme, sourceName, sourceTypeName;
    }
}
