package com.splunk.splunkjenkins.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.splunk.splunkjenkins.SplunkLogService;
import hudson.console.ConsoleNote;
import hudson.util.ByteArrayOutputStream2;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

public class LogEventHelper {
    private static final Gson gson = new GsonBuilder().registerTypeAdapter(EventRecord.class,
            new EventRecordSerializer()).disableHtmlEscaping().create();

    public static HttpPost buildPost(EventRecord record) {
        HttpPost postMethod = new HttpPost(SplunkLogService.config.url);
        postMethod.setHeader("Authorization", SplunkLogService.config.authToken);
        StringEntity entity = new StringEntity(gson.toJson(record), "utf-8");
        entity.setContentType("application/json; profile=urn:splunk:event:1.0; charset=utf-8");
        postMethod.setEntity(entity);
        return postMethod;
    }

    /**
     * the logical extracted from PlainTextConsoleOutputStream
     * console annotation will be removed, e.g.
     * Input:Started by user ESC[8mha:AAAAlh+LCAAAAAAAAP9b85aBtbiIQTGjNKU4P08vOT+vOD8nVc83PyU1x6OyILUoJzMv2y+/JJUBAhiZGBgqihhk0NSjKDWzXb3RdlLBUSYGJk8GtpzUvPSSDB8G5tKinBIGIZ+sxLJE/ZzEvHT94JKizLx0a6BxUmjGOUNodHsLgAzOEgYu/dLi1CL9vNKcHACFIKlWvwAAAA==ESC[0manonymous
     * Output:Started by user anonymous
     *
     * @param in     the byte array
     * @param length how many bytes we want to read in
     * @return
     * @see hudson.console.PlainTextConsoleOutputStream
     */
    public static String decodeConsoleBase64Text(byte[] in, int length) {
        ByteArrayOutputStream2 out = new ByteArrayOutputStream2(length);
        int next = ConsoleNote.findPreamble(in, 0, length);

        // perform byte[]->char[] while figuring out the char positions of the BLOBs
        int written = 0;
        while (next >= 0) {
            if (next > written) {
                out.write(in, written, next - written);
                written = next;
            } else {
                assert next == written;
            }

            int rest = length - next;
            ByteArrayInputStream b = new ByteArrayInputStream(in, next, rest);

            try {
                ConsoleNote.skip(new DataInputStream(b));
            } catch (IOException ex) {
                Logger.getLogger(LogEventHelper.class.getName()).log(Level.SEVERE, "failed to filter blob", ex);
            }

            int bytesUsed = rest - b.available(); // bytes consumed by annotations
            written += bytesUsed;


            next = ConsoleNote.findPreamble(in, written, length - written);
        }
        // finish the remaining bytes->chars conversion
        out.write(in, written, length - written);
        return new String(out.getBuffer(), 0, out.size());
    }

    public static String emptyToNull(String string) {
        return (string == "") ? null : string;
    }
}
