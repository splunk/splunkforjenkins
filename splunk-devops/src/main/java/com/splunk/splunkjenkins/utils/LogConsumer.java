package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.model.EventRecord;
import shaded.splk.org.apache.http.HttpEntity;
import shaded.splk.org.apache.http.HttpResponse;
import shaded.splk.org.apache.http.client.HttpClient;
import shaded.splk.org.apache.http.client.ResponseHandler;
import shaded.splk.org.apache.http.client.methods.HttpPost;
import shaded.splk.org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.utils.LogEventHelper.buildPost;

public class LogConsumer extends Thread {
    private static final Logger LOG = Logger.getLogger(LogConsumer.class.getName());
    private static final int retryInterval = Integer.parseInt(System.getProperty("splunk-retryinterval", "15"));
    private final HttpClient client;
    private final BlockingQueue<EventRecord> queue;
    private boolean acceptingTask = true;
    private AtomicLong outgoingCounter;
    private int errorCount;
    private boolean sending = false;
    private final int RETRY_SLEEP_THRESHOLD = 1 << 10;
    private List<Class<? extends IOException>> giveUpExceptions = Arrays.asList(
            UnknownHostException.class,
            SSLException.class,
            SplunkClientError.class);
    // Create a custom response handler
    private ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
        @Override
        public String handleResponse(
                final HttpResponse response) throws IOException {
            int status = response.getStatusLine().getStatusCode();
            String reason = response.getStatusLine().getReasonPhrase();
            if (status == 200) {
                outgoingCounter.incrementAndGet();
                HttpEntity entity = response.getEntity();
                //need consume entity so underlying connection can be released to pool
                return entity != null ? EntityUtils.toString(entity) : null;
            } else { //see also http://docs.splunk.com/Documentation/Splunk/6.3.0/RESTREF/RESTinput#services.2Fcollector
                String message;
                if (status == 503) {
                    throw new SplunkServiceError("Server is busy, maybe caused by blocked queue, please check " +
                            "https://wiki.splunk.com/Community:TroubleshootingBlockedQueues", status);
                }
                if (status == 403 || status == 401) {
                    //Token disabled or Invalid authorization
                    message = reason + ", http event collector token is invalid";
                } else if (status == 400) {
                    //Invalid data format or incorrect index
                    message = reason + ", incorrect index or invalid data format";
                } else {
                    message = reason;
                }
                throw new SplunkClientError(message, status);
            }
        }
    };

    public LogConsumer(HttpClient client, BlockingQueue<EventRecord> queue, AtomicLong counter) {
        this.client = client;
        this.queue = queue;
        this.errorCount = 0;
        this.outgoingCounter = counter;
    }

    @Override
    public void run() {
        while (acceptingTask) {
            try {
                EventRecord record = queue.take();
                if (!record.isDiscarded()) {
                    HttpPost post = buildPost(record, SplunkJenkinsInstallation.get());
                    try {
                        sending = true;
                        client.execute(post, responseHandler);
                    } catch (IOException ex) {
                        boolean isDiscarded = false;
                        for (Class<? extends IOException> giveUpException : giveUpExceptions) {
                            if (giveUpException.isInstance(ex)) {
                                isDiscarded = true;
                                LOG.log(Level.SEVERE, "message not delivered:" + record.getShortDescr(), ex);
                                break;
                            }
                        }
                        if (!isDiscarded) {
                            handleRetry(ex, record);
                        }
                    } finally {
                        sending = false;
                        post.releaseConnection();
                    }
                } else {
                    //message discarded
                    LOG.log(Level.SEVERE, "failed to send " + record.getShortDescr());
                }
            } catch (InterruptedException e) {
                errorCount++;
                //thread interrupted, just ignore
            }
        }
    }

    private void handleRetry(IOException ex, EventRecord record) throws InterruptedException {
        if (ex instanceof SplunkServiceError) {
            int sleepTime = 2 * retryInterval;
            LOG.log(Level.WARNING, "{0}, will wait {1} seconds and retry", new Object[]{ex.getMessage(), sleepTime});
            Thread.sleep(TimeUnit.SECONDS.toMillis(sleepTime));
            retry(record);
        } else if (ex instanceof ConnectException) {
            // splunk is restarting or network broke
            LOG.log(Level.WARNING, "{0} connect error, will wait {1} seconds and retry", new Object[]{this.getName(), retryInterval});
            Thread.sleep(TimeUnit.SECONDS.toMillis(retryInterval));
            retry(record);
        } else {
            //other errors
            LOG.log(Level.WARNING, "will resend the message:{0}", record.getShortDescr());
            retry(record);
        }
    }

    public void stopTask() {
        this.acceptingTask = false;
        for (int i = 0; i < 5; i++) {
            if (sending) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        if (this.isAlive()) {
            //queue.take() may block the thread
            this.interrupt();
        }
    }

    /**
     * @param record
     * @throws InterruptedException
     */
    private void retry(EventRecord record) throws InterruptedException {
        if (acceptingTask) {
            record.increase();
            SplunkLogService.getInstance().enqueue(record);
            if (queue.size() < RETRY_SLEEP_THRESHOLD) {
                //We don't have much data in queue so wait a while for the service to recovery(hopefully)
                Thread.sleep(100);
            }
        }
    }

    public long getSentCount() {
        return outgoingCounter.get();
    }

    public static class SplunkClientError extends IOException {
        int status;

        public SplunkClientError(String message, int status) {
            super(message + ", status code:" + status);
            this.status = status;
        }
    }

    public static class SplunkServiceError extends IOException {
        public SplunkServiceError(String message, int status) {
            super(message);
        }
    }
}
