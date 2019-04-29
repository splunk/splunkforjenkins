package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.plugins.testng.TestNGTestResultBuildAction;
import hudson.plugins.testng.results.ClassResult;
import hudson.plugins.testng.results.MethodResult;
import hudson.plugins.testng.results.MethodResultException;
import hudson.plugins.testng.results.TestNGTestResult;
import hudson.tasks.test.AbstractTestResultAction;

import java.util.ArrayList;
import java.util.List;

import static com.splunk.splunkjenkins.Constants.ERROR_MESSAGE_NA;

@Extension(optional = true)
public class TestNGResultAdapter extends AbstractTestResultAdapter<TestNGTestResultBuildAction> {
    @Override
    public List<TestCaseResult> getTestResult(TestNGTestResultBuildAction resultAction) {
        List<TestNGTestResult> testResults = resultAction.getResult().getTestList();
        List<TestCaseResult> caseResults = new ArrayList<>();
        for (TestNGTestResult testResult : testResults) {
            for (ClassResult classResult : testResult.getClassList()) {
                for (MethodResult methodResult : classResult.getTestMethods()) {
                    TestCaseResult testCaseResult = new TestCaseResult();
                    testCaseResult.setTestName(methodResult.getName());
                    testCaseResult.setUniqueName(methodResult.getSafeName());
                    testCaseResult.setDuration(methodResult.getDuration());
                    testCaseResult.setClassName(methodResult.getClassName());
                    testCaseResult.setGroupName(testResult.getName());
                    String status = Util.fixNull(methodResult.getStatus()).toLowerCase();
                    switch (status) {
                        case "fail":
                            MethodResultException exception = methodResult.getException();
                            if (exception != null) {
                                if (exception.getMessage() != null) {
                                    testCaseResult.setErrorDetails(exception.getMessage());
                                } else {
                                    testCaseResult.setErrorDetails(exception.getExceptionName());
                                }
                            } else {
                                testCaseResult.setErrorDetails(ERROR_MESSAGE_NA);

                            }
                            testCaseResult.setErrorStackTrace(methodResult.getErrorStackTrace());
                            Run run = methodResult.getRun();
                            if (run != null) {
                                int failedSince = getFailedSince(run, methodResult.getId());
                                testCaseResult.setFailedSince(failedSince);
                            }
                            testCaseResult.setStatus(TestStatus.FAILURE);
                            break;
                        case "skip":
                            testCaseResult.setSkipped(true);
                            testCaseResult.setStatus(TestStatus.SKIPPED);
                            break;
                        case "pass":
                            testCaseResult.setStatus(TestStatus.PASSED);
                            break;
                        default:
                            // empty status, marked as passed
                            testCaseResult.setStatus(TestStatus.PASSED);
                    }
                    testCaseResult.setSkippedMessage(methodResult.getDescription());
                    testCaseResult.setStdout(methodResult.getReporterOutput());
                    caseResults.add(testCaseResult);
                }
            }
        }
        return caseResults;
    }

    /**
     * If this test failed, then return the build number
     * when this test started failing.
     *
     * @return the build number when this test started failing.
     */
    private int getFailedSince(Run b, String id) {
        int i = 0;
        int buildNumber = 0;
        while (i++ < 20) {
            buildNumber = b.getNumber();
            b = b.getPreviousBuild();
            if (b == null) {
                return buildNumber;
            }
            AbstractTestResultAction r = b.getAction(targetType);
            if (r != null) {
                MethodResult result = (MethodResult) r.findCorrespondingResult(id);
                if (result == null || !"fail".equalsIgnoreCase(result.getStatus())) {
                    return buildNumber;
                }
            } else {
                return buildNumber;
            }
        }
        return buildNumber;
    }

}
