package com.splunk.splunkjenkins.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.TestResult;

import java.util.ArrayList;
import java.util.List;

@SuppressFBWarnings("URF_UNREAD_FIELD")
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
    List<TestResult> testcase = new ArrayList<>();

    public void add(TestResult result) {
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

    public List<TestResult> getTestcase() {
        return testcase;
    }

    @Override
    public String toString() {
        return "failures=" + failures +
                ", passes=" + passes +
                ", skips=" + skips +
                ", errors=" + errors +
                ", total=" + total +
                ", duration=" + Util.getPastTimeString((long)duration);

    }
}
