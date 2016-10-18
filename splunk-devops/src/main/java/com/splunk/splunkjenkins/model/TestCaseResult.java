package com.splunk.splunkjenkins.model;

import hudson.tasks.test.TestObject;
import hudson.tasks.test.TestResult;

public class TestCaseResult extends TestResult {
    private float duration;
    private String className;
    private String testName;
    private boolean skipped;
    private String skippedMessage;
    private String errorStackTrace;
    private String errorDetails;
    private String stdout, stderr;
    private int failedSince;
    private String uniqueName;
    private TestStatus status;

    @Override
    public TestObject getParent() {
        return null;
    }

    @Override
    public TestResult findCorrespondingResult(String id) {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public String getSkippedMessage() {
        return skippedMessage;
    }

    public void setSkippedMessage(String skippedMessage) {
        this.skippedMessage = skippedMessage;
    }

    @Override
    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    public void setErrorStackTrace(String errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }

    @Override
    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    @Override
    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    @Override
    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    @Override
    public int getFailedSince() {
        return failedSince;
    }

    public void setFailedSince(int failedSince) {
        this.failedSince = failedSince;
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public void setUniqueName(String uniqueName) {
        this.uniqueName = uniqueName;
    }

    public TestStatus getStatus() {
        return status;
    }

    public void setStatus(TestStatus status) {
        this.status = status;
    }

    @Override
    public int getPassCount() {
        return TestStatus.PASSED == status ? 1 : 0;
    }

    @Override
    public int getFailCount() {
        return TestStatus.FAILURE == status ? 1 : 0;
    }

    @Override
    public int getSkipCount() {
        return TestStatus.SKIPPED == status ? 1 : 0;
    }
}
