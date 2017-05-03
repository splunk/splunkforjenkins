package com.splunk.splunkjenkins.model;

import com.splunk.splunkjenkins.RunDelegate;
import groovy.lang.GroovyObject;
import hudson.util.spring.ClosureScript;
import org.apache.commons.lang.ClassUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.AbstractWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public abstract class DslDelegateScript extends ClosureScript {

    @Override
    public void setDelegate(GroovyObject delegate) {
        if (!(delegate instanceof RunDelegate)) {
            throw new UnsupportedOperationException("Only RunDelegate class is supported");
        }
        super.setDelegate(delegate);
    }

    public static class UserActionWhiteList extends AbstractWhitelist {
        private static Set<Method> allowedMethods = new HashSet<>();

        static {
            for (Method m : RunDelegate.class.getDeclaredMethods()) {
                if (!m.isSynthetic()) {
                    Whitelisted annotation = m.getAnnotation(Whitelisted.class);
                    if (annotation != null) {
                        allowedMethods.add(m);
                    }
                }
            }
        }

        @Override
        public boolean permitsMethod(Method method, Object receiver, Object[] args) {
            if (args.length != 2 || !(args[0] instanceof String)) {
                return false;
            }
            if (receiver instanceof DslDelegateScript) {
                String methodName = (String) args[0];
                Object[] invokeArgs = (Object[]) args[1];
                Class[] invokeTypes = new Class[invokeArgs.length];
                for (int i = 0; i < invokeArgs.length; i++) {
                    invokeTypes[i] = invokeArgs[i].getClass();
                }
                for (Method m : allowedMethods) {
                    if (m.getName().endsWith(methodName)) {
                        Class[] paramTypes = m.getParameterTypes();
                        // int to Integer.Type
                        Class[] defineTypes = ClassUtils.primitivesToWrappers(paramTypes);
                        if (Arrays.equals(defineTypes, invokeTypes) || Arrays.equals(paramTypes, invokeTypes)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
