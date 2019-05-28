package com.splunk.splunkjenkins;

import com.splunk.splunkjenkins.utils.SplunkLogService;
import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.util.ByteArrayOutputStream2;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.splunk.splunkjenkins.Constants.CONSOLE_TEXT_SINGLE_LINE_MAX_LENGTH;
import static com.splunk.splunkjenkins.Constants.LOG_TIME_FORMAT;
import static com.splunk.splunkjenkins.model.EventType.CONSOLE_LOG;
import static com.splunk.splunkjenkins.utils.LogEventHelper.decodeConsoleBase64Text;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * work like unix tee, one end is splunk http output, the other is console out
 * only need to tee the write(int b) method, leave write(byte b[], int off, int len)
 * and public void write(byte b[]) alone since they will call write(int b)
 * the filter apply order is determined by descent ordinal order
 * <p>
 * Some log filter's flush or close function is no-op, causes TeeConsoleLogFilter cache not flushed.
 * User an higher ordinal so it wil be created at last and will be the outermost filter,
 * the feed log will be un-filtered.
 */
@Extension(ordinal = Integer.MAX_VALUE - 1)
@SuppressWarnings("nouse")
public class TeeConsoleLogFilter extends ConsoleLogFilter implements Serializable {
    private static final Logger LOG = Logger.getLogger(TeeConsoleLogFilter.class.getName());
    private static final Pattern ALLOW_ONLY;
    private static final long serialVersionUID = 1091734060617902662L;
    private static final String SUFFIX = "console";
    private String source;

    static {
        Pattern filterPattern = null;
        String filterStr = System.getProperty("splunkins.allowConsoleLogPattern", "");
        try {
            if (StringUtils.isNotBlank(filterStr)) {
                filterPattern = Pattern.compile(filterStr);
            }
        } catch (PatternSyntaxException ex) {
            LOG.log(Level.SEVERE, "failed to parse console filter pattern=" + filterStr, ex);
        }
        ALLOW_ONLY = filterPattern;
    }

    public TeeConsoleLogFilter(String source) {
        this.source = source;
    }

    public TeeConsoleLogFilter(Run run) {
        if (run != null) {
            this.source = run.getUrl() + SUFFIX;
        }
    }

    public TeeConsoleLogFilter() {
        this.source = "Jobconsole";
    }

    //backwards compatibility
    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream output) throws IOException, InterruptedException {
        return decorateLogger((Run) build, output);
    }

    //introduced in jenkins 1.632
    public OutputStream decorateLogger(Run build, OutputStream output) throws IOException, InterruptedException {
        String logSource = this.source;
        boolean useLineNumber = true;
        if (build != null) {
            logSource = build.getUrl() + SUFFIX;
        } else {
            useLineNumber = false;
        }
        return teeOutput(output, logSource, useLineNumber, SplunkJenkinsInstallation.get().getMaxEventsBatchSize());

    }

    //introduced in jenkins 1.632
    public OutputStream decorateLogger(Computer computer, OutputStream logger) throws IOException, InterruptedException {
        String logSource = this.source;
        if (computer != null) {
            logSource = computer.getUrl() + SUFFIX;
        }
        long cacheSize = Math.min(SplunkJenkinsInstallation.get().getMaxEventsBatchSize(), Constants.SLAVE_LOG_BUFFER_SIZE);
        return teeOutput(logger, logSource, false, cacheSize);
    }

    private OutputStream teeOutput(OutputStream output, String source, boolean useLineNumber, long cacheSize) {
        if (SplunkJenkinsInstallation.get().isEventDisabled(CONSOLE_LOG)) {
            return output;
        }
        if (ALLOW_ONLY != null) {
            if (!ALLOW_ONLY.matcher(source).find()) {
                LOG.log(Level.FINE, "{0} is not allowed to send console log to splunk", source);
                return output;
            }
        } else if (SplunkJenkinsInstallation.get().isJobIgnored(source)) {
            return output;
        }
        TeeOutputStream teeOutput = new TeeOutputStream(output, source);
        teeOutput.setRequireLineNumber(useLineNumber);
        teeOutput.setCacheSize(cacheSize);
        return teeOutput;
    }

    public static class TeeOutputStream extends FilterOutputStream {
        private static final int LF = 0x0A;
        boolean requireLineNumber = true;
        String sourceName;
        long lineCounter = 0;
        private static final int RECEIVE_BUFFER_SIZE = 512;
        //holds data received, will be cleared when \n received
        private ByteArrayOutputStream2 branch = new ByteArrayOutputStream2(RECEIVE_BUFFER_SIZE);
        //holds decoded text with timestamp and line number, will be cleared when job is finished or batch size is reached
        private ByteArrayOutputStream2 logText = new ByteArrayOutputStream2(Constants.MIN_BUFFER_SIZE);
        SimpleDateFormat sdf = new SimpleDateFormat(LOG_TIME_FORMAT, Locale.US);
        private long cacheSize = Constants.MIN_BUFFER_SIZE;

        public void setCacheSize(long cacheSize) {
            this.cacheSize = cacheSize;
        }

        public void setRequireLineNumber(boolean requireLineNumber) {
            this.requireLineNumber = requireLineNumber;
        }

        public TeeOutputStream(OutputStream out, String sourceName) {
            super(out);
            this.sourceName = sourceName;
            LOG.log(Level.FINE, "created splunk output tee for " + sourceName);
        }

        @Override
        public void close() throws IOException {
            super.close();
            flushLog();
            logText.close();
            branch.close();
        }

        @Override
        public void flush() throws IOException {
            super.flush();
            flushLog();
            branch.reset();
        }

        @Override
        public void write(int b) throws IOException {
            super.write(b);
            branch.write(b);
            if (b == LF || branch.size() > CONSOLE_TEXT_SINGLE_LINE_MAX_LENGTH) {
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
            if (logText.size() >= cacheSize) {
                flushLog();
            }
            // reuse the buffer under normal circumstances
            branch.reset();
        }

        private void flushLog() {
            if (logText.size() == 0) {
                return;
            }
            try {
                String logs = logText.toString("UTF-8");
                SplunkLogService.getInstance().send(logs, CONSOLE_LOG, sourceName);
            } catch (UnsupportedEncodingException e) {//this should not happen, since utf-8 is an known charset
                e.printStackTrace();
            }
            logText.reset();
        }
    }

}
