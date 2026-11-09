package com.xander.student;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class XanderPrefs {
    public static final String DEFAULT_SERVER = "https://schoolmis.xanderglobalacademy.com";
    private static final String NAME = "xander_staff";

    private final SharedPreferences p;

    public XanderPrefs(Context context) {
        p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public String serverUrl() {
        String u = p.getString("server", DEFAULT_SERVER);
        if (u == null || u.length() == 0) {
            return DEFAULT_SERVER;
        }
        if (u.endsWith("/")) {
            return u.substring(0, u.length() - 1);
        }
        return u;
    }

    public void setServerUrl(String url) {
        p.edit().putString("server", url == null ? DEFAULT_SERVER : url.trim()).apply();
    }

    public boolean isLoggedIn() {
        return schoolId() > 0 && p.getBoolean("logged_in", false);
    }

    public int schoolId() {
        return p.getInt("school_id", 0);
    }

    public String schoolName() {
        return p.getString("school_name", "");
    }

    public String schoolAcronym() {
        return p.getString("school_acronym", "");
    }

    public String schoolLogo() {
        return p.getString("school_logo", "");
    }

    public void saveBootstrap(JSONObject json) {
        SharedPreferences.Editor e = p.edit();
        e.putBoolean("logged_in", true);
        JSONObject school = json.optJSONObject("school");
        if (school != null) {
            e.putInt("school_id", school.optInt("id", 0));
            e.putString("school_name", school.optString("name", ""));
            e.putString("school_acronym", school.optString("acronym", ""));
            e.putString("school_logo", school.optString("logo", ""));
        }
        e.apply();
        saveDashboard(json);
    }

    public void saveDashboard(JSONObject json) {
        if (json == null) {
            return;
        }
        SharedPreferences.Editor e = p.edit();
        JSONObject kpi = json.optJSONObject("kpi");
        if (kpi != null) {
            e.putString("kpi", kpi.toString());
        }
        JSONArray recent = json.optJSONArray("recent");
        if (recent != null) {
            e.putString("recent", recent.toString());
        }
        e.apply();
    }

    public JSONObject kpi() {
        try {
            return new JSONObject(p.getString("kpi", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public JSONArray recent() {
        try {
            return new JSONArray(p.getString("recent", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public String lastClock(int staffId) {
        if (staffId <= 0) {
            return "";
        }
        String v = p.getString("clock_" + staffId, "");
        return v == null ? "" : v;
    }

    public void setLastClock(int staffId, String status) {
        if (staffId <= 0 || status == null || status.length() == 0) {
            return;
        }
        p.edit().putString("clock_" + staffId, status.toUpperCase()).apply();
    }

    public boolean consumeClockReset() {
        if (p.getInt("clock_reset", 0) >= 4) {
            return false;
        }
        p.edit().putInt("clock_reset", 4).apply();
        return true;
    }

    public boolean isClockAcked(String key) {
        if (key == null || key.length() == 0) {
            return false;
        }
        Set<String> cur = p.getStringSet("acked_clocks", null);
        return cur != null && cur.contains(key);
    }

    public void ackClock(String key) {
        if (key == null || key.length() == 0) {
            return;
        }
        Set<String> cur = new HashSet<String>();
        Set<String> stored = p.getStringSet("acked_clocks", null);
        if (stored != null) {
            cur.addAll(stored);
        }
        cur.add(key);
        p.edit().putStringSet("acked_clocks", cur).apply();
    }

    public void clearLastClocks() {
        SharedPreferences.Editor e = p.edit();
        for (String key : p.getAll().keySet()) {
            if (key != null && key.startsWith("clock_")) {
                e.remove(key);
            }
        }
        e.apply();
    }

    public void logout() {
        String server = serverUrl();
        p.edit().clear()
                .putString("server", server)
                .putInt("school_id", 0)
                .apply();
    }
}
