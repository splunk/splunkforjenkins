//send job metadata and junit reports with page size set to 50 (each event contains max 50 test cases)
sendTestReport(50)
//send coverage, each event contains max 50 class metrics
sendCoverageReport(50)
//send all logs from workspace to splunk, with each file size limits to 10MB
archive("**/*.log", null, false, "10MB")
