package com.splunk.splunkjenkins

import com.splunk.splunkjenkins.listeners.LoggingRunListener
import com.splunk.splunkjenkins.model.DslDelegateScript

import com.splunk.splunkjenkins.utils.LogEventHelper
import hudson.model.Run
import hudson.model.TaskListener
import jenkins.model.Jenkins
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.ClassLoaderWhitelist
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SandboxInterceptor
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.kohsuke.groovy.sandbox.GroovyInterceptor

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
                ClassLoader cl = Jenkins.getActiveInstance().getPluginManager().uberClassLoader;
                // the script written before SecureGroovyScript was introduced, and can not be migrated automatically
                boolean legacyScript = SplunkJenkinsInstallation.get().isLegacyMode();
                try {
                    if (!legacyScript) {
                        SecureGroovyScript script = new SecureGroovyScript(scriptText, false, null).configuringWithKeyItem();
                        script.evaluate(cl, binding)
                    } else {
                        //had to call setDelegate
                        CompilerConfiguration cc = GroovySandbox.createSecureCompilerConfiguration();
                        cc.scriptBaseClass = DslDelegateScript.class.name;
                        ImportCustomizer ic = new ImportCustomizer()
                        ic.addStaticStars(LogEventHelper.class.name)
                        ic.addStarImport("jenkins.model")
                        cc.addCompilationCustomizers(ic)
                        ClassLoader secureClassLoader = GroovySandbox.createSecureClassLoader(cl);
                        GroovyShell shell = new GroovyShell(secureClassLoader, binding, cc);
                        DslDelegateScript dslScript = (DslDelegateScript) shell.parse(scriptText);
                        dslScript.setDelegate(delegate);
                        Whitelist wrapperWhitelist = new ProxyWhitelist(new ClassLoaderWhitelist(secureClassLoader), new DslDelegateScript.UserActionWhiteList(), Whitelist.all());
                        GroovyInterceptor sandbox = new SandboxInterceptor(wrapperWhitelist);
                        sandbox.register();
                        try {
                            dslScript.run();
                        } catch (RejectedAccessException x) {
                            //save for pending approval
                            throw ScriptApproval.get().accessRejected(x, ApprovalContext.create());
                        } finally {
                            sandbox.unregister();
                        }
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

