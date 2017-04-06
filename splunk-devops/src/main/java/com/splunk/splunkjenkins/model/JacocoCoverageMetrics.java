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

    @Override
    public List<CoverageDetail> getReport(JacocoBuildAction coverageAction) {
        CoverageReport report = coverageAction.getResult();
        List<CoverageDetail> result = new ArrayList<>();
        if (!report.hasChildren()) {
            return result;
        }
        Map<String, PackageReport> packages = report.getChildren();
        for (Map.Entry<String, PackageReport> entry : packages.entrySet()) {
            CoverageDetail packageDetail = new CoverageDetail(entry.getKey(), CoverageLevel.PACKAGE);
            result.add(packageDetail);
            packageDetail.putAll(extract(entry.getValue()));
            Map<String, ClassReport> classReports = entry.getValue().getChildren();
            for (Map.Entry<String, ClassReport> classEntry : classReports.entrySet()) {
                CoverageDetail classDetail = new CoverageDetail(classEntry.getKey(), CoverageLevel.CLASS);
                result.add(classDetail);
                classDetail.putAll(extract(classEntry.getValue()));
                Map<String, MethodReport> methodReports = classEntry.getValue().getChildren();
                for (Map.Entry<String, MethodReport> methodEntry : methodReports.entrySet()) {
                    CoverageDetail methodDetail = new CoverageDetail(methodEntry.getKey(), CoverageLevel.METHOD);
                    result.add(methodDetail);
                    methodDetail.putAll(extract(methodEntry.getValue()));
                }
            }
        }
        return result;
    }


}
