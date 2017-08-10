package com.splunk.splunkjenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResult;
import org.jvnet.tiger_types.Types;

import javax.annotation.Nonnull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
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

    /**
     * @param build jenkins build
     * @return all the test result added in the build
     */
    @Nonnull
    public static List<TestResult> getTestResult(Run build) {
        return getTestResult(build, null);
    }

    /**
     * @param build         jenkins build
     * @param ignoreActions a list of test action class name
     * @return the test result filtered by the test action name
     */
    @Nonnull
    public static List<TestResult> getTestResult(Run build, List<String> ignoreActions) {
        List<AbstractTestResultAdapter> adapters = ExtensionList.lookup(AbstractTestResultAdapter.class);
        List<TestResult> testResults = new ArrayList<>();
        for (AbstractTestResultAdapter adapter : adapters) {
            if (adapter.isApplicable(build)) {
                if (ignoreActions != null && ignoreActions.contains(adapter.targetType.getCanonicalName())) {
                    // the test action is ignored
                    continue;
                }
                testResults.addAll(adapter.getTestResult(adapter.getAction(build)));
            }
        }
        return testResults;
    }

    public abstract <T extends TestResult> List<T> getTestResult(A resultAction);
}
