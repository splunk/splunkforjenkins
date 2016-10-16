package com.splunk.splunkjenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import org.jvnet.tiger_types.Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class LoggingJobExtractor<R extends Run> implements ExtensionPoint {
    public final Class<R> targetType;

    public LoggingJobExtractor() {
        Type type = Types.getBaseClass(getClass(), LoggingJobExtractor.class);
        if (type instanceof ParameterizedType)
            targetType = Types.erasure(Types.getTypeArgument(type, 0));
        else
            throw new IllegalStateException(getClass() + " uses the raw type for extending LoggingJobExtractor");
    }

    public abstract Map<String, Object> extract(R r, boolean completed);

    /**
     * @return Returns all the registered {@link LoggingJobExtractor}s
     */
    public static ExtensionList<LoggingJobExtractor> all() {
        return ExtensionList.lookup(LoggingJobExtractor.class);
    }

    public static List<LoggingJobExtractor> canApply(Run run) {
        List<LoggingJobExtractor> extensions = new ArrayList<>();
        for (LoggingJobExtractor extendListener : LoggingJobExtractor.all()) {
            if (extendListener.targetType.isInstance(run)) {
                extensions.add(extendListener);
            }
        }
        return extensions;
    }
}
