package com.splunk.splunkjenkins.model;

import hudson.tasks.junit.CaseResult;

import java.util.ArrayList;
import java.util.List;

public class JunitTestCaseGroup {
    int failures;
    int passes;
    int skips;
    int total;
    float duration;
    //alias, fields for json serialization
    int tests;
    float time;
    //backward compatible with junit3 xml which has errors field
    int errors = 0;
    List<CaseResult> testcase = new ArrayList<>();

    public void add(CaseResult result) {
        this.failures += result.getFailCount();
        this.passes += result.getPassCount();
        this.skips += result.getSkipCount();
        this.total += result.getTotalCount();
        this.duration += result.getDuration();
        //update alias
        this.tests = this.total;
        this.time = this.duration;
        this.testcase.add(result);
    }

    public int getFailures() {
        return failures;
    }

    public int getPasses() {
        return passes;
    }

    public int getSkips() {
        return skips;
    }

    public int getTotal() {
        return total;
    }

    public float getDuration() {
        return duration;
    }

    public List<CaseResult> getTestcase() {
        return testcase;
    }
}
