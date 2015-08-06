SplunkJenkins
---------
A Jenkins plugin for Splunking Jenkins.

To Install
----------
 - clone the repo
 - `$ splunkjenkins/splunkjenkins`
 - `$ mvn package`
 -  That should produce `target/splunkjenkins.hpi` which you can install into Jenkins with either the web interface or by putting it in the `JENKINS_HOME/plugins` folder.

To Setup
--------
#### Configure plugin
 - Go to https://<jenkins-url>:8080/configure
 - Enter the necessary configuration data to connect to your Splunk server and save.

#### Send job data to Splunk
 - Create a new job.
     - Add a "post-build action" called "Send data to Splunk".
     - Enter an ant-style pattern matching string for your junit xml collection.
     - Run the job.
