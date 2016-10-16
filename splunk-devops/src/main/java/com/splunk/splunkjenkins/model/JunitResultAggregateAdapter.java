package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.test.AggregatedTestResultAction;

import java.util.ArrayList;
import java.util.List;

@Extension(optional = true)
public class JunitResultAggregateAdapter extends AbstractTestResultAdapter<AggregatedTestResultAction> {
    @Override
    public List<CaseResult> getTestResult(AggregatedTestResultAction resultAction) {
        List<CaseResult> caseResults = new ArrayList<>();
        for (AggregatedTestResultAction.ChildReport childReport : resultAction.getChildReports()) {
            if (childReport.result instanceof hudson.tasks.junit.TestResult) {
                hudson.tasks.junit.TestResult testResult = (hudson.tasks.junit.TestResult) childReport.result;
                for (SuiteResult suite : testResult.getSuites()) {
                    for (CaseResult testCase : suite.getCases()) {
                        caseResults.add(testCase);
                    }
                }
            }
        }
        return caseResults;
    }
}
