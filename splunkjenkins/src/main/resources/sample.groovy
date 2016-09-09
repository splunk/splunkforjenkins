//send junit reports if defined

sendReport({ event ->
    def junitResult = getJunitReport()
    if (junitResult) {
        event["testsuite"] = junitResult
    }
})