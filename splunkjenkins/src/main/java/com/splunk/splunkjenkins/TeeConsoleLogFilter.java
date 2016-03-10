package com.splunk.splunkjenkins;

import com.google.gson.Gson;
import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.util.ByteArrayOutputStream2;
import org.apache.commons.lang.StringUtils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static com.splunk.logging.Constants.BUILD_ID;
import static com.splunk.logging.utils.LogHelper.decodeConsoleBase64Text;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * work like unix tee, one end is splunk http output, the other is console out
 * only need to tee the write(int b) method, leave write(byte b[], int off, int len)
 * and public void write(byte b[]) alone since they will call write(int b)
 * the filter applicate order is determined by descent ordinal order
 */
@Extension(ordinal = 1)
@SuppressWarnings("nouse")
public class TeeConsoleLogFilter extends ConsoleLogFilter implements Serializable {

    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) throws IOException, InterruptedException {
        return new TeeOutputStrem(logger, build.getUrl());
    }

    private static class TeeOutputStrem extends FilterOutputStream {

        private static Logger logger = LoggerFactory.getLogger(TeeOutputStrem.class);
        private static final int LF = 0x0A;
        String buildUrl;
        long lineCounter = 0;
        private ByteArrayOutputStream2 branch = new ByteArrayOutputStream2();

        public TeeOutputStrem(OutputStream out, String buildUrl) {
            super(out);
            this.buildUrl = buildUrl;
            logger.debug("created splunk output tee for {} {}", out, buildUrl);
        }

        @Override
        public void close() throws IOException {
            super.close();
            branch.close();
        }

        @Override
        public void flush() throws IOException {
            super.flush();
            eol();
        }

        @Override
        public void write(int b) throws IOException {
            super.write(b);
            if (b == LF) {
                eol();
            } else {
                branch.write(b);
            }
        }

        private void eol() throws IOException {
            if (branch.size() == 0) {
                return;
            }
            String lineContent = decodeConsoleBase64Text(branch.getBuffer(),branch.size());
            //check blank lines? only check if the length <81
            if (!(lineContent.length() < 81 && StringUtils.isBlank(lineContent))) {
                //send to splunk
                lineCounter++;
                Map eventInfo=new HashMap();
                eventInfo.put("line_number",lineCounter);
                eventInfo.put(BUILD_ID,this.buildUrl);
                eventInfo.put("text",lineContent);
                SplunkLogService.send(eventInfo);
            }
            // reuse the buffer under normal circumstances
            branch.reset();
        }
    }
}
