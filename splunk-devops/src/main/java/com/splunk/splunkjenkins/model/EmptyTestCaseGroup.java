package com.splunk.splunkjenkins.model;

import hudson.tasks.test.TestResult;

import java.util.Collections;
import java.util.List;

public class EmptyTestCaseGroup extends JunitTestCaseGroup {
    private String message = "no test case found";

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
}
