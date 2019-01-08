package com.splunk.splunkjenkins.utils;

import com.google.common.base.Strings;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.model.EventRecord;
import com.splunk.splunkjenkins.model.EventType;
import shaded.splk.org.apache.http.HttpResponse;
import shaded.splk.org.apache.http.client.HttpClient;
import shaded.splk.org.apache.http.client.config.CookieSpecs;
import shaded.splk.org.apache.http.client.config.RequestConfig;
import shaded.splk.org.apache.http.config.Registry;
import shaded.splk.org.apache.http.config.RegistryBuilder;
import shaded.splk.org.apache.http.config.SocketConfig;
import shaded.splk.org.apache.http.conn.ConnectionKeepAliveStrategy;
import shaded.splk.org.apache.http.conn.HttpClientConnectionManager;
import shaded.splk.org.apache.http.conn.socket.ConnectionSocketFactory;
import shaded.splk.org.apache.http.conn.socket.PlainConnectionSocketFactory;
import shaded.splk.org.apache.http.conn.ssl.NoopHostnameVerifier;
import shaded.splk.org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import shaded.splk.org.apache.http.conn.ssl.TrustStrategy;
import shaded.splk.org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import shaded.splk.org.apache.http.impl.client.HttpClients;
import shaded.splk.org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import shaded.splk.org.apache.http.protocol.HttpContext;
import shaded.splk.org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import static com.splunk.splunkjenkins.model.EventType.BATCH_JSON;

public class SplunkLogService {
    public static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(SplunkLogService.class.getName());
    private final static int SOCKET_TIMEOUT = 3;
    private final static int QUEUE_SIZE = Integer.getInteger(SplunkLogService.class.getName() + ".queueSize", 1 << 17);
    private final static long KEEP_ALIVE_TIME_MINUTES = 2;
    int MAX_WORKER_COUNT = Integer.getInteger(SplunkLogService.class.getName() + ".workerCount", 2);
    BlockingQueue<EventRecord> logQueue;
    List<LogConsumer> workers;
    HttpClient client;
    HttpClientConnectionManager connMgr;
    private AtomicLong incomingCounter = new AtomicLong();
    private AtomicLong outgoingCounter = new AtomicLong();
    private Lock maintenanceLock = new ReentrantLock();

    private SplunkLogService() {
        this.logQueue = new LinkedBlockingQueue<EventRecord>(QUEUE_SIZE);
        this.workers = new ArrayList<LogConsumer>();
    }

