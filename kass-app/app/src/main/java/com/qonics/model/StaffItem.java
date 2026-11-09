package com.qonics.model;

import org.json.JSONObject;

public class StaffItem {
    public int id;
    public String fname = "";
    public String lname = "";
    public String name = "";
    public String phone = "";
    public String email = "";
    public String postTitle = "";
    public String statusLabel = "";
    public int status;
    public int taughtCourses;
    public int taughtPeriods;

    public static StaffItem fromJson(JSONObject o) {
        StaffItem s = new StaffItem();
        if (o == null) {
            return s;
        }
        s.id = o.optInt("id", 0);
        s.fname = o.optString("fname", "");
        s.lname = o.optString("lname", "");
        s.name = o.optString("name", (s.fname + " " + s.lname).trim());
        s.phone = o.optString("phone", "");
        s.email = o.optString("email", "");
        s.postTitle = o.optString("post_title", "");
        s.status = o.optInt("status", 1);
        s.statusLabel = o.optString("status_label",
                (s.status == 1 || s.status == 2) ? "Active" : "Locked");
        s.taughtCourses = o.optInt("taught_courses", 0);
        s.taughtPeriods = o.optInt("taught_periods", 0);
        if (s.name == null || s.name.trim().isEmpty()) {
            s.name = (s.fname + " " + s.lname).trim();
        }
        return s;
    }

    public boolean matches(String q) {
        if (q == null || q.trim().isEmpty()) {
            return true;
        }
        String n = q.toLowerCase();
        return contains(name, n) || contains(phone, n) || contains(email, n) || contains(postTitle, n)
                || contains(fname, n) || contains(lname, n);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }
}
