package com.xander.student;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * School MIS hostname often fails on kiosk LAN DNS. Reach the same VPS by IP
 * and keep TLS SNI as the real hostname so the certificate still matches.
 */
public class XanderHttps {
    public static final String SCHOOL_HOST = "schoolmis.xanderglobalacademy.com";
    public static final String SCHOOL_IP = "66.29.135.120";

    public static String rewriteHost(String url) {
        if (url == null) {
            return url;
        }
        if (url.contains(SCHOOL_HOST)) {
            return url.replace(SCHOOL_HOST, SCHOOL_IP);
        }
        return url;
    }

    public static boolean usesSchoolIp(String url) {
        return url != null && url.contains(SCHOOL_IP);
    }

    public static void apply(HttpsURLConnection c, boolean schoolIp) {
        if (c == null || !schoolIp) {
            return;
        }
        c.setSSLSocketFactory(new SniFactory(c.getSSLSocketFactory(), SCHOOL_HOST));
        c.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return HttpsURLConnection.getDefaultHostnameVerifier().verify(SCHOOL_HOST, session)
                        || SCHOOL_IP.equals(hostname);
            }
        });
    }

    public static boolean isDnsFail(Throwable t) {
        if (t instanceof UnknownHostException) {
            return true;
        }
        String m = t == null || t.getMessage() == null ? "" : t.getMessage();
        return m.contains("Unable to resolve host") || m.contains("UnknownHost");
    }

    private static class SniFactory extends SSLSocketFactory {
        private final SSLSocketFactory base;
        private final String sni;

        SniFactory(SSLSocketFactory base, String sni) {
            this.base = base == null ? (SSLSocketFactory) SSLSocketFactory.getDefault() : base;
            this.sni = sni;
        }

        private Socket tag(Socket s) {
            if (s instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) s;
                try {
                    Method m = ssl.getClass().getMethod("setHostname", String.class);
                    m.invoke(ssl, sni);
                } catch (Throwable ignored) {
                }
            }
            return s;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return base.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return base.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            return tag(base.createSocket(s, sni, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws java.io.IOException {
            return tag(base.createSocket(InetAddress.getByName(host), port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws java.io.IOException {
            return tag(base.createSocket(InetAddress.getByName(host), port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws java.io.IOException {
            return tag(base.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws java.io.IOException {
            return tag(base.createSocket(address, port, localAddress, localPort));
        }
    }
}
