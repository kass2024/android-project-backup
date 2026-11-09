package com.xander.student;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class XanderStore {
   private final Helper helper;
   private final XanderPrefs prefs;
   private final Context appContext;
   /** Shared by the kiosk and SyncService so a roster update is visible on the next tap. */
   private static final Object indexLock = new Object();
   private static Map<String, JSONObject> cardIndex;
   private static int indexGeneration;
   /** student:id:area -> yyyy-MM-dd|STATUS|epoch. Tap path never waits on SQLite. */
   private static final Map<String, String> clockMemory = new HashMap<String, String>();
   private static final Object flushLock = new Object();
   /** visitor card hex -> visitors saved on that card. */
   private static final Map<String, JSONArray> visitorByCard = new HashMap<String, JSONArray>();
   private static volatile boolean visitorMemoryReady;
   /** student:id:area -> M=;L=;B=;E= for today's four day-scholar clocks. */
   private static final Map<String, String> markMemory = new HashMap<String, String>();
   private static final long STUDENT_OUT_WAIT_SECONDS = 600L;
   private final ExecutorService clockWrite = Executors.newSingleThreadExecutor();

   public XanderStore(Context ctx, XanderPrefs prefs) {
      this.appContext = ctx.getApplicationContext();
      this.helper = new Helper(this.appContext);
      this.prefs = prefs;
      this.clockWrite.execute(new Runnable() {
         public void run() {
            XanderStore.this.seedClock();
            XanderStore.this.rebuildVisitorMemory();
         }
      });
   }

   private void seedClock() {
      Cursor c = null;
      try {
         SQLiteDatabase db = this.helper.getReadableDatabase();
         c = db.rawQuery("SELECT k, last_day, last_status, last_time, marks FROM local_state WHERE k LIKE 'student:%'", (String[])null);
         synchronized (this.clockMemory) {
            while (c.moveToNext()) {
               String key = c.getString(0);
               if (key == null || this.clockMemory.containsKey(key)) {
                  continue;
               }
               String day = c.getString(1) == null ? "" : c.getString(1);
               String status = c.getString(2) == null ? "" : c.getString(2);
               long time = c.getColumnCount() > 3 ? c.getLong(3) : 0L;
               this.clockMemory.put(key, packClock(day, status, time));
               String marks = c.getColumnCount() > 4 ? c.getString(4) : "";
               if (marks != null && marks.length() > 0) {
                  markMemory.put(key, day + "#" + marks);
               }
            }
         }
      } catch (Exception ignored) {
      } finally {
         if (c != null) {
            c.close();
         }
      }
   }

   public void replaceStudents(JSONArray students) {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      db.beginTransaction();

      try {
         db.delete("people", (String)null, (String[])null);
         if (students != null) {
            for(int i = 0; i < students.length(); ++i) {
               JSONObject o = students.optJSONObject(i);
               if (o != null) {
                  String[] cards = studentCardPair(o);
                  ContentValues v = new ContentValues();
                  v.put("person_id", o.optInt("id", 0));
                  v.put("name", o.optString("name", ""));
                  v.put("card", cards[0]);
                  v.put("card_raw", cards[1]);
                  v.put("photo", o.optString("photo", ""));
                  v.put("extra", o.optString("class", ""));
                  v.put("regno", o.optString("regno", ""));
                  v.put("studying_mode", studyingModeOf(o));
                  db.insert("people", (String)null, v);
               }
            }
         }

         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }

      synchronized(this.indexLock) {
         this.cardIndex = null;
         indexGeneration++;
      }
   }

   public int indexGeneration() {
      synchronized(this.indexLock) {
         return indexGeneration;
      }
   }

   /**
    * Apply a lightweight device_cards payload without wiping photos/classes.
    * Newly assigned UIDs land in the local index within seconds.
    */
   public int applyPeopleCards(JSONObject json) {
      if (json == null) {
         return 0;
      }
      int changed = 0;
      changed += this.applyStudentCards(json.optJSONArray("students"));
      changed += this.applyVisitorCards(json.optJSONArray("visitors"));
      changed += this.applyStaffCards(json.optJSONArray("staff"));
      if (changed > 0) {
         synchronized(this.indexLock) {
            this.cardIndex = null;
            indexGeneration++;
         }
      }
      return changed;
   }

   public void cacheStudentFromScan(String scannedCard, JSONObject json) {
      if (json == null) {
         return;
      }
      JSONObject person = json.optJSONObject("person");
      if (person == null) {
         return;
      }
      int id = person.optInt("id", 0);
      if (id <= 0) {
         return;
      }
      String card = norm(scannedCard);
      if (card.length() < 4) {
         card = norm(person.optString("card", ""));
      }
      if (card.length() < 4) {
         return;
      }
      SQLiteDatabase db = this.helper.getWritableDatabase();
      Cursor c = db.rawQuery("SELECT person_id FROM people WHERE person_id=? LIMIT 1",
              new String[]{Integer.toString(id)});
      boolean exists = false;
      try {
         exists = c.moveToFirst();
      } finally {
         c.close();
      }
      ContentValues v = new ContentValues();
      v.put("person_id", id);
      v.put("name", person.optString("name", ""));
      v.put("extra", person.optString("class", person.optString("extra", "")));
      v.put("regno", person.optString("regno", ""));
      v.put("photo", person.optString("photo", ""));
      v.put("card", card);
      v.put("card_raw", reverseBytes(card));
      int mode = studyingModeOf(person);
      if (mode >= 0) {
         v.put("studying_mode", mode);
      }
      if (exists) {
         db.update("people", v, "person_id=?", new String[]{Integer.toString(id)});
      } else {
         db.insert("people", (String)null, v);
      }
      synchronized(this.indexLock) {
         this.cardIndex = null;
         indexGeneration++;
      }
   }

   private int applyStudentCards(JSONArray students) {
      if (students == null || students.length() == 0) {
         return 0;
      }
      SQLiteDatabase db = this.helper.getWritableDatabase();
      Map<Integer, String[]> have = new HashMap<Integer, String[]>();
      Cursor all = db.rawQuery("SELECT person_id, card, card_raw, name, studying_mode FROM people", (String[])null);
      try {
         while (all.moveToNext()) {
            String mode = all.isNull(4) ? "" : Integer.toString(all.getInt(4));
            have.put(Integer.valueOf(all.getInt(0)), new String[]{
                    all.getString(1) == null ? "" : all.getString(1),
                    all.getString(2) == null ? "" : all.getString(2),
                    all.getString(3) == null ? "" : all.getString(3),
                    mode
            });
         }
      } finally {
         all.close();
      }
      int changed = 0;
      Set<Integer> seen = new HashSet<Integer>();
      db.beginTransaction();
      try {
         for (int i = 0; i < students.length(); ++i) {
            JSONObject o = students.optJSONObject(i);
            if (o == null) {
               continue;
            }
            int id = o.optInt("id", 0);
            if (id <= 0) {
               continue;
            }
            String[] cards = studentCardPair(o);
            if (cards[0].length() >= 4) {
               seen.add(Integer.valueOf(id));
            }
            int mode = studyingModeOf(o);
            String[] old = have.get(Integer.valueOf(id));
            if (old != null) {
               boolean sameCard = cards[0].equalsIgnoreCase(old[0]) && cards[1].equalsIgnoreCase(old[1]);
               int oldMode = old[3].length() == 0 ? -1 : Integer.parseInt(old[3]);
               boolean sameMode = mode < 0 || mode == oldMode;
               if (sameCard && sameMode) {
                  continue;
               }
               ContentValues v = new ContentValues();
               if (!sameCard) {
                  v.put("card", cards[0]);
                  v.put("card_raw", cards[1]);
               }
               if (mode >= 0) {
                  v.put("studying_mode", mode);
               }
               String name = o.optString("name", "");
               if (name.length() > 0 && old[2].length() == 0) {
                  v.put("name", name);
               }
               String regno = o.optString("regno", "");
               if (regno.length() > 0) {
                  v.put("regno", regno);
               }
               db.update("people", v, "person_id=?", new String[]{Integer.toString(id)});
               ++changed;
            } else if (cards[0].length() >= 4) {
               ContentValues v = new ContentValues();
               v.put("person_id", id);
               v.put("name", o.optString("name", ""));
               v.put("card", cards[0]);
               v.put("card_raw", cards[1]);
               v.put("photo", o.optString("photo", ""));
               v.put("extra", o.optString("class", ""));
               v.put("regno", o.optString("regno", ""));
               v.put("studying_mode", mode);
               db.insert("people", (String)null, v);
               ++changed;
            }
         }
         if (seen.size() > 0) {
            for (Map.Entry<Integer, String[]> e : have.entrySet()) {
               if (seen.contains(e.getKey())) {
                  continue;
               }
               if (e.getValue()[0].length() < 4 && e.getValue()[1].length() < 4) {
                  continue;
               }
               ContentValues clear = new ContentValues();
               clear.put("card", "");
               clear.put("card_raw", "");
               db.update("people", clear, "person_id=?", new String[]{Integer.toString(e.getKey().intValue())});
               ++changed;
            }
         }
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }
      return changed;
   }

   /** Visitor UID updates only. Does not touch the student card index or attendance. */
   public int applyVisitorCardIndex(JSONArray visitors) {
      return this.applyVisitorCards(visitors);
   }

   private int applyVisitorCards(JSONArray visitors) {
      if (visitors == null) {
         return 0;
      }
      SQLiteDatabase db = this.helper.getWritableDatabase();
      int changed = 0;
      db.beginTransaction();
      try {
         for (int i = 0; i < visitors.length(); ++i) {
            JSONObject o = visitors.optJSONObject(i);
            if (o == null) {
               continue;
            }
            int id = o.optInt("id", 0);
            if (id <= 0) {
               continue;
            }
            String stored = norm(o.optString("card", ""));
            Cursor c = db.rawQuery("SELECT card FROM visitors WHERE visitor_id=? LIMIT 1",
                    new String[]{Integer.toString(id)});
            boolean exists = false;
            String oldCard = "";
            try {
               if (c.moveToFirst()) {
                  exists = true;
                  oldCard = c.getString(0) == null ? "" : c.getString(0);
               }
            } finally {
               c.close();
            }
            if (exists) {
               ContentValues v = new ContentValues();
               if (stored.length() >= 4 && !stored.equalsIgnoreCase(oldCard)) {
                  v.put("card", stored);
                  v.put("card_raw", reverseBytes(stored));
               }
               if (o.has("student_id")) {
                  v.put("student_id", o.optInt("student_id", 0));
               }
               String names = o.optString("names", o.optString("name", ""));
               if (names.length() > 0) {
                  v.put("names", names);
               }
               String relationship = o.optString("relationship", "");
               if (relationship.length() > 0) {
                  v.put("relationship", relationship);
               }
               String phone = o.optString("phone", "");
               if (phone.length() > 0) {
                  v.put("phone", phone);
               }
               if (o.optString("student_name", "").length() > 0) {
                  v.put("student_name", o.optString("student_name", ""));
               }
               if (v.size() > 0) {
                  db.update("visitors", v, "visitor_id=?", new String[]{Integer.toString(id)});
                  ++changed;
               }
            } else if (stored.length() >= 4) {
               ContentValues v = new ContentValues();
               v.put("visitor_id", id);
               v.put("student_id", o.optInt("student_id", 0));
               v.put("names", o.optString("names", o.optString("name", "")));
               v.put("phone", o.optString("phone", ""));
               v.put("relationship", o.optString("relationship", ""));
               v.put("card", stored);
               v.put("card_raw", reverseBytes(stored));
               v.put("photo", o.optString("photo", ""));
               v.put("student_name", o.optString("student_name", ""));
               v.put("student_class", o.optString("student_class", ""));
               v.put("student_regno", o.optString("student_regno", ""));
               db.insert("visitors", (String)null, v);
               ++changed;
            }
         }
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }
      if (changed > 0) {
         this.rebuildVisitorMemory();
      }
      return changed;
   }

   private int applyStaffCards(JSONArray staff) {
      if (staff == null) {
         return 0;
      }
      SQLiteDatabase db = this.helper.getWritableDatabase();
      int changed = 0;
      db.beginTransaction();
      try {
         for (int i = 0; i < staff.length(); ++i) {
            JSONObject o = staff.optJSONObject(i);
            if (o == null) {
               continue;
            }
            int id = o.optInt("id", 0);
            String stored = norm(o.optString("card", ""));
            if (id <= 0) {
               continue;
            }
            db.delete("staff", "staff_id=?", new String[]{Integer.toString(id)});
            if (stored.length() < 4) {
               ++changed;
               continue;
            }
            ContentValues v = new ContentValues();
            v.put("staff_id", id);
            v.put("name", o.optString("name", ""));
            v.put("post", o.optString("post", ""));
            v.put("card", stored);
            v.put("card_raw", reverseBytes(stored));
            db.insert("staff", (String)null, v);
            ++changed;
         }
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }
      return changed;
   }

   /** card[0] = stored hex, card[1] = alt (nfc or reversed) for lookup. */
   private static String[] studentCardPair(JSONObject o) {
      String card = norm(o.optString("card", ""));
      String nfc = norm(o.optString("card_nfc", ""));
      String primary = card.length() >= 4 ? card : nfc;
      String alt = nfc.length() >= 4 && !nfc.equalsIgnoreCase(primary) ? nfc
              : (primary.length() >= 4 ? reverseBytes(primary) : primary);
      return new String[]{primary, alt};
   }

   /** 0 = boarding, 1 = day, -1 = unknown. Reads numbers and words. */
   public static int studyingModeOf(JSONObject o) {
      if (o == null) {
         return -1;
      }
      if (o.has("studying_mode")) {
         int mode = modeValue(o.opt("studying_mode"));
         if (mode >= 0) {
            return mode;
         }
      }
      if (o.has("mode")) {
         return modeValue(o.opt("mode"));
      }
      return -1;
   }

   private static int modeValue(Object raw) {
      if (raw == null || raw == JSONObject.NULL) {
         return -1;
      }
      if (raw instanceof Number) {
         int n = ((Number) raw).intValue();
         return (n == 0 || n == 1) ? n : -1;
      }
      String s = String.valueOf(raw).trim().toLowerCase(Locale.US);
      if (s.length() == 0 || "null".equals(s)) {
         return -1;
      }
      if ("0".equals(s) || s.contains("board")) {
         return 0;
      }
      if ("1".equals(s) || s.contains("day")) {
         return 1;
      }
      try {
         int n = Integer.parseInt(s);
         if (n == 0 || n == 1) {
            return n;
         }
      } catch (Exception ignored) {
      }
      return -1;
   }

   /** Card kiosk is day scholars only. Unknown is allowed until the roster lands. */
   public static boolean isDayScholar(JSONObject o) {
      return studyingModeOf(o) != 0;
   }

   public static boolean isBoarding(JSONObject o) {
      return studyingModeOf(o) == 0;
   }

   public static boolean isKnownDayScholar(JSONObject o) {
      return studyingModeOf(o) == 1;
   }

   public JSONArray listStudents() {
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = db.rawQuery("SELECT person_id, name, photo FROM people", (String[])null);
      JSONArray arr = new JSONArray();

      try {
         while(c.moveToNext()) {
            JSONObject o = new JSONObject();
            o.put("id", c.getInt(0));
            o.put("name", c.getString(1));
            o.put("photo", c.getString(2));
            arr.put(o);
         }
      } catch (Exception var8) {
      } finally {
         c.close();
      }

      return arr;
   }

   public void replaceVisitors(JSONArray visitors) {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      db.beginTransaction();

      try {
         db.delete("visitors", (String)null, (String[])null);
         if (visitors != null) {
            for(int i = 0; i < visitors.length(); ++i) {
               JSONObject o = visitors.optJSONObject(i);
               if (o != null) {
                  ContentValues v = new ContentValues();
                  v.put("visitor_id", o.optInt("id", 0));
                  v.put("student_id", o.optInt("student_id", 0));
                  v.put("names", o.optString("names", ""));
                  v.put("phone", o.optString("phone", ""));
                  v.put("relationship", o.optString("relationship", ""));
                  String cardStored = norm(o.optString("card", ""));
                  v.put("card", cardStored);
                  v.put("card_raw", cardStored.length() >= 4 ? reverseBytes(cardStored) : cardStored);
                  v.put("photo", o.optString("photo", ""));
                  v.put("student_name", o.optString("student_name", ""));
                  v.put("student_class", o.optString("student_class", ""));
                  v.put("student_regno", o.optString("student_regno", ""));
                  db.insert("visitors", (String)null, v);
                  Log.e("XanderVisitor", "SYNC visitor id=" + o.optInt("id", 0) + " card=" + cardStored + " raw=" + (cardStored.length() >= 4 ? reverseBytes(cardStored) : "") + " name=" + o.optString("names", ""));
               }
            }
         }

         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }

      this.applyVisitorCardAliases();
      this.rebuildVisitorMemory();
   }

   private void applyVisitorCardAliases() {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      Cursor c = db.rawQuery("SELECT visitor_id, names, phone, relationship, photo, student_id, student_name, student_class, student_regno, card FROM visitors", (String[])null);

      try {
         while(c.moveToNext()) {
            int vid = c.getInt(0);
            String alias = this.prefs.getVisitorCardAlias(vid);
            String current = norm(c.getString(9));
            if (alias.length() >= 4 && !alias.equalsIgnoreCase(current)) {
               ContentValues v = new ContentValues();
               v.put("visitor_id", vid);
               v.put("student_id", c.getInt(5));
               v.put("names", c.getString(1));
               v.put("phone", c.getString(2));
               v.put("relationship", c.getString(3));
               v.put("photo", c.getString(4));
               v.put("card", alias);
               v.put("card_raw", reverseBytes(alias));
               v.put("student_name", c.getString(6));
               v.put("student_class", c.getString(7));
               v.put("student_regno", c.getString(8));
               db.insert("visitors", (String)null, v);
               Log.e("XanderVisitor", "ALIAS visitor id=" + vid + " syncCard=" + current + " dkCard=" + alias);
            }
         }
      } finally {
         c.close();
      }

   }

   public void cacheVisitorsFromScan(String scannedCard, JSONObject scanJson) {
      if (scanJson == null) {
         return;
      }
      String scanned = norm(scannedCard);
      if (scanned.length() < 4) {
         scanned = norm(scanJson.optString("card", ""));
      }
      JSONArray visitors = scanJson.optJSONArray("visitors");
      if (visitors == null || visitors.length() == 0) {
         JSONObject one = scanJson.optJSONObject("visitor");
         if (one == null) {
            return;
         }
         visitors = new JSONArray();
         visitors.put(one);
      }
      Map<Integer, JSONObject> studentsById = new HashMap();
      JSONArray students = scanJson.optJSONArray("students");
      for (int i = 0; students != null && i < students.length(); ++i) {
         JSONObject student = students.optJSONObject(i);
         if (student != null) {
            int sid = student.optInt("id", 0);
            if (sid > 0) {
               studentsById.put(sid, student);
            }
         }
      }
      SQLiteDatabase db = this.helper.getWritableDatabase();
      db.beginTransaction();
      try {
         for (int i = 0; i < visitors.length(); ++i) {
            JSONObject o = visitors.optJSONObject(i);
            if (o == null) {
               continue;
            }
            int vid = o.optInt("id", o.optInt("visitor_id", 0));
            String ownCard = norm(o.optString("card", o.optString("card_stored", "")));
            boolean holder = scanned.length() >= 4 && ownCard.length() >= 4
                    && (ownCard.equalsIgnoreCase(scanned) || reverseBytes(ownCard).equalsIgnoreCase(scanned));
            String storeCard = holder ? scanned : ownCard;
            if (vid > 0) {
               db.delete("visitors", "visitor_id=?", new String[]{Integer.toString(vid)});
               if (holder) {
                  this.prefs.setVisitorCardAlias(vid, scanned);
               }
            }
            int sid = o.optInt("student_id", 0);
            JSONObject st = o.optJSONObject("student");
            if (st == null) {
               st = studentsById.get(sid);
            }
            ContentValues v = new ContentValues();
            v.put("visitor_id", vid);
            v.put("student_id", sid);
            v.put("names", o.optString("names", o.optString("name", "")));
            v.put("phone", o.optString("phone", ""));
            v.put("relationship", o.optString("relationship", ""));
            v.put("card", storeCard);
            v.put("card_raw", storeCard.length() >= 4 ? reverseBytes(storeCard) : storeCard);
            v.put("photo", o.optString("photo", ""));
            v.put("student_name", st != null ? st.optString("name", o.optString("student_name", "")) : o.optString("student_name", ""));
            v.put("student_class", st != null ? st.optString("class", o.optString("student_class", "")) : o.optString("student_class", ""));
            v.put("student_regno", st != null ? st.optString("regno", o.optString("student_regno", "")) : o.optString("student_regno", ""));
            db.insert("visitors", (String)null, v);
            Log.e("XanderVisitor", "CACHE visitor id=" + vid + " card=" + storeCard + " holder=" + holder + " name=" + v.getAsString("names"));
         }
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }
      this.rebuildVisitorMemory();
   }

   /** Mirror a visit the server already saved. Does not queue another upload. */
   public void rememberLiveVisitorClock(String card, JSONObject scan) {
      if (scan == null) {
         return;
      }
      String action = scan.optString("action", "in").toLowerCase(Locale.US);
      String status = "out".equals(action) ? "OUT" : "IN";
      long now = System.currentTimeMillis() / 1000L;
      String day = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date());
      String scanned = norm(card);
      JSONArray visitors = scan.optJSONArray("visitors");
      SQLiteDatabase db = this.helper.getWritableDatabase();
      for (int i = 0; visitors != null && i < visitors.length(); i++) {
         JSONObject visitor = visitors.optJSONObject(i);
         if (visitor == null) {
            continue;
         }
         int visitorId = visitor.optInt("id", 0);
         if (visitorId <= 0) {
            continue;
         }
         String own = norm(visitor.optString("card", visitor.optString("card_stored", "")));
         boolean holder = scanned.length() >= 4 && own.length() >= 4
                 && (own.equalsIgnoreCase(scanned) || reverseBytes(own).equalsIgnoreCase(scanned));
         if (!holder && visitors.length() > 1) {
            continue;
         }
         ContentValues st = new ContentValues();
         st.put("k", "visitor:" + visitorId);
         st.put("last_day", day);
         st.put("last_status", status);
         st.put("last_time", now);
         db.replace("local_state", (String) null, st);
      }
   }

   public void warmIndex() {
      this.ensureIndex();
   }

   public JSONObject findByCard(String card) {
      JSONObject hit = this.lookupIndex(card);
      if (hit != null) {
         return hit;
      } else {
         String n = norm(card);
         if (n.length() != 0 || card != null && card.trim().length() != 0) {
            if (n.length() == 0 && card != null && card.trim().matches("[0-9]+")) {
               n = decimalToHex(card.trim());
            }

            String rev = reverseBytes(n);
            SQLiteDatabase db = this.helper.getReadableDatabase();
            Cursor c = db.rawQuery("SELECT person_id, name, extra, regno, photo, card_raw, studying_mode FROM people WHERE card=? OR card=? OR card_raw=? OR card_raw=? LIMIT 1", new String[]{n, rev, card == null ? "" : card.trim(), rev});

            JSONObject o;
            try {
               if (c.moveToFirst()) {
                  o = this.personFromCursor(c);
                  this.remember(o);
                  JSONObject var15 = o;
                  return var15;
               }

               o = null;
            } catch (Exception var12) {
               Object var8 = null;
               return (JSONObject)var8;
            } finally {
               c.close();
            }

            return o;
         } else {
            return null;
         }
      }
   }

   public int studentCount() {
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = db.rawQuery("SELECT COUNT(*) FROM people", (String[])null);

      int var3;
      try {
         var3 = c.moveToFirst() ? c.getInt(0) : 0;
      } finally {
         c.close();
      }

      return var3;
   }

   public int pendingCount() {
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = db.rawQuery("SELECT COUNT(*) FROM pending", (String[])null);

      int var3;
      try {
         var3 = c.moveToFirst() ? c.getInt(0) : 0;
      } finally {
         c.close();
      }

      return var3;
   }

   public int visitorCount() {
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = db.rawQuery("SELECT COUNT(*) FROM visitors", (String[])null);

      int var3;
      try {
         var3 = c.moveToFirst() ? c.getInt(0) : 0;
      } finally {
         c.close();
      }

      return var3;
   }

   public JSONObject clockLocal(int personId, int areaId, String card, String name, String extra, String photo) {
      long now = System.currentTimeMillis() / 1000L;
      String day = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date());
      String key = "student:" + personId + ":" + areaId;
      DayScholarClock.Windows windows = DayScholarClock.windowsFor(this.prefs.dayClockJson(), extra);
      DayScholarClock.Marks prior = this.marksFor(key, day);
      DayScholarClock.Decision decision = DayScholarClock.decide(now, windows, prior);
      String status = decision.status;
      long shownTime = decision.shownEpoch > 0L ? decision.shownEpoch : now;
      if (decision.save) {
         String packedMarks = DayScholarClock.pack(decision.marks);
         String visible = "OUT".equals(status) ? "OUT" : "IN";
         synchronized (this.clockMemory) {
            this.clockMemory.put(key, packClock(day, visible, now));
            markMemory.put(key, day + "#" + packedMarks);
         }
         final String writeStatus = status;
         final String writeCard = card == null ? "" : card;
         final int writeArea = areaId;
         final int writePerson = personId;
         final long writeNow = now;
         final String writeDay = day;
         final String writeKey = key;
         final String writeMarks = packedMarks;
         final String writeVisible = visible;
         this.clockWrite.execute(new Runnable() {
            public void run() {
               XanderStore.this.persistClock(writeKey, writeDay, writeStatus, writeCard, writeArea, writePerson, writeNow, writeMarks, writeVisible);
            }
         });
      }
      JSONObject json = new JSONObject();

      try {
         json.put("success", 1);
         json.put("offline", 1);
         json.put("kind", "student");
         json.put("status", status);
         json.put("time", (new SimpleDateFormat("HH:mm", Locale.US)).format(new Date(shownTime * 1000L)));
         if (decision.wait) {
            json.put("wait", 1);
            json.put("message", decision.message);
         } else if (decision.already) {
            json.put("already", 1);
            json.put("message", decision.message);
         } else {
            json.put("message", decision.message);
         }
         JSONObject person = new JSONObject();
         person.put("id", personId);
         person.put("name", name == null ? "" : name);
         person.put("class", extra == null ? "" : extra);
         person.put("extra", extra == null ? "" : extra);
         person.put("regno", "");
         person.put("photo", photo == null ? "" : photo);
         json.put("person", person);
      } catch (Exception var26) {
      }

      return json;
   }

   private DayScholarClock.Marks marksFor(String key, String day) {
      synchronized (this.clockMemory) {
         String mem = markMemory.get(key);
         if (mem != null && mem.startsWith(day + "#")) {
            return DayScholarClock.unpack(mem.substring(day.length() + 1));
         }
         String packed = this.clockMemory.get(key);
         if (!day.equals(clockDay(packed))) {
            return new DayScholarClock.Marks();
         }
         DayScholarClock.Marks legacy = new DayScholarClock.Marks();
         long lastTime = clockTime(packed);
         if ("OUT".equals(clockStatus(packed))) {
            legacy.morning = lastTime;
            legacy.evening = lastTime;
         } else if ("IN".equals(clockStatus(packed))) {
            legacy.morning = lastTime;
         }
         return legacy;
      }
   }

   private static String packClock(String day, String status, long time) {
      return (day == null ? "" : day) + "|" + (status == null ? "" : status) + "|" + time;
   }

   private static String clockDay(String packed) {
      if (packed == null) {
         return "";
      }
      int bar = packed.indexOf('|');
      return bar > 0 ? packed.substring(0, bar) : "";
   }

   private static String clockStatus(String packed) {
      if (packed == null) {
         return "";
      }
      int bar = packed.indexOf('|');
      if (bar < 0 || bar + 1 >= packed.length()) {
         return "";
      }
      String rest = packed.substring(bar + 1);
      int bar2 = rest.indexOf('|');
      return bar2 >= 0 ? rest.substring(0, bar2) : rest;
   }

   private static long clockTime(String packed) {
      if (packed == null) {
         return 0L;
      }
      int bar = packed.lastIndexOf('|');
      int first = packed.indexOf('|');
      if (bar < 0 || bar == first || bar + 1 >= packed.length()) {
         return 0L;
      }
      try {
         return Long.parseLong(packed.substring(bar + 1));
      } catch (Exception ignored) {
         return 0L;
      }
   }

   private void persistClock(String key, String day, String status, String card, int areaId, int personId, long now, String marks, String visibleStatus) {
      try {
         SQLiteDatabase db = this.helper.getWritableDatabase();
         ContentValues st = new ContentValues();
         st.put("k", key);
         st.put("last_day", day);
         st.put("last_status", visibleStatus == null || visibleStatus.length() == 0 ? status : visibleStatus);
         st.put("last_time", now);
         st.put("marks", marks == null ? "" : marks);
         db.replace("local_state", (String)null, st);
         this.dropPendingSameStatus(db, card, areaId, personId, status);
         ContentValues p = new ContentValues();
         p.put("kind", "student");
         p.put("card", card);
         p.put("area_id", areaId);
         p.put("event_time", now);
         JSONObject ex = new JSONObject();
         ex.put("student_id", personId);
         ex.put("status", status);
         p.put("extra", ex.toString());
         db.insert("pending", (String)null, p);
      } catch (Exception e) {
         Log.e("XanderVisitor", "clock save " + e.getMessage());
      }
   }

   /** Keep one unsent row per clock (morning, lunch, back, evening). */
   private void dropPendingSameStatus(SQLiteDatabase db, String card, int areaId, int personId, String status) {
      Cursor old = null;
      try {
         old = db.rawQuery("SELECT id, extra FROM pending WHERE kind='student' AND card=? AND area_id=?", new String[]{card == null ? "" : card, Integer.toString(areaId)});
         String needle = "\"student_id\":" + personId;
         String statusNeedle = "\"status\":\"" + (status == null ? "" : status) + "\"";
         while (old.moveToNext()) {
            String extra = old.getString(1);
            if (extra != null && extra.contains(needle) && extra.contains(statusNeedle)) {
               db.delete("pending", "id=?", new String[]{Integer.toString(old.getInt(0))});
            }
         }
      } catch (Exception ignored) {
      } finally {
         if (old != null) {
            old.close();
         }
      }
   }

   private JSONArray visitorsFromMemory(String card) {
      synchronized (visitorByCard) {
         if (!visitorMemoryReady) {
            return null;
         }
         String[] variants = CardUid.lookupVariants(card);
         for (int i = 0; variants != null && i < variants.length; i++) {
            JSONArray hit = visitorByCard.get(norm(variants[i]));
            if (hit != null && hit.length() > 0) {
               return hit;
            }
         }
         String n = norm(card);
         if (n.length() >= 4) {
            JSONArray hit = visitorByCard.get(n);
            if (hit != null && hit.length() > 0) {
               return hit;
            }
            hit = visitorByCard.get(reverseBytes(n));
            if (hit != null && hit.length() > 0) {
               return hit;
            }
         }
         return new JSONArray();
      }
   }

   private void rebuildVisitorMemory() {
      Map<String, JSONArray> next = new HashMap<String, JSONArray>();
      Cursor c = null;
      boolean loaded = false;
      try {
         SQLiteDatabase db = this.helper.getReadableDatabase();
         c = db.rawQuery("SELECT visitor_id, student_id, names, phone, relationship, photo, student_name, student_class, student_regno, card_raw, card FROM visitors", (String[]) null);
         while (c.moveToNext()) {
            JSONObject o = new JSONObject();
            o.put("id", c.getInt(0));
            o.put("student_id", c.getInt(1));
            o.put("names", c.getString(2));
            o.put("phone", c.getString(3));
            o.put("relationship", c.getString(4));
            o.put("photo", c.getString(5));
            o.put("student_name", c.getString(6));
            o.put("student_class", c.getString(7));
            o.put("student_regno", c.getString(8));
            String raw = c.getString(9) == null ? "" : c.getString(9);
            String stored = c.getString(10) == null ? "" : c.getString(10);
            o.put("card", raw.length() > 0 ? raw : stored);
            if (stored.length() > 0) {
               o.put("card_stored", stored);
            }
            this.rememberVisitorKey(next, stored, o);
            this.rememberVisitorKey(next, raw, o);
            String[] variants = CardUid.lookupVariants(stored.length() > 0 ? stored : raw);
            for (int i = 0; variants != null && i < variants.length; i++) {
               this.rememberVisitorKey(next, variants[i], o);
            }
         }
         loaded = true;
      } catch (Exception ignored) {
      } finally {
         if (c != null) {
            c.close();
         }
      }
      if (!loaded) {
         return;
      }
      synchronized (visitorByCard) {
         visitorByCard.clear();
         visitorByCard.putAll(next);
         visitorMemoryReady = true;
      }
   }

   private void rememberVisitorKey(Map<String, JSONArray> into, String card, JSONObject visitor) {
      String key = norm(card);
      if (key.length() < 4 || visitor == null) {
         return;
      }
      JSONArray list = into.get(key);
      if (list == null) {
         list = new JSONArray();
         into.put(key, list);
      }
      int vid = visitor.optInt("id", 0);
      for (int i = 0; i < list.length(); i++) {
         JSONObject have = list.optJSONObject(i);
         if (have != null && vid > 0 && have.optInt("id", 0) == vid) {
            return;
         }
      }
      list.put(visitor);
   }

   public JSONArray findVisitorsByCard(String card) {
      JSONArray memory = this.visitorsFromMemory(card);
      if (memory != null) {
         return memory;
      }
      String n = norm(card);
      if (n.length() != 0 || card != null && card.trim().length() != 0) {
         if (n.length() == 0 && card != null && card.trim().matches("[0-9]+")) {
            n = decimalToHex(card.trim());
         }

         String rev = reverseBytes(n);
         SQLiteDatabase db = this.helper.getReadableDatabase();
         Cursor c = db.rawQuery("SELECT visitor_id, student_id, names, phone, relationship, photo, student_name, student_class, student_regno, card_raw, card FROM visitors WHERE card=? OR card=? OR card_raw=? OR card_raw=? ORDER BY visitor_id ASC", new String[]{n, rev, card == null ? "" : card.trim(), rev});
         JSONArray arr = new JSONArray();

         try {
            JSONObject o;
            try {
               for(; c.moveToNext(); arr.put(o)) {
                  o = new JSONObject();
                  o.put("id", c.getInt(0));
                  o.put("student_id", c.getInt(1));
                  o.put("names", c.getString(2));
                  o.put("phone", c.getString(3));
                  o.put("relationship", c.getString(4));
                  o.put("photo", c.getString(5));
                  o.put("student_name", c.getString(6));
                  o.put("student_class", c.getString(7));
                  o.put("student_regno", c.getString(8));
                  o.put("card", c.getString(9));
                  String stored = c.getString(10);
                  if (stored != null && stored.length() > 0) {
                     o.put("card_stored", stored);
                  }
               }
            } catch (Exception var12) {
            }
         } finally {
            c.close();
         }

         return arr;
      } else {
         return new JSONArray();
      }
   }

   /**
    * Same parent / same visitor identity on more than one student
    * is one visiting card: pull every locally cached sibling row.
    */
   public JSONArray expandLocalVisitGroup(JSONArray visitors) {
      if (visitors == null || visitors.length() == 0) {
         return visitors == null ? new JSONArray() : visitors;
      }
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Map<Integer, JSONObject> byId = new LinkedHashMap();
      for (int i = 0; i < visitors.length(); ++i) {
         JSONObject visitor = visitors.optJSONObject(i);
         if (visitor != null) {
            int vid = visitor.optInt("id", 0);
            if (vid > 0) {
               byId.put(vid, visitor);
            }
         }
      }

      for (int i = 0; i < visitors.length(); ++i) {
         JSONObject visitor = visitors.optJSONObject(i);
         if (visitor == null) {
            continue;
         }
         String names = visitor.optString("names", visitor.optString("name", "")).trim();
         String phone = visitor.optString("phone", "").replaceAll("\\D+", "");
         if (names.length() == 0 && phone.length() < 7) {
            continue;
         }
         Cursor c = null;
         try {
            if (names.length() > 0 && phone.length() >= 7) {
               c = db.rawQuery(
                       "SELECT visitor_id, student_id, names, phone, relationship, photo, student_name, student_class, student_regno, card_raw, card FROM visitors WHERE LOWER(names)=LOWER(?) OR replace(replace(replace(phone,' ',''),'+',''),'-','')=?",
                       new String[]{names, phone});
            } else if (names.length() > 0) {
               c = db.rawQuery(
                       "SELECT visitor_id, student_id, names, phone, relationship, photo, student_name, student_class, student_regno, card_raw, card FROM visitors WHERE LOWER(names)=LOWER(?)",
                       new String[]{names});
            } else {
               c = db.rawQuery(
                       "SELECT visitor_id, student_id, names, phone, relationship, photo, student_name, student_class, student_regno, card_raw, card FROM visitors WHERE replace(replace(replace(phone,' ',''),'+',''),'-','')=?",
                       new String[]{phone});
            }
            while (c.moveToNext()) {
               int vid = c.getInt(0);
               if (vid <= 0 || byId.containsKey(vid)) {
                  continue;
               }
               try {
                  JSONObject o = new JSONObject();
                  o.put("id", vid);
                  o.put("student_id", c.getInt(1));
                  o.put("names", c.getString(2));
                  o.put("phone", c.getString(3));
                  o.put("relationship", c.getString(4));
                  o.put("photo", c.getString(5));
                  o.put("student_name", c.getString(6));
                  o.put("student_class", c.getString(7));
                  o.put("student_regno", c.getString(8));
                  o.put("card", c.getString(9));
                  String stored = c.getString(10);
                  if (stored != null && stored.length() > 0) {
                     o.put("card_stored", stored);
                  }
                  byId.put(vid, o);
               } catch (Exception ignored) {
               }
            }
         } finally {
            if (c != null) {
               c.close();
            }
         }
      }

      JSONArray out = new JSONArray();
      for (JSONObject visitor : byId.values()) {
         out.put(visitor);
      }
      return out.length() > 0 ? out : visitors;
   }

   /**
    * Clock the tapped card holders plus the same parent on sibling students.
    * Do not clock other allowed visitors (mother/father/guardian) on the same student.
    */
   public JSONArray clockableVisitGroup(JSONArray cardHolders) {
      JSONArray expanded = this.expandLocalVisitGroup(cardHolders);
      Set<Integer> seedVisitorIds = new HashSet();
      Set<Integer> seedStudentIds = new HashSet();
      for (int i = 0; cardHolders != null && i < cardHolders.length(); ++i) {
         JSONObject visitor = cardHolders.optJSONObject(i);
         if (visitor == null) {
            continue;
         }
         int vid = visitor.optInt("id", 0);
         int sid = visitor.optInt("student_id", 0);
         if (vid > 0) {
            seedVisitorIds.add(vid);
         }
         if (sid > 0) {
            seedStudentIds.add(sid);
         }
      }
      JSONArray clock = new JSONArray();
      for (int i = 0; i < expanded.length(); ++i) {
         JSONObject visitor = expanded.optJSONObject(i);
         if (visitor == null) {
            continue;
         }
         int vid = visitor.optInt("id", 0);
         int sid = visitor.optInt("student_id", 0);
         if (seedVisitorIds.contains(vid) || (sid > 0 && !seedStudentIds.contains(sid))) {
            clock.put(visitor);
         }
      }
      return clock.length() > 0 ? clock : (cardHolders == null ? new JSONArray() : cardHolders);
   }

   /**
    * Every allowed visitor for the visited students, including parents who
    * have no card of their own.
    */
   public JSONArray allVisitorsForLinkedStudents(JSONArray seedVisitors) {
      JSONArray expanded = this.expandLocalVisitGroup(seedVisitors);
      Set<Integer> studentIds = new HashSet();
      Map<Integer, JSONObject> byId = new LinkedHashMap();
      for (int i = 0; i < expanded.length(); ++i) {
         JSONObject visitor = expanded.optJSONObject(i);
         if (visitor == null) {
            continue;
         }
         int vid = visitor.optInt("id", 0);
         int sid = visitor.optInt("student_id", 0);
         if (vid > 0) {
            byId.put(vid, visitor);
         }
         if (sid > 0) {
            studentIds.add(sid);
         }
      }
      if (studentIds.isEmpty()) {
         return expanded.length() > 0 ? expanded : (seedVisitors == null ? new JSONArray() : seedVisitors);
      }
      StringBuilder in = new StringBuilder();
      String[] args = new String[studentIds.size()];
      int idx = 0;
      for (Integer studentId : studentIds) {
         if (idx > 0) {
            in.append(',');
         }
         in.append('?');
         args[idx++] = Integer.toString(studentId);
      }
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = db.rawQuery(
              "SELECT visitor_id, student_id, names, phone, relationship, photo, student_name, student_class, student_regno, card_raw, card FROM visitors WHERE student_id IN (" + in + ") ORDER BY visitor_id ASC",
              args);
      try {
         while (c.moveToNext()) {
            int vid = c.getInt(0);
            if (vid <= 0 || byId.containsKey(vid)) {
               continue;
            }
            try {
               JSONObject o = new JSONObject();
               o.put("id", vid);
               o.put("student_id", c.getInt(1));
               o.put("names", c.getString(2));
               o.put("phone", c.getString(3));
               o.put("relationship", c.getString(4));
               o.put("photo", c.getString(5));
               o.put("student_name", c.getString(6));
               o.put("student_class", c.getString(7));
               o.put("student_regno", c.getString(8));
               o.put("card", c.getString(9));
               String stored = c.getString(10);
               if (stored != null && stored.length() > 0) {
                  o.put("card_stored", stored);
               }
               byId.put(vid, o);
            } catch (Exception ignored) {
            }
         }
      } finally {
         c.close();
      }
      JSONArray out = new JSONArray();
      for (JSONObject visitor : byId.values()) {
         out.put(visitor);
      }
      return out.length() > 0 ? out : expanded;
   }

   /** Parents registered to one student. Does not clock a visit. */
   public JSONArray visitorsForStudent(int studentId) {
      JSONArray out = new JSONArray();
      if (studentId <= 0) {
         return out;
      }
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = db.rawQuery(
              "SELECT visitor_id, student_id, names, phone, relationship, photo, student_name, student_class, student_regno, card_raw, card FROM visitors WHERE student_id=? ORDER BY visitor_id ASC",
              new String[]{Integer.toString(studentId)});
      try {
         while (c.moveToNext()) {
            int vid = c.getInt(0);
            if (vid <= 0) {
               continue;
            }
            try {
               JSONObject o = new JSONObject();
               o.put("id", vid);
               o.put("student_id", c.getInt(1));
               o.put("names", c.getString(2));
               o.put("phone", c.getString(3));
               o.put("relationship", c.getString(4));
               o.put("photo", c.getString(5));
               o.put("student_name", c.getString(6));
               o.put("student_class", c.getString(7));
               o.put("student_regno", c.getString(8));
               o.put("card", c.getString(9));
               String stored = c.getString(10);
               if (stored != null && stored.length() > 0) {
                  o.put("card_stored", stored);
               }
               out.put(o);
            } catch (Exception ignored) {
            }
         }
      } finally {
         c.close();
      }
      return out;
   }

   public JSONArray studentsForVisitors(JSONArray visitors) {
      return this.distinctStudents(visitors);
   }

   /**
    * One tile per visitor identity. Siblings sharing the same parent stay one card.
    */
   public JSONArray uniqueVisitorsForDisplay(JSONArray visitors) {
      Map<Integer, JSONObject> byId = new LinkedHashMap();
      Map<String, JSONObject> byKey = new LinkedHashMap();
      for (int i = 0; visitors != null && i < visitors.length(); ++i) {
         JSONObject visitor = visitors.optJSONObject(i);
         if (visitor == null) {
            continue;
         }
         int vid = visitor.optInt("id", 0);
         if (vid > 0 && byId.containsKey(vid)) {
            continue;
         }
         String names = visitor.optString("names", visitor.optString("name", "")).trim().toLowerCase(Locale.US);
         if (names.length() == 0) {
            continue;
         }
         String key = names;
         JSONObject existing = byKey.get(key);
         if (existing != null) {
            String existCard = existing.optString("card", existing.optString("card_stored", ""));
            String newCard = visitor.optString("card", visitor.optString("card_stored", ""));
            String existRel = existing.optString("relationship", "").trim();
            String newRel = visitor.optString("relationship", "").trim();
            try {
               if (newCard.length() >= 4 && existCard.length() < 4) {
                  existing.put("card", newCard);
               }
               if (betterRelationship(newRel, existRel)) {
                  existing.put("relationship", newRel);
               }
               if (vid > 0 && existing.optInt("id", 0) <= 0) {
                  existing.put("id", vid);
               }
            } catch (Exception ignored) {
            }
            continue;
         }
         byKey.put(key, visitor);
         if (vid > 0) {
            byId.put(vid, visitor);
         }
      }
      JSONArray out = new JSONArray();
      for (JSONObject visitor : byKey.values()) {
         out.put(visitor);
      }
      return out.length() > 0 ? out : (visitors == null ? new JSONArray() : visitors);
   }

   private static boolean betterRelationship(String candidate, String current) {
      return relationshipRank(candidate) > relationshipRank(current);
   }

   private static int relationshipRank(String rel) {
      String n = rel == null ? "" : rel.trim().toLowerCase(Locale.US);
      if (n.contains("father") || n.contains("mother") || n.contains("guardian")) {
         return 3;
      }
      if (n.length() > 0 && !n.equals("visitor")) {
         return 2;
      }
      return n.length() > 0 ? 1 : 0;
   }

   public void replaceStaff(JSONArray staff) {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      db.beginTransaction();
      try {
         db.delete("staff", (String)null, (String[])null);
         if (staff != null) {
            for (int i = 0; i < staff.length(); ++i) {
               JSONObject o = staff.optJSONObject(i);
               if (o == null) {
                  continue;
               }
               String cardStored = norm(o.optString("card", ""));
               if (cardStored.length() < 4) {
                  continue;
               }
               ContentValues v = new ContentValues();
               v.put("staff_id", o.optInt("id", 0));
               v.put("name", o.optString("name", ""));
               v.put("post", o.optString("post", ""));
               v.put("card", cardStored);
               v.put("card_raw", reverseBytes(cardStored));
               db.insert("staff", (String)null, v);
            }
         }
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }
   }

   public JSONObject findStaffByCard(String card) {
      String n = norm(card);
      if (n.length() < 4) {
         return null;
      }
      String rev = reverseBytes(n);
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = db.rawQuery(
              "SELECT staff_id, name, post, card FROM staff WHERE card=? OR card=? OR card_raw=? OR card_raw=? LIMIT 1",
              new String[]{n, rev, card == null ? "" : card.trim(), rev});
      try {
         if (!c.moveToFirst()) {
            return null;
         }
         JSONObject o = new JSONObject();
         o.put("id", c.getInt(0));
         o.put("name", c.getString(1));
         o.put("post", c.getString(2));
         o.put("card", c.getString(3));
         o.put("kind", "staff");
         return o;
      } catch (Exception e) {
         return null;
      } finally {
         c.close();
      }
   }

   public void queueVisitorReceive(String visitorCard, int staffId, String staffCard) {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      ContentValues p = new ContentValues();
      p.put("kind", "visitor_receive");
      p.put("card", visitorCard == null ? "" : visitorCard);
      p.put("area_id", staffId);
      p.put("event_time", System.currentTimeMillis() / 1000L);
      try {
         p.put("extra", staffCard == null ? "" : staffCard);
      } catch (Exception ignored) {
      }
      db.insert("pending", (String)null, p);
   }

   public JSONObject clockVisitorLocal(String card, JSONArray visitors) {
      long now = System.currentTimeMillis() / 1000L;
      String day = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date());
      SQLiteDatabase db = this.helper.getWritableDatabase();
      Map<Integer, String> nextStatuses = new LinkedHashMap();
      boolean tooSoon = false;
      boolean sawIn = false;
      boolean sawOut = false;

      for(int i = 0; visitors != null && i < visitors.length(); ++i) {
         JSONObject visitor = visitors.optJSONObject(i);
         int visitorId = visitor == null ? 0 : visitor.optInt("id", 0);
         if (visitorId > 0) {
            Cursor c = null;
            String lastDay = "";
            String lastStatus = "";
            long lastTime = 0L;

            try {
               c = db.rawQuery("SELECT last_day, last_status, last_time FROM local_state WHERE k=?", new String[]{"visitor:" + visitorId});
               if (c.moveToFirst()) {
                  lastDay = c.getString(0);
                  lastStatus = c.getString(1);
                  lastTime = c.isNull(2) ? 0L : c.getLong(2);
               }
            } finally {
               if (c != null) {
                  c.close();
               }

            }

            String nextStatus = "IN";
            if (day.equals(lastDay) && ("IN".equals(lastStatus) || "OUT".equals(lastStatus))) {
               if ("IN".equals(lastStatus) && lastTime > 0L && now - lastTime < 3L) {
                  tooSoon = true;
                  nextStatus = "IN";
               } else {
                  nextStatus = "OUT";
               }
            }

            nextStatuses.put(visitorId, nextStatus);
            if ("OUT".equals(nextStatus)) {
               sawOut = true;
            } else {
               sawIn = true;
            }
         }
      }

      String action = sawIn && sawOut ? "mixed" : (sawOut ? "out" : "in");

      for(int i = 0; visitors != null && i < visitors.length(); ++i) {
         JSONObject visitor = visitors.optJSONObject(i);
         int visitorId = visitor == null ? 0 : visitor.optInt("id", 0);
         if (visitorId > 0) {
            String nextStatus = (String)nextStatuses.get(visitorId);
            if (nextStatus == null) {
               nextStatus = "IN";
            }

            ContentValues st = new ContentValues();
            st.put("k", "visitor:" + visitorId);
            st.put("last_day", day);
            st.put("last_status", nextStatus);
            st.put("last_time", now);
            db.replace("local_state", (String)null, st);
         }
      }

      if ("out".equals(action) || "mixed".equals(action)) {
         Cursor keep = db.rawQuery("SELECT MIN(id) FROM pending WHERE kind='visitor' AND card=?", new String[]{card == null ? "" : card});
         int keepId = 0;

         try {
            if (keep.moveToFirst()) {
               keepId = keep.getInt(0);
            }
         } finally {
            keep.close();
         }

         if (keepId > 0) {
            db.delete("pending", "kind='visitor' AND card=? AND id!=?", new String[]{card == null ? "" : card, Integer.toString(keepId)});
         }
      }

      ContentValues p = new ContentValues();
      p.put("kind", "visitor");
      p.put("card", card == null ? "" : card);
      p.put("area_id", 0);
      p.put("event_time", now);
      db.insert("pending", (String)null, p);
      JSONArray students = this.distinctStudents(visitors);
      JSONObject firstVisitor = visitors != null ? visitors.optJSONObject(0) : null;
      JSONObject firstStudent = students.optJSONObject(0);
      JSONObject json = new JSONObject();

      try {
         json.put("success", 1);
         json.put("offline", 1);
         json.put("allowed", true);
         json.put("kind", "visitor");
         json.put("action", action);
         json.put("too_soon", tooSoon);
         if ("mixed".equals(action)) {
            json.put("message", "Offline visits updated for linked visitors");
         } else if ("out".equals(action)) {
            json.put("message", "Offline visit OUT saved");
         } else if (tooSoon) {
            json.put("message", "Already checked IN");
         } else {
            json.put("message", "Offline visit IN saved");
         }

         json.put("time_label", (new SimpleDateFormat("HH:mm", Locale.US)).format(new Date()));
         json.put("visitors", visitors == null ? new JSONArray() : visitors);
         json.put("students", students);
         if (firstVisitor != null) {
            json.put("visitor", firstVisitor);
         }

         if (firstStudent != null) {
            json.put("student", firstStudent);
         }
      } catch (Exception var27) {
      }

      return json;
   }

   private JSONObject lookupIndex(String card) {
      this.ensureIndex();
      synchronized(this.indexLock) {
         if (this.cardIndex != null && !this.cardIndex.isEmpty()) {
            String n = norm(card);
            JSONObject hit = (JSONObject)this.cardIndex.get(n);
            if (hit == null && card != null) {
               hit = (JSONObject)this.cardIndex.get(card.trim().toUpperCase(Locale.US));
            }

            if (hit == null && n.length() > 0) {
               hit = (JSONObject)this.cardIndex.get(reverseBytes(n));
            }

            if (hit == null && card != null && card.trim().matches("[0-9]+")) {
               hit = (JSONObject)this.cardIndex.get(decimalToHex(card.trim()));
            }

            return hit;
         } else {
            return null;
         }
      }
   }

   private void ensureIndex() {
      synchronized(this.indexLock) {
         if (this.cardIndex == null) {
            this.cardIndex = new HashMap();
            SQLiteDatabase db = this.helper.getReadableDatabase();
            Cursor c = db.rawQuery("SELECT person_id, name, extra, regno, photo, card_raw, studying_mode, card FROM people", (String[])null);

            try {
               while (c.moveToNext()) {
                  JSONObject o = this.personFromCursor(c);
                  String stored = "";
                  try {
                     int cardCol = c.getColumnIndex("card");
                     stored = cardCol >= 0 ? c.getString(cardCol) : "";
                  } catch (Exception ignored) {
                  }
                  this.indexPerson(o, stored);
               }
            } catch (Exception ignored) {
            } finally {
               c.close();
            }

         }
      }
   }

   private JSONObject personFromCursor(Cursor c) throws Exception {
      JSONObject o = new JSONObject();
      o.put("kind", "student");
      o.put("id", c.getInt(0));
      o.put("name", c.getString(1));
      o.put("extra", c.getString(2));
      o.put("class", c.getString(2));
      o.put("regno", c.getString(3));
      o.put("photo", c.getString(4));
      o.put("card", c.getString(5));
      int modeCol = c.getColumnIndex("studying_mode");
      if (modeCol >= 0 && !c.isNull(modeCol)) {
         o.put("studying_mode", c.getInt(modeCol));
      } else if (c.getColumnCount() > 6) {
         try {
            if (!c.isNull(6)) {
               o.put("studying_mode", c.getInt(6));
            }
         } catch (Exception ignored) {
         }
      }
      return o;
   }

   private void remember(JSONObject o) {
      synchronized(this.indexLock) {
         if (this.cardIndex == null) {
            this.cardIndex = new HashMap();
         }

         this.indexPerson(o, o.optString("card", ""));
      }
   }

   private void indexPerson(JSONObject o, String stored) {
      this.indexKey(o, o.optString("card", ""));
      this.indexKey(o, stored);
      String n = norm(o.optString("card", ""));
      if (n.length() >= 4) {
         this.indexKey(o, reverseBytes(n));
      }
      String primary = norm(stored);
      if (primary.length() >= 4) {
         this.indexKey(o, reverseBytes(primary));
      }

      String raw = stored == null ? "" : stored.trim();
      if (raw.matches("[0-9]+")) {
         this.indexKey(o, decimalToHex(raw));
      }
      String cardField = o.optString("card", "").trim();
      if (cardField.matches("[0-9]+")) {
         this.indexKey(o, decimalToHex(cardField));
      }
   }

   private void indexKey(JSONObject o, String key) {
      if (this.cardIndex != null && key != null) {
         String n = norm(key);
         if (n.length() >= 4) {
            this.cardIndex.put(n, o);
         }

         String t = key.trim().toUpperCase(Locale.US);
         if (t.length() >= 4) {
            this.cardIndex.put(t, o);
         }

      }
   }

   public void applyAttendanceToday(JSONArray rows) {
      if (rows != null) {
         String today = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date());
         SQLiteDatabase db = this.helper.getWritableDatabase();
         db.beginTransaction();

         try {
            for(int i = 0; i < rows.length(); ++i) {
               JSONObject o = rows.optJSONObject(i);
               if (o != null) {
                  int studentId = o.optInt("student_id", o.optInt("id", 0));
                  int areaId = o.optInt("area_id", 0);
                  String status = o.optString("status", "").toUpperCase(Locale.US);
                  String day = o.optString("day", today);
                  if (studentId > 0 && areaId > 0 && ("IN".equals(status) || "OUT".equals(status) || "LUNCH".equals(status) || "BACK".equals(status))) {
                     String useDay = day.length() == 0 ? today : day;
                     long timeIn = o.optLong("time_in", 0L);
                     long timeOut = o.optLong("time_out", 0L);
                     long lunchAt = o.optLong("lunch_at", 0L);
                     long backAt = o.optLong("back_at", 0L);
                     long stamp = "OUT".equals(status) ? timeOut : timeIn;
                     String key = "student:" + studentId + ":" + areaId;
                     DayScholarClock.Marks merged;
                     synchronized (this.clockMemory) {
                        DayScholarClock.Marks local = new DayScholarClock.Marks();
                        String mem = markMemory.get(key);
                        if (mem != null && mem.startsWith(useDay + "#")) {
                           local = DayScholarClock.unpack(mem.substring(useDay.length() + 1));
                        }
                        merged = local.copy();
                        if (timeIn > merged.morning) {
                           merged.morning = timeIn;
                        }
                        if (lunchAt > merged.lunch) {
                           merged.lunch = lunchAt;
                        }
                        if (backAt > merged.back) {
                           merged.back = backAt;
                        }
                        if (timeOut > merged.evening) {
                           merged.evening = timeOut;
                        }
                        String packed = this.clockMemory.get(key);
                        long localTime = clockTime(packed);
                        if (useDay.equals(clockDay(packed)) && localTime > stamp) {
                           status = clockStatus(packed);
                           stamp = localTime;
                        } else if (merged.evening > 0L) {
                           status = "OUT";
                           stamp = merged.evening;
                           this.clockMemory.put(key, packClock(useDay, status, stamp));
                        } else if (merged.morning > 0L) {
                           status = "IN";
                           stamp = merged.morning;
                           this.clockMemory.put(key, packClock(useDay, status, stamp));
                        }
                        markMemory.put(key, useDay + "#" + DayScholarClock.pack(merged));
                     }
                     ContentValues st = new ContentValues();
                     st.put("k", key);
                     st.put("last_day", useDay);
                     st.put("last_status", "OUT".equals(status) ? "OUT" : "IN");
                     st.put("last_time", stamp);
                     st.put("marks", DayScholarClock.pack(merged));
                     db.replace("local_state", (String)null, st);
                  }
               }
            }

            db.setTransactionSuccessful();
         } finally {
            db.endTransaction();
         }

      }
   }

   public void applyVisitorVisitsToday(JSONArray rows) {
      if (rows != null) {
         String today = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date());
         SQLiteDatabase db = this.helper.getWritableDatabase();
         db.beginTransaction();

         try {
            for(int i = 0; i < rows.length(); ++i) {
               JSONObject o = rows.optJSONObject(i);
               if (o != null) {
                  int visitorId = o.optInt("visitor_id", 0);
                  String status = o.optString("status", "").toUpperCase(Locale.US);
                  String day = o.optString("day", today);
                  if (visitorId > 0 && ("IN".equals(status) || "OUT".equals(status))) {
                     ContentValues st = new ContentValues();
                     st.put("k", "visitor:" + visitorId);
                     st.put("last_day", day.length() == 0 ? today : day);
                     st.put("last_status", status);
                     db.replace("local_state", (String)null, st);
                  }
               }
            }

            db.setTransactionSuccessful();
         } finally {
            db.endTransaction();
         }

      }
   }

   public int flush() {
      synchronized (flushLock) {
         return this.flushLocked();
      }
   }

   /** Send taps saved on this device as soon as the internet is up. */
   public void pushPendingNow() {
      this.clockWrite.execute(new Runnable() {
         public void run() {
            if (appContext == null || !NetWatch.online(appContext)) {
               return;
            }
            try {
               int sent = flush();
               if (sent > 0) {
                  Log.i("XanderVisitor", "live upload " + sent);
               }
            } catch (Exception e) {
               Log.w("XanderVisitor", "live upload " + e.getMessage());
            }
         }
      });
   }

   private int flushLocked() {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      int sent = this.flushStudents(db);
      sent += this.flushVisitors(db);
      return sent;
   }

   private int flushStudents(SQLiteDatabase db) {
      JSONArray batch = new JSONArray();
      Cursor c = null;
      try {
         c = db.rawQuery("SELECT id, card, area_id, event_time, extra FROM pending WHERE kind IS NULL OR kind='' OR kind='student' ORDER BY id ASC", (String[])null);
      } catch (Exception ignored) {
         return 0;
      }
      try {
         while (c.moveToNext()) {
            int id = c.getInt(0);
            String card = c.getString(1);
            int areaId = c.getInt(2);
            long time = c.getLong(3);
            String extra = c.getColumnCount() > 4 ? c.getString(4) : "";
            int studentId = 0;
            String status = "";
            if (extra != null && extra.startsWith("{")) {
               try {
                  JSONObject ex = new JSONObject(extra);
                  studentId = ex.optInt("student_id", 0);
                  status = ex.optString("status", "");
               } catch (Exception ignored) {
               }
            }
            if (studentId <= 0 && card != null && card.length() >= 4) {
               JSONObject person = this.findByCard(card);
               if (person != null) {
                  studentId = person.optInt("id", 0);
               }
            }
            JSONObject ev = new JSONObject();
            try {
               ev.put("local_id", id);
               ev.put("card", card == null ? "" : card);
               ev.put("area_id", areaId);
               ev.put("time", time);
               ev.put("student_id", studentId);
               ev.put("status", status == null ? "" : status);
               batch.put(ev);
            } catch (Exception ignored) {
            }
         }
      } finally {
         c.close();
      }
      if (batch.length() == 0) {
         return 0;
      }
      int sent = 0;
      int from = 0;
      while (from < batch.length()) {
         JSONArray chunk = new JSONArray();
         int to = Math.min(batch.length(), from + 40);
         for (int i = from; i < to; ++i) {
            JSONObject ev = batch.optJSONObject(i);
            if (ev != null) {
               chunk.put(ev);
            }
         }
         from = to;
         try {
            JSONObject json = XanderApi.uploadAttendance(this.prefs.serverUrl(), this.prefs.schoolId(), chunk.toString());
            sent += this.applyBatchResults(db, json, chunk);
         } catch (Exception batchFail) {
            Log.w("XanderSync", "batch upload: " + batchFail.getMessage());
            sent += this.flushStudentsOneByOne(db, chunk);
            break;
         }
      }
      return sent;
   }

   private int applyBatchResults(SQLiteDatabase db, JSONObject json, JSONArray chunk) {
      if (json == null || !XanderApi.ok(json)) {
         return this.flushStudentsOneByOne(db, chunk);
      }
      JSONArray results = json.optJSONArray("results");
      if (results == null) {
         return this.flushStudentsOneByOne(db, chunk);
      }
      int sent = 0;
      for (int i = 0; i < results.length(); ++i) {
         JSONObject row = results.optJSONObject(i);
         if (row == null) {
            continue;
         }
         int localId = row.optInt("local_id", 0);
         boolean drop = row.optInt("success", 0) == 1
                 || row.optInt("already", 0) == 1
                 || row.optInt("skipped", 0) == 1;
         if (localId > 0 && drop) {
            db.delete("pending", "id=?", new String[]{Integer.toString(localId)});
            ++sent;
         }
      }
      return sent;
   }

   private int flushStudentsOneByOne(SQLiteDatabase db, JSONArray chunk) {
      int sent = 0;
      for (int i = 0; i < chunk.length(); ++i) {
         JSONObject ev = chunk.optJSONObject(i);
         if (ev == null) {
            continue;
         }
         int id = ev.optInt("local_id", 0);
         try {
            JSONObject json = XanderApi.scanStudentEvent(
                    this.prefs.serverUrl(),
                    this.prefs.schoolId(),
                    ev.optString("card", ""),
                    ev.optInt("area_id", 0),
                    ev.optLong("time", 0L),
                    ev.optInt("student_id", 0),
                    ev.optString("status", ""));
            String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
            if (XanderApi.ok(json) || msg.contains("already") || msg.contains("cannot swipe")) {
               if (id > 0) {
                  db.delete("pending", "id=?", new String[]{Integer.toString(id)});
               }
               ++sent;
               this.mirrorStudentStatus(db, json, ev.optInt("area_id", 0), ev.optLong("time", 0L));
            }
         } catch (Exception e) {
            Log.w("XanderSync", "tap upload: " + e.getMessage());
            break;
         }
      }
      return sent;
   }

   private int flushVisitors(SQLiteDatabase db) {
      Cursor c = null;
      try {
         c = db.rawQuery("SELECT id, kind, card, area_id, event_time, extra FROM pending WHERE kind='visitor' OR kind='visitor_receive' ORDER BY id ASC", (String[])null);
      } catch (Exception ignored) {
         return 0;
      }
      int sent = 0;
      try {
         while (c.moveToNext()) {
            int id = c.getInt(0);
            String kind = c.getString(1);
            String card = c.getString(2);
            int areaId = c.getInt(3);
            long time = c.getLong(4);
            String extra = c.getColumnCount() > 5 ? c.getString(5) : "";
            try {
               JSONObject json;
               if ("visitor_receive".equals(kind)) {
                  json = XanderApi.receiveVisitor(this.prefs.serverUrl(), this.prefs.schoolId(), card, extra, areaId);
               } else {
                  json = XanderApi.scanVisitorCard(this.prefs.serverUrl(), this.prefs.schoolId(), card, "android", 0);
               }
               if (XanderApi.ok(json) || XanderApi.message(json, "").toLowerCase(Locale.US).contains("already")) {
                  db.delete("pending", "id=?", new String[]{Integer.toString(id)});
                  ++sent;
                  if ("visitor".equals(kind)) {
                     this.mirrorVisitorStatus(db, json, time);
                  }
               }
            } catch (Exception e) {
               Log.w("XanderSync", "visitor upload: " + e.getMessage());
               break;
            }
         }
      } finally {
         c.close();
      }
      return sent;
   }

   public static String reverseBytes(String hex) {
      if (hex != null && hex.length() >= 4 && hex.length() % 2 == 0) {
         StringBuilder b = new StringBuilder();

         for(int i = hex.length() - 2; i >= 0; i -= 2) {
            b.append(hex.substring(i, i + 2));
         }

         return b.toString();
      } else {
         return hex == null ? "" : hex;
      }
   }

   public static String decimalToHex(String dec) {
      try {
         long v = Long.parseLong(dec);

         String h;
         for(h = Long.toHexString(v).toUpperCase(Locale.US); h.length() < 8; h = "0" + h) {
         }

         return h;
      } catch (Exception var4) {
         return "";
      }
   }

   public static String norm(String card) {
      if (card == null) {
         return "";
      } else {
         StringBuilder b = new StringBuilder();

         for(int i = 0; i < card.length(); ++i) {
            char ch = card.charAt(i);
            if (ch >= '0' && ch <= '9' || ch >= 'A' && ch <= 'F' || ch >= 'a' && ch <= 'f') {
               b.append(Character.toUpperCase(ch));
            }
         }

         return b.toString();
      }
   }

   private void mirrorStudentStatus(SQLiteDatabase db, JSONObject json, int areaId, long time) {
      String st = json.optString("status", "").toUpperCase(Locale.US);
      JSONObject person = json.optJSONObject("person");
      int sid = person == null ? 0 : person.optInt("id", 0);
      JSONObject area = json.optJSONObject("area");
      int aid = area == null ? areaId : area.optInt("id", areaId);
      if (sid > 0 && aid > 0 && ("IN".equals(st) || "OUT".equals(st) || "LUNCH".equals(st) || "BACK".equals(st))) {
         String day = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date(time * 1000L));
         String key = "student:" + sid + ":" + aid;
         DayScholarClock.Marks marks;
         synchronized (this.clockMemory) {
            String mem = markMemory.get(key);
            marks = mem != null && mem.startsWith(day + "#")
                    ? DayScholarClock.unpack(mem.substring(day.length() + 1))
                    : new DayScholarClock.Marks();
            if ("LUNCH".equals(st)) {
               marks.lunch = time;
            } else if ("BACK".equals(st)) {
               marks.back = time;
            } else             if ("OUT".equals(st)) {
               marks.evening = time;
            } else if ("IN".equals(st) && (marks.morning <= 0L || time < marks.morning)) {
               marks.morning = time;
            }
            markMemory.put(key, day + "#" + DayScholarClock.pack(marks));
            this.clockMemory.put(key, packClock(day, "OUT".equals(st) ? "OUT" : "IN", time));
         }
         ContentValues ls = new ContentValues();
         ls.put("k", key);
         ls.put("last_day", day);
         ls.put("last_status", "OUT".equals(st) ? "OUT" : "IN");
         ls.put("last_time", time);
         ls.put("marks", DayScholarClock.pack(marks));
         db.replace("local_state", (String)null, ls);
      }

   }

   private void mirrorVisitorStatus(SQLiteDatabase db, JSONObject json, long time) {
      String action = json.optString("action", "").toLowerCase(Locale.US);
      String status = "out".equals(action) ? "OUT" : "IN";
      String day = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date(time * 1000L));
      JSONArray visitors = json.optJSONArray("visitors");
      if (visitors == null) {
         visitors = new JSONArray();
         JSONObject visitor = json.optJSONObject("visitor");
         if (visitor != null) {
            visitors.put(visitor);
         }
      }

      for(int i = 0; i < visitors.length(); ++i) {
         JSONObject visitor = visitors.optJSONObject(i);
         int visitorId = visitor == null ? 0 : visitor.optInt("id", 0);
         if (visitorId > 0) {
            ContentValues ls = new ContentValues();
            ls.put("k", "visitor:" + visitorId);
            ls.put("last_day", day);
            ls.put("last_status", status);
            ls.put("last_time", time);
            db.replace("local_state", (String)null, ls);
         }
      }

   }

   private JSONArray distinctStudents(JSONArray visitors) {
      JSONArray out = new JSONArray();
      Map<Integer, JSONObject> seen = new LinkedHashMap();

      for(int i = 0; visitors != null && i < visitors.length(); ++i) {
         JSONObject visitor = visitors.optJSONObject(i);
         int studentId = visitor == null ? 0 : visitor.optInt("student_id", 0);
         if (studentId > 0 && !seen.containsKey(studentId)) {
            JSONObject student = new JSONObject();

            try {
               student.put("id", studentId);
               student.put("name", visitor.optString("student_name", ""));
               student.put("class", visitor.optString("student_class", ""));
               student.put("regno", visitor.optString("student_regno", ""));
               JSONObject person = this.findStudentById(studentId);
               if (person != null) {
                  if (student.optString("name", "").length() == 0) {
                     student.put("name", person.optString("name", ""));
                  }

                  if (student.optString("class", "").length() == 0) {
                     student.put("class", person.optString("class", person.optString("extra", "")));
                  }

                  student.put("photo", person.optString("photo", ""));
               }
            } catch (Exception var9) {
            }

            seen.put(studentId, student);
         }
      }

      for(JSONObject student : seen.values()) {
         out.put(student);
      }

      return out;
   }

   public JSONObject findStudentById(int studentId) {
      if (studentId <= 0) {
         return null;
      } else {
         SQLiteDatabase db = this.helper.getReadableDatabase();
         Cursor c = db.rawQuery("SELECT person_id, name, extra, regno, photo, card_raw, studying_mode FROM people WHERE person_id=? LIMIT 1", new String[]{Integer.toString(studentId)});

         JSONObject var4;
         try {
            if (c.moveToFirst()) {
               var4 = this.personFromCursor(c);
               return var4;
            }

            var4 = null;
         } catch (Exception var9) {
            Object var5 = null;
            return (JSONObject)var5;
         } finally {
            c.close();
         }

         return var4;
      }
   }

   private static class Helper extends SQLiteOpenHelper {
      Helper(Context ctx) {
         super(ctx, "xander_student.db", (SQLiteDatabase.CursorFactory)null, 6);
      }

      public void onCreate(SQLiteDatabase db) {
         db.execSQL("CREATE TABLE people (person_id INTEGER, name TEXT, card TEXT, card_raw TEXT, photo TEXT, extra TEXT, regno TEXT, studying_mode INTEGER DEFAULT -1)");
         db.execSQL("CREATE INDEX idx_people_card ON people(card)");
         db.execSQL("CREATE TABLE visitors (visitor_id INTEGER, student_id INTEGER, names TEXT, phone TEXT, relationship TEXT, card TEXT, card_raw TEXT, photo TEXT, student_name TEXT, student_class TEXT, student_regno TEXT)");
         db.execSQL("CREATE INDEX idx_visitors_card ON visitors(card)");
         db.execSQL("CREATE TABLE staff (staff_id INTEGER, name TEXT, post TEXT, card TEXT, card_raw TEXT)");
         db.execSQL("CREATE INDEX idx_staff_card ON staff(card)");
         db.execSQL("CREATE TABLE local_state (k TEXT PRIMARY KEY, last_day TEXT, last_status TEXT, last_time INTEGER DEFAULT 0, marks TEXT)");
         db.execSQL("CREATE TABLE pending (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT DEFAULT 'student', card TEXT, area_id INTEGER, event_time INTEGER, extra TEXT)");
      }

      public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
         if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS visitors (visitor_id INTEGER, student_id INTEGER, names TEXT, phone TEXT, relationship TEXT, card TEXT, card_raw TEXT, photo TEXT, student_name TEXT, student_class TEXT, student_regno TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_visitors_card ON visitors(card)");

            try {
               db.execSQL("ALTER TABLE pending ADD COLUMN kind TEXT DEFAULT 'student'");
            } catch (Exception var6) {
            }
         }

         if (oldVersion < 3) {
            try {
               db.execSQL("ALTER TABLE local_state ADD COLUMN last_time INTEGER DEFAULT 0");
            } catch (Exception var5) {
            }
         }

         if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS staff (staff_id INTEGER, name TEXT, post TEXT, card TEXT, card_raw TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_staff_card ON staff(card)");
            try {
               db.execSQL("ALTER TABLE pending ADD COLUMN extra TEXT");
            } catch (Exception ignored) {
            }
         }

         if (oldVersion < 5) {
            try {
               db.execSQL("ALTER TABLE people ADD COLUMN studying_mode INTEGER DEFAULT -1");
            } catch (Exception ignored) {
            }
         }

         if (oldVersion < 6) {
            try {
               db.execSQL("ALTER TABLE local_state ADD COLUMN marks TEXT");
            } catch (Exception ignored) {
            }
         }

      }
   }
}
