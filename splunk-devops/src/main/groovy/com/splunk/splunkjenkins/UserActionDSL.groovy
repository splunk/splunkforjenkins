package com.splunk.splunkjenkins

import com.splunk.splunkjenkins.listeners.LoggingRunListener

import com.splunk.splunkjenkins.utils.LogEventHelper
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.spring.ClosureScript
import jenkins.model.Jenkins
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage

import java.util.logging.Level
import java.util.logging.Logger

import static com.splunk.splunkjenkins.utils.LogEventHelper.getBuildVariables

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
                Binding binding = new Binding();
                binding.setVariable("splunkins", delegate);
                binding.setVariable("build", build);
                binding.setVariable("env", buildParameters);
                // the script written before SecureGroovyScript was introduced, and can not be migrated automatically
                boolean legacyScript = SplunkJenkinsInstallation.get().isLegacyMode();
                try {
                    ApprovalContext context = ApprovalContext.create().withCurrentUser().withItemAsKey(build.getParent());
                    //saving pending for approval
                    String groovyScriptText = ScriptApproval.get().configuring(scriptText, GroovyLanguage.get(), context);
                    //check approval, will throw UnapprovedUsageException
                    ScriptApproval.get().using(groovyScriptText, GroovyLanguage.get());
                    ClassLoader cl = Jenkins.getActiveInstance().getPluginManager().uberClassLoader;
                    if (!legacyScript) {
                        SecureGroovyScript script = new SecureGroovyScript(groovyScriptText, false, null).configuringWithKeyItem();;
                        script.evaluate(cl, binding)
                    } else {
                        //had to call setDelegate
                        CompilerConfiguration cc = GroovySandbox.createSecureCompilerConfiguration();
                        cc.scriptBaseClass = ClosureScript.class.name;
                        ImportCustomizer ic = new ImportCustomizer()
                        ic.addStaticStars(LogEventHelper.class.name)
                        ic.addStarImport("jenkins.model")
                        cc.addCompilationCustomizers(ic)
                        ClassLoader secureClassLoader = GroovySandbox.createSecureClassLoader(cl);
                        GroovyShell shell = new GroovyShell(secureClassLoader, binding, cc);
                        ClosureScript dslScript = (ClosureScript) shell.parse(groovyScriptText);
                        dslScript.setDelegate(delegate);
                        dslScript.run();
                    }
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

