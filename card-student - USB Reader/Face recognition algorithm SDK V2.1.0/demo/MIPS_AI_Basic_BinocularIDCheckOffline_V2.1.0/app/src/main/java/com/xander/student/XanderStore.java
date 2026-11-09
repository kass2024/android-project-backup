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
   /** Students must wait this long after IN before a tap can clock OUT. */
   public static final long CHECKOUT_AFTER_IN_SECONDS = 600L;
   private final Helper helper;
   private final XanderPrefs prefs;
   private final Context appContext;
   private final Object indexLock = new Object();
   private Map<String, JSONObject> cardIndex;
   private final Map<String, ClockSnap> clocks = new HashMap<String, ClockSnap>();
   /** Shared so a sync and a tap see the same morning, lunch, back, and evening marks. */
   private static final Map<String, String> markMemory = new HashMap<String, String>();
   /** visitorId -> yyyy-MM-dd|STATUS|epoch so a saved visitor card does not wait on a write lock. */
   private static final Map<Integer, String> visitorClockMemory = new HashMap<Integer, String>();
   /** visitor card hex -> the visitors saved on that card. Survives a roster rewrite. */
   private static final Map<String, JSONArray> visitorByCard = new HashMap<String, JSONArray>();
   private static volatile boolean visitorMemoryReady;
   private final ExecutorService dbIo = Executors.newSingleThreadExecutor();

   public XanderStore(Context ctx, XanderPrefs prefs) {
      this.appContext = ctx.getApplicationContext();
      this.helper = new Helper(this.appContext);
      this.prefs = prefs;
      this.dbIo.execute(new Runnable() {
         @Override
         public void run() {
            rebuildVisitorMemory();
         }
      });
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
                  v.put("card", cards[0].length() >= 4 ? cards[0] : norm(o.optString("card", "")));
                  v.put("card_raw", cards[1].length() >= 4 ? cards[1] : o.optString("card", ""));
                  v.put("photo", o.optString("photo", ""));
                  v.put("extra", o.optString("class", ""));
                  v.put("regno", o.optString("regno", ""));
                  v.put("studying_mode", studyingModeOf(o));
                  v.put("feeding_paid", feedingPaidOf(o));
                  v.put("feeding_amount", feedingAmountOf(o));
                  v.put("transport_amount", transportAmountOf(o));
                  v.put("transport_required", transportRequiredOf(o));
                  v.put("hostel", o.optString("hostel", ""));
                  putFees(v, o);
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
      if (json.has("prep_rota")) {
         this.replacePrepRota(json.optJSONObject("prep_rota"));
      }
      return changed;
   }

   public void cacheStudentFromScan(String scannedCard, JSONObject json) {
      if (json == null) {
         return;
      }
      JSONObject person = json.optJSONObject("person");
      if (person == null) {
         person = json;
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
      String photo = person.optString("photo", "");
      if (photo.length() >= 8) {
         v.put("photo", photo);
      }
      v.put("card", card);
      v.put("card_raw", reverseBytes(card));
      int mode = studyingModeOf(person);
      if (mode >= 0) {
         v.put("studying_mode", mode);
      }
      if (person.has("feeding_paid")) {
         v.put("feeding_paid", feedingPaidOf(person));
      }
      if (person.has("feeding_amount")) {
         v.put("feeding_amount", feedingAmountOf(person));
      }
      if (person.has("transport_amount")) {
         v.put("transport_amount", transportAmountOf(person));
      }
      if (person.has("transport_required")) {
         v.put("transport_required", transportRequiredOf(person));
      }
      if (person.has("hostel")) {
         v.put("hostel", person.optString("hostel", ""));
      }
      putFees(v, person);
      if (exists) {
         db.update("people", v, "person_id=?", new String[]{Integer.toString(id)});
      } else {
         db.insert("people", (String)null, v);
      }
      try {
         person.put("card", card);
         person.put("id", id);
      } catch (Exception ignored) {
      }
      this.remember(person);
   }

   private int applyStudentCards(JSONArray students) {
      if (students == null) {
         return 0;
      }
      SQLiteDatabase db = this.helper.getWritableDatabase();
      int changed = 0;
      boolean reloadIndex = false;
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
            int mode = studyingModeOf(o);
            Cursor c = db.rawQuery("SELECT card, card_raw, name, studying_mode, feeding_paid, feeding_amount, hostel, fees_json, transport_amount, transport_required FROM people WHERE person_id=? LIMIT 1",
                    new String[]{Integer.toString(id)});
            boolean exists = false;
            String oldCard = "";
            String oldRaw = "";
            String oldName = "";
            int oldMode = -1;
            int oldFeed = -1;
            int oldAmount = -1;
            String oldHostel = "";
            String oldFees = "";
            int oldTransport = -1;
            int oldTransportNeed = -1;
            try {
               if (c.moveToFirst()) {
                  exists = true;
                  oldCard = c.getString(0) == null ? "" : c.getString(0);
                  oldRaw = c.getString(1) == null ? "" : c.getString(1);
                  oldName = c.getString(2) == null ? "" : c.getString(2);
                  if (c.getColumnCount() > 3 && !c.isNull(3)) {
                     oldMode = c.getInt(3);
                  }
                  if (c.getColumnCount() > 4 && !c.isNull(4)) {
                     oldFeed = c.getInt(4);
                  }
                  if (c.getColumnCount() > 5 && !c.isNull(5)) {
                     oldAmount = c.getInt(5);
                  }
                  if (c.getColumnCount() > 6 && c.getString(6) != null) {
                     oldHostel = c.getString(6);
                  }
                  if (c.getColumnCount() > 7 && c.getString(7) != null) {
                     oldFees = c.getString(7);
                  }
                  if (c.getColumnCount() > 8 && !c.isNull(8)) {
                     oldTransport = c.getInt(8);
                  }
                  if (c.getColumnCount() > 9 && !c.isNull(9)) {
                     oldTransportNeed = c.getInt(9);
                  }
               }
            } finally {
               c.close();
            }
            if (exists) {
               boolean sameCard = cards[0].equalsIgnoreCase(oldCard) && cards[1].equalsIgnoreCase(oldRaw);
               boolean sameMode = mode < 0 || mode == oldMode;
               int feed = feedingPaidOf(o);
               boolean sameFeed = !o.has("feeding_paid") || feed == oldFeed;
               int amount = feedingAmountOf(o);
               boolean sameAmount = !o.has("feeding_amount") || amount == oldAmount;
               String hostel = o.optString("hostel", "");
               boolean sameHostel = !o.has("hostel") || hostel.equals(oldHostel);
               String feesJson = feesJsonOf(o);
               boolean sameFees = feesJson.length() == 0 || feesJson.equals(oldFees);
               int transportPaid = transportAmountOf(o);
               boolean sameTransport = !o.has("transport_amount") || transportPaid == oldTransport;
               int transportNeed = transportRequiredOf(o);
               boolean sameTransportNeed = !o.has("transport_required") || transportNeed == oldTransportNeed;
               if (sameCard && sameMode && sameFeed && sameAmount && sameHostel && sameFees
                       && sameTransport && sameTransportNeed) {
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
               if (o.has("feeding_paid")) {
                  v.put("feeding_paid", feedingPaidOf(o));
               }
               if (o.has("feeding_amount")) {
                  v.put("feeding_amount", amount);
               }
               if (o.has("transport_amount")) {
                  v.put("transport_amount", transportPaid);
               }
               if (o.has("transport_required")) {
                  v.put("transport_required", transportNeed);
               }
               if (o.has("hostel")) {
                  v.put("hostel", hostel);
               }
               if (feesJson.length() > 0) {
                  v.put("fees_json", feesJson);
               }
               String name = o.optString("name", "");
               if (name.length() > 0 && oldName.length() == 0) {
                  v.put("name", name);
               }
               String regno = o.optString("regno", "");
               if (regno.length() > 0) {
                  v.put("regno", regno);
               }
               db.update("people", v, "person_id=?", new String[]{Integer.toString(id)});
               if (!sameAmount || !sameHostel || !sameFees || !sameTransport || !sameTransportNeed) {
                  reloadIndex = true;
               } else {
                  this.remember(o);
               }
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
               v.put("feeding_paid", feedingPaidOf(o));
               v.put("feeding_amount", feedingAmountOf(o));
               v.put("transport_amount", transportAmountOf(o));
               v.put("transport_required", transportRequiredOf(o));
               v.put("hostel", o.optString("hostel", ""));
               putFees(v, o);
               db.insert("people", (String)null, v);
               this.remember(o);
               ++changed;
            }
         }
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }
      if (reloadIndex) {
         synchronized (this.indexLock) {
            this.cardIndex = null;
         }
      }
      return changed;
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
               if (stored.equalsIgnoreCase(oldCard)) {
                  continue;
               }
               ContentValues v = new ContentValues();
               v.put("card", stored);
               v.put("card_raw", stored.length() >= 4 ? reverseBytes(stored) : stored);
               db.update("visitors", v, "visitor_id=?", new String[]{Integer.toString(id)});
               ++changed;
            } else {
               ContentValues v = new ContentValues();
               v.put("visitor_id", id);
               v.put("student_id", o.optInt("student_id", 0));
               v.put("names", o.optString("names", o.optString("name", "")));
               v.put("phone", o.optString("phone", ""));
               v.put("relationship", o.optString("relationship", ""));
               v.put("card", stored);
               v.put("card_raw", stored.length() >= 4 ? reverseBytes(stored) : "");
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
            v.put("photo", o.optString("photo", ""));
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

   /** 0 = boarding, 1 = day, -1 = unknown. */
   public static int studyingModeOf(JSONObject o) {
      if (o == null) {
         return -1;
      }
      if (!o.has("studying_mode") && !o.has("mode")) {
         return -1;
      }
      String raw = o.optString("studying_mode", o.optString("mode", "")).trim().toLowerCase(Locale.US);
      if ("0".equals(raw) || raw.contains("board")) {
         return 0;
      }
      if ("1".equals(raw) || raw.contains("day")) {
         return 1;
      }
      return o.optInt("studying_mode", -1);
   }

   /** Boarding (0). Unknown is not treated as boarding. */
   public static boolean isBoarding(JSONObject o) {
      return studyingModeOf(o) == 0;
   }

   /** Known day scholar (1). Unknown is not treated as day. */
   public static boolean isKnownDayScholar(JSONObject o) {
      return studyingModeOf(o) == 1;
   }

   /** 1 paid, 0 not paid, -1 not synced yet. Boarding rows are stored as 1. */
   public static int feedingPaidOf(JSONObject o) {
      if (o == null || !o.has("feeding_paid")) {
         return -1;
      }
      return o.optInt("feeding_paid", -1) == 1 ? 1 : 0;
   }

   /** Current-term feeding paid, in RWF. -1 when the tablet has not synced it yet. */
   public static int feedingAmountOf(JSONObject o) {
      if (o == null || !o.has("feeding_amount") || o.isNull("feeding_amount")) {
         return -1;
      }
      return o.optInt("feeding_amount", -1);
   }

   /** Current-term transport paid, in RWF. -1 when the tablet has not synced it yet. */
   public static int transportAmountOf(JSONObject o) {
      if (o == null || !o.has("transport_amount") || o.isNull("transport_amount")) {
         return -1;
      }
      return o.optInt("transport_amount", -1);
   }

   /** Current-term transport due for a day scholar, in RWF. 0 means no Transport fee. -1 if not synced. */
   public static int transportRequiredOf(JSONObject o) {
      if (o == null || !o.has("transport_required") || o.isNull("transport_required")) {
         return -1;
      }
      return o.optInt("transport_required", -1);
   }

   /**
    * Day scholars may use transport only when this term's Transport fee is paid in full.
    * Boarding students are not checked.
    */
   public static boolean transportMayRide(JSONObject o) {
      if (!isKnownDayScholar(o)) {
         return true;
      }
      int required = transportRequiredOf(o);
      int paid = transportAmountOf(o);
      return required > 0 && paid >= required;
   }

   public static String hostelOf(JSONObject o) {
      if (o == null) {
         return "";
      }
      return o.optString("hostel", "").trim();
   }

   /** Current-term school/other fee brief, or empty when the server did not send one. */
   private static String feesJsonOf(JSONObject o) {
      if (o == null || !o.has("fees") || o.isNull("fees")) {
         return "";
      }
      JSONObject fees = o.optJSONObject("fees");
      if (fees == null || fees.optInt("success", 0) != 1) {
         return "";
      }
      return fees.toString();
   }

   private static void putFees(ContentValues v, JSONObject o) {
      String json = feesJsonOf(o);
      if (json.length() > 0) {
         v.put("fees_json", json);
      }
   }

   /** Keep a live fee refresh for the next swipe, including when the network is gone. */
   public void saveStudentFees(int personId, JSONObject fees) {
      if (personId <= 0 || fees == null || fees.optInt("success", 0) != 1) {
         return;
      }
      ContentValues v = new ContentValues();
      v.put("fees_json", fees.toString());
      this.helper.getWritableDatabase().update("people", v, "person_id=?", new String[]{Integer.toString(personId)});
      synchronized (this.indexLock) {
         this.cardIndex = null;
      }
   }

   /** Day scholar (1) or unknown. Boarding is 0. */
   public static boolean isDayScholar(JSONObject o) {
      return studyingModeOf(o) != 0;
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

   public void warmIndex() {
      this.ensureIndex();
   }

   public JSONObject findByCard(String card) {
      JSONObject hit = this.lookupIndex(card);
      if (hit != null) {
         return hit;
      }
      synchronized(this.indexLock) {
         if (this.cardIndex != null && !this.cardIndex.isEmpty()) {
            return null;
         }
      }
      String n = norm(card);
      if (n.length() != 0 || card != null && card.trim().length() != 0) {
         if (n.length() == 0 && card != null && card.trim().matches("[0-9]+")) {
            n = decimalToHex(card.trim());
         }

         String rev = reverseBytes(n);
         SQLiteDatabase db = this.helper.getReadableDatabase();
         Cursor c = db.rawQuery("SELECT person_id, name, extra, regno, photo, card_raw, studying_mode, feeding_paid, feeding_amount, hostel, fees_json, transport_amount, transport_required FROM people WHERE card=? OR card=? OR card_raw=? OR card_raw=? LIMIT 1", new String[]{n, rev, card == null ? "" : card.trim(), rev});

         JSONObject o;
         try {
            if (c.moveToFirst()) {
               o = this.personFromCursor(c);
               this.remember(o);
               return o;
            }
            return null;
         } catch (Exception var12) {
            return null;
         } finally {
            c.close();
         }
      }
      return null;
   }

   public boolean hasRoster() {
      synchronized(this.indexLock) {
         return this.cardIndex != null && !this.cardIndex.isEmpty();
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
      return this.clockLocal(personId, areaId, card, name, extra, photo, false);
   }

   /**
    * stayIn is only for cafeteria and dormitory. Those taps stay IN.
    * Other locations use the four day-scholar clocks: morning, lunch, back, evening.
    */
   public JSONObject clockLocal(int personId, int areaId, String card, String name, String extra, String photo, boolean stayIn) {
      long now = System.currentTimeMillis() / 1000L;
      String day = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date());
      String key = "student:" + personId + ":" + areaId;
      if (!stayIn) {
         return this.clockFourTaps(personId, areaId, card, name, extra, photo, now, day, key);
      }
      ClockSnap snap;
      synchronized (this.clocks) {
         snap = this.clocks.get(key);
         if (snap == null) {
            snap = this.loadClockSnap(key);
            this.clocks.put(key, snap);
         }
      }
      String lastDay = snap.day;
      String lastStatus = snap.status;
      long lastTime = snap.time;

      if (!day.equals(lastDay)) {
         lastStatus = "";
         lastTime = 0L;
      }

      boolean already = false;
      String status;
      if (stayIn) {
         status = "IN";
         if ("IN".equals(lastStatus) && day.equals(lastDay)) {
            already = true;
            if (lastTime <= 0L) {
               lastTime = now;
            }
         }
      } else if (lastStatus == null || lastStatus.length() == 0) {
         status = "IN";
      } else if ("IN".equals(lastStatus)) {
         if (lastTime <= 0L) {
            lastTime = now;
         }
         if (now - lastTime < CHECKOUT_AFTER_IN_SECONDS) {
            already = true;
            status = "IN";
         } else {
            status = "OUT";
         }
      } else {
         status = "OUT";
      }

      final long persistTime = already ? lastTime : now;
      snap.day = day;
      snap.status = status;
      snap.time = persistTime;
      final boolean persistAlready = already;
      final String persistStatus = status;
      final String persistDay = day;
      final String persistCard = card == null ? "" : card;
      final int persistArea = areaId;
      if (!already) {
         this.enqueueStudentTap(personId, persistArea, persistCard, persistTime, persistStatus);
      }
         this.dbIo.execute(new Runnable() {
            @Override
            public void run() {
               persistClock(key, persistDay, persistStatus, persistTime, persistCard, persistArea, persistAlready);
               if (!persistAlready) {
                  uploadPendingNow();
               }
            }
         });
      JSONObject json = new JSONObject();
      try {
         json.put("success", 1);
         json.put("already", stayIn ? false : already);
         json.put("offline", 1);
         json.put("kind", "student");
         json.put("status", status);
         json.put("time", (new SimpleDateFormat("HH:mm", Locale.US)).format(
                 new Date(((already && !stayIn) ? lastTime : now) * 1000L)));
         json.put("message", (!stayIn && already) ? "Already IN" : ("IN".equals(status) ? "IN time saved" : "OUT time updated"));
         JSONObject person = new JSONObject();
         person.put("id", personId);
         person.put("name", name == null ? "" : name);
         person.put("class", extra == null ? "" : extra);
         person.put("extra", extra == null ? "" : extra);
         person.put("regno", "");
         person.put("photo", photo == null ? "" : photo);
         json.put("person", person);
      } catch (Exception ignored) {
      }
      return json;
   }

   /** Morning any time, lunch and return on the class timetable, evening from the end of the day. */
   private JSONObject clockFourTaps(int personId, int areaId, String card, String name, String extra, String photo, long now, String day, String key) {
      DayScholarClock.Windows windows = DayScholarClock.windowsFor(this.prefs.dayClockJson(), extra);
      DayScholarClock.Marks prior = this.marksFor(key, day);
      DayScholarClock.Decision decision = DayScholarClock.decide(now, windows, prior);
      String status = decision.status;
      long shownTime = decision.shownEpoch > 0L ? decision.shownEpoch : now;
      if (decision.save) {
         String packedMarks = DayScholarClock.pack(decision.marks);
         String visible = "OUT".equals(status) ? "OUT" : "IN";
         synchronized (markMemory) {
            markMemory.put(key, day + "#" + packedMarks);
         }
         ClockSnap snap;
         synchronized (this.clocks) {
            snap = this.clocks.get(key);
            if (snap == null) {
               snap = new ClockSnap();
               this.clocks.put(key, snap);
            }
            snap.day = day;
            snap.status = visible;
            snap.time = now;
            snap.marks = packedMarks;
         }
         this.enqueueFourTap(personId, areaId, card == null ? "" : card, now, status);
         final String writeKey = key;
         final String writeDay = day;
         final String writeVisible = visible;
         final long writeNow = now;
         final String writeMarks = packedMarks;
         this.dbIo.execute(new Runnable() {
            @Override
            public void run() {
               persistFourTap(writeKey, writeDay, writeVisible, writeNow, writeMarks);
               uploadPendingNow();
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
      } catch (Exception ignored) {
      }
      return json;
   }

   private DayScholarClock.Marks marksFor(String key, String day) {
      synchronized (markMemory) {
         String mem = markMemory.get(key);
         if (mem != null && mem.startsWith(day + "#")) {
            return DayScholarClock.unpack(mem.substring(day.length() + 1));
         }
      }
      ClockSnap snap = this.loadClockSnap(key);
      synchronized (this.clocks) {
         this.clocks.put(key, snap);
      }
      if (day.equals(snap.day) && snap.marks != null && snap.marks.length() > 0) {
         synchronized (markMemory) {
            markMemory.put(key, day + "#" + snap.marks);
         }
         return DayScholarClock.unpack(snap.marks);
      }
      DayScholarClock.Marks legacy = new DayScholarClock.Marks();
      if (day.equals(snap.day)) {
         if ("OUT".equals(snap.status)) {
            legacy.morning = snap.time;
            legacy.evening = snap.time;
         } else if ("IN".equals(snap.status)) {
            legacy.morning = snap.time;
         }
      }
      return legacy;
   }

   private void persistFourTap(String key, String day, String visible, long time, String marks) {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      ContentValues st = new ContentValues();
      st.put("k", key);
      st.put("last_day", day);
      st.put("last_status", visible);
      st.put("last_time", time);
      st.put("marks", marks == null ? "" : marks);
      db.replace("local_state", (String) null, st);
   }

   private void enqueueFourTap(int personId, int areaId, String card, long time, String status) {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      this.dropPendingSameStatus(db, card, areaId, personId, status);
      ContentValues p = new ContentValues();
      p.put("kind", "student");
      p.put("card", card);
      p.put("area_id", areaId);
      p.put("event_time", time);
      try {
         JSONObject extra = new JSONObject();
         extra.put("student_id", personId);
         extra.put("status", status == null ? "" : status);
         p.put("extra", extra.toString());
      } catch (Exception ignored) {
      }
      db.insert("pending", (String) null, p);
   }

   /** Keep one unsent row per clock (morning, lunch, back, evening). */
   private void dropPendingSameStatus(SQLiteDatabase db, String card, int areaId, int personId, String status) {
      Cursor old = null;
      try {
         old = db.rawQuery("SELECT id, extra FROM pending WHERE kind='student' AND card=? AND area_id=?",
                 new String[]{card == null ? "" : card, Integer.toString(areaId)});
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

   private ClockSnap loadClockSnap(String key) {
      ClockSnap snap = new ClockSnap();
      Cursor c = null;
      try {
         SQLiteDatabase db = this.helper.getReadableDatabase();
         c = db.rawQuery("SELECT last_day, last_status, last_time, marks FROM local_state WHERE k=?", new String[]{key});
         if (c.moveToFirst()) {
            snap.day = c.getString(0) == null ? "" : c.getString(0);
            snap.status = c.getString(1) == null ? "" : c.getString(1);
            snap.time = c.getColumnCount() > 2 && !c.isNull(2) ? c.getLong(2) : 0L;
            snap.marks = c.getColumnCount() > 3 && c.getString(3) != null ? c.getString(3) : "";
         }
      } catch (Exception ignored) {
      } finally {
         if (c != null) {
            c.close();
         }
      }
      return snap;
   }

   private void persistClock(String key, String day, String status, long time, String card, int areaId, boolean already) {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      ContentValues st = new ContentValues();
      st.put("k", key);
      st.put("last_day", day);
      st.put("last_status", status);
      st.put("last_time", time);
      db.replace("local_state", (String) null, st);
   }

   private void enqueueStudentTap(int personId, int areaId, String card, long time, String status) {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      if ("OUT".equals(status)) {
         Cursor keep = db.rawQuery("SELECT MIN(id) FROM pending WHERE kind='student' AND card=? AND area_id=?",
                 new String[]{card, Integer.toString(areaId)});
         int keepId = 0;
         try {
            if (keep.moveToFirst()) {
               keepId = keep.getInt(0);
            }
         } finally {
            keep.close();
         }
         if (keepId > 0) {
            db.delete("pending", "kind='student' AND card=? AND area_id=? AND id!=?",
                    new String[]{card, Integer.toString(areaId), Integer.toString(keepId)});
         }
      }
      ContentValues p = new ContentValues();
      p.put("kind", "student");
      p.put("card", card);
      p.put("area_id", areaId);
      p.put("event_time", time);
      try {
         JSONObject extra = new JSONObject();
         extra.put("student_id", personId);
         extra.put("status", status == null ? "" : status);
         p.put("extra", extra.toString());
      } catch (Exception ignored) {
      }
      db.insert("pending", (String) null, p);
   }

   private static final class ClockSnap {
      String day = "";
      String status = "";
      long time;
      String marks = "";
   }

   /** null means the saved list is not loaded yet. An empty array means this card is not a visitor card. */
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
               v.put("photo", o.optString("photo", ""));
               db.insert("staff", (String)null, v);
            }
         }
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }
   }

   /** Photo-cache ids for invigilators, kept away from student photo files. */
   public static final int PREP_PHOTO_BASE = 1500000000;
   /** Photo-cache ids for staff location taps, kept away from student and prep photos. */
   public static final int STAFF_PHOTO_BASE = 1600000000;

   public void replacePrepRota(JSONObject rota) {
      if (rota == null) {
         return;
      }
      JSONArray days = rota.optJSONArray("days");
      SQLiteDatabase db = this.helper.getWritableDatabase();
      db.beginTransaction();
      try {
         db.delete("prep_duty", (String) null, (String[]) null);
         if (days != null) {
            for (int i = 0; i < days.length(); i++) {
               JSONObject day = days.optJSONObject(i);
               if (day == null) {
                  continue;
               }
               int dow = day.optInt("day", -1);
               if (dow < 0 || dow > 6) {
                  continue;
               }
               this.insertPrepSlot(db, dow, "morning", day.optJSONArray("morning"));
               this.insertPrepSlot(db, dow, "evening", day.optJSONArray("evening"));
            }
         }
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }
   }

   private void insertPrepSlot(SQLiteDatabase db, int dow, String slot, JSONArray people) {
      if (people == null) {
         return;
      }
      for (int i = 0; i < people.length(); i++) {
         JSONObject o = people.optJSONObject(i);
         if (o == null) {
            continue;
         }
         String cardStored = norm(o.optString("card", ""));
         int id = o.optInt("id", 0);
         if (id <= 0 || cardStored.length() < 4) {
            continue;
         }
         ContentValues v = new ContentValues();
         v.put("day", dow);
         v.put("slot", slot);
         v.put("staff_id", id);
         v.put("name", o.optString("name", ""));
         v.put("post", o.optString("post", ""));
         v.put("card", cardStored);
         v.put("card_raw", reverseBytes(cardStored));
         v.put("photo", o.optString("photo", ""));
         db.insert("prep_duty", (String) null, v);
      }
   }

   public JSONObject findPrepDuty(String card, int day, String slot) {
      String[] variants = CardUid.lookupVariants(card);
      if (variants == null || variants.length == 0) {
         variants = new String[]{card == null ? "" : card};
      }
      for (int i = 0; i < variants.length; i++) {
         JSONObject hit = this.queryPrepDuty(variants[i], day, slot);
         if (hit != null) {
            return hit;
         }
      }
      return this.queryPrepDuty(card, day, slot);
   }

   private JSONObject queryPrepDuty(String card, int day, String slot) {
      String n = norm(card);
      if (n.length() < 4 || day < 0 || slot == null || slot.length() == 0) {
         return null;
      }
      String rev = reverseBytes(n);
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = db.rawQuery(
              "SELECT staff_id, name, post, card, photo FROM prep_duty WHERE day=? AND slot=? AND (card=? OR card=? OR card_raw=? OR card_raw=?) LIMIT 1",
              new String[]{Integer.toString(day), slot, n, rev, card == null ? "" : card.trim(), rev});
      try {
         if (!c.moveToFirst()) {
            return null;
         }
         JSONObject o = new JSONObject();
         o.put("id", c.getInt(0));
         o.put("name", c.getString(1));
         o.put("post", c.getString(2));
         o.put("card", c.getString(3));
         o.put("photo", c.getString(4));
         o.put("kind", "prep");
         return o;
      } catch (Exception e) {
         return null;
      } finally {
         c.close();
      }
   }

   public JSONObject findStaffByAnyCard(String card) {
      String[] variants = CardUid.lookupVariants(card);
      if (variants != null) {
         for (int i = 0; i < variants.length; i++) {
            JSONObject hit = this.findStaffByCard(variants[i]);
            if (hit != null) {
               return hit;
            }
         }
      }
      return this.findStaffByCard(card);
   }

   /**
    * Staff card at any location. First tap is IN. A later tap is OUT after 10 minutes.
    * Cafeteria, dormitory, and evening prep stay IN. Separate from student taps.
    */
   public JSONObject clockStaffLocal(int staffId, int areaId, String card, String name, String post, String photo, boolean stayIn) {
      return this.clockStaffLocal(staffId, areaId, card, name, post, photo, stayIn, false);
   }

   public JSONObject clockStaffLocal(int staffId, int areaId, String card, String name, String post, String photo, boolean stayIn, boolean oncePerDay) {
      long now = System.currentTimeMillis() / 1000L;
      String day = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date());
      String key = "staffloc:" + staffId + ":" + areaId;
      ClockSnap snap;
      synchronized (this.clocks) {
         snap = this.clocks.get(key);
         if (snap == null) {
            snap = this.loadClockSnap(key);
            this.clocks.put(key, snap);
         }
      }
      String lastDay = snap.day;
      String lastStatus = snap.status;
      long lastTime = snap.time;
      if (!day.equals(lastDay)) {
         lastStatus = "";
         lastTime = 0L;
      }
      boolean already = false;
      String status;
      if (stayIn) {
         status = "IN";
         if ("IN".equals(lastStatus)) {
            already = true;
            if (lastTime <= 0L) {
               lastTime = now;
            }
         }
      } else if (lastStatus == null || lastStatus.length() == 0) {
         status = "IN";
      } else if ("IN".equals(lastStatus)) {
         if (lastTime <= 0L) {
            lastTime = now;
         }
         if (now - lastTime < CHECKOUT_AFTER_IN_SECONDS) {
            already = true;
            status = "IN";
         } else {
            status = "OUT";
         }
      } else {
         status = "OUT";
         already = true;
      }
      final long persistTime = already ? lastTime : now;
      if (!already) {
         SQLiteDatabase db = this.helper.getWritableDatabase();
         ContentValues p = new ContentValues();
         p.put("kind", "staff");
         p.put("card", card == null ? "" : card);
         p.put("area_id", areaId);
         p.put("event_time", persistTime);
         try {
            JSONObject extra = new JSONObject();
            extra.put("staff_id", staffId);
            extra.put("status", status);
            p.put("extra", extra.toString());
         } catch (Exception ignored) {
         }
         db.insert("pending", (String) null, p);
         snap.day = day;
         snap.status = status;
         snap.time = persistTime;
         final String writeDay = day;
         final String writeStatus = status;
         this.dbIo.execute(new Runnable() {
            @Override
            public void run() {
               persistClock(key, writeDay, writeStatus, persistTime, card == null ? "" : card, areaId, false);
               uploadPendingNow();
            }
         });
      }
      JSONObject json = new JSONObject();
      try {
         boolean blocked = oncePerDay && already;
         json.put("success", blocked ? 0 : 1);
         if (blocked) {
            json.put("denied", "once");
         }
         json.put("already", already);
         json.put("offline", 1);
         json.put("kind", "staff");
         json.put("status", status);
         json.put("time", (new SimpleDateFormat("HH:mm", Locale.US)).format(new Date((already ? lastTime : now) * 1000L)));
         json.put("message", blocked ? "Already tapped today" : (already ? ("OUT".equals(status) ? "Already OUT" : "Already IN") : ("OUT".equals(status) ? "Staff OUT" : "Staff IN")));
         JSONObject person = new JSONObject();
         person.put("id", staffId);
         person.put("name", name == null ? "" : name);
         person.put("class", post == null ? "" : post);
         person.put("extra", post == null ? "" : post);
         person.put("regno", "");
         person.put("photo", photo == null ? "" : photo);
         person.put("kind", "staff");
         json.put("person", person);
      } catch (Exception ignored) {
      }
      return json;
   }

   /**
    * One tap per invigilator per location per day. Queued for upload when internet returns.
    */
   public JSONObject clockPrepLocal(int staffId, int areaId, String card, String name, String post, String photo) {
      long now = System.currentTimeMillis() / 1000L;
      String day = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date());
      String key = "prep:" + staffId + ":" + areaId;
      ClockSnap snap;
      synchronized (this.clocks) {
         snap = this.clocks.get(key);
         if (snap == null) {
            snap = this.loadClockSnap(key);
            this.clocks.put(key, snap);
         }
      }
      boolean already = day.equals(snap.day) && "IN".equals(snap.status);
      long shown = already && snap.time > 0L ? snap.time : now;
      if (!already) {
         snap.day = day;
         snap.status = "IN";
         snap.time = now;
         SQLiteDatabase db = this.helper.getWritableDatabase();
         ContentValues p = new ContentValues();
         p.put("kind", "prep");
         p.put("card", card == null ? "" : card);
         p.put("area_id", areaId);
         p.put("event_time", now);
         db.insert("pending", (String) null, p);
         final String persistDay = day;
         final long persistTime = now;
         this.dbIo.execute(new Runnable() {
            @Override
            public void run() {
               persistClock(key, persistDay, "IN", persistTime, card == null ? "" : card, areaId, false);
               uploadPendingNow();
            }
         });
      }
      JSONObject json = new JSONObject();
      try {
         json.put("success", 1);
         json.put("already", already);
         json.put("offline", 1);
         json.put("kind", "prep");
         json.put("status", "IN");
         json.put("time", (new SimpleDateFormat("HH:mm", Locale.US)).format(new Date(shown * 1000L)));
         json.put("message", already ? "Already present" : "Invigilator present");
         JSONObject person = new JSONObject();
         person.put("id", staffId);
         person.put("name", name == null ? "" : name);
         person.put("class", post == null ? "" : post);
         person.put("extra", post == null ? "" : post);
         person.put("regno", "");
         person.put("photo", photo == null ? "" : photo);
         person.put("kind", "prep");
         json.put("person", person);
      } catch (Exception ignored) {
      }
      return json;
   }

   /** Student roster plus invigilator photos, using separate cache ids. */
   public JSONArray rosterWithPrepPhotos(JSONArray students) {
      JSONArray out = new JSONArray();
      if (students != null) {
         for (int i = 0; i < students.length(); i++) {
            JSONObject o = students.optJSONObject(i);
            if (o != null) {
               out.put(o);
            }
         }
      }
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = null;
      try {
         c = db.rawQuery("SELECT staff_id, photo FROM prep_duty WHERE photo IS NOT NULL AND photo != '' GROUP BY staff_id", (String[]) null);
         while (c.moveToNext()) {
            int id = c.getInt(0);
            if (id <= 0) {
               continue;
            }
            JSONObject o = new JSONObject();
            o.put("id", PREP_PHOTO_BASE + id);
            o.put("photo", c.getString(1));
            out.put(o);
         }
      } catch (Exception ignored) {
      } finally {
         if (c != null) {
            c.close();
         }
      }
      return out;
   }

   public JSONObject findStaffByCard(String card) {
      String n = norm(card);
      if (n.length() < 4) {
         return null;
      }
      String rev = reverseBytes(n);
      SQLiteDatabase db = this.helper.getReadableDatabase();
      Cursor c = db.rawQuery(
              "SELECT staff_id, name, post, card, photo FROM staff WHERE card=? OR card=? OR card_raw=? OR card_raw=? LIMIT 1",
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
         o.put("photo", c.getColumnCount() > 4 && c.getString(4) != null ? c.getString(4) : "");
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
      this.dbIo.execute(new Runnable() {
         @Override
         public void run() {
            uploadPendingNow();
         }
      });
   }

   public JSONObject clockVisitorLocal(String card, JSONArray visitors) {
      long now = System.currentTimeMillis() / 1000L;
      String day = (new SimpleDateFormat("yyyy-MM-dd", Locale.US)).format(new Date());
      Map<Integer, String> nextStatuses = new LinkedHashMap();
      boolean tooSoon = false;
      boolean sawIn = false;
      boolean sawOut = false;

      for(int i = 0; visitors != null && i < visitors.length(); ++i) {
         JSONObject visitor = visitors.optJSONObject(i);
         int visitorId = visitor == null ? 0 : visitor.optInt("id", 0);
         if (visitorId > 0) {
            String lastDay = "";
            String lastStatus = "";
            long lastTime = 0L;
            String mem;
            synchronized (visitorClockMemory) {
               mem = visitorClockMemory.get(Integer.valueOf(visitorId));
            }
            if (mem == null) {
               Cursor c = null;
               try {
                  SQLiteDatabase db = this.helper.getReadableDatabase();
                  c = db.rawQuery("SELECT last_day, last_status, last_time FROM local_state WHERE k=?", new String[]{"visitor:" + visitorId});
                  if (c.moveToFirst()) {
                     lastDay = c.getString(0) == null ? "" : c.getString(0);
                     lastStatus = c.getString(1) == null ? "" : c.getString(1);
                     lastTime = c.isNull(2) ? 0L : c.getLong(2);
                  }
               } finally {
                  if (c != null) {
                     c.close();
                  }
               }
            } else {
               int bar = mem.indexOf('|');
               int bar2 = bar < 0 ? -1 : mem.indexOf('|', bar + 1);
               if (bar > 0 && bar2 > bar) {
                  lastDay = mem.substring(0, bar);
                  lastStatus = mem.substring(bar + 1, bar2);
                  try {
                     lastTime = Long.parseLong(mem.substring(bar2 + 1));
                  } catch (Exception ignored) {
                     lastTime = 0L;
                  }
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

            nextStatuses.put(Integer.valueOf(visitorId), nextStatus);
            synchronized (visitorClockMemory) {
               visitorClockMemory.put(Integer.valueOf(visitorId), day + "|" + nextStatus + "|" + now);
            }
            if ("OUT".equals(nextStatus)) {
               sawOut = true;
            } else {
               sawIn = true;
            }
         }
      }

      String action = sawIn && sawOut ? "mixed" : (sawOut ? "out" : "in");
      final String persistCard = card == null ? "" : card;
      final String persistDay = day;
      final long persistNow = now;
      final String persistAction = action;
      final Map<Integer, String> persistStatuses = nextStatuses;
      this.dbIo.execute(new Runnable() {
         @Override
         public void run() {
            persistVisitorClock(persistCard, persistDay, persistNow, persistAction, persistStatuses);
            uploadPendingNow();
         }
      });
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

   private void persistVisitorClock(String card, String day, long now, String action, Map<Integer, String> nextStatuses) {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      if (nextStatuses != null) {
         for (Map.Entry<Integer, String> entry : nextStatuses.entrySet()) {
            int visitorId = entry.getKey().intValue();
            String nextStatus = entry.getValue() == null ? "IN" : entry.getValue();
            ContentValues st = new ContentValues();
            st.put("k", "visitor:" + visitorId);
            st.put("last_day", day);
            st.put("last_status", nextStatus);
            st.put("last_time", now);
            db.replace("local_state", (String) null, st);
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
      db.insert("pending", (String) null, p);
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
            Cursor c = db.rawQuery("SELECT person_id, name, extra, regno, photo, card_raw, studying_mode, card, feeding_paid, feeding_amount, hostel, fees_json, transport_amount, transport_required FROM people", (String[])null);

            try {
               JSONObject o;
               String stored;
               try {
                  for(; c.moveToNext(); this.indexPerson(o, stored)) {
                     o = this.personFromCursor(c);
                     stored = "";

                     try {
                        int cardCol = c.getColumnIndex("card");
                        stored = cardCol >= 0 ? c.getString(cardCol) : c.getString(5);
                     } catch (Exception var13) {
                     }
                  }
               } catch (Exception var14) {
               }
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
      }
      int feedCol = c.getColumnIndex("feeding_paid");
      if (feedCol >= 0 && !c.isNull(feedCol)) {
         o.put("feeding_paid", c.getInt(feedCol));
      }
      int amountCol = c.getColumnIndex("feeding_amount");
      if (amountCol >= 0 && !c.isNull(amountCol)) {
         o.put("feeding_amount", c.getInt(amountCol));
      }
      int transportCol = c.getColumnIndex("transport_amount");
      if (transportCol >= 0 && !c.isNull(transportCol)) {
         o.put("transport_amount", c.getInt(transportCol));
      }
      int transportNeedCol = c.getColumnIndex("transport_required");
      if (transportNeedCol >= 0 && !c.isNull(transportNeedCol)) {
         o.put("transport_required", c.getInt(transportNeedCol));
      }
      int hostelCol = c.getColumnIndex("hostel");
      if (hostelCol >= 0 && c.getString(hostelCol) != null) {
         o.put("hostel", c.getString(hostelCol));
      }
      int feesCol = c.getColumnIndex("fees_json");
      if (feesCol >= 0 && c.getString(feesCol) != null && c.getString(feesCol).length() > 1) {
         try {
            o.put("fees", new JSONObject(c.getString(feesCol)));
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

      String raw = stored == null ? "" : stored.trim();
      if (raw.matches("[0-9]+")) {
         this.indexKey(o, decimalToHex(raw));
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
                     synchronized (markMemory) {
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
                        markMemory.put(key, useDay + "#" + DayScholarClock.pack(merged));
                     }
                     if (merged.evening > 0L) {
                        status = "OUT";
                        stamp = merged.evening;
                     } else if (merged.morning > 0L) {
                        status = "IN";
                        if (stamp <= 0L) {
                           stamp = merged.morning;
                        }
                     }
                     ContentValues st = new ContentValues();
                     st.put("k", key);
                     st.put("last_day", useDay);
                     st.put("last_status", "OUT".equals(status) ? "OUT" : "IN");
                     st.put("last_time", stamp);
                     st.put("marks", DayScholarClock.pack(merged));
                     db.replace("local_state", (String)null, st);
                     ClockSnap snap = new ClockSnap();
                     snap.day = useDay;
                     snap.status = "OUT".equals(status) ? "OUT" : "IN";
                     snap.time = stamp;
                     snap.marks = DayScholarClock.pack(merged);
                     synchronized (this.clocks) {
                        this.clocks.put(key, snap);
                     }
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

   /** One upload at a time so a tap is not posted twice. */
   private static final Object flushLock = new Object();

   /** Push saved taps as soon as the phone is online. Does not wait for a roster sync. */
   private void uploadPendingNow() {
      if (!NetWatch.online(this.appContext)) {
         return;
      }
      try {
         int sent = this.flush();
         if (sent > 0) {
            android.util.Log.i("XanderStore", "live upload " + sent);
         }
      } catch (Exception e) {
         android.util.Log.w("XanderStore", "live upload " + e.getMessage());
      }
   }

   public int flush() {
      synchronized (flushLock) {
         return this.flushPending();
      }
   }

   private int flushPending() {
      SQLiteDatabase db = this.helper.getWritableDatabase();
      Cursor c = null;
      try {
         c = db.rawQuery("SELECT id, kind, card, area_id, event_time, extra FROM pending ORDER BY id ASC", (String[])null);
      } catch (Exception ignored) {
         c = db.rawQuery("SELECT id, kind, card, area_id, event_time FROM pending ORDER BY id ASC", (String[])null);
      }
      int sent = 0;
      JSONArray studentBatch = new JSONArray();
      java.util.ArrayList<Object[]> otherRows = new java.util.ArrayList<Object[]>();

      try {
         while (c.moveToNext()) {
            int id = c.getInt(0);
            String kind = c.getString(1);
            String card = c.getString(2);
            int areaId = c.getInt(3);
            long time = c.getLong(4);
            String extra = c.getColumnCount() > 5 ? c.getString(5) : "";
            if (kind == null || kind.length() == 0 || "student".equals(kind)) {
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
               try {
                  JSONObject ev = new JSONObject();
                  ev.put("local_id", id);
                  ev.put("card", card == null ? "" : card);
                  ev.put("area_id", areaId);
                  ev.put("time", time);
                  ev.put("student_id", studentId);
                  ev.put("status", status);
                  studentBatch.put(ev);
               } catch (Exception ignored) {
               }
            } else {
               otherRows.add(new Object[]{Integer.valueOf(id), kind, card, Integer.valueOf(areaId), Long.valueOf(time), extra});
            }
         }
      } finally {
         c.close();
      }

      if (studentBatch.length() > 0) {
         sent += this.flushStudentBatch(db, studentBatch);
      }
      for (int i = 0; i < otherRows.size(); i++) {
         Object[] row = otherRows.get(i);
         int id = ((Integer) row[0]).intValue();
         String kind = (String) row[1];
         String card = (String) row[2];
         int areaId = ((Integer) row[3]).intValue();
         long time = ((Long) row[4]).longValue();
         String extra = (String) row[5];
         try {
            JSONObject json;
            if ("visitor".equals(kind)) {
               json = XanderApi.scanVisitorCardFlush(this.prefs.serverUrl(), this.prefs.schoolId(), card);
            } else if ("visitor_receive".equals(kind)) {
               json = XanderApi.receiveVisitor(this.prefs.serverUrl(), this.prefs.schoolId(), card, extra, areaId);
            } else if ("staff".equals(kind)) {
               String status = "";
               if (extra != null && extra.startsWith("{")) {
                  try {
                     status = new JSONObject(extra).optString("status", "");
                  } catch (Exception ignored) {
                  }
               }
               json = XanderApi.scanCardFlush(this.prefs.serverUrl(), this.prefs.schoolId(), card, areaId, time, 0, status);
            } else {
               json = XanderApi.scanCardFlush(this.prefs.serverUrl(), this.prefs.schoolId(), card, areaId, time, 0, "");
            }
            if (flushAcked(json)) {
               db.delete("pending", "id=?", new String[]{Integer.toString(id)});
               ++sent;
               if ("visitor".equals(kind)) {
                  this.mirrorVisitorStatus(db, json, time);
               }
            }
         } catch (Exception e) {
            android.util.Log.w("XanderStore", "flush row " + id + ": " + e.getMessage());
            if (isOfflineError(e)) {
               break;
            }
         }
      }
      return sent;
   }

   private int flushStudentBatch(SQLiteDatabase db, JSONArray events) {
      try {
         JSONObject json = XanderApi.scanBatch(this.prefs.serverUrl(), this.prefs.schoolId(), events);
         if (XanderApi.ok(json)) {
            JSONArray results = json.optJSONArray("results");
            if (results != null) {
               int sent = 0;
               for (int i = 0; i < results.length(); i++) {
                  JSONObject r = results.optJSONObject(i);
                  if (r == null) {
                     continue;
                  }
                  if (flushAcked(r)) {
                     int localId = r.optInt("local_id", 0);
                     if (localId > 0) {
                        db.delete("pending", "id=?", new String[]{Integer.toString(localId)});
                        ++sent;
                     }
                  }
               }
               return sent;
            }
         }
      } catch (Exception e) {
         android.util.Log.w("XanderStore", "batch flush: " + e.getMessage());
      }
      int sent = 0;
      for (int i = 0; i < events.length(); i++) {
         JSONObject ev = events.optJSONObject(i);
         if (ev == null) {
            continue;
         }
         int id = ev.optInt("local_id", 0);
         try {
            JSONObject json = XanderApi.scanCardFlush(
                    this.prefs.serverUrl(),
                    this.prefs.schoolId(),
                    ev.optString("card", ""),
                    ev.optInt("area_id", 0),
                    ev.optLong("time", 0L),
                    ev.optInt("student_id", 0),
                    ev.optString("status", ""));
            if (flushAcked(json)) {
               db.delete("pending", "id=?", new String[]{Integer.toString(id)});
               ++sent;
               this.mirrorStudentStatus(db, json, ev.optInt("area_id", 0), ev.optLong("time", 0L));
            }
         } catch (Exception e) {
            android.util.Log.w("XanderStore", "flush student " + id + ": " + e.getMessage());
            if (isOfflineError(e)) {
               break;
            }
         }
      }
      return sent;
   }

   private static boolean flushAcked(JSONObject json) {
      if (json == null) {
         return false;
      }
      if (XanderApi.ok(json) || json.optInt("already", 0) == 1 || json.optInt("skipped", 0) == 1) {
         return true;
      }
      String denied = json.optString("denied", "");
      if (denied.length() > 0) {
         return true;
      }
      String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
      return msg.contains("already") || msg.contains("student in") || msg.contains("student out");
   }

   private static boolean isOfflineError(Exception e) {
      String m = e == null || e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.US);
      return m.contains("unable to resolve") || m.contains("failed to connect")
              || m.contains("timeout") || m.contains("timed out") || m.contains("econnreset")
              || m.contains("network is unreachable") || m.contains("ssl handshake");
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
         synchronized (markMemory) {
            String mem = markMemory.get(key);
            marks = mem != null && mem.startsWith(day + "#")
                    ? DayScholarClock.unpack(mem.substring(day.length() + 1))
                    : new DayScholarClock.Marks();
            if ("LUNCH".equals(st)) {
               marks.lunch = time;
            } else if ("BACK".equals(st)) {
               marks.back = time;
            } else if ("OUT".equals(st)) {
               marks.evening = time;
               if (marks.morning <= 0L) {
                  marks.morning = time;
               }
            } else if (marks.morning <= 0L || time < marks.morning) {
               marks.morning = time;
            }
            markMemory.put(key, day + "#" + DayScholarClock.pack(marks));
         }
         ContentValues ls = new ContentValues();
         ls.put("k", key);
         ls.put("last_day", day);
         ls.put("last_status", "OUT".equals(st) ? "OUT" : "IN");
         ls.put("last_time", time);
         ls.put("marks", DayScholarClock.pack(marks));
         db.replace("local_state", (String)null, ls);
         ClockSnap snap = new ClockSnap();
         snap.day = day;
         snap.status = "OUT".equals(st) ? "OUT" : "IN";
         snap.time = time;
         snap.marks = DayScholarClock.pack(marks);
         synchronized (this.clocks) {
            this.clocks.put(key, snap);
         }
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
         Cursor c = db.rawQuery("SELECT person_id, name, extra, regno, photo, card_raw, studying_mode, feeding_paid, feeding_amount, hostel, fees_json, transport_amount, transport_required FROM people WHERE person_id=? LIMIT 1", new String[]{Integer.toString(studentId)});

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
         super(ctx, "xander_student.db", (SQLiteDatabase.CursorFactory)null, 12);
      }

      public void onConfigure(SQLiteDatabase db) {
         db.enableWriteAheadLogging();
      }

      public void onCreate(SQLiteDatabase db) {
         db.execSQL("CREATE TABLE people (person_id INTEGER, name TEXT, card TEXT, card_raw TEXT, photo TEXT, extra TEXT, regno TEXT, studying_mode INTEGER DEFAULT -1, feeding_paid INTEGER DEFAULT -1, feeding_amount INTEGER DEFAULT -1, transport_amount INTEGER DEFAULT -1, transport_required INTEGER DEFAULT -1, hostel TEXT DEFAULT '', fees_json TEXT DEFAULT '')");
         db.execSQL("CREATE INDEX idx_people_card ON people(card)");
         db.execSQL("CREATE TABLE visitors (visitor_id INTEGER, student_id INTEGER, names TEXT, phone TEXT, relationship TEXT, card TEXT, card_raw TEXT, photo TEXT, student_name TEXT, student_class TEXT, student_regno TEXT)");
         db.execSQL("CREATE INDEX idx_visitors_card ON visitors(card)");
         db.execSQL("CREATE TABLE staff (staff_id INTEGER, name TEXT, post TEXT, card TEXT, card_raw TEXT, photo TEXT)");
         db.execSQL("CREATE INDEX idx_staff_card ON staff(card)");
         db.execSQL("CREATE TABLE local_state (k TEXT PRIMARY KEY, last_day TEXT, last_status TEXT, last_time INTEGER DEFAULT 0, marks TEXT)");
         db.execSQL("CREATE TABLE pending (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT DEFAULT 'student', card TEXT, area_id INTEGER, event_time INTEGER, extra TEXT)");
         db.execSQL("CREATE TABLE prep_duty (day INTEGER, slot TEXT, staff_id INTEGER, name TEXT, post TEXT, card TEXT, card_raw TEXT, photo TEXT)");
         db.execSQL("CREATE INDEX idx_prep_duty_card ON prep_duty(card, day, slot)");
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
               db.execSQL("ALTER TABLE people ADD COLUMN feeding_paid INTEGER DEFAULT -1");
            } catch (Exception ignored) {
            }
         }

         if (oldVersion < 7) {
            try {
               db.execSQL("ALTER TABLE people ADD COLUMN feeding_amount INTEGER DEFAULT -1");
            } catch (Exception ignored) {
            }
            try {
               db.execSQL("ALTER TABLE people ADD COLUMN hostel TEXT DEFAULT ''");
            } catch (Exception ignored) {
            }
         }

         if (oldVersion < 8) {
            try {
               db.execSQL("ALTER TABLE people ADD COLUMN fees_json TEXT DEFAULT ''");
            } catch (Exception ignored) {
            }
         }

         if (oldVersion < 9) {
            try {
               db.execSQL("ALTER TABLE people ADD COLUMN transport_amount INTEGER DEFAULT -1");
            } catch (Exception ignored) {
            }
            try {
               db.execSQL("ALTER TABLE people ADD COLUMN transport_required INTEGER DEFAULT -1");
            } catch (Exception ignored) {
            }
         }

         if (oldVersion < 10) {
            db.execSQL("CREATE TABLE IF NOT EXISTS prep_duty (day INTEGER, slot TEXT, staff_id INTEGER, name TEXT, post TEXT, card TEXT, card_raw TEXT, photo TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_prep_duty_card ON prep_duty(card, day, slot)");
         }

         if (oldVersion < 11) {
            try {
               db.execSQL("ALTER TABLE local_state ADD COLUMN marks TEXT");
            } catch (Exception ignored) {
            }
         }

         if (oldVersion < 12) {
            try {
               db.execSQL("ALTER TABLE staff ADD COLUMN photo TEXT");
            } catch (Exception ignored) {
            }
         }

      }
   }
}
