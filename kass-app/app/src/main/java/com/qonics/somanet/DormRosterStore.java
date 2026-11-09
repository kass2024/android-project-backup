package com.qonics.somanet;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Dormitory roll-call lists kept on the phone for offline marking. */
final class DormRosterStore {
    private DormRosterStore() {}

    static void replaceAll(SQLiteDatabase db, JSONArray dorms, String date) {
        if (db == null || dorms == null) return;
        String day = date == null || date.length() < 8
                ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())
                : date;
        db.beginTransaction();
        try {
            db.execSQL("delete from tb_dorm_sheet");
            for (int i = 0; i < dorms.length(); i++) {
                JSONObject row = dorms.optJSONObject(i);
                if (row == null) continue;
                int hostelId = row.optInt("id", 0);
                if (hostelId < 1) continue;
                JSONArray students = row.optJSONArray("students");
                JSONArray present = row.optJSONArray("present");
                db.execSQL(
                        "insert or replace into tb_dorm_sheet (hostel_id,name,in_charge,students_json,present_json,taken,roster_date) values (?,?,?,?,?,?,?)",
                        new Object[]{
                                hostelId,
                                row.optString("name", ""),
                                row.optInt("in_charge_staff_id", 0),
                                students == null ? "[]" : students.toString(),
                                present == null ? "[]" : present.toString(),
                                row.optBoolean("taken", false) ? 1 : 0,
                                day
                        });
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    static void saveOne(SQLiteDatabase db, int hostelId, String name, JSONArray students, JSONArray present, boolean taken, String date) {
        if (db == null || hostelId < 1) return;
        String day = date == null || date.length() < 8
                ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())
                : date;
        db.execSQL(
                "insert or replace into tb_dorm_sheet (hostel_id,name,in_charge,students_json,present_json,taken,roster_date) values (?,?,?,?,?,?,?)",
                new Object[]{
                        hostelId,
                        name == null ? "" : name,
                        0,
                        students == null ? "[]" : students.toString(),
                        present == null ? "[]" : present.toString(),
                        taken ? 1 : 0,
                        day
                });
    }

    static JSONObject read(SQLiteDatabase db, int hostelId) {
        if (db == null || hostelId < 1) return null;
        Cursor c = db.rawQuery(
                "select name, students_json, present_json, taken, roster_date from tb_dorm_sheet where hostel_id=?",
                new String[]{String.valueOf(hostelId)});
        JSONObject out = null;
        if (c.moveToFirst()) {
            out = new JSONObject();
            try {
                out.put("name", c.getString(0));
                out.put("students", new JSONArray(c.getString(1) == null ? "[]" : c.getString(1)));
                out.put("present", new JSONArray(c.getString(2) == null ? "[]" : c.getString(2)));
                out.put("taken", c.getInt(3) == 1);
                out.put("date", c.getString(4));
            } catch (Exception ignored) {
                out = null;
            }
        }
        c.close();
        return out;
    }
}