    private void initHttpClient() {
        this.connMgr = buildConnectionManager();
        ConnectionKeepAliveStrategy myStrategy = new DefaultConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(HttpResponse httpResponse, HttpContext httpContext) {
                long keepAliveTime = super.getKeepAliveDuration(httpResponse, httpContext);
                if (keepAliveTime == -1L) {
                    keepAliveTime = TimeUnit.MINUTES.toMillis(KEEP_ALIVE_TIME_MINUTES);
                }
                return keepAliveTime;
            }
        };
        this.client = HttpClients.custom()
            .setConnectionManager(this.connMgr).setKeepAliveStrategy(myStrategy)
            .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
            .build();
    }

    public static SplunkLogService getInstance() {
        return InstanceHolder.service;
    }

    private HttpClientConnectionManager buildConnectionManager() {
        SSLContext sslContext = null;
        try {
            TrustStrategy acceptingTrustStrategy = new TrustAllStrategy();
            sslContext = SSLContexts.custom().useProtocol("TLSv1.2").loadTrustMaterial(
                    null, acceptingTrustStrategy).build();
        } catch (Exception e) {
            sslContext = SSLContexts.createDefault();
        }
        SSLConnectionSocketFactory sslConnectionSocketFactory = new CustomSSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslConnectionSocketFactory)
                .build();
        PoolingHttpClientConnectionManager cm =
                new PoolingHttpClientConnectionManager(registry, null, null, new MultipleHostResolver(), -1L, TimeUnit.MINUTES);
        // Increase max total connection to 200
        cm.setMaxTotal(200);
        // Increase default max connection per route to 20
        cm.setDefaultMaxPerRoute(20);
        //socket timeout for 2 minutes
        SocketConfig defaultSocketConfig = SocketConfig.custom().setSoTimeout((int) TimeUnit.MINUTES.toMillis(SOCKET_TIMEOUT)).build();
        cm.setDefaultSocketConfig(defaultSocketConfig);
        return cm;
    }

    /**
     * @param message the message to send
     * @return true if enqueue successfully, false if the message is discarded
     */
    public boolean send(Object message) {
        if (message != null && message instanceof EventRecord) {
            return enqueue((EventRecord) message);
        } else {
            return send(message, null, null);
        }
    }

    /**
     * @param message    the message to send
     * @param sourceName the source for splunk metadata
     * @return true if enqueue successfully, false if the message is discarded
     */
    public boolean send(Object message, String sourceName) {
        return send(message, null, sourceName);
    }

    /**
     * @param message   the message to send, will use CONSOLE_LOG's config
     * @param eventType the type of event, @see EventType
     * @return true if enqueue successfully, false if the message is discarded
     */
    public boolean send(Object message, EventType eventType) {
        return send(message, eventType, null);
    }

    /**
     * @param messages  the messages to send
     * @param eventType the type of event, @see EventType
     * @return true if enqueue successfully, false if some message are discarded
     */
    public boolean sendBatch(Collection<? extends Object> messages, EventType eventType) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        StringBuffer stringBuffer = new StringBuffer();
        long batchSize = SplunkJenkinsInstallation.get().getMaxEventsBatchSize();
        boolean isQueued = false;
        for (Object message : messages) {
            EventRecord record;
            if (!(message instanceof EventRecord)) {
                record = new EventRecord(message, eventType);
            } else {
                record = (EventRecord) message;
            }
            stringBuffer.append(LogEventHelper.toJson(record));
            stringBuffer.append("\n");
            if (stringBuffer.length() > batchSize) {
                isQueued = enqueue(new EventRecord(stringBuffer.toString(), BATCH_JSON));
                stringBuffer.setLength(0);
                if (!isQueued) {
                    return isQueued;
                }
            }
        }
        if (stringBuffer.length() > 0) {
            isQueued = enqueue(new EventRecord(stringBuffer.toString(), BATCH_JSON));
        }
        return isQueued;
    }

    /**
     * @param message    the message to send
     * @param eventType  the type of event, @see EventType
     * @param sourceName the source for splunk metadata
     * @return true if enqueue successfully, false if the message is discarded
     */
    public boolean send(Object message, EventType eventType, String sourceName) {
        if (this.connMgr == null) {
            return false;
        }
        if (message == null) {
            LOG.warning("null message discarded");
            return false;
        } else if ((message instanceof String) && ((String) message).length() == 0) {
            LOG.warning("empty message discarded");
            return false;
        }
        EventRecord record = new EventRecord(message, eventType);
        if (!Strings.isNullOrEmpty(sourceName)) {
            record.setSource(sourceName);
        }
        return enqueue(record);
    }

    public boolean enqueue(EventRecord record) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(record.getEventType())) {
            LOG.log(Level.FINE, "config invalid or eventType {0} is disabled, can not send {1}", new String[]{record.getEventType().toString(), record.getShortDescription()});
            return false;
        }
        boolean added = logQueue.offer(record);
        if (!added) {
            added = maintainQueue(record);
        }
        if (workers.size() < MAX_WORKER_COUNT) {
            synchronized (workers) {
                int worksToCreate = MAX_WORKER_COUNT - workers.size();
                for (int i = 0; i < worksToCreate; i++) {
                    LogConsumer workerThread = new LogConsumer(client, logQueue, outgoingCounter);
                    workers.add(workerThread);
                    String workerThreadName = "splunkins-worker-" + workers.size();
                    workerThread.setName(workerThreadName);
                    workerThread.start();
                }
            }
        }
        long incomingCount = incomingCounter.incrementAndGet();
        if (incomingCount % 4000 == 0 && getQueueSize() > 4000) {
            LOG.info(this.getStats());
            synchronized (InstanceHolder.service) {
                connMgr.closeIdleConnections(KEEP_ALIVE_TIME_MINUTES, TimeUnit.MINUTES);
            }
        }
        return added;
    }

    /**
     * discard non critical events, then queue the event
     *
     * @param record the event record to be queued
     * @return true if the event is queued
     */
    private boolean maintainQueue(EventRecord record) {
        boolean added = false;
        maintenanceLock.lock();
        try {
            removeDeadWorker();
            if (logQueue.remainingCapacity() > 2000) {
                //logQueue was already cleaned up
                added = logQueue.offer(record);
            }
            if (!added) {
                //the event in the queue may have format issue and caused congestion, remove raw events
                List<EventRecord> stuckRecords = new ArrayList<>(logQueue.size());
                logQueue.drainTo(stuckRecords);
                LOG.log(Level.SEVERE, "jenkins is too busy or has too few workers, clearing up queue");
                int count = 0;
                for (EventRecord queuedRecord : stuckRecords) {
                    if (queuedRecord.getEventType().ordinal() > EventType.JENKINS_CONFIG.ordinal()) {
                        count++;
                        continue;
                    }
                    logQueue.offer(queuedRecord);
                }
                added = logQueue.offer(record);
                LOG.log(Level.INFO, "discarded: {0}, remaining: {1}", new Object[]{count, logQueue.size()});
            }
        } finally {
            maintenanceLock.unlock();
        }
        return added;
    }

    private void removeDeadWorker() {
        //check if sending LogConsumer thread alive
        Iterator<LogConsumer> workerIterator = workers.iterator();
        while (workerIterator.hasNext()) {
            if (!workerIterator.next().isAlive()) {
                workerIterator.remove();
            }
        }
    }

    public void stopWorker() {
        synchronized (workers) {
            for (LogConsumer consumer : workers) {
                consumer.stopTask();
            }
            workers.clear();
        }
        long queueLength = this.getQueueSize();
        if (queueLength > 0) {
            logQueue.clear();
            LOG.severe("remaining " + queueLength + " record(s) not sent");
        }
    }

    public void releaseConnection() {
        connMgr.closeIdleConnections(0, TimeUnit.SECONDS);
    }

    public long getSentCount() {
        return outgoingCounter.get();
    }

    public long getQueueSize() {
        return this.logQueue.size();
    }

    public HttpClient getClient() {
        return client;
    }

    private static class InstanceHolder {
        static SplunkLogService service;

        static {
            service = new SplunkLogService();
            try {
                service.initHttpClient();
            } catch (java.lang.NoSuchFieldError e) {
                /*
                  java.lang.NoSuchFieldError: INSTANCE
                  at shaded.splk.org.apache.http.conn.ssl.SSLConnectionSocketFactory.<clinit>(SSLConnectionSocketFactory.java:144)
                */
                LOG.log(Level.SEVERE, "init httpclient failed, version conflicts", e);
            }
        }
    }

    public String getStats() {
        StringBuilder sbr = new StringBuilder();
        sbr.append("remaining:").append(this.getQueueSize()).append(" ")
                .append("sent:").append(this.getSentCount());
        return sbr.toString();
    }

    static class TrustAllStrategy implements TrustStrategy {
        public boolean isTrusted(X509Certificate[] certificate,
                                 String type) {
            return true;
        }
    }
}
