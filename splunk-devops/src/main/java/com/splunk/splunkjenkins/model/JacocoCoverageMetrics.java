package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.plugins.jacoco.model.Coverage;
import hudson.plugins.jacoco.model.CoverageObject;
import hudson.plugins.jacoco.report.ClassReport;
import hudson.plugins.jacoco.report.CoverageReport;
import hudson.plugins.jacoco.report.MethodReport;
import hudson.plugins.jacoco.report.PackageReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.splunk.splunkjenkins.Constants.COVERAGE_OVERALL_NAME;

/**
 * CoverageMetric for <a href="https://wiki.jenkins-ci.org/display/JENKINS/JaCoCo+Plugin">JaCoCo</a>
 */
@Extension(optional = true)
public class JacocoCoverageMetrics extends CoverageMetricsAdapter<JacocoBuildAction> {
    @Override
    public Map<Metric, Integer> getMetrics(JacocoBuildAction coverageAction) {
        return extract(coverageAction);
    }

    private Map<Metric, Integer> extract(CoverageObject coverageObject) {
        Map<Metric, Integer> result = new HashMap<>();
        addMetric(result, Metric.CLASS, coverageObject.getClassCoverage());
        addMetric(result, Metric.METHOD, coverageObject.getMethodCoverage());
        addMetric(result, Metric.BRANCH, coverageObject.getBranchCoverage());
        addMetric(result, Metric.COMPLEXITY, coverageObject.getComplexityScore());
        addMetric(result, Metric.INSTRUCTION, coverageObject.getInstructionCoverage());
        addMetric(result, Metric.LINE, coverageObject.getLineCoverage());
        return result;
    }

    private void addMetric(Map<Metric, Integer> result, Metric metric, Coverage coverage) {
        if (coverage != null) {
            result.put(metric, coverage.getPercentage());
        }
    }

    private void appendDetail(CoverageDetail result, CoverageObject coverageObject) {
        appendDetail(result, Metric.CLASS, coverageObject.getClassCoverage());
        appendDetail(result, Metric.METHOD, coverageObject.getMethodCoverage());
        appendDetail(result, Metric.BRANCH, coverageObject.getBranchCoverage());
        appendDetail(result, Metric.COMPLEXITY, coverageObject.getComplexityScore());
        appendDetail(result, Metric.INSTRUCTION, coverageObject.getInstructionCoverage());
        appendDetail(result, Metric.LINE, coverageObject.getLineCoverage());
    }

    /**
     * add percentage, covered and total
     *
     * @param detail
     * @param reportMetric
     * @param coverage
     */
    private void appendDetail(CoverageDetail detail, Metric reportMetric, Coverage coverage) {
        if (coverage != null) {
            detail.add(reportMetric + PERCENTAGE_SUFFIX, coverage.getPercentage());
            detail.add(reportMetric + TOTAL_SUFFIX, coverage.getTotal());
            detail.add(reportMetric + COVERED_SUFFIX, coverage.getCovered());
        }
    }

    @Override
    public List<CoverageDetail> getReport(JacocoBuildAction coverageAction) {
        CoverageReport report = coverageAction.getResult();
        List<CoverageDetail> result = new ArrayList<>();
        if (!report.hasChildren()) {
            return result;
        }
        CoverageDetail summary = new CoverageDetail(COVERAGE_OVERALL_NAME, CoverageLevel.SUMMARY);
        result.add(summary);
        appendDetail(summary, coverageAction);
        Map<String, PackageReport> packages = report.getChildren();
        for (Map.Entry<String, PackageReport> entry : packages.entrySet()) {
            CoverageDetail packageDetail = new CoverageDetail(entry.getKey(), CoverageLevel.PACKAGE);
            result.add(packageDetail);
            appendDetail(packageDetail, entry.getValue());
            Map<String, ClassReport> classReports = entry.getValue().getChildren();
            for (Map.Entry<String, ClassReport> classEntry : classReports.entrySet()) {
                CoverageDetail classDetail = new CoverageDetail(classEntry.getKey(), CoverageLevel.CLASS);
                result.add(classDetail);
                appendDetail(classDetail, classEntry.getValue());
                Map<String, MethodReport> methodReports = classEntry.getValue().getChildren();
                for (Map.Entry<String, MethodReport> methodEntry : methodReports.entrySet()) {
                    CoverageDetail methodDetail = new CoverageDetail(methodEntry.getKey(), CoverageLevel.METHOD);
                    result.add(methodDetail);
                    appendDetail(methodDetail, methodEntry.getValue());
                }
            }
        }
        return result;
    }


}
