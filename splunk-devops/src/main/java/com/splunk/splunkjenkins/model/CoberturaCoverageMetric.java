package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.Util;
import hudson.plugins.cobertura.CoberturaBuildAction;
import hudson.plugins.cobertura.targets.CoverageElement;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;

import java.util.*;

/**
 * CoverageMetric for <a href="https://github.com/cobertura/cobertura/wiki">Cobertura</a>
 */
@Extension(optional = true)
public class CoberturaCoverageMetric extends CoverageMetricAdapter<CoberturaBuildAction> {
    private static List<CoverageElement> TOP_LEVELS = Arrays.asList(CoverageElement.PROJECT,
            CoverageElement.JAVA_PACKAGE, CoverageElement.JAVA_FILE);

    /**
     * @return coverage summary
     * {@inheritDoc}
     */
    @Override
    public Map<Metric, Integer> getMetrics(CoberturaBuildAction coverageAction) {
        return getMetrics(coverageAction.getResult());
    }

    private Map<Metric, Integer> getMetrics(CoverageResult coverageResult) {
        Map<Metric, Integer> result = new HashMap<>();
        Set<CoverageMetric> metrics = coverageResult.getMetrics();
        for (CoverageMetric metric : metrics) {
            int percentage = coverageResult.getCoverage(metric).getPercentage();
            Metric reportMetric = Metric.getMetric(metric.name());
            if (reportMetric != null) {
                result.put(reportMetric, percentage);
            }
        }
        return result;
    }

    @Override
    public List<CoverageDetail> getReport(CoberturaBuildAction coverageAction) {
        CoverageResult coverageResult = coverageAction.getResult();
        return getReport(coverageResult);
    }

    private List<CoverageDetail> getReport(CoverageResult coverage) {
        List<CoverageDetail> report = new ArrayList<>();
        if (TOP_LEVELS.contains(coverage.getElement())) {
            for (CoverageResult child : coverage.getChildrenReal().values()) {
                report.addAll(getReport(child));
            }
        } else if (CoverageElement.JAVA_CLASS.equals(coverage.getElement())) {
            String fileName = getPackageName(coverage) + coverage.getName();
            CoverageDetail detail = new CoverageDetail(fileName);
            Map<Metric, Integer> values = getMetrics(coverage);
            for (Metric metric : values.keySet()) {
                detail.addMetric(metric, values.get(metric));
            }
            report.add(detail);
        }
        return report;
    }

    private String getPackageName(CoverageResult result) {
        if (result == null) {
            return "";
        }
        if (CoverageElement.JAVA_PACKAGE.equals(result.getElement())) {
            return result.getName() + ".";
        } else {
            return getPackageName(result.getParent());
        }
    }
}
