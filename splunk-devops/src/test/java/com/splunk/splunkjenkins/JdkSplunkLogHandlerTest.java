package com.splunk.splunkjenkins;

import org.junit.Before;
import org.junit.Test;

import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.SplunkConfigUtil.verifySplunkSearchResult;
import static org.junit.Assert.assertTrue;

public class JdkSplunkLogHandlerTest extends BaseTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        LoggingInitStep.registerHandler();
    }

    @Test
    public void publish() throws Exception {
        Handler[] handlers = Logger.getLogger("").getHandlers();
        boolean found = false;
        for (Handler handler : handlers) {
            if (handler instanceof JdkSplunkLogHandler) {
                found = true;
                break;
            }
        }
        assertTrue("LoggingInitStep.setupSplunkJenkins() should be called", found);
        String message = "test_log_" + UUID.randomUUID();
        Logger.getLogger("test_info_logger").info(message);
        Logger.getLogger("test_warning_logger").warning(message);
        verifySplunkSearchResult(message + " level=WARNING", 1);
        verifySplunkSearchResult(message + " level=INFO", 1);
    }

}