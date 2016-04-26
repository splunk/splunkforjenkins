SplunkJenkins
---------
A Jenkins plugin for Splunking Jenkins.

To Install
----------
 - clone the repo
 - `$ cd splunkjenkins`
 - `$ mvn package`
 -  That should produce `target/splunkjenkins.hpi` which you can install into Jenkins with either the web interface or by putting it in the `JENKINS_HOME/plugins` folder.

To Setup
--------
#### Configure plugin
 - Go to `https://<jenkins-url>/configure`
 - Enter the necessary configuration data to connect to your Splunk server and save.
###### Example Global Configuration
![](assets/images/global-configure.png?raw=true "Example Global Configuration")

#### Send job data to Splunk
 - Create/edit a job/trigger.
     - Add a "post-build action" called "Send data to Splunk".
     - Enter an ant-style pattern matching string for your junit xml collection.
     - (Optional) You can add a json file which will be appended to the event. This useful for event metadata.
     - Run the job.
     - Verify that the events were sent to your Splunk instance.

###### Example metadata.json
```
metadata: {
      aggregate_report_base_name:  multi_(HWF_to_2IDX_SSL_True_useACK_False)
      browser:  null
      build_url: https://sustaining-qa-jenkins.sv.splunk.com/job/Splunk/job/generic_jobs/job/generic_new_test_WINDOWS/38131/
      feature:  framework_forwarding
      job_name:  Splunk/generic_jobs/generic_new_test_WINDOWS
      num_of_vm:  2
      os:  windows2008r2
      product:  splunk
      pytest_args: test_forwarding_multi_autolb.py --fwd_type=hwf --enable_ssl=true --useACK=false --branch=ember-sustain  
      qa_git_branch:  sustain/ember
      qa_git_commit:  e398ced7ced50e4b09b72e1bd577009bfa882133
      root_trigger:  Splunk/sustain_ember/platform/trigger_branch
      root_trigger_build_no:  90
      splunk_build_branch:  ember-sustain
      splunk_build_commit:  b548517757b8
      splunk_home:  c:/splunk-install/
}
```

###### Example Trigger Configuration
![](assets/images/trigger-configure.png?raw=true "Example Trigger Configuration")
