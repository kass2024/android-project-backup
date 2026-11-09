package com.xander.student;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

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

    /** This device follows daily start times. Manual devices stay on the picked location. */
    public boolean locationFollowsSchedule() {
        return !"manual".equals(p.getString("location_follow", "auto"));
    }

    public void setLocationFollowsSchedule(boolean automatic) {
        p.edit().putString("location_follow", automatic ? "auto" : "manual").apply();
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
        int currentArea = p.getInt("area_id", 0);
        boolean stillThere = false;
        if (locs != null) {
            for (int i = 0; i < locs.length(); i++) {
                JSONObject loc = locs.optJSONObject(i);
                if (loc != null && loc.optInt("id", 0) == currentArea) {
                    stillThere = true;
                    String nm = loc.optString("name", "");
                    if (nm.length() > 0) {
                        e.putString("area_name", nm);
                    }
                    break;
                }
            }
        }
        if ((!stillThere || currentArea <= 0) && locs != null && locs.length() > 0) {
            JSONObject first = locs.optJSONObject(0);
            if (first != null && currentArea <= 0) {
                e.putInt("area_id", first.optInt("id", 0));
                e.putString("area_name", first.optString("name", ""));
            }
        }
        JSONObject dayClock = json.optJSONObject("day_clock");
        if (dayClock != null) {
            e.putString("day_clock", dayClock.toString());
        }
        String visitMode = json.optString("visit_mode", "normal");
        e.putString("visit_mode", "visiting".equalsIgnoreCase(visitMode) ? "visiting" : "normal");
        e.apply();
        applyScheduledLocation();
    }

    /** Keep the last downloaded location schedules for offline use. */
    public void saveLocations(JSONArray locs) {
        if (locs == null) {
            return;
        }
        p.edit().putString("locations", locs.toString()).apply();
        applyScheduledLocation();
    }

    /**
     * Move to the scheduled location whose daily start has been reached.
     * The latest start time already due today wins, and that location stays
     * until a later location's start time arrives.
     *
     * @return true when the active location changed
     */
    public boolean applyScheduledLocation() {
        if (!locationFollowsSchedule()) {
            return false;
        }
        JSONArray locs;
        try {
            locs = new JSONArray(locationsJson());
        } catch (Exception e) {
            return false;
        }
        Calendar cal = Calendar.getInstance();
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int bestMin = -1;
        int bestId = 0;
        String bestName = "";
        for (int i = 0; i < locs.length(); i++) {
            JSONObject loc = locs.optJSONObject(i);
            if (loc == null || !scheduleEnabled(loc)) {
                continue;
            }
            int start = clockMinutes(loc.optString("daily_start", ""));
            if (start < 0 || start > nowMin || start < bestMin) {
                continue;
            }
            int id = loc.optInt("id", 0);
            if (id <= 0) {
                continue;
            }
            bestMin = start;
            bestId = id;
            bestName = loc.optString("name", "");
        }
        if (bestId <= 0 || bestId == areaId()) {
            return false;
        }
        setArea(bestId, bestName == null ? "" : bestName);
        return true;
    }

    private static boolean scheduleEnabled(JSONObject loc) {
        Object value = loc.opt("schedule_on");
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static int clockMinutes(String hhmm) {
        if (hhmm == null) {
            return -1;
        }
        int colon = hhmm.indexOf(':');
        if (colon <= 0 || colon >= hhmm.length() - 1) {
            return -1;
        }
        try {
            int hour = Integer.parseInt(hhmm.substring(0, colon).trim());
            String minPart = hhmm.substring(colon + 1).trim();
            if (minPart.length() > 2) {
                minPart = minPart.substring(0, 2);
            }
            int minute = Integer.parseInt(minPart);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1;
            }
            return hour * 60 + minute;
        } catch (Exception e) {
            return -1;
        }
    }

    public String dayClockJson() {
        return p.getString("day_clock", "");
    }

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
