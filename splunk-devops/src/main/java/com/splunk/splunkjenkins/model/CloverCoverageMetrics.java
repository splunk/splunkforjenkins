package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.plugins.clover.CloverBuildAction;
import hudson.plugins.clover.results.ClassCoverage;
import hudson.plugins.clover.results.FileCoverage;
import hudson.plugins.clover.results.PackageCoverage;
import hudson.plugins.clover.results.ProjectCoverage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CoverageMetric for <a href="https://confluence.atlassian.com/display/CLOVER">clover</a>
 */
@Extension(optional = true)
public class CloverCoverageMetrics extends CoverageMetricsAdapter<CloverBuildAction> {
    /**
     * @return coverage summary
     * {@inheritDoc}
     */
    @Override
    public Map<Metric, Integer> getMetrics(CloverBuildAction coverageAction) {
        ProjectCoverage projectCoverage = coverageAction.getResult();
        Map<Metric, Integer> result = new HashMap<>();
        result.put(Metric.METHOD, projectCoverage.getMethodCoverage().getPercentage());
        result.put(Metric.STATEMENT, projectCoverage.getStatementCoverage().getPercentage());
        result.put(Metric.ELEMENT, projectCoverage.getElementCoverage().getPercentage());
        return result;
    }

    @Override
    public List<CoverageDetail> getReport(CloverBuildAction coverageAction) {
        ProjectCoverage projectCoverage = coverageAction.getResult();
        List<CoverageDetail> result = new ArrayList<>();
        for (PackageCoverage pcover : projectCoverage.getChildren()) {
            for (FileCoverage fcover : pcover.getChildren()) {
                for (ClassCoverage clazzCover : fcover.getChildren()) {
                    CoverageDetail detail = new CoverageDetail(clazzCover.getName());
                    result.add(detail);
                    detail.addMetric(Metric.METHOD, clazzCover.getMethodCoverage().getPercentage());
                    detail.addMetric(Metric.STATEMENT, clazzCover.getStatementCoverage().getPercentage());
                    detail.addMetric(Metric.ELEMENT, clazzCover.getElementCoverage().getPercentage());
                }
            }
        }
        return result;
    }
}
