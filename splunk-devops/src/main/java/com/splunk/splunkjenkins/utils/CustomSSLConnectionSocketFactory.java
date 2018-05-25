package com.splunk.splunkjenkins.utils;

import shaded.splk.org.apache.http.HttpHost;
import shaded.splk.org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import shaded.splk.org.apache.http.protocol.HttpContext;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static com.splunk.splunkjenkins.utils.MultipleHostResolver.NAME_DELIMITER;

public class CustomSSLConnectionSocketFactory extends SSLConnectionSocketFactory {

    public CustomSSLConnectionSocketFactory(SSLContext sslContext, HostnameVerifier hostnameVerifier) {
        super(sslContext, hostnameVerifier);
    }

    @Override
    public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
        if (host.getHostName().contains(NAME_DELIMITER)) {
            HttpHost resolvedHost = new HttpHost(remoteAddress.getHostName(), host.getPort(), host.getSchemeName());
            return super.connectSocket(connectTimeout, socket, resolvedHost, remoteAddress, localAddress, context);
        } else{
            return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
        }
    }
}
