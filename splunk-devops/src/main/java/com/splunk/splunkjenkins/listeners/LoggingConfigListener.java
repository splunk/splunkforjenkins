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
import java.util.regex.Pattern;

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
    //queue.xml or build/*/config.xml
    private static final Pattern IGNORED = Pattern.compile("(queue|nodeMonitors|UpdateCenter|global-build-stats" +
            "|fingerprint|build)(.*?xml)", Pattern.CASE_INSENSITIVE);
    private WeakHashMap cached = new WeakHashMap(512);

    @Override
    public void onChange(Saveable saveable, XmlFile file) {
        if (!SplunkJenkinsInstallation.isLoadCompleted()) {
            return;
        }
        String configPath = file.getFile().getAbsolutePath();
        if (saveable == null || IGNORED.matcher(configPath).find()) {
            return;
        }
        if (saveable instanceof User) {
            //we use SecurityListener to capture login/logout events
            return;
        }
        if ("SYSTEM".equals(Jenkins.getAuthentication().getName()) || SplunkJenkinsInstallation.get().isEventDisabled(JENKINS_CONFIG)) {
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
            String relativePath = getRelativeJenkinsHomePath(configPath);
            String sourceName = JENKINS_CONFIG_PREFIX + relativePath;
            String userName = getUserName();
            String comment = String.format(XML_COMMENT, userName);
            SplunkLogService.getInstance().send(comment + configContent, JENKINS_CONFIG, sourceName);
            //log audit trail, excludes Item instances which were already tracked by other listener
            if (!(saveable instanceof Item)) {
                logUserAction(getUserName(), Messages.audit_update_item(relativePath));
            }
        } catch (IOException e) {
            //just ignore
        }
    }
}
