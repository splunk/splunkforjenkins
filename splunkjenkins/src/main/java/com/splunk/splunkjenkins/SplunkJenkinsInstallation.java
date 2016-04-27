package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.LogEventHelper;
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
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
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

    public static SplunkJenkinsInstallation get() {
        return GlobalConfiguration.all().get(SplunkJenkinsInstallation.class);
    }

    private transient final Pattern uuidPattern = Pattern.compile("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}", CASE_INSENSITIVE);
    private transient final static Logger LOGGER = Logger.getLogger(SplunkJenkinsInstallation.class.getName());

    // Defaults plugin global config values:
    public boolean enabled = true;
    public String host;
    public String token;
    public boolean useSSL = true;
    public Integer port = 8088;
    //for console log default cache size for 512KB
    public long maxEventsBatchSize = 512 * 1024 * 1024;
    public long retriesOnError = 3;
    public String indexName;
    public String sourceName;
    public String sourceHost = getHostName();
    public String jsonType;
    public String rawType;
    public boolean rawEventEnabled = false;
    //groovy script path
    public String scriptPath;
    public boolean monitorConfig = false;

    //groovy content if file path not set
    public String scriptContent;
    private boolean monitoringConfig = false;
    //cached values, will not be saved to disk!
    private transient URI jsonUrl;
    private transient URI rawUrl;
    private transient File scriptFile;
    private transient long scriptTimestamp;
    private transient String postActionScript;
    //retain backward compatibility when query parameter is not supported
    public transient Map metaData;

    public SplunkJenkinsInstallation(boolean useConfigFile) {
        if (useConfigFile) {
            XmlFile file = getConfigFile();
            if (file.exists()) {
                try {
                    String xmlText = file.asString();
                    if (xmlText.contains("com.splunk.splunkjenkins.SplunkJenkinsInstallation_-Descriptor")) {
                        //migration
                        SplunkJenkinsInstallation.Descriptor desc = (SplunkJenkinsInstallation.Descriptor) file.read();
                        this.host = desc.host;
                        this.port = Integer.parseInt(desc.httpInputPort);
                        this.indexName = desc.indexName;
                        this.sourceName = desc.sourceName;
                        this.token = desc.httpInputToken;
                        this.jsonType = desc.sourceTypeName;
                        this.useSSL = "https".equalsIgnoreCase(desc.scheme);
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
     * Gets the master's hostname
     */
    private static String getHostName() {
        String hostname = null;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            LOGGER.warning(e.getMessage());
        }
        return hostname;
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
                                          @QueryParameter String token, @QueryParameter boolean useSSL) {
        //create new instance to avoid pollution global config
        SplunkJenkinsInstallation config = new SplunkJenkinsInstallation(false);
        config.host = host;
        config.port = port;
        config.token = token;
        config.useSSL = useSSL;
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

        LogEventHelper.UrlQueryBuilder jsonMetaDataBuilder = new LogEventHelper.UrlQueryBuilder();
        String jsonQueryStr = jsonMetaDataBuilder
                .add("index", indexName)
                .add("host", sourceHost)
                .add("sourcetype", "_json")
                .add("sourcetype", jsonType)
                .add("source", sourceName).build();
        metaData = jsonMetaDataBuilder.getQueryMap();

        //rawdata has different sourcetype and source
        String rawSource = sourceName == null ? "console" : sourceName + ":console";
        String rawQueryStr = (new LogEventHelper.UrlQueryBuilder())
                .add("index", indexName)
                .add("host", sourceHost)
                .add("sourcetype", "generic_single_line")
                .add("sourcetype", rawType)
                .add("source", rawSource).build();
        try {
            String scheme = useSSL ? "https" : "http";
            jsonUrl = new URI(scheme, null, host, port, JSON_ENDPOINT, jsonQueryStr, null);
            rawUrl = new URI(scheme, null, host, port, RAW_ENDPOINT, rawQueryStr, null);
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

    public String getSouceName(String suffix) {
        String eventSouce = sourceName == null ? "splunkins" : sourceName;
        if (suffix == null) {
            return eventSouce;
        } else {
            return eventSouce + ":" + suffix;
        }
    }

    public boolean isRawEventEnabled() {
        return rawEventEnabled;
    }

    public boolean isMonitoringConfig() {
        return monitoringConfig;
    }

    public String getToken() {
        return token;
    }

    public URI getJsonUrl() {
        return jsonUrl;
    }

    public URI getRawUrl() {
        return rawUrl;
    }

    public long getMaxRetries() {
        return retriesOnError;
    }

    /**
     * retaining backward compatibility, before v5.0.1
     */
    protected static class Descriptor {
        protected String host, httpInputToken, httpInputPort, indexName, scheme, sourceName, sourceTypeName;
    }
}
