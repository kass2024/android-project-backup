package com.qonics.utils;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Staff accounts saved on the phone so login works after the sync finishes.
 */
public final class StaffLoginStore {
    private StaffLoginStore() {}

    /** Replaces the local staff list. Returns how many accounts were saved. */
    public static int save(SQLiteDatabase db, JSONObject object) {
        if (db == null || object == null) return 0;
        JSONArray accounts = object.optJSONArray("staff_accounts");
        if (accounts == null) return 0;
        db.execSQL("delete from tb_staff_login");
        db.execSQL("delete from tb_staff_course");
        db.execSQL("delete from tb_staff_class");
        int saved = 0;
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject row = accounts.optJSONObject(i);
            if (row == null) continue;
            String hash = row.optString("password_hash", "");
            if (!hash.startsWith("$2")) continue;
            db.execSQL("insert into tb_staff_login (id,school_id,email,phone,password_hash,name,post,post_title,status,menu_keys,app_menus,menu_full) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                    new Object[]{
                            row.optInt("id"),
                            row.optInt("school_id"),
                            row.optString("email", ""),
                            row.optString("phone", ""),
                            hash,
                            row.optString("name", ""),
                            row.optInt("post"),
                            row.optString("post_title", ""),
                            row.optInt("status"),
                            row.optJSONArray("menu_keys") == null ? "[]" : row.optJSONArray("menu_keys").toString(),
                            row.optJSONObject("app_menus") == null ? "{}" : row.optJSONObject("app_menus").toString(),
                            row.optBoolean("menu_full_access", false) || row.optInt("menu_full_access") == 1 ? 1 : 0
                    });
            saved++;
        }
        JSONArray courses = object.optJSONArray("staff_courses");
        if (courses != null) {
            for (int i = 0; i < courses.length(); i++) {
                JSONObject row = courses.optJSONObject(i);
                if (row == null) continue;
                db.execSQL("insert into tb_staff_course (staff_id,id,title,code,marks,class_id) values (?,?,?,?,?,?)",
                        new Object[]{
                                row.optInt("staff_id"),
                                row.optInt("id"),
                                row.optString("title", ""),
                                row.optString("code", ""),
                                row.optString("marks", "0"),
                                row.optInt("class_id")
                        });
            }
        }
        JSONArray classes = object.optJSONArray("staff_classes");
        if (classes != null) {
            for (int i = 0; i < classes.length(); i++) {
                JSONObject row = classes.optJSONObject(i);
                if (row == null) continue;
                String title = row.optString("title", "").trim().replaceAll("\\s+", " ");
                db.execSQL("insert into tb_staff_class (staff_id,id,title,is_nursery) values (?,?,?,?)",
                        new Object[]{
                                row.optInt("staff_id"),
                                row.optInt("id"),
                                title,
                                row.optInt("is_nursery")
                        });
            }
        }
        return saved;
    }

    public static void saveDevice(SharedPreferences prefs, JSONObject device) {
        if (prefs == null || device == null) return;
        prefs.edit()
                .putInt("device_term", device.optInt("soma_term", prefs.getInt("device_term", 0)))
                .putInt("device_term_number", device.optInt("soma_term_number", prefs.getInt("device_term_number", 0)))
                .putInt("device_academic", device.optInt("soma_academic", prefs.getInt("device_academic", 0)))
                .putString("device_academic_title", device.optString("soma_academic_title", prefs.getString("device_academic_title", "")))
                .putInt("device_use_period", device.optInt("soma_use_period", prefs.getInt("device_use_period", 0)))
                .putInt("staff_login_saved", 1)
                .apply();
    }
}
