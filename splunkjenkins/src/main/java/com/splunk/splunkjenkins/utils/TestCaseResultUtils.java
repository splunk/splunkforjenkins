package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.model.JunitTestCaseGroup;
import hudson.model.Run;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestCaseResultUtils {
    /**
     * split test result into groups, each contains maximum pageSize testcases
     *
     * @param result
     * @param pageSize
     * @return
     */
    public static List<JunitTestCaseGroup> split(TestResult result, int pageSize) {
        List<JunitTestCaseGroup> testCasesCollect = new ArrayList<>();
        JunitTestCaseGroup group = new JunitTestCaseGroup();
        testCasesCollect.add(group);
        for (SuiteResult suite : result.getSuites()) {
            for (CaseResult testCase : suite.getCases()) {
                group.add(testCase);
                if (group.getTotal() > pageSize) {
                    group = new JunitTestCaseGroup();
                    testCasesCollect.add(group);
                }
            }
        }
        return testCasesCollect;
    }

    /**
     * @param resultAction
     * @param pageSize
     * @return
     */
    public static List<JunitTestCaseGroup> splitRaw(AbstractTestResultAction resultAction, int pageSize) {
        List<JunitTestCaseGroup> testCasesCollect = new ArrayList<>();
        JunitTestCaseGroup group = new JunitTestCaseGroup();
        testCasesCollect.add(group);
        List<CaseResult> results = new ArrayList<>();
        results.addAll(resultAction.getFailedTests());
        results.addAll(resultAction.getSkippedTests());
        results.addAll(resultAction.getPassedTests());
        for (CaseResult testCase : results) {
            group.add(testCase);
            if (group.getTotal() > pageSize) {
                group = new JunitTestCaseGroup();
                testCasesCollect.add(group);
            }
        }
        return testCasesCollect;
    }

    /**
     * split aggregated test result into groups, each contains maximum pageSize testcases
     *
     * @param aggregatedResult
     * @param pageSize
     * @return
     */
    public static List<JunitTestCaseGroup> split(AggregatedTestResultAction aggregatedResult, int pageSize) {
        List<JunitTestCaseGroup> testCasesCollect = new ArrayList<>();
        JunitTestCaseGroup group = new JunitTestCaseGroup();
        testCasesCollect.add(group);
        for (AggregatedTestResultAction.ChildReport childReport : aggregatedResult.getChildReports()) {
            if (childReport.result instanceof TestResult) {
                TestResult testResult = (TestResult) childReport.result;
                for (SuiteResult suite : testResult.getSuites()) {
                    for (CaseResult testCase : suite.getCases()) {
                        group.add(testCase);
                        if (group.getTotal() > pageSize) {
                            group = new JunitTestCaseGroup();
                            testCasesCollect.add(group);
                        }
                    }
                }
            }
        }
        return testCasesCollect;
    }

    /**
     * Get the Junit report  from build
     * Extract from either TestResultAction or AggregatedTestResultAction
     *
     * @param build
     * @param pageSize
     * @return
     */
    public static List<JunitTestCaseGroup> getBuildReport(Run build, int pageSize) {
        List<JunitTestCaseGroup> junitReports = new ArrayList<>();
        if (build == null) {
            return junitReports;
        }
        TestResultAction resultAction = build.getAction(TestResultAction.class);
        if (resultAction != null) {
            return split(resultAction.getResult(), pageSize);
        }
        AggregatedTestResultAction aggAction = build.getAction(AggregatedTestResultAction.class);
        if (aggAction != null) {
            return split(aggAction, pageSize);
        }
        //last resort, try AbstractTestResultAction
        AbstractTestResultAction abstractTestResultAction = build.getAction(AbstractTestResultAction.class);
        if (abstractTestResultAction != null) {
            return splitRaw(abstractTestResultAction, pageSize);
        }
        return junitReports;
    }

    /**
     * @param build
     * @return summary of failures,passes,skips, total and duration
     */
    public static Map<String, Object> getSummary(Run build) {
        List<JunitTestCaseGroup> results = getBuildReport(build, Integer.MAX_VALUE);
        Map<String, Object> summary = new HashMap();
        if (results.isEmpty()) {
            return summary;
        }
        JunitTestCaseGroup testResult = results.get(0);
        summary.put("failures", testResult.getFailures());
        summary.put("passes", testResult.getPasses());
        summary.put("skips", testResult.getSkips());
        summary.put("total", testResult.getTotal());
        summary.put("duration", testResult.getDuration());
        return summary;
    }
}
