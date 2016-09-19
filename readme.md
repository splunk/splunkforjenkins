SplunkJenkins
---------
A Jenkins plugin for Splunking Jenkins.

To Install
----------
 - clone the repo
 - `$ cd splunkjenkins`
 - `$ mvn package`
 -  That should produce `target/splunkjenkins.hpi` which you can install into Jenkins with either the web interface or by putting it in the `JENKINS_HOME/plugins` folder.
 - `$mvn clean verify -Dsplunk-token-setup=true -Dsplunk-host=localhost -Dsplunk-username=admin -Dsplunk-passwd=changeme` to run tests on fresh install splunk instance
 - `$mvn clean verify -Dsplunk-host=10.140.1.1 -Dsplunk-token=235EAFC7-924F-44AA-9601-73245B9E086A -Dsplunk-index=plugin_sandbox` to run tests agaiant pre-configured splunk instance

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
     
 - Customize junit rest processing
      - In the advance configure section, you can customize the junit processing
      - `send(Object message)` will send the information to splunk
      - `Map junitReport`, `AbstractBuild build`, `Map env`, `TestResult testResult` can be used directly
      - env is a Map of Environment variables
      - testResult is hudson.tasks.junit.TestResult
      - build is hudson.model.AbstractBuild
      - junitReport is a wrapup of testResult, which contains total, passes, failures, skips, time and tests
      - sample code


```java

def lookup_mapping = ["root_trigger_name"         : "root_trigger",
                      "root_trigger_build_no"     : "root_trigger_build_no",
                      "product"                   : "product",
                      "commit"                    : "splunk_build_commit",
                      "branch"                    : "splunk_build_branch",
                      "GIT_COMMIT"                : "qa_git_commit",
                      "git_branch"                : "qa_git_branch",
                      "aggregate_report_base_name": "aggregate_report_base_name"
]

def metadata = [:]
metadata.putAll(build.buildVariables)
lookup_mapping.each { name, new_key_name ->
    if (env[name] != null && metadata[new_key_name] == null) {
        metadata[new_key_name] = env[name]
        if(name != new_key_name){
          metadata.remove(name)
        }
    }
}

def testsuite = getJunitReport()
testsuite.put("tests", testsuite.get("total"))
if(testsuite.get("errors")==null){
  setup_error=testsuite.get("testcase")==null?1:0;
  testsuite.put("errors", setup_error)
}

def result = [
        "build_url": build.url,
        "category" : "tests",
        "metadata" : metadata,
        "testsuite": testsuite
]
//will send the result to Splunk
send(result);      

```
