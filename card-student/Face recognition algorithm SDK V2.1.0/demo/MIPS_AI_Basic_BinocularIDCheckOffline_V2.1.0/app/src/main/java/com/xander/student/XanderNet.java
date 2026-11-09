package com.xander.student;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
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
 */
public class XanderNet {
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
        Socket plain = new Socket();
        SSLSocket ssl = null;
        try {
            plain.connect(new InetSocketAddress(XanderHttps.SCHOOL_IP, 443), connectMs);
            SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
            ssl = (SSLSocket) f.createSocket(plain, XanderHttps.SCHOOL_HOST, 443, true);
            try {
                Method setHostname = ssl.getClass().getMethod("setHostname", String.class);
                setHostname.invoke(ssl, XanderHttps.SCHOOL_HOST);
            } catch (Throwable ignored) {
            }
            try {
                ssl.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            } catch (Throwable ignored) {
            }
            ssl.setUseClientMode(true);
            ssl.setSoTimeout(readMs);
            ssl.startHandshake();
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(XanderHttps.SCHOOL_HOST, ssl.getSession())) {
                throw new Exception("TLS certificate is not for " + XanderHttps.SCHOOL_HOST);
            }
            OutputStream out = ssl.getOutputStream();
            StringBuilder head = new StringBuilder();
            head.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
            head.append("Host: ").append(XanderHttps.SCHOOL_HOST).append("\r\n");
            head.append("Accept: application/json,*/*\r\n");
            head.append("Connection: close\r\n");
            if ("POST".equals(method)) {
                head.append("Content-Type: application/x-www-form-urlencoded; charset=UTF-8\r\n");
                head.append("Content-Length: ").append(body.length).append("\r\n");
            }
            head.append("\r\n");
            out.write(head.toString().getBytes("UTF-8"));
            if (body.length > 0) {
                out.write(body);
            }
            out.flush();
            return readHttpBody(ssl.getInputStream());
        } finally {
            try {
                if (ssl != null) {
                    ssl.close();
                }
            } catch (Exception ignored) {
            }
            try {
                plain.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static byte[] readHttpBody(InputStream in) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            raw.write(buf, 0, n);
        }
        byte[] all = raw.toByteArray();
        int split = indexOf(all, new byte[]{'\r', '\n', '\r', '\n'});
        if (split < 0) {
            throw new Exception("No HTTP headers from school server");
        }
        String headers = new String(all, 0, split, "UTF-8");
        byte[] body = new byte[all.length - split - 4];
        System.arraycopy(all, split + 4, body, 0, body.length);
        String first = headers.split("\r\n")[0];
        if (first.indexOf(" 200") < 0 && first.indexOf(" 201") < 0) {
            KioskLog.e("XanderNet", first);
        }
        Map<String, String> h = parseHeaders(headers);
        String te = h.get("transfer-encoding");
        if (te != null && te.toLowerCase(Locale.US).contains("chunked")) {
            body = dechunk(body);
        }
        String enc = h.get("content-encoding");
        if (enc != null && enc.toLowerCase(Locale.US).contains("gzip") && body.length > 0) {
            body = gunzip(body);
        }
        return body;
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

    private static byte[] gunzip(byte[] body) throws Exception {
        GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private static byte[] dechunk(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < body.length) {
            int lineEnd = indexOf(body, new byte[]{'\r', '\n'}, i);
            if (lineEnd < 0) {
                break;
            }
            String hex = new String(body, i, lineEnd - i);
            int size;
            try {
                size = Integer.parseInt(hex.trim(), 16);
            } catch (Exception e) {
                break;
            }
            if (size <= 0) {
                break;
            }
            i = lineEnd + 2;
            if (i + size > body.length) {
                break;
            }
            out.write(body, i, size);
            i = i + size + 2;
        }
        return out.toByteArray();
    }

    private static int indexOf(byte[] data, byte[] pat) {
        return indexOf(data, pat, 0);
    }

    private static int indexOf(byte[] data, byte[] pat, int from) {
        outer:
        for (int i = from; i <= data.length - pat.length; i++) {
            for (int j = 0; j < pat.length; j++) {
                if (data[i + j] != pat[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static InputStream get(String urlStr, int connectMs, int readMs) throws Exception {
        byte[] body = request("GET", urlStr, new byte[0], connectMs, readMs);
        return new ByteArrayInputStream(body);
    }
}
