package com.splunk.splunkjenkins.listeners;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Item;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.model.listeners.SaveableListener;
import jenkins.model.Jenkins;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.splunk.splunkjenkins.Constants.JENKINS_CONFIG_PREFIX;
import static com.splunk.splunkjenkins.model.EventType.JENKINS_CONFIG;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getRelativeJenkinsHomePath;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getUserName;
import static com.splunk.splunkjenkins.utils.LogEventHelper.logUserAction;

/**
 * record jenkins config and job changes
 * send config content to splunk
 */

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
@Extension
public class LoggingConfigListener extends SaveableListener {
    private static final String XML_COMMENT = "<!--<![CDATA[%s]]>-->\n";
    private static final Logger LOGGER = Logger.getLogger(LoggingConfigListener.class.getName());
    //queue.xml or build/*/config.xml
    private static final String IGNORE_CONFIG_CHANGE_PATTERN = "(queue|nodeMonitors|UpdateCenter|global-build-stats" +
            "|fingerprint|build)(.*?xml)";
    private static final Pattern IGNORED;

    static {
        String ignorePatternStr = System.getProperty("splunkins.ignoreConfigChangePattern", IGNORE_CONFIG_CHANGE_PATTERN);
        Pattern ignorePattern;
        try {
            ignorePattern = Pattern.compile(ignorePatternStr, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException ex) {
            ignorePattern = Pattern.compile(IGNORE_CONFIG_CHANGE_PATTERN, Pattern.CASE_INSENSITIVE);
        }
        IGNORED = ignorePattern;
    }

    private WeakHashMap cached = new WeakHashMap(512);

    @Override
    public void onChange(Saveable saveable, XmlFile file) {
        if (!SplunkJenkinsInstallation.isLogHandlerRegistered()) {
            return;
        }
        String configPath = file.getFile().getAbsolutePath();
        if (saveable == null || IGNORED.matcher(configPath).find()) {
            LOGGER.log(Level.FINE, "{} is ignored", configPath);
            return;
        }
        if (saveable instanceof User) {
            //we use SecurityListener to capture login/logout events
            return;
        }
        if (SplunkJenkinsInstallation.get().isEventDisabled(JENKINS_CONFIG)) {
            return;
        }
        //log audit trail, excludes Item instances which were already tracked by other listener
        String relativePath = getRelativeJenkinsHomePath(configPath);
        if (!(saveable instanceof Item)) {
            logUserAction(getUserName(), Messages.audit_update_item(relativePath));
        }
        if ("SYSTEM".equals(Jenkins.getAuthentication().getName())) {
            LOGGER.log(Level.FINE, "{} is changed by system", configPath);
            //ignore changes made by daemons or background jobs
            return;
        }
        try {
            String configContent = file.asString();
            String checkSum = DigestUtils.md5Hex(configPath + configContent);
            if (cached.containsKey(checkSum)) {
                //Save a job can trigger multiple SaveableListener, depends on jenkins versions
                // e.g. AbstractProject.submit may call setters which can trigger save()
                return;
            }
            cached.put(checkSum, 0);
            String sourceName = JENKINS_CONFIG_PREFIX + relativePath;
            String userName = getUserName();
            String comment = String.format(XML_COMMENT, userName);
            SplunkLogService.getInstance().send(comment + configContent, JENKINS_CONFIG, sourceName);
        } catch (IOException e) {
            //just ignore
        }
    }
}
