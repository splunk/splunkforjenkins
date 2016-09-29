package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.util.ByteArrayOutputStream2;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.splunk.splunkjenkins.Constants.LOG_TIME_FORMAT;
import static com.splunk.splunkjenkins.SplunkJenkinsInstallation.MIN_BUFFER_SIZE;
import static com.splunk.splunkjenkins.model.EventType.CONSOLE_LOG;
import static com.splunk.splunkjenkins.utils.LogEventHelper.decodeConsoleBase64Text;
import static java.nio.charset.StandardCharsets.UTF_8;


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
    private static final long serialVersionUID = 1091734060617902662L;

    //backwards compatibility
    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream output) throws IOException, InterruptedException {
        return teeOutput(output, build.getUrl() + "console", true);
    }

    //introduced in jenkins 1.632
    public OutputStream decorateLogger(Run build, OutputStream output) throws IOException, InterruptedException {
        return teeOutput(output, build.getUrl() + "console", true);

    }

    //introduced in jenkins 1.632
    public OutputStream decorateLogger(Computer computer, OutputStream logger) throws IOException, InterruptedException {
        return teeOutput(logger, computer.getUrl() + "log", false);
    }

    private OutputStream teeOutput(OutputStream output, String source, boolean addLineNumber) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(CONSOLE_LOG)) {
            return output;
        }
        return new TeeOutputStream(output, addLineNumber, source);
    }

    public static class TeeOutputStream extends FilterOutputStream {
        private static final int LF = 0x0A;
        boolean requireLineNumber = true;
        String sourceName;
        long lineCounter = 0;
        //holds data received, will be cleared when \n received
        private ByteArrayOutputStream2 branch = new ByteArrayOutputStream2(512);
        //holds decoded text with timestamp and line number, will be cleared when job is finished or batch size is reached
        private ByteArrayOutputStream2 logText = new ByteArrayOutputStream2(MIN_BUFFER_SIZE);
        SimpleDateFormat sdf = new SimpleDateFormat(LOG_TIME_FORMAT, Locale.US);

        public TeeOutputStream(OutputStream out, String sourceName) {
            super(out);
            this.sourceName = sourceName;
            LOG.log(Level.FINE, "created splunk output tee for " + sourceName);
        }

        public TeeOutputStream(OutputStream out, boolean requireLineNumber, String sourceName) {
            this(out, sourceName);
            this.requireLineNumber = requireLineNumber;
        }

        @Override
        public void close() throws IOException {
            super.close();
            logText.close();
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
            String prefix = sdf.format(new Date()) + "  ";
            logText.write(prefix.getBytes(UTF_8));
            if (requireLineNumber) {
                logText.write(("line:" + lineCounter + "  ").getBytes(UTF_8));
            }
            decodeConsoleBase64Text(branch.getBuffer(), branch.size(), logText);
            if (logText.size() > SplunkJenkinsInstallation.get().getMaxEventsBatchSize()) {
                flushLog();
            }
            // reuse the buffer under normal circumstances
            branch.reset();
        }

        private void flushLog() {
            try {
                String logs = logText.toString("UTF-8");
                SplunkLogService.getInstance().send(logs, CONSOLE_LOG, sourceName);
            } catch (UnsupportedEncodingException e) {//this should not happen, since is utf-8 is an known charset
                e.printStackTrace();
            }
            logText.reset();
        }
    }

}
