package com.splunk.splunkjenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResult;
import org.jvnet.tiger_types.Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public abstract class AbstractTestResultAdapter<A extends AbstractTestResultAction> implements ExtensionPoint {
    public final Class<A> targetType;

    public AbstractTestResultAdapter() {
        Type type = Types.getBaseClass(getClass(), AbstractTestResultAdapter.class);
        if (type instanceof ParameterizedType)
            targetType = Types.erasure(Types.getTypeArgument(type, 0));
        else
            throw new IllegalStateException(getClass() + " uses the raw type for extending AbstractTestResultAdapter");

    }

    public A getAction(Run run) {
        return run.getAction(targetType);
    }

    public boolean isApplicable(Run build) {
        return getAction(build) != null;
    }

    public static List<TestResult> getTestResult(Run build) {
        List<AbstractTestResultAdapter> adapters = ExtensionList.lookup(AbstractTestResultAdapter.class);
        for (AbstractTestResultAdapter adapter : adapters) {
            if (adapter.isApplicable(build)) {
                return adapter.getTestResult(adapter.getAction(build));
            }
        }
        return Collections.EMPTY_LIST;
    }

    public abstract <T extends TestResult> List<T> getTestResult(A resultAction);
}
