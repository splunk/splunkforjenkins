//send job metadata and junit reports with page size set to 50 (each event contains max 50 test cases)
def results = getJunitReport(50)
def buildEvent = getBuildEvent()
results.eachWithIndex { junitResult, idx ->
    Map pagedEvent = buildEvent + ["testsuite": junitResult, "page_num": idx + 1]
    send(pagedEvent)
}

//send all logs from workspace to splunk, with each file size limits to 10MB
archive("**/*.log", null, false, "10MB")