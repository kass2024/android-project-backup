package com.xander.student;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class XanderPrefs {
    public static final String DEFAULT_SERVER = "https://schoolmis.xanderglobalacademy.com";
    public static final String MODE_ATTENDANCE = "attendance";
    public static final String MODE_PARENT_VISIT = "parent_visit";
    public static final String MODE_AUTO = "auto";
    private static final String NAME = "xander_student";

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

    public int areaId() {
        return p.getInt("area_id", 0);
    }

    public String areaName() {
        return p.getString("area_name", "");
    }

    public String locationsJson() {
        return p.getString("locations", "[]");
    }

    public void setArea(int id, String name) {
        p.edit().putInt("area_id", id).putString("area_name", name == null ? "" : name).apply();
    }

    public String schoolLogo() {
        return p.getString("school_logo", "");
    }

    public String verificationMode() {
        // Always auto-detect student vs visitor from the card (same as web device_scan).
        return MODE_AUTO;
    }

    public boolean isParentVisitMode() {
        return false;
    }

    public boolean isAutoMode() {
        return true;
    }

    public void setVerificationMode(String mode) {
        p.edit().putString("verification_mode", MODE_AUTO).apply();
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
        JSONArray locs = json.optJSONArray("locations");
        e.putString("locations", locs == null ? "[]" : locs.toString());
        JSONObject dayClock = json.optJSONObject("day_clock");
        if (dayClock != null) {
            e.putString("day_clock", dayClock.toString());
        }
        String visitMode = json.optString("visit_mode", "normal");
        e.putString("visit_mode", "visiting".equalsIgnoreCase(visitMode) ? "visiting" : "normal");
        if (areaId() <= 0 && locs != null && locs.length() > 0) {
            JSONObject first = locs.optJSONObject(0);
            if (first != null) {
                e.putInt("area_id", first.optInt("id", 0));
                e.putString("area_name", first.optString("name", ""));
            }
        }
        e.apply();
    }

    public String dayClockJson() {
        return p.getString("day_clock", "");
    }

    /** School setting: visiting-only kiosk, or normal attendance plus visiting. */
    public boolean visitingOnly() {
        return "visiting".equals(p.getString("visit_mode", "normal"));
    }

    public void saveVisitMode(String mode) {
        p.edit().putString("visit_mode", "visiting".equalsIgnoreCase(mode) ? "visiting" : "normal").apply();
    }

    public void logout() {
        String server = serverUrl();
        p.edit().clear()
                .putString("server", server)
                .putInt("school_id", 0)
                .apply();
    }

    /** Remember DK-scanned UID for a visitor so sync cannot wipe local-only card form. */
    public void setVisitorCardAlias(int visitorId, String card) {
        if (visitorId <= 0 || card == null || card.length() < 4) {
            return;
        }
        p.edit().putString("visitor_alias_" + visitorId, card.trim().toUpperCase()).apply();
    }

    public String getVisitorCardAlias(int visitorId) {
        if (visitorId <= 0) {
            return "";
        }
        String a = p.getString("visitor_alias_" + visitorId, "");
        return a == null ? "" : a;
    }
}
