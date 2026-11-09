package com.example.smdt.smdtaisdkdemo.xander;

import android.util.Log;

import org.json.JSONObject;

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

    public static JSONObject openSchool(String server, String acronym) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("acronym", acronym);
        return post(server + "/api/device_open_school", f, 12000, 90000);
    }

    public static JSONObject sync(String server, int schoolId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        return post(server + "/api/device_sync", f, 12000, 90000);
    }

    public static JSONObject bootstrap(String server, String email, String password) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("email", email);
        f.put("password", password);
        return post(server + "/api/device_bootstrap", f, 8000, 30000);
    }

    public static JSONObject staffList(String server, int schoolId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        return post(server + "/api/device_staff_list", f, 8000, 30000);
    }

    public static JSONObject scanCard(String server, int schoolId, String card, int areaId) throws Exception {
        return scanCard(server, schoolId, card, areaId, 0);
    }

    public static JSONObject scanCard(String server, int schoolId, String card, int areaId, long time) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("card", card);
        f.put("area_id", Integer.toString(areaId));
        if (time > 0) {
            f.put("time", Long.toString(time));
        }
        return post(server + "/api/device_scan", f, 8000, 15000);
    }

    public static JSONObject scanFace(String server, int schoolId, int staffId) throws Exception {
        return scanFace(server, schoolId, staffId, 0, "");
    }

    public static JSONObject scanFace(String server, int schoolId, int staffId, long time) throws Exception {
        return scanFace(server, schoolId, staffId, time, "");
    }

    public static JSONObject scanFace(String server, int schoolId, int staffId, long time, String status) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("staff_id", Integer.toString(staffId));
        if (time > 0) {
            f.put("time", Long.toString(time));
        }
        if (status != null && status.length() > 0) {
            f.put("status", status);
        }
        return post(server + "/api/device_face", f, 8000, 15000);
    }

    public static JSONObject enrollFace(String server, int schoolId, int staffId, String photoB64) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("staff_id", Integer.toString(staffId));
        if (photoB64 != null && photoB64.length() > 0) {
            f.put("photo", photoB64);
        }
        return post(server + "/api/device_enroll_face", f, 8000, 20000);
    }

    public static JSONObject removeFace(String server, int schoolId, int staffId) throws Exception {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("school_id", Integer.toString(schoolId));
        f.put("staff_id", Integer.toString(staffId));
        return post(server + "/api/device_unenroll_face", f, 8000, 15000);
    }

    public static JSONObject post(String urlStr, Map<String, String> fields) throws Exception {
        return post(urlStr, fields, 8000, 12000);
    }

    public static JSONObject post(String urlStr, Map<String, String> fields, int connectMs, int readMs) throws Exception {
        boolean school = urlStr != null && (urlStr.contains(XanderHttps.SCHOOL_HOST) || urlStr.contains(XanderHttps.SCHOOL_IP));
        String body = encode(fields);
        if (school) {
            try {
                String orig = urlStr.contains(XanderHttps.SCHOOL_IP)
                        ? urlStr.replace(XanderHttps.SCHOOL_IP, XanderHttps.SCHOOL_HOST)
                        : urlStr;
                byte[] raw = XanderNet.request("POST", orig, body.getBytes("UTF-8"), connectMs, readMs);
                String text = new String(raw, "UTF-8").trim();
                KioskLog.i(TAG, "OK school IP+SNI " + new URL(orig).getPath());
                return new JSONObject(text);
            } catch (Exception ipFail) {
                KioskLog.e(TAG, "IP+SNI failed: " + ipFail.getClass().getSimpleName() + " " + ipFail.getMessage());
                throw ipFail;
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

            OutputStream os = c.getOutputStream();
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            w.write(body.toString());
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
            return new JSONObject(raw);
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
            return XanderNet.get(orig, 8000, 20000);
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
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setUseCaches(false);
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
}
