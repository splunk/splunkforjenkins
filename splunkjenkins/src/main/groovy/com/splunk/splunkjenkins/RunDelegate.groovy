package com.splunk.splunkjenkins

import com.splunk.splunkjenkins.model.EventType
import com.splunk.splunkjenkins.utils.LogEventHelper
import com.splunk.splunkjenkins.utils.SplunkLogService
import hudson.EnvVars
import hudson.model.AbstractBuild
import hudson.model.Action
import hudson.model.TaskListener
import hudson.tasks.Publisher
import hudson.tasks.junit.TestResult
import hudson.tasks.junit.TestResultAction
import hudson.tasks.test.AggregatedTestResultAction

import static com.splunk.splunkjenkins.Constants.BUILD_ID
import static com.splunk.splunkjenkins.Constants.TAG
import static com.splunk.splunkjenkins.Constants.JOB_RESULT
import static com.splunk.splunkjenkins.Constants.TESTCASE
import static com.splunk.splunkjenkins.Constants.USER_NAME_KEY
import static com.splunk.splunkjenkins.Constants.BUILD_REPORT_ENV_TAG
import static com.splunk.splunkjenkins.utils.LogEventHelper.parseFileSize
import static com.splunk.splunkjenkins.utils.LogEventHelper.sendFiles
import static com.splunk.splunkjenkins.utils.LogEventHelper.getTriggerUserName


public class RunDelegate {
    AbstractBuild build;
    Map env;
    TaskListener listener

    public RunDelegate(AbstractBuild build, EnvVars enVars, TaskListener listener) {
        this.build = build;
        if (enVars != null) {
            this.env = enVars;
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
        getOut().println("sending build report")
        return SplunkLogService.getInstance().send(message, EventType.BUILD_REPORT);
    }

    /**
     *
     * @param message the message to send
     * @param sourceName source for splunk metadata
     * @return true if enqueue successfully, false if the message is discarded
     */
    def send(def message, String eventSourceName) {
        getOut().println("sending build report with source name " + eventSourceName)
        return SplunkLogService.getInstance().send(message, EventType.BUILD_REPORT, eventSourceName);
    }
    /**
     * Archive all configured artifacts from a build, using ant patterns defined in
     * @see <a href="http://ant.apache.org/manual/Types/fileset.html">the Ant glob syntax</a>
     * such as  {@code *&#42;&#47;build/*.log }
     * @param includes ant glob pattern
     */
    def archive(String includes) {
        archive(includes, null, false, "10MB");
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
    def archive(String includes, String excludes, boolean uploadFromSlave, String fileSizeLimit) {
        SplunkArtifactNotifier notifier = build.getProject().getPublishersList().get(SplunkArtifactNotifier.class)
        if (notifier != null) {
            //already defined on job level
            if (notifier.skipGlobalSplunkArchive) {
                return;
            } else {
                //do not send duplicate files
                if (excludes == null) {
                    excludes = notifier.includeFiles;
                } else {
                    excludes = excludes + "," + notifier.includeFiles;
                }
            }
        }
        return sendFiles(build, env, listener, includes, excludes, uploadFromSlave, parseFileSize(fileSizeLimit));
    }

    def getJunitReport() {
        TestResultAction resultAction = getAction(TestResultAction.class);
        if (resultAction != null) {
            println("has TestResultAction")
            return getJunitXmlCompatibleResult(resultAction.result);
        }
        AggregatedTestResultAction aggAction = getAction(AggregatedTestResultAction);
        if (aggAction != null) {
            println("has AggregatedTestResultAction")
            return getAggregatedJunitReport(aggAction)
        }
        return [];
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
    def Action getActionByClassName(String className) {
        Class actionClz = Class.forName(className);
        if (!actionClz instanceof Action) {
            return null;
        }
        return build.getAction(actionClz);
    }
    /**
     * Gets the action (first instance to be found) of a specified type that contributed to this build.
     *
     * @param type
     * @return The first action item or <code>null</code> if no such actions exist.
     *
     */
    public Action getAction(Class<? extends Action> type) {
        return build.getAction(type);
    }
    /**
     * check if the project has publisher
     * @param className , common used publishers are
     * @return
     */
    def boolean hasPublisherName(String className) {
        boolean found = false;
        Class publisherClazz = Class.forName(className);
        for (Publisher publisher : build.getProject().getPublishersList()) {
            if (publisherClazz.isInstance(publisher)) {
                found = true;
                break;
            }
        }
        return found;
    }

    @Override
    public String toString() {
        return "RunDelegate on build:" + this.build;
    }

    /**
     * <pre>
     * {@code
     * sendReport ({report - >
     * report["foo"] = "bar";
     * report["testsuite"] = junitReport;
     *})
     *}</pre>
     * send build reports with build variables as metadata
     * @param closure Groovy closure with a Map as parameter
     */
    public void sendReport(Closure closure) {
        String url = build.getUrl();
        Map event = new HashMap();
        event.put(TAG, "build_report")
        event.put(USER_NAME_KEY, getTriggerUserName(build));
        event.put(JOB_RESULT, build.getResult().toString());
        event.put(BUILD_ID, url);
        event.put("build_number", build.getNumber());
        event.put("job_name", build.getParent().getUrl());
        event.put(BUILD_REPORT_ENV_TAG, build.buildVariables);
        closure(event);
        send(event);
    }
    /**
     *
     * @param testResult
     * @return compatible with junit xml result send directly to splunk
     */
    public static Map getJunitXmlCompatibleResult(TestResult resultAction) {
        if (resultAction == null) {
            return ["message": "No TestResult"];
        }
        def testsuite = LogEventHelper.getTestSummary(resultAction)
        def testcase = [];
        testsuite.put(TESTCASE, testcase);
        try {
            resultAction.getSuites().each { suite ->
                testcase.addAll(suite.getCases());
            }
        } catch (UnsupportedOperationException ex) {
            //not support, just ignore
        }
        testsuite.put("errors", testcase.size() > 0 ? 0 : 1);
        return testsuite;
    }

    def getAggregatedJunitReport(AggregatedTestResultAction resultAction) {
        if (resultAction == null) {
            return ["message": "No AggregatedTestResultAction"];
        }
        def testsuite = LogEventHelper.getAggregateTestSummary(resultAction);
        def testcase = [];
        testsuite.put(TESTCASE, testcase);
        for (AggregatedTestResultAction.ChildReport childReport : resultAction.getChildReports()) {
            if (childReport.result instanceof TestResult) {
                TestResult testResult = (TestResult) childReport.result
                try {
                    testResult.getSuites().each { suite ->
                        testcase.addAll(suite.getCases());
                    }
                } catch (UnsupportedOperationException ex) {
                    //just ignore
                }
            }
        }
        testsuite.put("errors", testcase.size() > 0 ? 0 : 1);
        return testsuite;
    }
}