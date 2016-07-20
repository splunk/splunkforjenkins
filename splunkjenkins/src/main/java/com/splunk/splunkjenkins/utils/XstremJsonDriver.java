package com.splunk.splunkjenkins.utils;

import com.thoughtworks.xstream.io.AbstractDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.json.JsonWriter;

import java.io.*;

public class XstremJsonDriver extends AbstractDriver {
    @Override
    public HierarchicalStreamReader createReader(Reader in) {
        return null;
    }

    @Override
    public HierarchicalStreamReader createReader(InputStream in) {
        return null;
    }

    @Override
    public HierarchicalStreamWriter createWriter(Writer out) {
        return new JsonWriter(out,0, new JsonWriter.Format(
                new char[]{' ', ' '}, new char[]{' '}, JsonWriter.Format.SPACE_AFTER_LABEL
                | JsonWriter.Format.COMPACT_EMPTY_ELEMENT));
    }

    @Override
    public HierarchicalStreamWriter createWriter(OutputStream out) {
        try {
            // JSON spec requires UTF-8
            return createWriter(new OutputStreamWriter(out, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new StreamException(e);
        }
    }
}
