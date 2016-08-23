package com.splunk.splunkjenkins


import com.splunk.splunkjenkins.utils.LogEventHelper
import hudson.EnvVars
import hudson.model.AbstractBuild
import hudson.model.TaskListener
import hudson.util.spring.ClosureScript
import jenkins.model.Jenkins
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import java.util.logging.Level
import java.util.logging.Logger

public class UserActionDSL {
    static final LOG = Logger.getLogger(LoggingRunListener.class.name)

    public void perform(AbstractBuild build, TaskListener listener) {
        try {
            EnvVars enVars = build.getEnvironment(listener);
            SplunkJenkinsInstallation splunkConfig = SplunkJenkinsInstallation.get();

            if (splunkConfig != null && StringUtils.isNotEmpty(splunkConfig.getScript())) {
                RunDelegate delegate = new RunDelegate(build, enVars, listener);
                CompilerConfiguration cc = new CompilerConfiguration();
                cc.scriptBaseClass = ClosureScript.class.name;
                ImportCustomizer ic = new ImportCustomizer()
                ic.addStaticStars(LogEventHelper.class.name)
                ic.addStarImport("jenkins.model")
                cc.addCompilationCustomizers(ic)
                String scriptText = splunkConfig.getScript();
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

