package com.splunk.splunkjenkins

import com.splunk.splunkjenkins.utils.EventType
import com.splunk.splunkjenkins.utils.LogEventHelper
import com.splunk.splunkjenkins.utils.SplunkLogService
import hudson.EnvVars
import hudson.model.AbstractBuild
import hudson.model.TaskListener
import hudson.util.spring.ClosureScript
import jenkins.model.Jenkins
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import java.util.logging.Level
import java.util.logging.Logger

import static com.splunk.splunkjenkins.RunDelegate.genJunitTestReportWithEnv;


public class UserActionDSL {
    static final LOG = Logger.getLogger(UserActionDSL.class.name)

    public void perform(AbstractBuild build, TaskListener listener) {
        try {
            EnvVars enVars = build.getEnvironment(listener);
            SplunkJenkinsInstallation splunkConfig = SplunkJenkinsInstallation.get();

            if (splunkConfig != null && splunkConfig.getScript() != null) {
                RunDelegate delegate = new RunDelegate(build, enVars, listener);
                CompilerConfiguration cc = new CompilerConfiguration();
                cc.scriptBaseClass = ClosureScript.class.name;
                ImportCustomizer ic = new ImportCustomizer()
                ic.addStaticStars(LogEventHelper.class.name)
                cc.addCompilationCustomizers(ic)
                ClosureScript dslScript = (ClosureScript) new GroovyShell(Jenkins.instance.pluginManager.uberClassLoader, new Binding(), cc)
                        .parse(splunkConfig.getScript())
                dslScript.setDelegate(delegate);
                try {
                    dslScript.run()
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "DSL script failed", e);
                    listener.println("failed to run script " + e)
                }
            } else {
                //user not provide post action, use default
                Map event = genJunitTestReportWithEnv(build, enVars);
                SplunkLogService.getInstance().send(event, EventType.BUILD_REPORT);
            }
            listener.getLogger().flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

