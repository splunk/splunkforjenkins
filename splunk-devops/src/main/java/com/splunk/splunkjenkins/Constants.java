package com.splunk.splunkjenkins;

import java.util.Arrays;
import java.util.List;

public class Constants {
    public static final String TESTCASE = "testcase";
    public static final String TESTSUITE = "testsuite";
    public static final String BUILD_ID = "build_url";
    public static final String TAG = "event_tag";
    public static final String JOB_RESULT = "job_result";
    public static final String JSON_ENDPOINT = "/services/collector/event";
    public static final String RAW_ENDPOINT = "/services/collector/raw";
    public static final String LOG_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    public final static String SLAVE_TAG_NAME = "slave";
    public final static String QUEUE_TAG_NAME = "queue";
    public final static String QUEUE_WAITING_ITEM_NAME = "queue_item";
    public static final String JOB_EVENT_TAG_NAME = "job_event";
    public static final String JOB_EVENT_MONITOR = "job_monitor";
    public static final String MASTER = "(master)";
    public static final String BUILD_REPORT_ENV_TAG = "metadata";
    public static final String JENKINS_CONFIG_PREFIX = "jenkins://";
    public static final String JENKINS_SOURCE_SEP = "/";
    public static final String AUDIT_SOURCE = "audit_trail";
    public static final String USER_NAME_KEY = "user";
    public static final String EVENT_CAUSED_BY = "event_src";
    public static final String EVENT_SOURCE_TYPE = "sourcetype";
    public static final String NODE_NAME = "node_name";
    public static final String ERROR_MESSAGE_NA = "(none)";
    public static final String MASK_PASSWORD = "***";
    public static final String NO_TEST_REPORT_FOUND = "No TestResult";
    public static final String TEST_REPORT_NOT_CONFIGURED = "Junit or xUnit report not configured";
    public static final List<String> SCRIPT_TEXT_MD5_HASH = Arrays.asList("729ac3b82ecf2e0afc0cb00d73c22892",
            "f43916477139eb890e72c1602e0851b4", "aac4abe92db9bf90e3b27a4e41728526");
    // min buffer size for raw data (usually log file and console)
    public static final int MIN_BUFFER_SIZE = Integer.getInteger("splunkins.buffer", 4096);
    // max buffer size for raw data (usually log file and console)
    public static final int MAX_BUFFER_SIZE = 1 << 21;
    // use gzip for http posting
    public static final int GZIP_THRESHOLD = 1024; //1kb
    // 20 KB for slave log
    public static final int SLAVE_LOG_BUFFER_SIZE = MIN_BUFFER_SIZE * 4;
}
