package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.plugins.cobertura.CoberturaBuildAction;
import hudson.plugins.cobertura.Ratio;
import hudson.plugins.cobertura.targets.CoverageElement;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;

import java.util.*;

/**
 * CoverageMetric for <a href="https://github.com/cobertura/cobertura/wiki">Cobertura</a>
 */
@Extension(optional = true)
public class CoberturaCoverageMetrics extends CoverageMetricsAdapter<CoberturaBuildAction> {
    private static List<CoverageElement> TOP_LEVELS = Arrays.asList(CoverageElement.PROJECT,
            CoverageElement.JAVA_PACKAGE, CoverageElement.JAVA_FILE);

    /**
     * @return coverage summary
     * {@inheritDoc}
     */
    @Override
    public Map<Metric, Integer> getMetrics(CoberturaBuildAction coverageAction) {
        return extract(coverageAction.getResult());
    }

    private Map<Metric, Integer> extract(CoverageResult coverageResult) {
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

    private void appendDetail(CoverageDetail detail, CoverageResult coverageResult) {
        Set<CoverageMetric> metrics = coverageResult.getMetrics();
        for (CoverageMetric metric : metrics) {
            Metric reportMetric = Metric.getMetric(metric.name());
            if (reportMetric != null) {
                Ratio ratio = coverageResult.getCoverage(metric);
                detail.add(reportMetric + PERCENTAGE_SUFFIX, ratio.getPercentage());
                detail.add(reportMetric + TOTAL_SUFFIX, (int) ratio.denominator);
                detail.add(reportMetric + COVERED_SUFFIX, (int) ratio.numerator);
            }
        }
    }

    @Override
    public List<CoverageDetail> getReport(CoberturaBuildAction coverageAction) {
        CoverageResult coverageResult = coverageAction.getResult();
        return getReport(coverageResult, "");
    }

    private List<CoverageDetail> getReport(CoverageResult coverage, String prefix) {
        List<CoverageDetail> report = new ArrayList<>();
        CoverageLevel level;
        prefix = prefix == null ? "" : prefix;
        String coverageName = prefix + coverage.getName();
        String childPrefix = prefix;
        switch (coverage.getElement()) {
            case JAVA_FILE:
                level = CoverageLevel.FILE;
                break;
            case JAVA_PACKAGE:
                level = CoverageLevel.PACKAGE;
                childPrefix = coverageName + ".";
                break;
            case PROJECT:
                level = CoverageLevel.PROJECT;
                break;
            case JAVA_METHOD:
                level = CoverageLevel.METHOD;
                break;
            case JAVA_CLASS:
                level = CoverageLevel.CLASS;
                childPrefix = coverageName + "#";
                break;
            default:
                level = CoverageLevel.PACKAGE;
        }
        CoverageDetail detail = new CoverageDetail(coverageName, level);
        appendDetail(detail, coverage);
        report.add(detail);
        Map<String, CoverageResult> children = coverage.getChildrenReal();
        if (children == null || children.isEmpty()) {
            return report;
        }
        for (CoverageResult child : children.values()) {
            report.addAll(getReport(child, childPrefix));
        }
        return report;
    }
}
