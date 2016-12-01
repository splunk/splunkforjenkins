package com.splunk.splunkjenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.HealthReportingAction;
import hudson.model.Run;
import org.jvnet.tiger_types.Types;

import javax.annotation.Nonnull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Extracts  Coverage metric
 *
 * @param <M> Coverage Action
 */
public abstract class CoverageMetricAdapter<M extends HealthReportingAction> implements ExtensionPoint {
    public final Class<M> targetType;

    public CoverageMetricAdapter() {
        Type type = Types.getBaseClass(getClass(), CoverageMetricAdapter.class);
        if (type instanceof ParameterizedType)
            targetType = Types.erasure(Types.getTypeArgument(type, 0));
        else
            throw new IllegalStateException(getClass() + " uses the raw type for extending CoverageMetricAdapter");

    }

    public M getAction(Run run) {
        return run.getAction(targetType);
    }

    public boolean isApplicable(Run build) {
        return getAction(build) != null;
    }

    @Nonnull
    public static Map<Metric, Integer> getReport(Run build) {
        List<CoverageMetricAdapter> adapters = ExtensionList.lookup(CoverageMetricAdapter.class);
        for (CoverageMetricAdapter adapter : adapters) {
            if (adapter.isApplicable(build)) {
                return adapter.getReport(adapter.getAction(build));
            }
        }
        return Collections.EMPTY_MAP;
    }

    public abstract Map<Metric, Integer> getReport(M coverageAction);

    public enum Metric {
        PACKAGE("packages"),
        FILE("files"),
        CLASS("classes"),
        METHOD("methods"),
        CONDITIONAL("conditionals"),
        STATEMENT("statements"),
        ELEMENT("elements");

        private String description;

        Metric(String description) {
            this.description = description;
        }

        public String toString() {
            return description;
        }

        /**
         * Clover and Cobertura use different metrics name, try to alain them using nearest
         *
         * @param name Metrics name
         * @return enum if defined, otherwise null
         */
        public static Metric getMetric(String name) {
            for (Metric metric : values()) {
                String metricsName=metric.name();
                if (metricsName.equals(name) || name.startsWith(metricsName)) {
                    return metric;
                }
            }
            return null;
        }
    }
}
