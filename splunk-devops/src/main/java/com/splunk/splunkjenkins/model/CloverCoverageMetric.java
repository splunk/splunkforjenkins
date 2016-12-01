package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.plugins.clover.CloverBuildAction;
import hudson.plugins.clover.results.ProjectCoverage;

import java.util.HashMap;
import java.util.Map;

/**
 * CoverageMetric for <a href="https://confluence.atlassian.com/display/CLOVER">clover</a>
 */
@Extension(optional = true)
public class CloverCoverageMetric extends CoverageMetricAdapter<CloverBuildAction> {
    /**
     * @return coverage summary
     * {@inheritDoc}
     */
    @Override
    public Map<Metric, Integer> getReport(CloverBuildAction coverageAction) {
        ProjectCoverage projectCoverage = coverageAction.getResult();
        Map<Metric, Integer> result = new HashMap<>();
        int methods = projectCoverage.getMethodCoverage().getPercentage();
        int statements = projectCoverage.getMethodCoverage().getPercentage();
        int elements = projectCoverage.getElementCoverage().getPercentage();
        result.put(Metric.METHOD, methods);
        result.put(Metric.STATEMENT, statements);
        result.put(Metric.ELEMENT, elements);
        return result;
    }
}
