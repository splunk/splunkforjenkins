package com.splunk.splunkjenkins.model;

import hudson.Extension;
import hudson.plugins.clover.CloverBuildAction;
import hudson.plugins.clover.results.*;

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
        Map<Metric, Integer> result = extract(projectCoverage);
        return result;
    }

    @Override
    public List<CoverageDetail> getReport(CloverBuildAction coverageAction) {
        ProjectCoverage projectCoverage = coverageAction.getResult();
        List<CoverageDetail> result = new ArrayList<>();
        for (PackageCoverage pcover : projectCoverage.getChildren()) {
            CoverageDetail packageDetail = new CoverageDetail(pcover.getName(), CoverageLevel.PACKAGE);
            result.add(packageDetail);
            packageDetail.putAll(extract(pcover));
            for (FileCoverage fcover : pcover.getChildren()) {
                CoverageDetail fileDetail = new CoverageDetail(pcover.getName(), CoverageLevel.FILE);
                result.add(fileDetail);
                fileDetail.putAll(extract(fcover));
                for (ClassCoverage clazzCover : fcover.getChildren()) {
                    CoverageDetail clazzDetail = new CoverageDetail(clazzCover.getName(), CoverageLevel.CLASS);
                    result.add(clazzDetail);
                    clazzDetail.putAll(extract(clazzCover));
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
}
