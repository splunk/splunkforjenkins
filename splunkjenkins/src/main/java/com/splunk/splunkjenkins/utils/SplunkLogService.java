package com.splunk.splunkjenkins.utils;

import com.google.common.base.Strings;
import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import com.splunk.splunkjenkins.model.EventRecord;
import com.splunk.splunkjenkins.model.EventType;
import org.apache.http.client.HttpClient;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class SplunkLogService {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(SplunkLogService.class.getName());
    private final static int SOCKET_TIMEOUT = 3;
    private final static int QUEUE_SIZE = 1 << 17;
    int MAX_WORKER_COUNT = Integer.getInteger(SplunkLogService.class.getName() + ".workerCount", 2);
    BlockingQueue<EventRecord> logQueue;
    List<LogConsumer> workers;
    HttpClient client;
    HttpClientConnectionManager connMgr;
    private AtomicLong incomingCounter = new AtomicLong();
    private AtomicLong outgoingCounter = new AtomicLong();
    private Lock maintenanceLock =new ReentrantLock();

    private SplunkLogService() {
        this.logQueue = new LinkedBlockingQueue<EventRecord>(QUEUE_SIZE);
        this.workers = new ArrayList<LogConsumer>();
        this.connMgr = buildConnectionManager();
        this.client = HttpClients.custom().setConnectionManager(this.connMgr).build();
    }

    public static SplunkLogService getInstance() {
        return InstanceHolder.service;
    }

    private HttpClientConnectionManager buildConnectionManager() {
        SSLContext sslContext = null;
        try {
            TrustStrategy acceptingTrustStrategy = new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] certificate,
                                         String type) {
                    return true;
                }
            };
            sslContext = SSLContexts.custom().loadTrustMaterial(
                    null, acceptingTrustStrategy).build();
        } catch (Exception e) {
            sslContext = SSLContexts.createDefault();
        }
        SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
                new NoopHostnameVerifier());
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslConnectionSocketFactory)
                .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
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
     * @param message   the message to send, will use GENERIC_TEXT's config
     * @param eventType the type of event, @see EventType
     * @return true if enqueue successfully, false if the message is discarded
     */
    public boolean send(Object message, EventType eventType) {
        return send(message, eventType, null);
    }

    /**
     * @param message    the message to send
     * @param eventType  the type of event, @see EventType
     * @param sourceName the source for splunk metadata
     * @return true if enqueue successfully, false if the message is discarded
     */
    public boolean send(Object message, EventType eventType, String sourceName) {
        if (message == null) {
            LOG.warning("null message discarded");
            return false;
        } else if (message instanceof String && "".equals(message)) {
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
        if (!SplunkJenkinsInstallation.get().isEnabled()) {
            return false;
        }
        if (!SplunkJenkinsInstallation.get().isValid()) {
            LOG.log(Level.SEVERE, "Splunk plugin config is not invalid, can not send " + record.getShortDescr());
            return false;
        }
        boolean added = logQueue.offer(record);
        if (!added) {
            LOG.log(Level.SEVERE, "failed to send message due to queue is full");
            if (maintenanceLock.tryLock()) {
                try {
                    //clear the queue, the event in the queue may have format issue and caused congestion
                    List<EventRecord> stuckRecords = new ArrayList<>(logQueue.size());
                    logQueue.drainTo(stuckRecords);
                    LOG.log(Level.SEVERE, "jenkins is too busy or has too few workers, clearing up queue");
                    for (EventRecord queuedRecord : stuckRecords) {
                        if(queuedRecord.isFailed() && !queuedRecord.getEventType().equals(EventType.BUILD_REPORT)){
                            continue;
                        }
                        logQueue.offer(queuedRecord);
                    }
                } finally {
                    maintenanceLock.unlock();
                }
            }
            return false;
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
        if (incomingCount % 2000 == 0) {
            LOG.info(this.getStats());
            synchronized (InstanceHolder.service) {
                connMgr.closeIdleConnections(SOCKET_TIMEOUT, TimeUnit.MINUTES);
            }
        }
        return true;
    }

    public void stopWorker() {
        synchronized (workers) {
            for (LogConsumer consumer : workers) {
                consumer.stopTask();
            }
            workers.clear();
        }
        if (this.getQueueSize() != 0) {
            LOG.severe("remaining " + this.getQueueSize() + " record(s) not sent");
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
        static SplunkLogService service = new SplunkLogService();
    }

    public String getStats() {
        StringBuilder sbr = new StringBuilder();
        sbr.append("remaining:").append(this.getQueueSize()).append(" ")
                .append("sent:").append(this.getSentCount());
        return sbr.toString();
    }
}
