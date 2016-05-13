package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.SplunkJenkinsInstallation;
import hudson.model.Computer;
import org.apache.http.client.HttpClient;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
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
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class SplunkLogService {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(InstanceHolder.class.getName());
    int MAX_WORKER_COUNT = Integer.getInteger(SplunkLogService.class.getName() + ".workerCount", 2);
    private AtomicLong incomingCounter = new AtomicLong();
    private AtomicLong outgoingCounter = new AtomicLong();
    BlockingQueue<EventRecord> logQueue;
    List<LogConsumer> workers;
    HttpClient client;
    HttpClientConnectionManager connMgr;

    private SplunkLogService() {
        this.logQueue = new LinkedBlockingQueue<EventRecord>();
        this.workers = new ArrayList<LogConsumer>();
        this.connMgr = buildConnectionManager();
        this.client = HttpClients.custom().setConnectionManager(this.connMgr).build();
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
        return cm;
    }

    public boolean send(Object message) {
        if (message == null) {
            LOG.warning("null message discarded");
            return false;
        }
        if (!SplunkJenkinsInstallation.get().enabled) {
            return false;
        }
        if (!SplunkJenkinsInstallation.get().isValid()) {
            LOG.log(Level.SEVERE, "Splunk plugin config is not invalid, can not send " + message);
            return false;
        }
        EventRecord record = new EventRecord(message);
        return enqueue(record);
    }

    public boolean enqueue(EventRecord record) {
        boolean added = logQueue.offer(record);
        if (!added) {
            LOG.log(Level.SEVERE, "log queue is full, workers count " + workers.size() + ",jenkins too busy or too few workers?");
            return false;
        }
        if (workers.size() < MAX_WORKER_COUNT) {
            synchronized (workers) {
                int worksToCreate = MAX_WORKER_COUNT - workers.size();
                for (int i = 0; i < worksToCreate; i++) {
                    LogConsumer runnable = new LogConsumer(client, logQueue, outgoingCounter);
                    workers.add(runnable);
                    Thread workerThread = new Thread(runnable);
                    workerThread.start();
                }
            }
        }
        long sent = incomingCounter.incrementAndGet();
        if (sent % 2000 == 0) {
            synchronized (InstanceHolder.service) {
                connMgr.closeIdleConnections(60, TimeUnit.SECONDS);
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
    }

    public void releaseConnection() {
        connMgr.closeIdleConnections(0, TimeUnit.SECONDS);
    }

    public long getSentCount() {
        return outgoingCounter.get();
    }

    public static SplunkLogService getInstance() {
        return InstanceHolder.service;
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
}
