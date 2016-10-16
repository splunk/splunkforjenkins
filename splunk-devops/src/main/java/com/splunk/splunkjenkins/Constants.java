package com.splunk.splunkjenkins;

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
    public static final String MASTER = "(master)";
    public static final String BUILD_REPORT_ENV_TAG = "metadata";
    public static final String JENKINS_CONFIG_PREFIX = "jenkins://";
    public static final String AUDIT_SOURCE = "audit_trail";
    public static final String USER_NAME_KEY = "user";
    public static final String EVENT_CAUSED_BY="event_src";
    public static final String EVENT_SOURCE_TYPE="sourcetype";
    public static final String NODE_NAME = "node_name";
    public static final String ERROR_MESSAGE_NA="(none)";
}
