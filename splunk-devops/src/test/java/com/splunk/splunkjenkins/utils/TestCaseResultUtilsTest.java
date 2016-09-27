package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.model.JunitTestCaseGroup;
import hudson.tasks.junit.TestResult;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.List;

import static org.junit.Assert.*;

public class TestCaseResultUtilsTest {
    @Test
    public void splitJunitTestCase() throws Exception {
        TestResult result = new TestResult();
        File junitFile = File.createTempFile("junit", ".xml");
        FileWriter out = new FileWriter(junitFile);
        int total = 512;
        out.write("<testsuite time=\"8.597\" tests=\"" + total + "\" errors=\"0\" skipped=\"0\" failures=\"1\">\n");
        out.write("<testcase name=\"a1\" classname=\"c1\" time=\"0\">\n" +
                "\t<failure message=\"expected:954 but was:945\" type=\"java.lang.AssertionError\">\n" +
                "\t</failure>\n" +
                "</testcase>");
        for (int i = 1; i < total; i++) {
            out.write("<testcase name=\"".concat("t" + i)
                    .concat("\" time=\"0\" classname=\"c1\" >")
                    .concat("</testcase>\n"));
        }
        out.write("</testsuite>");
        out.close();
        result.parse(junitFile);
        result.tally();
        assertEquals(total, result.getTotalCount());
        assertEquals(1, result.getFailCount());

        int pageSize = 5;
        List<JunitTestCaseGroup> suites = TestCaseResultUtils.split(result, pageSize);
        int remained = (total % pageSize == 0) ? 0 : 1;
        int pageCount = total / pageSize + remained;
        assertEquals(pageCount, suites.size());
    }

}