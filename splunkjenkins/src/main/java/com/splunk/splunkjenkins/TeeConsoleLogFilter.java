package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.model.EventRecord;
import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.util.ByteArrayOutputStream2;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static com.splunk.splunkjenkins.Constants.LOG_TIME_FORMAT;
import static com.splunk.splunkjenkins.model.EventType.CONSOLE_LOG;
import static com.splunk.splunkjenkins.utils.LogEventHelper.decodeConsoleBase64Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * work like unix tee, one end is splunk http output, the other is console out
 * only need to tee the write(int b) method, leave write(byte b[], int off, int len)
 * and public void write(byte b[]) alone since they will call write(int b)
 * the filter apply order is determined by descent ordinal order
 */
@Extension(ordinal = 1)
@SuppressWarnings("nouse")
public class TeeConsoleLogFilter extends ConsoleLogFilter implements Serializable {
    private static final Logger LOG = Logger.getLogger(TeeConsoleLogFilter.class.getName());

    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream output) throws IOException, InterruptedException {
        if (SplunkJenkinsInstallation.get().isValid()) {
            return new TeeOutputStrem(output, build.getUrl());
        } else {
            if (SplunkJenkinsInstallation.get().isEnabled()) {
                LOG.log(Level.WARNING, "invalid splunk config, skipped sending console logs for build " + build.getUrl());
            }
            return output;
        }
    }

    private static class TeeOutputStrem extends FilterOutputStream {


        private static final int LF = 0x0A;
        String buildUrl;
        long lineCounter = 0;
        //holds data received, will be cleared when \n received
        private ByteArrayOutputStream2 branch = new ByteArrayOutputStream2();
        //holds decoded console text with timestamp and line number
        private ByteArrayOutputStream2 logText = new ByteArrayOutputStream2();

        public TeeOutputStrem(OutputStream out, String buildUrl) {
            super(out);
            this.buildUrl = buildUrl;
            LOG.log(Level.FINE, "created splunk output tee for " + buildUrl);
        }

        @Override
        public void close() throws IOException {
            super.close();
            branch.close();
        }

        @Override
        public void flush() throws IOException {
            super.flush();
            if (logText.size() > 0) {
                flushLog();
            }
            branch.reset();
        }

        @Override
        public void write(int b) throws IOException {
            super.write(b);
            branch.write(b);
            if (b == LF) {
                eol();
            }
        }

        private void eol() throws IOException {
            if (branch.size() == 0) {
                return;
            }
            lineCounter++;
            //ISO 8601 datetime, and build url and line number
            SimpleDateFormat sdf = new SimpleDateFormat(LOG_TIME_FORMAT, Locale.US);
            String prefix = sdf.format(new Date()) + "  line:" + lineCounter + "  ";
            logText.write(prefix.getBytes());
            decodeConsoleBase64Text(branch.getBuffer(), branch.size(), logText);
            if (logText.size() > SplunkJenkinsInstallation.get().getMaxEventsBatchSize()) {
                flushLog();
            }
            // reuse the buffer under normal circumstances
            branch.reset();
        }

        private void flushLog() {
            EventRecord record = new EventRecord(logText.toString(), CONSOLE_LOG);
            record.setSource(this.buildUrl + "console");
            SplunkLogService.getInstance().enqueue(record);
            logText.reset();
        }
    }
}
