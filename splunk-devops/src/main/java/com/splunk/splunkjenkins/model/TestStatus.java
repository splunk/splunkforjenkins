package com.splunk.splunkjenkins.model;

/**
 * TestNG used PASS,FAIL,SKIP, Junit(CaseResult.Status) used passed,skipped,pass,failed,fixed,regression
 * collapse them into 3 (passed, failure, skipped)
 */
public enum TestStatus {
    PASSED, FAILURE, SKIPPED
}
