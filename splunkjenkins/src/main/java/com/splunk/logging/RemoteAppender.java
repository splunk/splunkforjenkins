package com.splunk.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.splunk.logging.HttpEventCollectorErrorHandler.ErrorCallback;

import java.lang.reflect.Field;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This appender is similar to HttpEventCollectorLogbackAppender but allows
 * update sender property such as host, token, index, etc.
 */
public class RemoteAppender extends AppenderBase<ILoggingEvent> {

    private HttpEventCollectorSender sender = null;
    private Hashtable metaConfig = new Hashtable();
    private String cachedConfig = null;
    long retryNumber=3;
    private HttpEventCollectorResendMiddleware retryConfig = new HttpEventCollectorResendMiddleware(retryNumber);
    AtomicLong eventCount = new AtomicLong();
    AtomicLong errorCounter = new AtomicLong();

    @Override
    protected void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing();
        event.getCallerData();
        if (event != null && started) {
            eventCount.incrementAndGet();
            this.sender.send(event.getLevel().toString(), event.getMessage());
        }
    }

    public boolean updateSender(final String Url, final String token,
            long delay, long maxEventsBatchCount, long maxEventsBatchSize,
            String sendModeStr) {
        String configStr = Url + "|" + token + "|" + delay + "|" + maxEventsBatchCount + "|" + maxEventsBatchCount + "|" + sendModeStr;
        if (configStr.equals(cachedConfig)) {
            return true;
        }
        if (this.sender != null) {
            this.stop();
            try {
                this.sender.close();
            } catch (Exception ex) {
                //ignore error
            }
        }
        //don't trust re-occurence period lower than 3s or greater than 5 minute
        delay=Math.min(300000,Math.max(1000, delay));
        this.sender = new HttpEventCollectorSender(Url, token, delay, maxEventsBatchCount, maxEventsBatchSize, sendModeStr, metaConfig);
        this.sender.disableCertificateValidation();
        this.sender.addMiddleware(retryConfig);
        this.start();
        return validate();
    }

    public boolean validate() {
        long oldErrorCount = this.getErrorCount();
        this.sender.send("debug", "ping test");
        this.sender.flush();
        long oldRetry=this.retryNumber;
        setRetry(1);
        //wait 5 seconds because of retry
        long timeToWait=System.currentTimeMillis()+TimeUnit.SECONDS.toMillis(10);
        //the sender is async, need wait for result.
        //there is no feed back on sucessful sent, have to wait on failure
        while (this.getErrorCount() == oldErrorCount) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                Logger.getLogger(RemoteAppender.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
            if(System.currentTimeMillis()>timeToWait){
                break;
            }
        }
        boolean passed=this.getErrorCount() <= oldErrorCount;
        if(passed){ //set retry back
            setRetry(oldRetry);
        }
        return passed;
    }

    public void updateIndex(String index, String source, String sourceType) {
        if (index == null) {
            index = "_debug";
        }
        metaConfig.put(HttpEventCollectorSender.MetadataIndexTag, index);
        if (source != null) {
            metaConfig.put(HttpEventCollectorSender.MetadataSourceTag, source);
        }else{
            metaConfig.remove(HttpEventCollectorSender.MetadataSourceTag);
        }

        if (sourceType != null) {
            metaConfig.put(HttpEventCollectorSender.MetadataSourceTypeTag, sourceType);
        }else{
            metaConfig.remove(HttpEventCollectorSender.MetadataSourceTypeTag);
        }

    }

    public void setRetry(long retriesOnError) {
        // it is a pity that retriesOnError is private
        try {
            Field retryField = HttpEventCollectorResendMiddleware.class.getDeclaredField("retriesOnError");
            retryField.setAccessible(true);
            retryField.setLong(retryConfig, retriesOnError);
            this.retryNumber=retriesOnError;
        } catch (Exception e) {
            //just ignore
        }
    }

    public void flush() {
        if (this.sender != null) {
            this.sender.flush();
        }
    }

    @Override
    public void start() {
        if (sender == null) {
            return;
        }
        super.start();
        HttpEventCollectorErrorHandler.onError(
                new ErrorCallback() {
            @Override
            public void error(List<HttpEventCollectorEventInfo> data, Exception ex) {
                errorCounter.incrementAndGet();
                Logger.getLogger(RemoteAppender.class.getName()).log(Level.SEVERE, "failed to send data " + data, ex);
            }

        });
    }

    public long getSentCount() {
        return eventCount.longValue();
    }
    public long getErrorCount(){
        return errorCounter.longValue();
    }
}
