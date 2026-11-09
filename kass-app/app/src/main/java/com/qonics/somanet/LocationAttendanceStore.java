package com.qonics.somanet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.qonics.dbhelper.DatabaseHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Offline location attendance. Same IN / OUT rules as the USB kiosk.
 */
public class LocationAttendanceStore {
    public static final long CHECKOUT_AFTER_IN_SECONDS = 600L;
    public static final int CAFETERIA_EAT_MIN = 60000;

    private final SQLiteDatabase db;
    private final Map<String, Student> cardIndex = new HashMap<>();

    public LocationAttendanceStore(Context context) {
        db = new DatabaseHandler(context.getApplicationContext()).getWritableDatabase();
    }

    public void replaceRules(JSONArray rules) {
        db.beginTransaction();
        try {
            db.delete("tb_loc_fee", null, null);
            if (rules != null) {
                for (int i = 0; i < rules.length(); i++) {
                    JSONObject row = rules.optJSONObject(i);
                    if (row == null || row.optInt("id") <= 0) continue;
                    ContentValues v = new ContentValues();
                    v.put("student_id", row.optInt("id"));
                    v.put("studying_mode", row.optInt("studying_mode", 1));
                    v.put("feeding_amount", row.optInt("feeding_amount", 0));
                    v.put("transport_amount", row.optInt("transport_amount", 0));
                    v.put("transport_required", row.optInt("transport_required", 0));
                    db.insert("tb_loc_fee", null, v);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void replaceLocations(JSONArray locations) {
        db.beginTransaction();
        try {
            db.delete("tb_loc_area", null, null);
            if (locations != null) {
                for (int i = 0; i < locations.length(); i++) {
                    JSONObject row = locations.optJSONObject(i);
                    if (row == null || row.optInt("id") <= 0) continue;
                    ContentValues v = new ContentValues();
                    v.put("id", row.optInt("id"));
                    v.put("name", row.optString("name", ""));
                    db.insert("tb_loc_area", null, v);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public JSONArray locations() {
        JSONArray out = new JSONArray();
        Cursor c = db.rawQuery("SELECT id, name FROM tb_loc_area ORDER BY name COLLATE NOCASE", null);
        while (c.moveToNext()) {
            JSONObject row = new JSONObject();
            try {
                row.put("id", c.getInt(0));
                row.put("name", c.getString(1));
                out.put(row);
            } catch (Exception ignored) {
            }
        }
        c.close();
        return out;
    }

    /** Apply today's server clocks unless this phone still has an unsynced tap. */
    public void mergeToday(JSONArray rows) {
        if (rows == null) return;
        String day = today();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            int studentId = row.optInt("student_id");
            int areaId = row.optInt("area_id");
            if (studentId <= 0 || areaId <= 0) continue;
            if (pendingFor(studentId, areaId)) continue;
            long time = row.optLong("time_out") > 0 ? row.optLong("time_out") : row.optLong("time_in");
            ContentValues v = new ContentValues();
            v.put("student_id", studentId);
            v.put("area_id", areaId);
            v.put("day", row.optString("day", day));
            v.put("status", row.optString("status", "IN"));
            v.put("event_time", time);
            db.insertWithOnConflict("tb_loc_clock", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    public JSONArray pendingEvents() {
        JSONArray out = new JSONArray();
        Cursor c = db.rawQuery(
                "SELECT id, student_id, area_id, card, event_time, status FROM tb_loc_pending WHERE sync=1 ORDER BY id",
                null);
        while (c.moveToNext()) {
            JSONObject row = new JSONObject();
            try {
                row.put("local_id", c.getInt(0));
                row.put("student_id", c.getInt(1));
                row.put("area_id", c.getInt(2));
                row.put("card", c.getString(3));
                row.put("time", c.getLong(4));
                row.put("status", c.getString(5));
                out.put(row);
            } catch (Exception ignored) {
            }
        }
        c.close();
        return out;
    }

    public void markSynced(int localId) {
        if (localId <= 0) return;
        ContentValues v = new ContentValues();
        v.put("sync", 0);
        db.update("tb_loc_pending", v, "id=?", new String[]{String.valueOf(localId)});
    }

    public int pendingCount() {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM tb_loc_pending WHERE sync=1", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    /** One pass over the local roster so each tap is a memory lookup. */
    public void loadCardIndex() {
        cardIndex.clear();
        Cursor c = db.rawQuery(
                "SELECT s.id, s.name, s.regno, s.classe, IFNULL(s.studying_mode,-1), s.card, IFNULL(s.photo,''), "
                        + "f.studying_mode, f.feeding_amount, f.transport_amount, f.transport_required "
                        + "FROM tb_student s LEFT JOIN tb_loc_fee f ON f.student_id = s.id "
                        + "WHERE s.card IS NOT NULL AND TRIM(s.card)<>'' AND IFNULL(s.status,1)<>0",
                null);
        while (c.moveToNext()) {
            Student s = new Student();
            s.id = c.getInt(0);
            s.name = c.getString(1) == null ? "" : c.getString(1);
            s.regno = c.getString(2) == null ? "" : c.getString(2);
            s.classe = c.getString(3) == null ? "" : c.getString(3);
            s.mode = c.isNull(4) ? -1 : c.getInt(4);
            s.photo = c.getString(6) == null ? "" : c.getString(6);
            s.feesKnown = !c.isNull(8);
            if (s.feesKnown) {
                if (!c.isNull(7)) s.mode = c.getInt(7);
                s.feedingAmount = c.getInt(8);
                s.transportAmount = c.isNull(9) ? 0 : c.getInt(9);
                s.transportRequired = c.isNull(10) ? 0 : c.getInt(10);
            }
            String stored = c.getString(5);
            for (String variant : NFCForegroundUtil.lookupVariants(stored)) {
                if (variant != null && variant.length() > 0) {
                    cardIndex.put(variant.toUpperCase(Locale.US), s);
                }
            }
        }
        c.close();
    }

    public Student findStudent(String cardId) {
        if (cardId == null) return null;
        if (cardIndex.isEmpty()) loadCardIndex();
        for (String variant : NFCForegroundUtil.lookupVariants(cardId)) {
            if (variant == null || variant.length() == 0) continue;
            Student hit = cardIndex.get(variant.toUpperCase(Locale.US));
            if (hit != null) return hit;
        }
        return null;
    }

    public Result clock(Student student, int areaId, String areaName, String card) {
        Result result = new Result();
        result.name = student.name;
        result.classe = student.classe;
        String place = norm(areaName);
        boolean dorm = place.contains("dorm") || place.contains("hostel");
        boolean cafe = place.contains("cafeteria") || place.contains("cafetria")
                || place.contains("canteen") || place.contains("dining") || place.contains("refectory");
        boolean gate = place.equals("gate") || place.equals("school gate")
                || place.contains("school gate") || place.endsWith(" gate");
        boolean transport = place.contains("transport");
        if (gate && student.mode == 0) {
            return deny(result, "NOT ALLOWED", "DAY SCHOLARS ONLY");
        }
        if (dorm && student.mode == 1) {
            return deny(result, "NOT ALLOWED", "BOARDING ONLY");
        }
        boolean rulesLoaded = rulesLoaded();
        boolean mayEat = student.mode == 0
                || (rulesLoaded && student.feesKnown && student.feedingAmount >= CAFETERIA_EAT_MIN);
        if (cafe && rulesLoaded && !mayEat) {
            return deny(result, "NOT ALLOWED", "FEEDING NOT PAID");
        }
        boolean mayRide = student.mode != 1
                || (rulesLoaded && student.feesKnown && student.transportRequired > 0
                && student.transportAmount >= student.transportRequired);
        if (transport && rulesLoaded && student.mode == 1 && !mayRide) {
            return deny(result, "NOT ALLOWED", "TRANSPORT NOT PAID");
        }
        boolean stayIn = cafe || dorm;
        long now = System.currentTimeMillis() / 1000L;
        String day = today();
        Clock last = loadClock(student.id, areaId);
        if (!day.equals(last.day)) {
            last.status = "";
            last.time = 0;
        }
        boolean already = false;
        String status;
        if (stayIn) {
            status = "IN";
            if ("IN".equals(last.status)) {
                already = true;
                if (last.time <= 0) last.time = now;
            }
        } else if (last.status == null || last.status.length() == 0) {
            status = "IN";
        } else if ("IN".equals(last.status)) {
            if (last.time <= 0) last.time = now;
            if (now - last.time < CHECKOUT_AFTER_IN_SECONDS) {
                already = true;
                status = "IN";
            } else {
                status = "OUT";
            }
        } else {
            status = "OUT";
        }
        long savedTime = already ? last.time : now;
        saveClock(student.id, areaId, day, status, savedTime);
        if (!already) {
            ContentValues p = new ContentValues();
            p.put("student_id", student.id);
            p.put("area_id", areaId);
            p.put("card", card == null ? "" : card);
            p.put("event_time", savedTime);
            p.put("status", status);
            p.put("sync", 1);
            db.insert("tb_loc_pending", null, p);
        }
        result.ok = true;
        result.status = status;
        result.already = !stayIn && already;
        result.timeLabel = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(savedTime * 1000L));
        String who = student.classe.length() > 0 ? student.classe : student.regno;
        if (cafe && mayEat) {
            result.sound = "eat";
            result.headline = "ALLOWED TO EAT";
            result.detail = (student.mode == 0 ? "BOARDING STUDENT" : "FEEDING 60,000 OR MORE")
                    + (who.length() > 0 ? "  ·  " + who : "");
        } else if (transport && student.mode == 1 && mayRide) {
            result.sound = "ride";
            result.headline = "ALLOWED TO RIDE";
            result.detail = "TRANSPORT PAID" + (who.length() > 0 ? "  ·  " + who : "");
        } else if (result.already) {
            result.sound = "already";
            result.headline = "ALREADY IN";
            result.detail = "IN  " + result.timeLabel + (who.length() > 0 ? "  ·  " + who : "");
        } else if ("OUT".equals(status)) {
            result.sound = "goodbye";
            result.headline = "GOODBYE";
            result.detail = "OUT  " + result.timeLabel + (who.length() > 0 ? "  ·  " + who : "");
        } else {
            result.sound = "welcome";
            result.headline = "WELCOME";
            result.detail = "IN  " + result.timeLabel + (who.length() > 0 ? "  ·  " + who : "");
        }
        result.message = result.headline;
        return result;
    }

    private Result deny(Result result, String headline, String detail) {
        result.denied = true;
        result.sound = "alert";
        result.headline = headline;
        result.detail = detail + (result.name.length() > 0 ? "  ·  " + result.name : "");
        result.message = headline;
        return result;
    }

    private boolean rulesLoaded() {
        Cursor c = db.rawQuery("SELECT 1 FROM tb_loc_fee LIMIT 1", null);
        boolean yes = c.moveToFirst();
        c.close();
        return yes;
    }

    private boolean pendingFor(int studentId, int areaId) {
        Cursor c = db.rawQuery(
                "SELECT 1 FROM tb_loc_pending WHERE sync=1 AND student_id=? AND area_id=? LIMIT 1",
                new String[]{String.valueOf(studentId), String.valueOf(areaId)});
        boolean yes = c.moveToFirst();
        c.close();
        return yes;
    }

    private Clock loadClock(int studentId, int areaId) {
        Clock clock = new Clock();
        Cursor c = db.rawQuery(
                "SELECT day, status, event_time FROM tb_loc_clock WHERE student_id=? AND area_id=?",
                new String[]{String.valueOf(studentId), String.valueOf(areaId)});
        if (c.moveToFirst()) {
            clock.day = c.getString(0) == null ? "" : c.getString(0);
            clock.status = c.getString(1) == null ? "" : c.getString(1);
            clock.time = c.isNull(2) ? 0 : c.getLong(2);
        }
        c.close();
        return clock;
    }

    private void saveClock(int studentId, int areaId, String day, String status, long time) {
        ContentValues v = new ContentValues();
        v.put("student_id", studentId);
        v.put("area_id", areaId);
        v.put("day", day);
        v.put("status", status);
        v.put("event_time", time);
        db.insertWithOnConflict("tb_loc_clock", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String norm(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static class Student {
        public int id;
        public String name = "";
        public String regno = "";
        public String classe = "";
        public String photo = "";
        public int mode = -1;
        public boolean feesKnown;
        public int feedingAmount = -1;
        public int transportAmount = -1;
        public int transportRequired = -1;
    }

    public static class Result {
        public boolean ok;
        public boolean denied;
        public boolean already;
        public String status = "";
        public String message = "";
        public String sound = "";
        public String headline = "";
        public String detail = "";
        public String name = "";
        public String classe = "";
        public String timeLabel = "";
    }

    private static class Clock {
        String day = "";
        String status = "";
        long time;
    }
}
