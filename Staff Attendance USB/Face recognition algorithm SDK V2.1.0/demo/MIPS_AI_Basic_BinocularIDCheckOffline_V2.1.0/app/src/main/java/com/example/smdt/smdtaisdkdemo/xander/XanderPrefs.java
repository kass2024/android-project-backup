package com.example.smdt.smdtaisdkdemo.xander;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class XanderPrefs {
    public static final String DEFAULT_SERVER = "https://schoolmis.xanderglobalacademy.com";
    public static final String DEFAULT_LIC = "/sdcard/mipsLic/mipsAi.lic";
    private static final String NAME = "xander_gate";

    private final SharedPreferences p;

    public XanderPrefs(Context context) {
        p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        if (!p.getBoolean("hw_fix_v20", false)) {
            p.edit()
                    .putInt("cam_orient", 1)
                    .putInt("preview_rot", 90)
                    .putInt("rgb_id", 0)
                    .putInt("ir_id", 1)
                    .putBoolean("hw_fix_v13", true)
                    .putBoolean("hw_fix_v20", true)
                    .apply();
        }
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

    public String operatorName() {
        return p.getString("operator_name", "");
    }

    public int areaId() {
        return p.getInt("area_id", 0);
    }

    public String areaName() {
        return p.getString("area_name", "");
    }

    public String licPath() {
        String v = p.getString("lic_path", DEFAULT_LIC);
        return v == null || v.length() == 0 ? DEFAULT_LIC : v;
    }

    public void setLicPath(String path) {
        p.edit().putString("lic_path", path).apply();
    }

    public int cameraFacing() {
        return p.getInt("cam_facing", 0);
    }

    public void setCameraFacing(int facing) {
        p.edit().putInt("cam_facing", facing).apply();
    }

    public String schoolAcronym() {
        return p.getString("school_acronym", "");
    }

    public int rgbCameraId() {
        return p.getInt("rgb_id", 0);
    }

    public int irCameraId() {
        return p.getInt("ir_id", 0);
    }

    public void setCameraIds(int rgb, int ir) {
        p.edit().putInt("rgb_id", rgb).putInt("ir_id", ir).apply();
    }

    public void swapCameras() {
        int rgb = rgbCameraId();
        int ir = irCameraId();
        setCameraIds(ir, rgb);
    }

    public int cameraOrientation() {
        return p.getInt("cam_orient", 3);
    }

    public void setCameraOrientation(int o) {
        p.edit().putInt("cam_orient", o).apply();
    }

    public int previewRotation() {
        return p.getInt("preview_rot", 90);
    }

    public void setPreviewRotation(int deg) {
        int d = deg % 360;
        if (d < 0) {
            d += 360;
        }
        p.edit().putInt("preview_rot", d).apply();
    }

    public void flipPreview180() {
        setPreviewRotation(previewRotation() == 180 ? 0 : 180);
        int o = cameraOrientation();
        if (o == 1) {
            setCameraOrientation(3);
        } else if (o == 3) {
            setCameraOrientation(1);
        } else if (o == 0) {
            setCameraOrientation(2);
        } else if (o == 2) {
            setCameraOrientation(0);
        }
    }

    public String locationsJson() {
        return p.getString("locations", "[]");
    }

    public String staffJson() {
        return p.getString("staff", "[]");
    }

    public void setArea(int id, String name) {
        p.edit().putInt("area_id", id).putString("area_name", name == null ? "" : name).apply();
    }

    public void saveBootstrap(JSONObject json) {
        SharedPreferences.Editor e = p.edit();
        e.putBoolean("logged_in", true);
        JSONObject school = json.optJSONObject("school");
        if (school != null) {
            e.putInt("school_id", school.optInt("id", 0));
            e.putString("school_name", school.optString("name", ""));
            e.putString("school_acronym", school.optString("acronym", ""));
        }
        JSONObject op = json.optJSONObject("operator");
        if (op != null) {
            e.putString("operator_name", op.optString("name", ""));
            e.putInt("operator_id", op.optInt("id", 0));
        }
        JSONArray locs = json.optJSONArray("locations");
        e.putString("locations", locs == null ? "[]" : locs.toString());
        JSONArray staff = json.optJSONArray("staff");
        e.putString("staff", staff == null ? "[]" : staff.toString());
        if (areaId() <= 0 && locs != null && locs.length() > 0) {
            JSONObject first = locs.optJSONObject(0);
            if (first != null) {
                e.putInt("area_id", first.optInt("id", 0));
                e.putString("area_name", first.optString("name", ""));
            }
        }
        e.apply();
    }

    public void saveStaffJson(JSONArray staff) {
        p.edit().putString("staff", staff == null ? "[]" : staff.toString()).apply();
    }

    public void mapFace(int staffId, int dbIdx) {
        p.edit().putInt("face_staff_" + dbIdx, staffId).putInt("face_db_" + staffId, dbIdx).apply();
    }

    public int staffIdForFaceDb(int dbIdx) {
        return p.getInt("face_staff_" + dbIdx, 0);
    }

    public void mapFaceToken(int staffId, String token) {
        addFaceToken(staffId, token);
    }

    public void addFaceToken(int staffId, String token) {
        if (token == null || token.length() == 0) {
            return;
        }
        String cur = p.getString("fp_token_" + staffId, "");
        if (cur != null && cur.length() > 0) {
            String[] parts = cur.split("\n");
            for (int i = 0; i < parts.length; i++) {
                if (token.equals(parts[i])) {
                    p.edit().putInt("fp_rev_" + token.hashCode(), staffId).apply();
                    return;
                }
            }
            cur = cur + "\n" + token;
        } else {
            cur = token;
        }
        p.edit().putString("fp_token_" + staffId, cur).putInt("fp_rev_" + token.hashCode(), staffId).apply();
    }

    public void clearFaceTokens(int staffId) {
        String old = tokenForStaff(staffId);
        SharedPreferences.Editor e = p.edit().remove("fp_token_" + staffId).putInt("fp_web_" + staffId, 0);
        if (old != null && old.length() > 0) {
            String[] parts = old.split("\n");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null && parts[i].length() > 0) {
                    e.remove("fp_rev_" + parts[i].hashCode());
                }
            }
        }
        e.apply();
    }

    public void setStaffFaceEnrolled(int staffId, boolean enrolled) {
        try {
            JSONArray arr = new JSONArray(staffJson());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && o.optInt("id", 0) == staffId) {
                    o.put("face_enrolled", enrolled ? 1 : 0);
                }
            }
            saveStaffJson(arr);
        } catch (Exception ignored) {
        }
        setWebFaceFlag(staffId, enrolled);
    }

    public void setWebFaceFlag(int staffId, boolean enrolled) {
        p.edit().putInt("fp_web_" + staffId, enrolled ? 1 : 0).apply();
    }

    public boolean webFaceWasEnrolled(int staffId) {
        return p.getInt("fp_web_" + staffId, 0) == 1;
    }

    public String tokenForStaff(int staffId) {
        return p.getString("fp_token_" + staffId, "");
    }

    public java.util.Map<Integer, String> allFaceTokens() {
        java.util.Map<Integer, String> out = new java.util.LinkedHashMap<Integer, String>();
        java.util.Map<String, ?> all = p.getAll();
        for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
            String key = e.getKey();
            if (key == null || !key.startsWith("fp_token_")) {
                continue;
            }
            try {
                int id = Integer.parseInt(key.substring("fp_token_".length()));
                String val = String.valueOf(e.getValue());
                if (id > 0 && val != null && val.length() > 0) {
                    out.put(id, val);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public String staffName(int staffId) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(staffJson());
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o != null && o.optInt("id", 0) == staffId) {
                    String n = o.optString("name", "");
                    if (n.length() > 0) {
                        return n;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public boolean hasFaceToken(int staffId) {
        String t = tokenForStaff(staffId);
        return t != null && t.length() > 0;
    }

    public int staffIdForToken(String token) {
        if (token == null || token.length() == 0) {
            return 0;
        }
        int hashed = p.getInt("fp_rev_" + token.hashCode(), 0);
        if (hashed > 0 && tokenInStored(tokenForStaff(hashed), token)) {
            return hashed;
        }
        java.util.Map<String, ?> all = p.getAll();
        for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith("fp_token_")) {
                if (tokenInStored(String.valueOf(e.getValue()), token)) {
                    try {
                        return Integer.parseInt(e.getKey().substring("fp_token_".length()));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return 0;
    }

    public int staffIdForTokenBytes(byte[] token) {
        if (token == null || token.length == 0) {
            return 0;
        }
        int id = staffIdForToken(android.util.Base64.encodeToString(token, android.util.Base64.NO_WRAP));
        if (id > 0) {
            return id;
        }
        try {
            id = staffIdForToken(new String(token, "ISO-8859-1"));
            if (id > 0) {
                return id;
            }
        } catch (Exception ignored) {
        }
        return staffIdForToken(new String(token));
    }

    private static boolean tokenInStored(String stored, String token) {
        if (stored == null || token == null) {
            return false;
        }
        if (stored.equals(token)) {
            return true;
        }
        String[] parts = stored.split("\n");
        for (int i = 0; i < parts.length; i++) {
            if (token.equals(parts[i])) {
                return true;
            }
        }
        return false;
    }

    public void logout() {
        String server = serverUrl();
        String lic = licPath();
        p.edit().clear()
                .putString("server", server)
                .putString("lic_path", lic)
                .putInt("school_id", 0)
                .putBoolean("hw_fix_v13", true)
                .putInt("cam_orient", cameraOrientation())
                .putInt("preview_rot", previewRotation())
                .apply();
    }
}
