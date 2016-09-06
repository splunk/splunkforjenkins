package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.model.JenkinsJsonConfig;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import com.splunk.splunkjenkins.utils.XstremJsonDriver;
import com.thoughtworks.xstream.XStream;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Describable;
import hudson.model.Job;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.model.listeners.SaveableListener;
import hudson.util.ReflectionUtils;
import hudson.util.XStream2;
import jenkins.model.Jenkins;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.splunk.splunkjenkins.Constants.AUDIT_SOURCE;
import static com.splunk.splunkjenkins.Constants.TAG;
import static com.splunk.splunkjenkins.model.EventType.JENKINS_CONFIG;
import static com.splunk.splunkjenkins.utils.LogEventHelper.getUserName;
import static com.splunk.splunkjenkins.utils.LogEventHelper.logUserAction;
import static org.apache.commons.lang.reflect.MethodUtils.getAccessibleMethod;

import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * record jenkins config and job changes
 * send config content to splunk
 */

@Extension
public class LoggingConfigListener extends SaveableListener {
    //queue.xml or nodes/*/config.xml
    private static final Pattern IGNORED = Pattern.compile("(queue|nodeMonitors|UpdateCenter|global-build-stats|nodes|build)(\\.xml|/[^/]+/config.xml)", Pattern.CASE_INSENSITIVE);
    private static final XStream xstream = new XStream2(new XstremJsonDriver());
    private boolean enabled = false;
    private WeakHashMap cached = new WeakHashMap(512);

    @Override
    public void onChange(Saveable saveable, XmlFile file) {
        String configPath = file.getFile().getAbsolutePath();
        String jenkinsHome = Jenkins.getInstance().getRootDir().getPath();
        if (saveable == null || !enabled || !SplunkJenkinsInstallation.loaded || IGNORED.matcher(configPath).find()) {
            return;
        }
        if (saveable instanceof User) {
            //we use SecurityListener to capture login/logout events
            return;
        }
        String user = getUserName();
        if ("SYSTEM".equals(user)) {
            return;
        }
        String configHash = file.getFile().getName() + saveable.hashCode();
        if (cached.containsKey(configHash)) {
            //Save a job can trigger multiple SaveableListener, depends on jenkins versions
            // e.g. AbstractProject.submit may call setters which can trigger save()
            return;
        }
        cached.put(configHash, 0);
        String configUrl = getUrl(saveable);
        if (configUrl == null) {
            if (configPath.startsWith(jenkinsHome)) {
                configUrl = configPath.substring(jenkinsHome.length() + 1);
            } else {
                configUrl = configPath;
            }
        }
        String sourceName = "jenkins://" + configUrl;
        SplunkLogService.getInstance().send(new JenkinsJsonConfig(xstream.toXML(saveable)), JENKINS_CONFIG, sourceName);
        logUserAction(user, "updated " + configUrl);
    }

    private String getUrl(Saveable saveable) {
        Method method = getAccessibleMethod(saveable.getClass(), "getUrl", new Class<?>[0]);
        if (method != null) {
            try {
                String url = "" + method.invoke(saveable, new Class<?>[0]);
                return url;
            } catch (Exception e) {//just ignore
            }
        }
        return null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
