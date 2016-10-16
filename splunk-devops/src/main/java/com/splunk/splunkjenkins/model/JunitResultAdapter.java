package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResultAction;

import java.util.ArrayList;
import java.util.List;

@Extension(optional = true)
public class JunitResultAdapter extends AbstractTestResultAdapter<TestResultAction> {
    @Override
    public List<CaseResult> getTestResult(TestResultAction resultAction) {
        List<CaseResult> caseResults = new ArrayList<>();
        hudson.tasks.junit.TestResult result = resultAction.getResult();
        for (SuiteResult suite : result.getSuites()) {
            for (CaseResult testCase : suite.getCases()) {
                caseResults.add(testCase);
            }
        }
        return caseResults;
    }
}
