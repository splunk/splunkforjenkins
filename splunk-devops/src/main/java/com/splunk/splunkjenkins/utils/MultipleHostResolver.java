package com.splunk.splunkjenkins.utils;

import shaded.splk.org.apache.http.conn.DnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MultipleHostResolver implements DnsResolver {
    public static final String NAME_DELIMITER = ",";

    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
        String hostname = host;
        //split by comma
        String[] hosts = hostname.split(NAME_DELIMITER);
        if (hosts == null) {
            return null;
        }
        List<InetAddress> addressList = new ArrayList<>();
        for (String endpointHost : hosts) {
            InetAddress[] addresses = InetAddress.getAllByName(endpointHost);
            for (InetAddress address : addresses) {
                addressList.add(address);
            }
        }
        return addressList.toArray(new InetAddress[0]);
    }
}
