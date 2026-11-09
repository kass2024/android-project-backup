package com.xander.student;

import android.util.Log;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class XanderApi {
    private static final String TAG = "XanderApi";
    /** Dedicated visitor endpoint — fail fast; UI must never sit on CHECKING. */
    private static final int VISITOR_CONNECT_MS = 700;
    private static final int VISITOR_READ_MS = 900;
    /** Student attendance / auto device_scan — local roster is primary; online is backup only. */
    private static final int SCAN_CONNECT_MS = 700;
    private static final int SCAN_READ_MS = 900;
    /** Pending-queue upload must survive slow school Wi‑Fi so reports get every tap. */
    private static final int FLUSH_CONNECT_MS = 15000;
    private static final int FLUSH_READ_MS = 45000;
    /**
     * Login / full roster sync. School LAN shows ~25–50% loss + 300ms+ RTT;
     * 12s connect was failing before TLS finished.
     */
    private static final int SYNC_CONNECT_MS = 45000;
    private static final int SYNC_READ_MS = 120000;
    private static final int SYNC_ATTEMPTS = 3;

    public static JSONObject openSchool(String server, String acronym) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("acronym", acronym);
        return postWithRetry(server + "/api/device_open_school", f, SYNC_CONNECT_MS, SYNC_READ_MS, SYNC_ATTEMPTS);
    }

    public static JSONObject sync(String server, int schoolId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        return postWithRetry(server + "/api/device_sync", f, SYNC_CONNECT_MS, SYNC_READ_MS, SYNC_ATTEMPTS);
    }

    /** Location names and daily start times only. Small enough to refresh often. */
    public static JSONObject syncLocations(String server, int schoolId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        return postWithRetry(server + "/api/device_locations", f, 8000, 15000, 2);
    }

    /**
     * Lightweight UID poll — newly assigned student cards without a full roster download.
     */
    public static JSONObject syncCards(String server, int schoolId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        return postWithRetry(server + "/api/device_cards", f, 4000, 10000, 2);
    }

    private static JSONObject postWithRetry(String url, Map<String, String> fields,
            int connectMs, int readMs, int attempts) throws Exception {
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                if (i > 0) {
                    KioskLog.i(TAG, "retry " + (i + 1) + "/" + attempts + " " + url);
                }
                return post(url, fields, connectMs, readMs);
            } catch (Exception e) {
                last = e;
                KioskLog.e(TAG, "attempt " + (i + 1) + " failed: " + e.getMessage());
                if (i < attempts - 1) {
                    try {
                        Thread.sleep(1500L * (i + 1));
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        throw last != null ? last : new Exception("Cannot reach server");
    }

    public static JSONObject feeBrief(String server, int schoolId, int studentId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("student_id", Integer.toString(studentId));
        return post(server + "/api/device_fee_brief", f, 4000, 8000);
    }

    public static JSONObject scanCard(String server, int schoolId, String card, int areaId) throws Exception {
        return scanCard(server, schoolId, card, areaId, 0);
    }

    public static JSONObject scanCard(String server, int schoolId, String card, int areaId, long time) throws Exception {
        return scanCard(server, schoolId, card, areaId, time, 0, "", SCAN_CONNECT_MS, SCAN_READ_MS);
    }

    public static JSONObject scanCardFlush(String server, int schoolId, String card, int areaId, long time,
                                          int studentId, String status) throws Exception {
        return scanCard(server, schoolId, card, areaId, time, studentId, status, FLUSH_CONNECT_MS, FLUSH_READ_MS);
    }

    public static JSONObject scanCard(String server, int schoolId, String card, int areaId, long time,
                                      int studentId, String status, int connectMs, int readMs) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("card", card == null ? "" : card);
        f.put("area_id", Integer.toString(areaId));
        if (time > 0) {
            f.put("time", Long.toString(time));
        }
        if (studentId > 0) {
            f.put("student_id", Integer.toString(studentId));
        }
        if (status != null && status.length() > 0) {
            f.put("status", status);
        }
        return post(server + "/api/device_scan", f, connectMs, readMs);
    }

    public static JSONObject scanBatch(String server, int schoolId, org.json.JSONArray events) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("events", events == null ? "[]" : events.toString());
        return post(server + "/api/device_scan_batch", f, FLUSH_CONNECT_MS, FLUSH_READ_MS);
    }

    /** Current Parent visiting switch. One short request so an online device changes immediately. */
    public static JSONObject visitMode(String server, int schoolId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        String url = server + "/api/visit_kiosk_mode";
        try {
            return postOnce(url, f, 800, 1200, false);
        } catch (Exception first) {
            return post(url, f, 1000, 1500);
        }
    }

    /** Live allowed-visitor list for one student. Does not clock. */
    public static JSONObject allowedVisitors(String server, int schoolId, int studentId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("student_id", Integer.toString(studentId));
        return post(server + "/api/visitor_allowed", f, 2500, 4000);
    }

    public static JSONObject scanVisitorCard(String server, int schoolId, String card) throws Exception {
        return scanVisitorCard(server, schoolId, card, "android", 0);
    }

    public static JSONObject scanVisitorCard(String server, int schoolId, String card, String source, int operator) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("card", card);
        f.put("source", source == null || source.length() == 0 ? "android" : source);
        if (operator > 0) {
            f.put("operator", Integer.toString(operator));
        }
        return post(server + "/api/visitor_card_scan", f, VISITOR_CONNECT_MS, VISITOR_READ_MS);
    }

    public static JSONObject scanVisitorCardFlush(String server, int schoolId, String card) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("card", card);
        f.put("source", "android");
        return post(server + "/api/visitor_card_scan", f, FLUSH_CONNECT_MS, FLUSH_READ_MS);
    }

    public static JSONObject receiveVisitor(String server, int schoolId, String visitorCard, String staffCard, int staffId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("card", visitorCard == null ? "" : visitorCard);
        f.put("staff_card", staffCard == null ? "" : staffCard);
        f.put("source", "android");
        if (staffId > 0) {
            f.put("operator", Integer.toString(staffId));
        }
        return post(server + "/api/visitor_receive_scan", f, VISITOR_CONNECT_MS, VISITOR_READ_MS);
    }

    public static JSONObject post(String urlStr, Map<String, String> fields, int connectMs, int readMs) throws Exception {
        boolean school = urlStr != null && (urlStr.contains(XanderHttps.SCHOOL_HOST) || urlStr.contains(XanderHttps.SCHOOL_IP));
        String body = encode(fields);
        if (school) {
            final String orig = urlStr.contains(XanderHttps.SCHOOL_IP)
                    ? urlStr.replace(XanderHttps.SCHOOL_IP, XanderHttps.SCHOOL_HOST)
                    : urlStr;
            try {
                byte[] raw = XanderNet.request("POST", orig, body.getBytes("UTF-8"), connectMs, readMs);
                String text = new String(raw, "UTF-8").trim();
                KioskLog.i(TAG, "OK school IP+SNI " + new URL(orig).getPath());
                return parseJson(text, new URL(orig).getPath());
            } catch (Exception ipFail) {
                KioskLog.e(TAG, "IP+SNI failed: " + ipFail.getClass().getSimpleName() + " " + ipFail.getMessage());
                try {
                    return postOnce(orig, fields, connectMs, readMs, false);
                } catch (Exception hostFail) {
                    KioskLog.e(TAG, "HTTPS retry: " + hostFail.getMessage());
                    return postOnce(orig, fields, connectMs, readMs, true);
                }
            }
        }
        return postOnce(urlStr, fields, connectMs, readMs, false);
    }

    private static String encode(Map<String, String> fields) throws Exception {
        StringBuilder body = new StringBuilder();
        Iterator<Map.Entry<String, String>> it = fields.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(e.getKey(), "UTF-8"));
            body.append('=');
            body.append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        return body.toString();
    }

    private static JSONObject postOnce(String urlStr, Map<String, String> fields, int connectMs, int readMs, boolean viaIp) throws Exception {
        if (viaIp) {
            urlStr = XanderHttps.rewriteHost(urlStr);
        }
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            if (c instanceof HttpsURLConnection) {
                XanderHttps.apply((HttpsURLConnection) c, viaIp || XanderHttps.usesSchoolIp(urlStr));
            }
            c.setRequestMethod("POST");
            c.setDoInput(true);
            c.setDoOutput(true);
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setUseCaches(false);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            c.setRequestProperty("Accept", "application/json");
            if (viaIp || XanderHttps.usesSchoolIp(urlStr)) {
                c.setRequestProperty("Host", XanderHttps.SCHOOL_HOST);
            }

            OutputStream os = c.getOutputStream();
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            w.write(encode(fields));
            w.flush();
            w.close();
            os.close();

            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) {
                throw new Exception("Empty response (" + code + ")");
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            String raw = sb.toString();
            if (raw.length() == 0) {
                throw new Exception("Empty body (" + code + ")");
            }
            KioskLog.i(TAG, "OK " + url.getPath() + " " + code);
            return parseJson(raw, url.getPath());
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    public static InputStream openStream(String urlStr) throws Exception {
        boolean school = urlStr != null && (urlStr.contains(XanderHttps.SCHOOL_HOST) || urlStr.contains(XanderHttps.SCHOOL_IP));
        if (school) {
            String orig = urlStr.contains(XanderHttps.SCHOOL_IP)
                    ? urlStr.replace(XanderHttps.SCHOOL_IP, XanderHttps.SCHOOL_HOST)
                    : urlStr;
            return XanderNet.get(orig, 6000, 15000);
        }
        return openStreamOnce(urlStr, false);
    }

    private static InputStream openStreamOnce(String urlStr, boolean viaIp) throws Exception {
        if (viaIp) {
            urlStr = XanderHttps.rewriteHost(urlStr);
        }
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        if (c instanceof HttpsURLConnection) {
            XanderHttps.apply((HttpsURLConnection) c, viaIp || XanderHttps.usesSchoolIp(urlStr));
        }
        if (viaIp || XanderHttps.usesSchoolIp(urlStr)) {
            c.setRequestProperty("Host", XanderHttps.SCHOOL_HOST);
        }
        c.setConnectTimeout(6000);
        c.setReadTimeout(15000);
        c.setUseCaches(true);
        return c.getInputStream();
    }

    public static boolean ok(JSONObject json) {
        if (json == null) {
            return false;
        }
        if (json.optInt("success", -1) == 1) {
            return true;
        }
        Object s = json.opt("success");
        return s instanceof Boolean && ((Boolean) s).booleanValue();
    }

    public static String message(JSONObject json, String fallback) {
        if (json == null) {
            return fallback;
        }
        String m = json.optString("message", "");
        if (m.length() == 0) {
            m = json.optString("error", "");
        }
        return m.length() == 0 ? fallback : m;
    }

    public static void logErr(String msg, Exception e) {
        Log.e(TAG, msg, e);
    }

    private static JSONObject parseJson(String raw, String path) throws Exception {
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            String sample = raw == null ? "" : raw.trim();
            if (sample.length() > 80) {
                sample = sample.substring(0, 80);
            }
            if (sample.startsWith("<")) {
                throw new Exception("Server returned HTML for " + path);
            }
            throw new Exception("Invalid JSON from " + path + ": " + sample);
        }
    }
}
