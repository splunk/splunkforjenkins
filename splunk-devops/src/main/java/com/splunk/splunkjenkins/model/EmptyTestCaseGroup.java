package com.splunk.splunkjenkins.model;

import hudson.tasks.test.TestResult;

import java.util.Collections;
import java.util.List;

import static com.splunk.splunkjenkins.Constants.NO_TEST_REPORT_FOUND;

public class EmptyTestCaseGroup extends JunitTestCaseGroup {
    private String message;

    public String getMessage() {
        return message;
    }

    @Override
    public int getFailures() {
        return 0;
    }

    @Override
    public int getPasses() {
        return 0;
    }

    @Override
    public int getSkips() {
        return 0;
    }

    @Override
    public int getTotal() {
        return 0;
    }

    @Override
    public float getDuration() {
        return 0;
    }

    @Override
    public List<TestResult> getTestcase() {
        return Collections.emptyList();
    }

    public void setWarning(boolean flag) {
        this.message = flag?NO_TEST_REPORT_FOUND:null;
    }
}
