package com.xander.student;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Talk to the school VPS by IP when LAN DNS is dead. TCP goes to
 * {@link XanderHttps#SCHOOL_IP}; TLS SNI stays the real hostname so the
 * certificate matches (plain https://IP is reset by the server).
 *
 * Reuses one TLS socket with HTTP/1.1 keep-alive so staff scans skip the
 * handshake after {@link #warm(int)}.
 */
public class XanderNet {
    private static final long IDLE_MS = 25000L;
    private static final Object LOCK = new Object();
    private static SSLSocket pooled;
    private static long lastUsedAt;

    public static void warm(final int connectMs) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    try {
                        ensureSocket(connectMs, Math.max(connectMs, 4000));
                    } catch (Exception e) {
                        closePooled();
                        KioskLog.e("XanderNet", "warm: " + e.getMessage());
                    }
                }
            }
        }, "xander-warm").start();
    }

    public static byte[] request(String method, String urlStr, byte[] body, int connectMs, int readMs) throws Exception {
        URL u = new URL(urlStr);
        String path = u.getPath();
        if (path == null || path.length() == 0) {
            path = "/";
        }
        if (u.getQuery() != null) {
            path = path + "?" + u.getQuery();
        }
        if (body == null) {
            body = new byte[0];
        }
        synchronized (LOCK) {
            try {
                return exchange(method, path, body, connectMs, readMs);
            } catch (Exception first) {
                closePooled();
                try {
                    return exchange(method, path, body, connectMs, readMs);
                } catch (Exception second) {
                    closePooled();
                    throw first;
                }
            }
        }
    }

    private static byte[] exchange(String method, String path, byte[] body, int connectMs, int readMs) throws Exception {
        SSLSocket ssl = ensureSocket(connectMs, readMs);
        ssl.setSoTimeout(readMs);
        OutputStream out = ssl.getOutputStream();
        StringBuilder head = new StringBuilder();
        head.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        head.append("Host: ").append(XanderHttps.SCHOOL_HOST).append("\r\n");
        head.append("Accept: application/json,*/*\r\n");
        head.append("Connection: keep-alive\r\n");
        if ("POST".equals(method) || body.length > 0) {
            if ("POST".equals(method)) {
                head.append("Content-Type: application/x-www-form-urlencoded; charset=UTF-8\r\n");
            }
            head.append("Content-Length: ").append(body.length).append("\r\n");
        }
        head.append("\r\n");
        out.write(head.toString().getBytes("UTF-8"));
        if (body.length > 0) {
            out.write(body);
        }
        out.flush();

        InputStream in = ssl.getInputStream();
        HeaderBlock hb = readHeaderBlock(in);
        if (hb.status.indexOf(" 200") < 0 && hb.status.indexOf(" 201") < 0) {
            KioskLog.e("XanderNet", hb.status);
        }
        byte[] resp = readBody(in, hb.headers);
        lastUsedAt = System.currentTimeMillis();
        String conn = hb.headers.get("connection");
        if (conn != null && conn.toLowerCase(Locale.US).contains("close")) {
            closePooled();
        }
        return resp;
    }

    private static SSLSocket ensureSocket(int connectMs, int readMs) throws Exception {
        if (pooled != null) {
            if (pooled.isClosed() || !pooled.isConnected()
                    || System.currentTimeMillis() - lastUsedAt > IDLE_MS) {
                closePooled();
            }
        }
        if (pooled != null) {
            return pooled;
        }
        Socket plain = new Socket();
        plain.setTcpNoDelay(true);
        plain.connect(new InetSocketAddress(XanderHttps.SCHOOL_IP, 443), connectMs);
        SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) f.createSocket(plain, XanderHttps.SCHOOL_HOST, 443, true);
        try {
            Method setHostname = ssl.getClass().getMethod("setHostname", String.class);
            setHostname.invoke(ssl, XanderHttps.SCHOOL_HOST);
        } catch (Throwable ignored) {
        }
        try {
            ssl.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        } catch (Throwable ignored) {
        }
        try {
            ssl.setTcpNoDelay(true);
        } catch (Throwable ignored) {
        }
        ssl.setUseClientMode(true);
        ssl.setSoTimeout(readMs);
        ssl.startHandshake();
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(XanderHttps.SCHOOL_HOST, ssl.getSession())) {
            try {
                ssl.close();
            } catch (Exception ignored) {
            }
            throw new Exception("TLS certificate is not for " + XanderHttps.SCHOOL_HOST);
        }
        pooled = ssl;
        lastUsedAt = System.currentTimeMillis();
        return ssl;
    }

    private static void closePooled() {
        SSLSocket s = pooled;
        pooled = null;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static class HeaderBlock {
        final String status;
        final Map<String, String> headers;

        HeaderBlock(String status, Map<String, String> headers) {
            this.status = status;
            this.headers = headers;
        }
    }

    private static HeaderBlock readHeaderBlock(InputStream in) throws Exception {
        byte[] raw = readUntil(in, new byte[]{'\r', '\n', '\r', '\n'}, 65536);
        String headers = new String(raw, 0, raw.length - 4, "UTF-8");
        String[] lines = headers.split("\r\n");
        if (lines.length == 0 || lines[0].length() == 0) {
            throw new Exception("No HTTP headers from school server");
        }
        return new HeaderBlock(lines[0], parseHeaders(headers));
    }

    private static byte[] readBody(InputStream in, Map<String, String> h) throws Exception {
        String te = h.get("transfer-encoding");
        if (te != null && te.toLowerCase(Locale.US).contains("chunked")) {
            return readChunked(in);
        }
        String cl = h.get("content-length");
        if (cl != null && cl.length() > 0) {
            int len = Integer.parseInt(cl.trim());
            if (len < 0) {
                throw new Exception("Bad Content-Length");
            }
            return readFully(in, len);
        }
        return new byte[0];
    }

    private static byte[] readChunked(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String hex = readLine(in);
            int semi = hex.indexOf(';');
            if (semi >= 0) {
                hex = hex.substring(0, semi);
            }
            hex = hex.trim();
            if (hex.length() == 0) {
                continue;
            }
            int size;
            try {
                size = Integer.parseInt(hex, 16);
            } catch (Exception e) {
                throw new Exception("Bad chunk size");
            }
            if (size <= 0) {
                String line;
                while ((line = readLine(in)).length() > 0) {
                    // trailers
                }
                break;
            }
            out.write(readFully(in, size));
            readFully(in, 2);
        }
        return out.toByteArray();
    }

    private static byte[] readUntil(InputStream in, byte[] end, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int match = 0;
        while (match < end.length) {
            int b = in.read();
            if (b < 0) {
                throw new Exception("No HTTP headers from school server");
            }
            out.write(b);
            if (b == (end[match] & 0xff)) {
                match++;
            } else if (b == (end[0] & 0xff)) {
                match = 1;
            } else {
                match = 0;
            }
            if (out.size() > max) {
                throw new Exception("HTTP headers too large");
            }
        }
        return out.toByteArray();
    }

    private static byte[] readFully(InputStream in, int len) throws Exception {
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(buf, off, len - off);
            if (n < 0) {
                throw new Exception("Unexpected EOF from school server");
            }
            off += n;
        }
        return buf;
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new Exception("Unexpected EOF from school server");
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                out.write(b);
            }
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    private static Map<String, String> parseHeaders(String headers) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        String[] lines = headers.split("\r\n");
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) {
                map.put(lines[i].substring(0, c).trim().toLowerCase(Locale.US), lines[i].substring(c + 1).trim());
            }
        }
        return map;
    }

    public static InputStream get(String urlStr, int connectMs, int readMs) throws Exception {
        byte[] body = request("GET", urlStr, new byte[0], connectMs, readMs);
        return new ByteArrayInputStream(body);
    }
}
