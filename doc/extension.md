Plugin implements some interfaces and mark the implementation to use annotation @Extension so Jenkins can load it dynamically

# Configure

### SplunkJenkinsInstallation 
	package com.splunk.splunkjenkins
    Follow jenkins conventions, using java bean property host, port, token, useSSL, scriptPath etc and some helper method such as
    doCheckHost to validate host, doTestHttpInput to verify connection, see also https://wiki.jenkins-ci.   org/display/JENKINS/Form+Validation

# Function
### Listeners 
	package com.splunk.splunkjenkins.listeners, receives notifications from jenkins, for example in Jenkins delete job action
```
    /**
     * Called in response to {@link Job#doDoDelete(StaplerRequest, StaplerResponse)}
     */
    public void onDeleted(TopLevelItem item) throws IOException {
        ItemListener.fireOnDeleted(item);

        items.remove(item.getName());
        // For compatibility with old views:
        for (View v : views)
            v.onJobRenamed(item, item.getName(), null);
    }

```
    it will call fireOnDeleted to notify all ItemListeners

## Listeners list
#### [SecurityListener](http://javadoc.jenkins-ci.org/jenkins/security/SecurityListener.html)
    record user login/logout and failedToLogIn events
#### [SaveableListener](http://javadoc.jenkins-ci.org/hudson/model/listeners/SaveableListener.html)
    when user made changes to jenkins config (either plugin config or job config), record the config xml. disabled by default until user add jenkins_config.monitoring=true into metadata config
#### [ItemListener](http://javadoc.jenkins-ci.org/hudson/model/listeners/RunListener.html)
	similar to SaveableListener but for Job only, it has finer grained audit event. used to capture job created, renamed, copied and deleted

#### [QueueListener](http://javadoc.jenkins-ci.org/hudson/model/queue/QueueListener.html)
	listen for onEnterWaiting and onLeft and record the job queueTime and jenkins metrics
#### [RunListener](http://javadoc.jenkins-ci.org/hudson/model/listeners/RunListener.html)
	listen for onStarted and onCompleted, and extract upstream job, build cause, scm, job result, and invoke DSL if defined
#### [ComputerListener](http://javadoc.jenkins-ci.org/hudson/slaves/ComputerListener.html)
	record slave online, offline, temporarilyOffline, and launchFailure

## [Notifier](http://javadoc.jenkins-ci.org/hudson/tasks/Notifier.html)
    Contribute to post build step, user can custom the logs to send to splunk in addition to what we have in Groovy DSL.

## Links
### RootAction
    add link to home page
### ManagementLink 
    add link to management page
### TransientComputerActionFactory
    and link to other types, such as Build, Computer

## Task
    we extends AsyncPeriodicWork to run tasks periodic to gather agent metrics

# Service
     SplunkLogService launched two threads to drain the splunk event queue, used (Splunk HTTP Event Collector)[http://dev.splunk.com/view/event-collector/SP-CAAAE6M] to pump data into splunk, message format

```

{
    "time": 1426279439, 
    "host": "localhost",
    "source": "datasource",
    "sourcetype": "txt",
    "index": "main",
    "event": { jenkins_event_json_here }
}

```

