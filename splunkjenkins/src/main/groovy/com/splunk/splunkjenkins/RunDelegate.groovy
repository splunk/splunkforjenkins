package com.splunk.splunkjenkins

import com.splunk.splunkjenkins.utils.EventType
import com.splunk.splunkjenkins.utils.SplunkLogService
import hudson.EnvVars
import hudson.model.AbstractBuild
import hudson.model.Action
import hudson.model.TaskListener
import hudson.tasks.junit.TestResult
import hudson.tasks.junit.TestResultAction

import static com.splunk.splunkjenkins.Constants.BUILD_ID
import static com.splunk.splunkjenkins.Constants.TAG
import static com.splunk.splunkjenkins.Constants.JOB_RESULT
import static com.splunk.splunkjenkins.Constants.METADATA
import static com.splunk.splunkjenkins.Constants.TESTCASE
import static com.splunk.splunkjenkins.Constants.TESTSUITE

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

    def send(def message) {
        SplunkLogService.getInstance().send(message, EventType.BUILD_REPORT);
    }

    /**
     *
     * @param message
     * @param eventSourceName @see EventType
     * @return
     */
    def send(def message, String eventSourceName) {
        SplunkLogService.getInstance().send(message, EventType.valueOf(eventSourceName));
    }

    def getJunitReport() {
        TestResultAction resultAction = build.getAction(TestResultAction.class);
        if (resultAction == null) {
            return null;
        }
        TestResult testResult = resultAction.result;
        if (testResult == null) {
            return ["error": "junit report is missing"];
        }
        return getJunitXmlCompatibleResult(testResult);
    }

    def getOut() {
        return listener.logger
    }

    def println(String s) {
        out.print(s)
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
    public Action getActionByClassName(String className) {
        for (Action action : build.getAllActions()) {
            if(action.getClass().getName().equals(className)){
                return action;
            }
        }
        return null;
    }
    /**
     * Gets the action (first instance to be found) of a specified type that contributed to this build.
     *
     * @param type
     * @return The action or <code>null</code> if no such actions exist.
     *
     */
    public Action getAction(Class<? extends Action> type){
        return build.getActions(type);
    }

    @Override
    public String toString() {
        return "RunDelegate on build:" + this.build;
    }

    public static Map genJunitTestReportWithEnv(AbstractBuild build, EnvVars enVars) {
        String url = build.getUrl();
        Map event = new HashMap();
        event.put(TAG, "test_result")
        event.put(JOB_RESULT, build.getResult().toString());
        event.put(BUILD_ID, url);
        event.put(METADATA, enVars);
        TestResultAction resultAction = build.getAction(TestResultAction.class);
        TestResult testResult;
        if (resultAction != null) {
            testResult = resultAction?.result;
            //only mark result not found if user defined test result action but failed to generate test report
            boolean reportMissing = testResult == null;
            if (reportMissing) {
                event.put("error", "failed to retrieve test report")
            }else{
                event.put(TESTSUITE, getJunitXmlCompatibleResult(testResult))
            }
        }
        return event
    }

    public static Map getJunitXmlCompatibleResult(TestResult testResult) {
        def testsuite = [:]
        if (testResult != null) {
            testsuite.put("time", testResult.getDuration());
            testsuite.put("total", testResult.getTotalCount());
            testsuite.put("passes", testResult.getPassCount());
            testsuite.put("failures", testResult.getFailCount());
            testsuite.put("skips", testResult.getSkipCount())
            testsuite.put("tests", testResult.getTotalCount())
            def testcase = [];
            testsuite.put(TESTCASE, testcase);
            try {
                testResult.getSuites().each { suite ->
                    testcase.addAll(suite.getCases());
                }
            } catch (UnsupportedOperationException ex) {
                //not support, just ignore
            }
        }
        return testsuite;
    }
}