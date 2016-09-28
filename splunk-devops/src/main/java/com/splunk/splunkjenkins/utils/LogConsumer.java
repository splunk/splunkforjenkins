package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.model.EventRecord;
import hudson.Util;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.utils.LogEventHelper.buildPost;

public class LogConsumer extends Thread {
    private static final Logger LOG = Logger.getLogger(LogConsumer.class.getName());

    private final HttpClient client;
    private final BlockingQueue<EventRecord> queue;
    private boolean acceptingTask = true;
    private AtomicLong outgoingCounter;
    private int errorCount;
    private boolean sending = false;
    private final int RETRY_SLEEP_THRESHOLD = 1 << 10;
    private List<Class<? extends IOException>> giveUpExceptions = Arrays.asList(
            UnknownHostException.class,
            ConnectException.class,
            SSLException.class);
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
                SplunkClientError error = null;
                if (status == 403 || status == 401) {
                    //Token disabled or Invalid authorization
                    error = new SplunkClientError("splunk token is invalid," + reason, status);
                } else if (status == 400) {
                    //Invalid data format or incorrect index, will discard
                    error = new SplunkClientError("incorrect index or invalid data format," + reason, status);
                }
                throw new IOException("failed to send data," + reason, error);
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
                if (!record.discard()) {
                    HttpPost post = buildPost(record, SplunkJenkinsInstallation.get());
                    try {
                        sending = true;
                        client.execute(post, responseHandler);
                    } catch (Exception ex) {
                        LOG.log(Level.WARNING, "content length:" + post.getEntity().getContentLength(), ex);
                        boolean needRetry = Util.filter(giveUpExceptions, ex.getClass()).isEmpty();
                        if (!needRetry) {
                            LOG.log(Level.SEVERE, "remote server error, will not retry");
                            return;
                        }
                        Throwable cause = ex.getCause();
                        if (cause != null && cause instanceof SplunkClientError) {
                            String content;
                            try {
                                content = IOUtils.toString(post.getEntity().getContent());
                            } catch (IOException e) {
                                content = record.getMessageString();
                            }
                            LOG.log(Level.SEVERE, "invalid client config, will discard data and no retry:{0}"
                                    , StringUtils.abbreviate(content, 80));
                        } else {
                            //other errors
                            LOG.log(Level.SEVERE, "will resend the message:{0}", record.getShortDescr());
                            retry(record);
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

    public static class SplunkClientError extends Throwable {
        int status;

        public SplunkClientError(String message, int status) {
            super(message);
            this.status = status;
        }
    }
}
