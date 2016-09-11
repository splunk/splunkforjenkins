//send junit reports if defined
//junit report with pagination set to 200
def results = getJunitReport(200)
def event = getBuildEvent()
def pageNumber = 0;
results.each { junitResult ->
    event["testsuite"] = junitResult;
    event["page_num"] = pageNumber;
    pageNumber++;
    send(event)
}