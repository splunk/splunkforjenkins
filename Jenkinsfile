#!/usr/bin/env groovy
// Parameters to run test
properties([
        parameters([string(defaultValue: '', description: 'Splunk host', name: 'host')
                    , string(defaultValue: '8089', description: 'Splunk management port', name: 'port')
                    , string(defaultValue: '', description: 'Username', name: 'username')
                    , string(defaultValue: '', description: 'Password', name: 'password')
                    , string(defaultValue: '', description: 'Local Repo Url', name: 'repoUrl')
        ]),
        [$class: 'jenkins.model.BuildDiscarderProperty', strategy: [$class              : 'LogRotator',
                                                                    numToKeepStr        : '50',
                                                                    artifactNumToKeepStr: '20']]
])

node {
    checkout scm;
    def mvnHome = tool name: 'default', type: 'hudson.tasks.Maven$MavenInstallation'
    def mvnCmd = "${mvnHome}/bin/mvn";
    if (params.password) {
        mvnCmd += " -Dhost=${params.host} -Dport=${params.port} -Dpassword=${params.password} -Dusername=${params.username}"
    }
    mvnCmd += " -Djava.net.preferIPv4Stack=true clean verify"

    if (params.repoUrl) {
        mvnCmd += " -Plocal -Drepos.url=${params.repoUrl} deploy cobertura:cobertura"
    }
    sh mvnCmd
}
