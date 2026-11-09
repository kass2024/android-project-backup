package com.example.smdt.smdtaisdkdemo.xander;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Offline copy of students + staff, plus a queue of scans to upload later.
 */
public class XanderStore {
    private final Helper helper;
    private final XanderPrefs prefs;

    public XanderStore(Context ctx, XanderPrefs prefs) {
        this.helper = new Helper(ctx.getApplicationContext());
        this.prefs = prefs;
    }

    public void replacePeople(JSONArray students, JSONArray staff) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("people", null, null);
            insertPeople(db, "student", students);
            insertPeople(db, "staff", staff);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void insertPeople(SQLiteDatabase db, String kind, JSONArray arr) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            ContentValues v = new ContentValues();
            v.put("kind", kind);
            v.put("person_id", o.optInt("id", 0));
            v.put("name", o.optString("name", ""));
            v.put("card", norm(o.optString("card", "")));
            v.put("card_raw", o.optString("card", ""));
            v.put("photo", o.optString("photo", ""));
            if ("student".equals(kind)) {
                v.put("extra", o.optString("class", ""));
                v.put("regno", o.optString("regno", ""));
            } else {
                v.put("extra", o.optString("post", ""));
                v.put("regno", o.optString("shift", ""));
            }
            db.insert("people", null, v);
        }
    }

    public JSONObject findByCard(String card) {
        String n = norm(card);
        if (n.length() == 0 && (card == null || card.trim().length() == 0)) {
            return null;
        }
        if (n.length() == 0 && card != null && card.trim().matches("[0-9]+")) {
            n = decimalToHex(card.trim());
        }
        String rev = reverseBytes(n);
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT kind, person_id, name, extra, regno, photo, card_raw FROM people WHERE card=? OR card=? OR card_raw=? OR card_raw=? LIMIT 1",
                new String[]{n, rev, card == null ? "" : card.trim(), n});
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            JSONObject o = new JSONObject();
            o.put("kind", c.getString(0));
            o.put("id", c.getInt(1));
            o.put("name", c.getString(2));
            o.put("extra", c.getString(3));
            o.put("regno", c.getString(4));
            o.put("photo", c.getString(5));
            o.put("card", c.getString(6));
            return o;
        } catch (Exception e) {
            return null;
        } finally {
            c.close();
        }
    }

    public JSONObject findStaff(int id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT name, extra, photo FROM people WHERE kind='staff' AND person_id=? LIMIT 1",
                new String[]{Integer.toString(id)});
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            JSONObject o = new JSONObject();
            o.put("kind", "staff");
            o.put("id", id);
            o.put("name", c.getString(0));
            o.put("extra", c.getString(1));
            o.put("photo", c.getString(2));
            return o;
        } catch (Exception e) {
            return null;
        } finally {
            c.close();
        }
    }

    public JSONArray listStaff() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT person_id, name, photo FROM people WHERE kind='staff'", null);
        JSONArray arr = new JSONArray();
        try {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getInt(0));
                o.put("name", c.getString(1));
                o.put("photo", c.getString(2));
                arr.put(o);
            }
        } catch (Exception ignored) {
        } finally {
            c.close();
        }
        return arr;
    }

    public boolean staffKnown(int id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT 1 FROM people WHERE kind='staff' AND person_id=? LIMIT 1",
                new String[]{Integer.toString(id)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public int studentCount() {
        return countKind("student");
    }

    public int staffCount() {
        return countKind("staff");
    }

    public int pendingCount() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM pending", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private int countKind(String kind) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM people WHERE kind=?", new String[]{kind});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /**
     * Local IN/OUT using last event of the day, then queue for upload.
     */
    public JSONObject clockLocal(String kind, int personId, int areaId, String card, String name, String extra, String photo, String scanType) {
        return clockLocal(kind, personId, areaId, card, name, extra, photo, scanType, "");
    }

    public JSONObject clockLocal(String kind, int personId, int areaId, String card, String name, String extra, String photo, String scanType, String forcedStatus) {
        long now = System.currentTimeMillis() / 1000L;
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String key = kind + ":" + personId + ":" + ("student".equals(kind) ? areaId : 0);
        SQLiteDatabase db = helper.getWritableDatabase();
        String lastDay = "";
        String lastStatus = "";
        int inCount = 0;
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT last_day, last_status, in_count FROM local_state WHERE k=?", new String[]{key});
            if (c.moveToFirst()) {
                lastDay = c.getString(0);
                lastStatus = c.getString(1);
                inCount = c.getInt(2);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (!day.equals(lastDay)) {
            lastStatus = "";
            inCount = 0;
        }
        if (inCount < 1 && lastStatus != null && lastStatus.length() > 0) {
            inCount = 1;
        }
        String status;
        boolean blocked = false;
        if ("staff".equals(kind)) {
            if (lastStatus == null || lastStatus.length() == 0) {
                status = "IN";
                inCount = 1;
            } else if ("IN".equals(lastStatus)) {
                status = "OUT";
            } else {
                if (inCount >= 2 && (forcedStatus == null || !"OUT".equalsIgnoreCase(forcedStatus))) {
                    status = "IN";
                    blocked = true;
                } else if (forcedStatus != null && ("IN".equalsIgnoreCase(forcedStatus) || "OUT".equalsIgnoreCase(forcedStatus))) {
                    status = forcedStatus.toUpperCase(Locale.US);
                    if ("IN".equals(status)) {
                        inCount++;
                    }
                } else {
                    status = "IN";
                    inCount++;
                }
            }
        } else {
            if (lastStatus == null || lastStatus.length() == 0) {
                status = "IN";
            } else if (forcedStatus != null && ("IN".equalsIgnoreCase(forcedStatus) || "OUT".equalsIgnoreCase(forcedStatus))) {
                status = forcedStatus.toUpperCase(Locale.US);
            } else if ("IN".equals(lastStatus)) {
                status = "OUT";
            } else {
                status = "IN";
            }
        }
        boolean skipQueue = blocked
                || ("IN".equals(status) && "IN".equals(lastStatus) && day.equals(lastDay));
        if (!blocked) {
            ContentValues st = new ContentValues();
            st.put("k", key);
            st.put("last_day", day);
            st.put("last_status", status);
            st.put("in_count", inCount);
            db.replace("local_state", null, st);
        }

        if (!skipQueue) {
            ContentValues p = new ContentValues();
            p.put("kind", "face".equals(scanType) ? "face" : "card");
            p.put("person_kind", kind);
            p.put("person_id", personId);
            p.put("card", card == null ? "" : card);
            p.put("area_id", areaId);
            p.put("event_time", now);
            p.put("wanted_status", status);
            db.insert("pending", null, p);
        }

        JSONObject json = new JSONObject();
        try {
            json.put("success", blocked ? 0 : 1);
            json.put("offline", 1);
            json.put("kind", kind);
            json.put("status", status);
            json.put("in_count", inCount);
            json.put("time", new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
            if (blocked) {
                json.put("message", "Already checked IN twice today");
            } else if ("OUT".equals(status) && "staff".equals(kind)) {
                json.put("message", "Already checked IN — now OUT");
            } else {
                json.put("message", ("staff".equals(kind) ? "Staff " : "Student ") + status + " (saved offline)");
            }
            JSONObject person = new JSONObject();
            person.put("id", personId);
            person.put("name", name == null ? "" : name);
            person.put("class", extra == null ? "" : extra);
            person.put("post", extra == null ? "" : extra);
            person.put("regno", extra == null ? "" : extra);
            person.put("photo", photo == null ? "" : photo);
            json.put("person", person);
        } catch (Exception ignored) {
        }
        return json;
    }

    public void enqueueFace(int staffId) {
        ContentValues p = new ContentValues();
        p.put("kind", "face");
        p.put("person_kind", "staff");
        p.put("person_id", staffId);
        p.put("card", "");
        p.put("area_id", 0);
        p.put("event_time", System.currentTimeMillis() / 1000L);
        p.put("wanted_status", "");
        helper.getWritableDatabase().insert("pending", null, p);
    }

    public void flush() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id, kind, person_kind, person_id, card, area_id, event_time, wanted_status FROM pending ORDER BY id ASC", null);
        try {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String kind = c.getString(1);
                int personId = c.getInt(3);
                String card = c.getString(4);
                int areaId = c.getInt(5);
                long time = c.getLong(6);
                String wanted = c.getColumnCount() > 7 ? c.getString(7) : "";
                try {
                    JSONObject json;
                    if ("face".equals(kind) || (card == null || card.length() == 0) && personId > 0 && "staff".equals(c.getString(2))) {
                        json = XanderApi.scanFace(prefs.serverUrl(), prefs.schoolId(), personId, time, wanted);
                    } else {
                        json = XanderApi.scanCard(prefs.serverUrl(), prefs.schoolId(), card, areaId, time);
                    }
                    if (XanderApi.ok(json) || XanderApi.message(json, "").toLowerCase(Locale.US).contains("already")) {
                        db.delete("pending", "id=?", new String[]{Integer.toString(id)});
                    }
                } catch (Exception e) {
                    break;
                }
            }
        } finally {
            c.close();
        }
    }

    public static String reverseBytes(String hex) {
        if (hex == null || hex.length() < 4 || hex.length() % 2 != 0) {
            return hex == null ? "" : hex;
        }
        StringBuilder b = new StringBuilder();
        for (int i = hex.length() - 2; i >= 0; i -= 2) {
            b.append(hex.substring(i, i + 2));
        }
        return b.toString();
    }

    public static String decimalToHex(String dec) {
        try {
            long v = Long.parseLong(dec);
            String h = Long.toHexString(v).toUpperCase(Locale.US);
            while (h.length() < 8) {
                h = "0" + h;
            }
            return h;
        } catch (Exception e) {
            return "";
        }
    }

    public static String norm(String card) {
        if (card == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < card.length(); i++) {
            char ch = card.charAt(i);
            if ((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F') || (ch >= 'a' && ch <= 'f')) {
                b.append(Character.toUpperCase(ch));
            }
        }
        return b.toString();
    }

    private static class Helper extends SQLiteOpenHelper {
        Helper(Context ctx) {
            super(ctx, "xander_gate.db", null, 2);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE people (kind TEXT, person_id INTEGER, name TEXT, card TEXT, card_raw TEXT, photo TEXT, extra TEXT, regno TEXT)");
            db.execSQL("CREATE INDEX idx_people_card ON people(card)");
            db.execSQL("CREATE TABLE local_state (k TEXT PRIMARY KEY, last_day TEXT, last_status TEXT, in_count INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE pending (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, person_kind TEXT, person_id INTEGER, card TEXT, area_id INTEGER, event_time INTEGER, wanted_status TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                try {
                    db.execSQL("ALTER TABLE local_state ADD COLUMN in_count INTEGER DEFAULT 0");
                } catch (Exception ignored) {
                }
            }
        }
    }
}
