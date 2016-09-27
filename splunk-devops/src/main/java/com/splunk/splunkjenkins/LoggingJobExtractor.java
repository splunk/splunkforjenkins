package com.splunk.splunkjenkins;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import org.jvnet.tiger_types.Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
     * Returns all the registered {@link LoggingJobExtractor}s.
     */
    public static ExtensionList<LoggingJobExtractor> all() {
        return ExtensionList.lookup(LoggingJobExtractor.class);
    }
}
