package com.splunk.splunkjenkins

import com.splunk.splunkjenkins.listeners.LoggingRunListener
import com.splunk.splunkjenkins.model.CoverageMetricsAdapter
import com.splunk.splunkjenkins.model.EventType
import com.splunk.splunkjenkins.model.JunitTestCaseGroup
import com.splunk.splunkjenkins.utils.LogEventHelper
import com.splunk.splunkjenkins.utils.SplunkLogService
import com.splunk.splunkjenkins.utils.TestCaseResultUtils
import hudson.EnvVars
import hudson.FilePath
import hudson.model.AbstractBuild
import hudson.model.Action
import hudson.model.Run
import hudson.model.TaskListener
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

import java.util.logging.Level
import java.util.logging.Logger

import static com.splunk.splunkjenkins.Constants.BUILD_ID
import static com.splunk.splunkjenkins.Constants.TAG
import static com.splunk.splunkjenkins.Constants.JOB_RESULT
import static com.splunk.splunkjenkins.Constants.USER_NAME_KEY
import static com.splunk.splunkjenkins.Constants.BUILD_REPORT_ENV_TAG
import static com.splunk.splunkjenkins.utils.LogEventHelper.parseFileSize
import static com.splunk.splunkjenkins.utils.LogEventHelper.sendFiles
import static com.splunk.splunkjenkins.utils.LogEventHelper.getTriggerUserName


public class RunDelegate {
    static final LOG = Logger.getLogger(LoggingRunListener.class.name)

    Run build;
    Map env;
    TaskListener listener;
    FilePath workSpace;

    RunDelegate() {
    }

    public RunDelegate(Run build, Map env, TaskListener listener) {
        this.build = build;
        if (env != null) {
            this.env = env;
        } else {
            this.env = new HashMap();
        }
        this.listener = listener;
    }

    /**
     *
     * @param message
     * @return true if enqueue successfully, false if the message is discarded
     */
    def send(def message) {
        return SplunkLogService.getInstance().send(message, EventType.BUILD_REPORT);
    }

    /**
     *
     * @param message the message to send
     * @param sourceName source for splunk metadata
     * @return true if enqueue successfully, false if the message is discarded
     */
    def send(def message, String eventSourceName) {
        return SplunkLogService.getInstance().send(message, EventType.BUILD_REPORT, eventSourceName);
    }

    /**
     * Archive all configured artifacts from a build, using ant patterns defined in
     * @see <a href="http://ant.apache.org/manual/Types/fileset.html">the Ant glob syntax</a>
     * such as  {@code *&#42;&#47;build/*.log }
     * @param includes ant glob pattern
     * @param excludes ant glob pattern
     * @param uploadFromSlave <code>true</code> if need upload directly from the slave
     * @parm fileSizeLimit max size per file to send to splunk, to prevent sending huge files by wildcard includes
     */
    @Whitelisted
    def archive(String includes, String excludes = null, boolean uploadFromSlave = false, String fileSizeLimit = "") {
        if (build instanceof AbstractBuild) {
            def notifier = build.project.getPublishersList().get(SplunkArtifactNotifier)
            if (notifier != null) {
                //already defined on job level
                if (notifier.skipGlobalSplunkArchive) {
                    return;
                }
            }
        }
        return sendFiles(build, workSpace, env, listener, includes, excludes, uploadFromSlave, parseFileSize(fileSizeLimit));
    }

    @Whitelisted
    def getJunitReport() {
        //no pagination, use MAX_VALUE as page size
        List<JunitTestCaseGroup> results = getJunitReport(Integer.MAX_VALUE, null);
        if (!results.isEmpty()) {
            return results.get(0)
        } else {
            return results;
        }
    }

