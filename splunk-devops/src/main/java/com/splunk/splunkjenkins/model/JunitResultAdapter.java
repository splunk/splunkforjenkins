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
    public List<TestCaseResult> getTestResult(TestResultAction resultAction) {
        List<TestCaseResult> caseResults = new ArrayList<>();
        hudson.tasks.junit.TestResult result = resultAction.getResult();
        for (SuiteResult suite : result.getSuites()) {
            for (CaseResult testCase : suite.getCases()) {
                caseResults.add(convert(testCase, suite.getName()));
            }
        }
        return caseResults;
    }

    /**
     * @param methodResult
     * @return unified test case result
     */
    private TestCaseResult convert(CaseResult methodResult, String suiteName) {
        TestCaseResult testCaseResult = new TestCaseResult();
        testCaseResult.setTestName(methodResult.getName());
        testCaseResult.setUniqueName(methodResult.getFullName());
        testCaseResult.setDuration(methodResult.getDuration());
        testCaseResult.setClassName(methodResult.getClassName());
        testCaseResult.setErrorDetails(methodResult.getErrorDetails());
        testCaseResult.setErrorStackTrace(methodResult.getErrorStackTrace());
        testCaseResult.setSkippedMessage(methodResult.getSkippedMessage());
        testCaseResult.setFailedSince(methodResult.getFailedSince());
        testCaseResult.setStderr(methodResult.getStderr());
        testCaseResult.setStdout(methodResult.getStdout());
        testCaseResult.setGroupName(suiteName);
        TestStatus status = TestStatus.SKIPPED;
        if (!methodResult.isSkipped()) {
            status = methodResult.isPassed() ? TestStatus.PASSED : TestStatus.FAILURE;
        }
        testCaseResult.setStatus(status);
        return testCaseResult;
    }
}
