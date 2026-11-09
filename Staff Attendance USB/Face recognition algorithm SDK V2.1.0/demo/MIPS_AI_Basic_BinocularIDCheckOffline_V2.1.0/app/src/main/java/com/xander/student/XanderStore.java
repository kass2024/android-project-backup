package com.xander.student;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Local copy of staff records already assigned RFID cards in the school DB,
 * plus a queue of taps to upload through /api/gate_staff_scan.
 */
public class XanderStore {
    private static final String TAG = "XanderStore";
    private final Helper helper;
    private final XanderPrefs prefs;

    public XanderStore(Context ctx, XanderPrefs prefs) {
        this.helper = new Helper(ctx.getApplicationContext());
        this.prefs = prefs;
    }

    public void replaceStaff(JSONArray staff) {
        SQLiteDatabase db = helper.getWritableDatabase();
        Map<Integer, long[]> clocks = loadClocks(db);
        Map<Integer, String> clockDays = loadClockDays(db);
        db.beginTransaction();
        try {
            db.delete("people", null, null);
            if (staff != null) {
                for (int i = 0; i < staff.length(); i++) {
                    JSONObject o = staff.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    String card = CardUid.cleanHexOnly(o.optString("card", ""));
                    if (card.length() < 4) {
                        card = CardUid.cleanRaw(o.optString("card", ""));
                    }
                    int pid = o.optInt("id", 0);
                    ContentValues v = new ContentValues();
                    v.put("person_id", pid);
                    v.put("name", o.optString("name", ""));
                    v.put("card", card);
                    v.put("card_raw", card.length() >= 4 ? CardUid.reverseBytes(card) : card);
                    v.put("photo", o.optString("photo", ""));
                    v.put("extra", o.optString("post", ""));
                    v.put("regno", o.optString("shift", ""));
                    v.put("shift_options", o.optString("shift_options", ""));
                    long[] ck = clocks.get(pid);
                    if (ck != null) {
                        v.put("clock_in", ck[0]);
                        v.put("clock_out", ck[1]);
                    } else {
                        v.put("clock_in", 0);
                        v.put("clock_out", 0);
                    }
                    String day = clockDays.get(pid);
                    v.put("clock_day", day == null ? "" : day);
                    db.insert("people", null, v);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static Map<Integer, long[]> loadClocks(SQLiteDatabase db) {
        Map<Integer, long[]> map = new HashMap<Integer, long[]>();
        Cursor c = db.rawQuery("SELECT person_id, clock_in, clock_out FROM people", null);
        try {
            while (c.moveToNext()) {
                map.put(c.getInt(0), new long[]{c.getLong(1), c.getLong(2)});
            }
        } catch (Exception ignored) {
        } finally {
            c.close();
        }
        return map;
    }

    private static Map<Integer, String> loadClockDays(SQLiteDatabase db) {
        Map<Integer, String> map = new HashMap<Integer, String>();
        Cursor c = db.rawQuery("SELECT person_id, clock_day FROM people", null);
        try {
            while (c.moveToNext()) {
                map.put(c.getInt(0), c.getString(1) == null ? "" : c.getString(1));
            }
        } catch (Exception ignored) {
        } finally {
            c.close();
        }
        return map;
    }

    public void saveClock(int id, long in, long out, String day) {
        if (id <= 0) {
            return;
        }
        ContentValues v = new ContentValues();
        v.put("clock_in", in);
        v.put("clock_out", out);
        v.put("clock_day", day == null ? "" : day);
        helper.getWritableDatabase().update("people", v, "person_id=?", new String[]{Integer.toString(id)});
    }

    public void clearClocks() {
        ContentValues v = new ContentValues();
        v.put("clock_in", 0);
        v.put("clock_out", 0);
        v.put("clock_day", "");
        helper.getWritableDatabase().update("people", v, null, null);
        helper.getWritableDatabase().delete("pending", null, null);
    }

    public void replaceBlockedCards(JSONArray students, JSONArray visitors) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("blocked", null, null);
            insertBlocked(db, "student", students, "card");
            insertBlocked(db, "visitor", visitors, "card");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void insertBlocked(SQLiteDatabase db, String type, JSONArray arr, String key) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String card = CardUid.cleanHexOnly(o.optString(key, ""));
            if (card.length() < 4) {
                continue;
            }
            ContentValues v = new ContentValues();
            v.put("kind", type);
            v.put("card", card);
            v.put("card_rev", CardUid.reverseBytes(card));
            db.insert("blocked", null, v);
        }
    }

    public JSONArray listStaff() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT person_id, name, photo FROM people", null);
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

    public JSONObject findStaffByName(String name) {
        if (name == null) {
            return null;
        }
        String needle = name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
        if (needle.length() < 2) {
            return null;
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery(staffSelect() + " FROM people", null);
        try {
            JSONObject fallback = null;
            while (c.moveToNext()) {
                String stored = c.getString(1);
                if (stored == null) {
                    continue;
                }
                String norm = stored.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
                if (norm.equals(needle)) {
                    return staffRow(c);
                }
                if (fallback == null && (norm.contains(needle) || needle.contains(norm))) {
                    fallback = staffRow(c);
                }
            }
            return fallback;
        } catch (Exception ignored) {
        } finally {
            c.close();
        }
        return null;
    }

    private static String staffSelect() {
        return "SELECT person_id, name, extra, regno, photo, card, shift_options, clock_in, clock_out, clock_day";
    }

    private static JSONObject staffRow(Cursor c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("kind", "staff");
        o.put("id", c.getInt(0));
        o.put("name", c.getString(1));
        o.put("post", c.getString(2) == null ? "" : c.getString(2));
        o.put("shift", c.getString(3) == null ? "" : c.getString(3));
        o.put("photo", c.getString(4) == null ? "" : c.getString(4));
        o.put("card", c.getString(5) == null ? "" : c.getString(5));
        o.put("card_stored", o.optString("card", ""));
        o.put("shift_options", c.getString(6) == null ? "" : c.getString(6));
        o.put("clock_in", c.getLong(7));
        o.put("clock_out", c.getLong(8));
        o.put("clock_day", c.getString(9) == null ? "" : c.getString(9));
        return o;
    }

    public JSONObject findStaffByCard(String raw) {
        String[] variants = CardUid.lookupVariants(raw);
        SQLiteDatabase db = helper.getReadableDatabase();
        for (int i = 0; i < variants.length; i++) {
            String n = variants[i];
            if (n == null || n.length() < 4) {
                continue;
            }
            Cursor c = db.rawQuery(
                    staffSelect() + " FROM people WHERE card=? OR card_raw=? LIMIT 1",
                    new String[]{n, n});
            try {
                if (c.moveToFirst()) {
                    return staffRow(c);
                }
            } catch (Exception ignored) {
            } finally {
                c.close();
            }
        }
        return null;
    }

    public JSONObject findStaffById(int id) {
        if (id <= 0) {
            return null;
        }
        Cursor c = helper.getReadableDatabase().rawQuery(
                staffSelect() + " FROM people WHERE person_id=? LIMIT 1",
                new String[]{Integer.toString(id)});
        try {
            if (c.moveToFirst()) {
                return staffRow(c);
            }
        } catch (Exception ignored) {
        } finally {
            c.close();
        }
        return null;
    }

    public String blockedKind(String raw) {
        String[] variants = CardUid.lookupVariants(raw);
        SQLiteDatabase db = helper.getReadableDatabase();
        for (int i = 0; i < variants.length; i++) {
            String n = variants[i];
            if (n == null || n.length() < 4) {
                continue;
            }
            Cursor c = db.rawQuery(
                    "SELECT kind FROM blocked WHERE card=? OR card_rev=? LIMIT 1",
                    new String[]{n, n});
            try {
                if (c.moveToFirst()) {
                    return c.getString(0);
                }
            } finally {
                c.close();
            }
        }
        return "";
    }

    public int staffCount() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM people", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int staffWithCards() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM people WHERE LENGTH(card) >= 4", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
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

    public void enqueueCard(String card, long time) {
        enqueueCard(card, time, "");
    }

    public void enqueueCard(String card, long time, String status) {
        if (card == null || card.length() < 4) {
            return;
        }
        ContentValues p = new ContentValues();
        p.put("card", card);
        p.put("event_time", time > 0 ? time : System.currentTimeMillis() / 1000L);
        p.put("wanted_status", status == null ? "" : status);
        helper.getWritableDatabase().insert("pending", null, p);
    }

    public int flush() {
        requeueTodayClocks();
        SQLiteDatabase db = helper.getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id, card, event_time, wanted_status FROM pending ORDER BY id ASC", null);
        int sent = 0;
        try {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String card = c.getString(1);
                long time = c.getLong(2);
                String wanted = c.getColumnCount() > 3 ? c.getString(3) : "";
                if (wanted == null) {
                    wanted = "";
                }
                try {
                    JSONObject json = postClock(card, time, wanted);
                    String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
                    if (doneMessage(json, msg)) {
                        db.delete("pending", "id=?", new String[]{Integer.toString(id)});
                        prefs.ackClock(ackKey(card, time, wanted));
                        sent++;
                        Log.i(TAG, "uploaded " + wanted + " " + card + " " + msg);
                    } else {
                        Log.w(TAG, "keep pending " + wanted + " " + card + " " + msg);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "flush: " + e.getMessage());
                    break;
                }
            }
        } finally {
            c.close();
        }
        return sent;
    }

    /** Put today's on-screen clocks back in the upload queue if a tap was shown locally and then dropped. */
    private void requeueTodayClocks() {
        String today = StaffClock.dayKey(System.currentTimeMillis());
        SQLiteDatabase db = helper.getWritableDatabase();
        Cursor c = db.rawQuery("SELECT name, card, clock_in, clock_out, clock_day FROM people", null);
        int clocks = 0;
        try {
            while (c.moveToNext()) {
                String name = c.getString(0);
                String card = c.getString(1);
                long in = c.getLong(2);
                long out = c.getLong(3);
                String day = c.getString(4) == null ? "" : c.getString(4);
                if (in > 0 || out > 0) {
                    Log.i(TAG, "local " + name + " day=" + day + " in=" + in + " out=" + out);
                }
                if (card == null || card.length() < 4 || in <= 0 || !today.equals(day)) {
                    continue;
                }
                clocks++;
                ensurePending(db, card, in, "IN");
                if (out > 0) {
                    ensurePending(db, card, out, "OUT");
                }
            }
        } finally {
            c.close();
        }
        Log.i(TAG, "replay clocks today=" + today + " people=" + clocks + " pending=" + pendingCount());
    }

    private void ensurePending(SQLiteDatabase db, String card, long time, String status) {
        if (prefs.isClockAcked(ackKey(card, time, status))) {
            return;
        }
        Cursor c = db.rawQuery(
                "SELECT id FROM pending WHERE card=? AND event_time=? AND ifnull(wanted_status,'')=? LIMIT 1",
                new String[]{card, Long.toString(time), status});
        boolean exists = false;
        try {
            exists = c.moveToFirst();
        } finally {
            c.close();
        }
        if (exists) {
            return;
        }
        ContentValues p = new ContentValues();
        p.put("card", card);
        p.put("event_time", time);
        p.put("wanted_status", status);
        db.insert("pending", null, p);
        Log.i(TAG, "requeued " + status + " " + card + " @" + time);
    }

    private JSONObject postClock(String card, long time, String wanted) throws Exception {
        JSONObject json = XanderApi.scanStaffCard(prefs.serverUrl(), prefs.schoolId(), card, time, wanted);
        String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
        if (!msg.contains("staff card not found")) {
            return json;
        }
        String alt = CardUid.reverseBytes(card);
        if (alt == null || alt.length() < 4 || alt.equalsIgnoreCase(card)) {
            return json;
        }
        JSONObject altJson = XanderApi.scanStaffCard(prefs.serverUrl(), prefs.schoolId(), alt, time, wanted);
        String altMsg = XanderApi.message(altJson, "").toLowerCase(Locale.US);
        if (doneMessage(altJson, altMsg) && !altMsg.contains("staff card not found")) {
            return altJson;
        }
        return json;
    }

    private static boolean doneMessage(JSONObject json, String msg) {
        if (msg == null) {
            msg = "";
        }
        if (msg.contains("waiting for checkout") || msg.contains("not allowed")) {
            return false;
        }
        return XanderApi.ok(json)
                || msg.contains("already")
                || msg.contains("student card")
                || msg.contains("visitor card")
                || msg.contains("parent visitor")
                || msg.contains("staff card not found")
                || msg.contains("not a working day");
    }

    private static String ackKey(String card, long time, String status) {
        return (card == null ? "" : card) + "|" + time + "|" + (status == null ? "" : status);
    }

    public void warmIndex() {
        // Card lookup is SQL; kept for call-site compatibility.
    }

    private static class Helper extends SQLiteOpenHelper {
        Helper(Context ctx) {
            super(ctx, "xander_staff.db", null, 2);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE people (person_id INTEGER, name TEXT, card TEXT, card_raw TEXT, photo TEXT, extra TEXT, regno TEXT, shift_options TEXT, clock_in INTEGER, clock_out INTEGER, clock_day TEXT)");
            db.execSQL("CREATE INDEX idx_people_card ON people(card)");
            db.execSQL("CREATE TABLE blocked (kind TEXT, card TEXT, card_rev TEXT)");
            db.execSQL("CREATE TABLE pending (id INTEGER PRIMARY KEY AUTOINCREMENT, card TEXT, event_time INTEGER, wanted_status TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                try {
                    db.execSQL("ALTER TABLE people ADD COLUMN shift_options TEXT");
                } catch (Exception ignored) {
                }
                try {
                    db.execSQL("ALTER TABLE people ADD COLUMN clock_in INTEGER");
                } catch (Exception ignored) {
                }
                try {
                    db.execSQL("ALTER TABLE people ADD COLUMN clock_out INTEGER");
                } catch (Exception ignored) {
                }
                try {
                    db.execSQL("ALTER TABLE people ADD COLUMN clock_day TEXT");
                } catch (Exception ignored) {
                }
                try {
                    db.execSQL("ALTER TABLE pending ADD COLUMN wanted_status TEXT");
                } catch (Exception ignored) {
                }
            }
        }
    }
}
