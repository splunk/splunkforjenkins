package com.splunk.splunkjenkins.model;

import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;
import hudson.Extension;
import hudson.tasks.test.TestResult;
import org.apache.commons.lang.reflect.FieldUtils;
import org.jenkinsci.plugins.cucumber.jsontestsupport.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Extension(optional = true)
public class CucumberTestResultAdapter extends AbstractTestResultAdapter<CucumberTestResultAction> {
    @Override
    public List<TestCaseResult> getTestResult(CucumberTestResultAction resultAction) {
        List<TestCaseResult> caseResults = new ArrayList<>();
        Collection<FeatureResult> featureResults = resultAction.getResult().getChildren();
        //prefix is cucumber/
        String prefix=resultAction.getResult().getName()+"/";
        for (FeatureResult featureResult : featureResults) {
            for (ScenarioResult scenarioResult : featureResult.getChildren()) {
                String className=scenarioResult.getId();
                if(className.startsWith(prefix)){
                    className=className.substring(prefix.length());
                }
                for (BeforeAfterResult beforeAfterResult : scenarioResult.getBeforeResults()) {
                    TestCaseResult testCaseResult = convert(beforeAfterResult, className);
                    caseResults.add(testCaseResult);
                }
                for (StepResult stepResult : scenarioResult.getStepResults()) {
                    TestCaseResult testCaseResult = convert(stepResult, className);
                    testCaseResult.setTestName(getStepName(stepResult));
                    caseResults.add(testCaseResult);
                }
                for (BeforeAfterResult afterResult : scenarioResult.getAfterResults()) {
                    TestCaseResult testCaseResult = convert(afterResult, className);
                    caseResults.add(testCaseResult);
                }
            }
        }
        return caseResults;
    }

    /**
     * @param testResult   bdd test result
     * @param className bdd test scenario name
     * @return
     */
    private TestCaseResult convert(TestResult testResult, String className) {
        TestCaseResult testCaseResult = new TestCaseResult();
        testCaseResult.setTestName(testResult.getName());
        testCaseResult.setClassName(className);
        testCaseResult.setSkipped(testResult.getSkipCount() > 0);
        testCaseResult.setDuration(testResult.getDuration());
        testCaseResult.setUniqueName(testResult.getId());
        if (testResult.getSkipCount() > 0) {
            testCaseResult.setStatus(TestStatus.SKIPPED);
        } else if (testResult.getFailCount() > 0) {
            testCaseResult.setStatus(TestStatus.FAILURE);
        } else {
            testCaseResult.setStatus(TestStatus.PASSED);
        }
        testCaseResult.setFailedSince(testResult.getFailedSince());
        if (testCaseResult.getStatus() == TestStatus.FAILURE) {
            updateErrorDetails(testResult, testCaseResult);
        }
        return testCaseResult;
    }

    private void updateErrorDetails(TestResult testResult, TestCaseResult testCaseResult) {
        try {
            Result result = (Result) FieldUtils.readField(testResult, "result", true);
            String errorMessage = result.getErrorMessage();
            if (errorMessage != null) {
                //errorMessage is stack trace
                int idx = errorMessage.indexOf(": ");
                idx = idx > 0 ? idx : errorMessage.indexOf("\n");
                if (idx == -1) { //errorMessage is not an exception
                    idx = Math.min(80, errorMessage.length());
                }
                testCaseResult.setErrorDetails(errorMessage.substring(0, idx));
                testCaseResult.setErrorStackTrace(errorMessage);
            }
        } catch (IllegalArgumentException e) {
            //just ignore, api changed
        } catch (ReflectiveOperationException e) {
        }
    }

    private String getStepName(StepResult stepResult) {
        String stepName = "";
        try {
            Step step = (Step) FieldUtils.readField(stepResult, "step", true);
            stepName = step.getKeyword() + " " + step.getName();
        } catch (IllegalArgumentException e) {
            //just ignore, api changed
        } catch (ReflectiveOperationException e) {
        }
        return stepName;
    }
}
