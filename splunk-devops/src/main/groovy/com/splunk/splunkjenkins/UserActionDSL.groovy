package com.splunk.splunkjenkins

import com.splunk.splunkjenkins.listeners.LoggingRunListener
import com.splunk.splunkjenkins.utils.LogEventHelper
import hudson.EnvVars
import hudson.model.AbstractBuild
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.spring.ClosureScript
import jenkins.model.Jenkins
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import java.util.logging.Level
import java.util.logging.Logger

import static com.splunk.splunkjenkins.utils.LogEventHelper.getBuildVariables
import static com.splunk.splunkjenkins.utils.LogEventHelper.getEnvironment

public class UserActionDSL {
    static final LOG = Logger.getLogger(LoggingRunListener.class.name)

    public void perform(Run build, TaskListener listener, String scriptText) {
        try {
            Map buildParameters = getBuildVariables(build);
            if (StringUtils.isNotEmpty(scriptText)) {
                def workSpace;
                if (build.metaClass.respondsTo(build, "getWorkspace")) {
                    //getWorkspace defined in build
                    workSpace = build.workspace;
                }
                RunDelegate delegate = new RunDelegate(build: build, workSpace: workSpace,
                        env: buildParameters, listener: listener);
                CompilerConfiguration cc = new CompilerConfiguration();
                cc.scriptBaseClass = ClosureScript.class.name;
                ImportCustomizer ic = new ImportCustomizer()
                ic.addStaticStars(LogEventHelper.class.name)
                ic.addStarImport("jenkins.model")
                cc.addCompilationCustomizers(ic)
                ClosureScript dslScript = (ClosureScript) new GroovyShell(Jenkins.instance.pluginManager.uberClassLoader, new Binding(), cc)
                        .parse(scriptText)
                dslScript.setDelegate(delegate);
                try {
                    dslScript.run()
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "UserActionDSL script failed", e);
                    e.printStackTrace(listener.getLogger())
                }
                listener.getLogger().flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

