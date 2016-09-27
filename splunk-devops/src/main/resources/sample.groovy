//send job metadata and junit reports with page size set to 100 (each event contains max 100 test cases)
def results = getJunitReport(50)
def buildEvent = getBuildEvent()
results.eachWithIndex { junitResult, idx ->
    Map pagedEvent = event + ["testsuite": junitResult, "page_num": idx + 1]
    send(pagedEvent)
}

//send all logs from workspace to splunk, with each file size limits to 10MB
archive("**/*.log", null, false, "10MB")