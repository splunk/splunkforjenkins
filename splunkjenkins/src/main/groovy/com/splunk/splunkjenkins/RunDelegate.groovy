package com.splunk.splunkjenkins

import com.splunk.splunkjenkins.utils.SplunkLogService
import hudson.EnvVars
import hudson.model.AbstractBuild
import hudson.model.TaskListener
import hudson.tasks.junit.TestResult

import static com.splunk.splunkjenkins.Constants.BUILD_ID
import static com.splunk.splunkjenkins.Constants.TAG
import static com.splunk.splunkjenkins.Constants.JOB_RESULT
import static com.splunk.splunkjenkins.Constants.METADATA
import static com.splunk.splunkjenkins.Constants.TESTCASE
import static com.splunk.splunkjenkins.Constants.TESTSUITE

public class RunDelegate {
    AbstractBuild build;
    Map env;
    TestResult testResult
    TaskListener listener
    //user defined test action but failed to generate test result due to slave issue
    Boolean reportMissing;

    public RunDelegate(AbstractBuild build, EnvVars enVars,
                       TestResult testResult, TaskListener listener, reportMissing) {
        this.build = build;
        if (enVars != null) {
            this.env = enVars;
        } else {
            this.env = new HashMap();
        }
        this.testResult = testResult;
        this.listener = listener;
        this.reportMissing = reportMissing;
    }

    def send(def message) {
        SplunkLogService.getInstance().send(message);
    }

    def getJunitReport() {
        return getJunitXmlCompatibleResult(testResult);
    }

    def getDefaultTestEvent() {
        return genTestEvent(build, env, testResult, reportMissing)
    }

    def getOut() {
        return listener.logger
    }

    def println(String s) {
        out.print(s)
    }

    def getTestResult() {
        return testResult
    }

    def getEnv() {
        return env
    }

    def getBuild() {
        return build
    }

    Boolean isTestReportMissing() {
        return reportMissing
    }

    @Override
    public String toString() {
        return "RunDelegate on build:" + this.build;
    }

    public static Map genTestEvent(AbstractBuild build, EnvVars enVars, TestResult testResult, boolean reportMissing) {
        String url = build.getUrl();
        Map event = new HashMap();
        event.put(TAG, "test_result")
        event.put(JOB_RESULT, build.getResult().toString());
        event.put(BUILD_ID, url);
        event.put(METADATA, enVars);
        if (reportMissing) {
            event.put("error", "failed to retrieve test report")
        }
        event.put(TESTSUITE, getJunitXmlCompatibleResult(testResult))
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