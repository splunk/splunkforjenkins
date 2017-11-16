package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.plugins.clover.CloverBuildAction;
import hudson.plugins.clover.Ratio;
import hudson.plugins.clover.results.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.splunk.splunkjenkins.Constants.COVERAGE_OVERALL_NAME;

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
        Map<Metric, Integer> result = extract(projectCoverage);
        return result;
    }

    @Override
    public List<CoverageDetail> getReport(CloverBuildAction coverageAction) {
        ProjectCoverage projectCoverage = coverageAction.getResult();
        List<CoverageDetail> result = new ArrayList<>();
        CoverageDetail summary = new CoverageDetail(COVERAGE_OVERALL_NAME, CoverageLevel.SUMMARY);
        result.add(summary);
        appendDetail(summary, coverageAction);
        for (PackageCoverage pcover : projectCoverage.getChildren()) {
            CoverageDetail packageDetail = new CoverageDetail(pcover.getName(), CoverageLevel.PACKAGE);
            result.add(packageDetail);
            appendDetail(packageDetail, pcover);
            for (FileCoverage fcover : pcover.getChildren()) {
                CoverageDetail fileDetail = new CoverageDetail(pcover.getName(), CoverageLevel.FILE);
                result.add(fileDetail);
                appendDetail(fileDetail, fcover);
                for (ClassCoverage clazzCover : fcover.getChildren()) {
                    CoverageDetail clazzDetail = new CoverageDetail(clazzCover.getName(), CoverageLevel.CLASS);
                    result.add(clazzDetail);
                    appendDetail(clazzDetail, clazzCover);
                }
            }
        }
        return result;
    }

    private Map<Metric, Integer> extract(AbstractCloverMetrics coverageObject) {
        Map<Metric, Integer> result = new HashMap<>();
        result.put(Metric.METHOD, coverageObject.getMethodCoverage().getPercentage());
        result.put(Metric.STATEMENT, coverageObject.getStatementCoverage().getPercentage());
        result.put(Metric.CONDITIONAL, coverageObject.getConditionalCoverage().getPercentage());
        result.put(Metric.ELEMENT, coverageObject.getElementCoverage().getPercentage());
        return result;
    }

    /**
     * get detail report about percentage, covered, and total number
     *
     * @param detail
     * @param coverageObject
     */
    private void appendDetail(CoverageDetail detail, AbstractCloverMetrics coverageObject) {
        appendDetail(detail, Metric.METHOD, coverageObject.getMethodCoverage());
        appendDetail(detail, Metric.STATEMENT, coverageObject.getStatementCoverage());
        appendDetail(detail, Metric.CONDITIONAL, coverageObject.getConditionalCoverage());
        appendDetail(detail, Metric.ELEMENT, coverageObject.getElementCoverage());
    }

    private void appendDetail(CoverageDetail detail, Metric metricName, Ratio ratio) {
        detail.add(metricName + PERCENTAGE_SUFFIX, ratio.getPercentage());
        detail.add(metricName + TOTAL_SUFFIX, (int) ratio.denominator);
        detail.add(metricName + COVERED_SUFFIX, (int) ratio.numerator);
    }
}