    @Whitelisted
    def getJunitReport(int pageSize, List<String> ignoredActions = null) {
        try {
            return TestCaseResultUtils.getBuildReport(build, pageSize, ignoredActions);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "failed to get junit report", ex)
            return Collections.emptyList();
        }
    }

    @Whitelisted
    def sendTestReport(int pageSize) {
        def results = getJunitReport(pageSize, null)
        def buildEvent = getBuildEvent()
        String sourceName = SplunkJenkinsInstallation.get().getMetadataSource("test")
        results.eachWithIndex { junitResult, idx ->
            Map pagedEvent = buildEvent + ["testsuite": junitResult, "page_num": idx + 1]
            send(pagedEvent, sourceName)
        }
    }

    @Whitelisted
    def sendCoverageReport(int pageSize) {
        def coverageList = CoverageMetricsAdapter.getReport(build, pageSize);
        if (coverageList.isEmpty()) {
            return;
        }
        String sourceName = SplunkJenkinsInstallation.get().getMetadataSource("coverage")
        def buildEvent = getBuildEvent()
        buildEvent.put(TAG, "coverage")
        coverageList.eachWithIndex { coverage, idx ->
            Map pagedEvent = buildEvent + ["coverage": coverage, "page_num": idx + 1]
            send(pagedEvent, sourceName)
        }
    }

    def getOut() {
        return listener.logger
    }

    def println(String s) {
        try {
            getOut().println(s)
        } catch (Exception ex) {//shouldn't happen!
            ex.printStackTrace();
        }
    }

    def getEnv() {
        return env
    }

    def getBuild() {
        return build
    }

    /**
     * get specified actionName that contributed to build object.
     *
     * refer to
     * https://wiki.jenkins-ci.org/display/JENKINS/Plugins#Plugins-Buildreports
     * https://wiki.jenkins-ci.org/display/JENKINS/Plugins#Plugins-Otherpostbuildactions
     * @param className
     * @return
     */
    @Whitelisted
    def Action getActionByClassName(String className) {
        try {
            Class actionClz = Class.forName(className);
            if (!(Action.isAssignableFrom(actionClz))) {
                return null;
            }
            return build.getAction(actionClz);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }
    /**
     * Gets the action (first instance to be found) of a specified type that contributed to this build.
     *
     * @param type
     * @return The first action item or <code>null</code> if no such actions exist.
     *
     */
    @Whitelisted
    public Action getAction(Class<? extends Action> type) {
        return build.getAction(type);
    }

    /**
     * check if the project has publisher
     * @param shortClassName , common used publishers are junit.JUnitResultArchiver, testng.Publisher
     * @return
     */
    @Whitelisted
    def boolean hasPublisherName(String shortClassName) {
        return LogEventHelper.hasPublisherName(shortClassName, build);
    }

    @Override
    public String toString() {
        return "RunDelegate on build:" + this.build;
    }

    @Whitelisted
    def getBuildEvent() {
        String url = build.getUrl();
        Map event = new HashMap();
        event.put(TAG, "build_report")
        event.put(USER_NAME_KEY, getTriggerUserName(build));
        event.put(JOB_RESULT, build.getResult().toString());
        event.put(BUILD_ID, url);
        event.put(BUILD_REPORT_ENV_TAG, env);
        event.put("build_number", build.getNumber());
        event.put("job_name", build.getParent().getUrl());
        return event;
    }

    /**
     * <pre>
     * {@code
     * //modify the build the event on the fly
     * sendReport ({report - >
     * report["foo"] = "bar";
     * report["testsuite"] = junitReport;
     *})
     * //use a map as return
     * sendReport{
     *     return ["ke":"value"]
     *}*}</pre>
     * send build reports with build variables as metadata
     * @param closure Groovy closure with a Map as parameter
     */
    @Whitelisted
    public void sendReport(Closure closure) {
        Map event = getBuildEvent();
        //closure may/not return a new build event
        def res = closure(event);
        if (res != null) {
            if (res instanceof Map) {
                event = event + res;
            } else if (res instanceof JunitTestCaseGroup) {
                event = event + ["testsuite": res]
            } else {
                event = event + ["report": res]
            }
        }
        send(event);
    }

}