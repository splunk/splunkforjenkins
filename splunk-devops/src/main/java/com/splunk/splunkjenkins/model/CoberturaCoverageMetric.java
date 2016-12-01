package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.plugins.cobertura.CoberturaBuildAction;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CoverageMetric for <a href="https://github.com/cobertura/cobertura/wiki">Cobertura</a>
 */
@Extension(optional = true)
public class CoberturaCoverageMetric extends CoverageMetricAdapter<CoberturaBuildAction> {
    /**
     * @return coverage summary
     * {@inheritDoc}
     */
    @Override
    public Map<Metric, Integer> getReport(CoberturaBuildAction coverageAction) {
        Map<Metric, Integer> result = new HashMap<>();
        CoverageResult coverageResult = coverageAction.getResult();
        Set<CoverageMetric> metrics = coverageAction.getResult().getMetrics();
        for (CoverageMetric metric : metrics) {
            int percentage = coverageResult.getCoverage(metric).getPercentage();
            Metric reportMetric = Metric.getMetric(metric.name());
            if (reportMetric != null) {
                result.put(reportMetric, percentage);
            }
        }
        return result;
    }
}
