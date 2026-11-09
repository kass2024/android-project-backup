package com.xander.visiting;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class LocalStore extends SQLiteOpenHelper {
   public LocalStore(Context ctx) {
      super(ctx.getApplicationContext(), "gate_offline.db", (SQLiteDatabase.CursorFactory)null, 2);
      this.resetStaleGoneList();
      try {
         this.getWritableDatabase().execSQL("ALTER TABLE cards ADD COLUMN permit_from INTEGER DEFAULT 0");
      } catch (Exception ignored) {
      }
      try {
         this.getWritableDatabase().execSQL("ALTER TABLE cards ADD COLUMN permit_until INTEGER DEFAULT 0");
      } catch (Exception ignored) {
      }
   }

   private void resetStaleGoneList() {
      if (!"1".equals(this.getMeta("gone_exact"))) {
         SQLiteDatabase db = this.getWritableDatabase();
         this.putMeta(db, "gone_cards", new JSONArray());
         this.putMeta(db, "gone_exact", "1");
      }
   }

   public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE cards (uid TEXT PRIMARY KEY, kind TEXT, owner_id INTEGER, name TEXT, post TEXT, photo TEXT, last_status TEXT, permit_from INTEGER, permit_until INTEGER)");
      db.execSQL("CREATE TABLE inside (card TEXT PRIMARY KEY, payload TEXT)");
      db.execSQL("CREATE TABLE queue (id INTEGER PRIMARY KEY AUTOINCREMENT, op TEXT, payload TEXT, event_time INTEGER, created_at INTEGER)");
      db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)");
   }

   public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      db.execSQL("DROP TABLE IF EXISTS cards");
      db.execSQL("DROP TABLE IF EXISTS inside");
      db.execSQL("DROP TABLE IF EXISTS queue");
      db.execSQL("DROP TABLE IF EXISTS meta");
      this.onCreate(db);
   }

   public static String clean(String uid) {
      return RfidWedge.clean(uid);
   }

   public static boolean sameCard(String a, String b) {
      return CardUid.same(a, b);
   }

   public void saveSnapshot(JSONObject json) {
      if (json != null) {
         SQLiteDatabase db = this.getWritableDatabase();
         db.beginTransaction();

         try {
            JSONArray cards = json.optJSONArray("cards");
            if (cards != null) {
               db.delete("cards", (String)null, (String[])null);

               for(int i = 0; i < cards.length(); ++i) {
                  this.upsertCard(db, cards.optJSONObject(i));
               }
            }

            JSONObject board = json.optJSONObject("board");
            if (board != null) {
               this.mergeRemoteInside(db, board);
            }

            this.putMeta(db, "kpi", json.optJSONObject("kpi"));
            this.putMeta(db, "recent", json.optJSONArray("recent"));
            db.setTransactionSuccessful();
         } finally {
            db.endTransaction();
         }

         this.replayPendingInside();
         this.dedupeInside();
      }
   }

   public void rememberCard(JSONObject row) {
      if (row != null) {
         SQLiteDatabase db = this.getWritableDatabase();
         db.beginTransaction();

         try {
            this.upsertCard(db, row);
            db.setTransactionSuccessful();
         } finally {
            db.endTransaction();
         }

      }
   }

   public void markStaffStatus(int ownerId, String status) {
      if (ownerId > 0) {
         ContentValues cv = new ContentValues();
         cv.put("last_status", status == null ? "" : status);
         this.getWritableDatabase().update("cards", cv, "kind=? AND owner_id=?", new String[]{"staff", String.valueOf(ownerId)});
      }
   }

   private void upsertCard(SQLiteDatabase db, JSONObject row) {
      if (row != null) {
         String kind = row.optString("kind");
         int ownerId = row.optInt("id");
         String name = row.optString("name");
         String post = row.optString("post");
         String photo = row.optString("photo");
         String lastStatus = row.optString("last_status");
         boolean canOut = row.optBoolean("can_out") || row.optInt("can_out", 0) == 1;
         if ("IN".equalsIgnoreCase(lastStatus) && canOut) {
            lastStatus = "READY_OUT";
         }

         JSONArray extra = row.optJSONArray("card_variants");
         LinkedHashSet<String> uids = new LinkedHashSet();

         for(String uid : CardUid.variants(row.optString("card"))) {
            uids.add(uid);
         }

         if (extra != null) {
            for(int i = 0; i < extra.length(); ++i) {
               for(String uid : CardUid.variants(extra.optString(i))) {
                  uids.add(uid);
               }
            }
         }

         for(String uid : uids) {
            if (uid.length() >= 4) {
               ContentValues cv = new ContentValues();
               cv.put("uid", uid);
               cv.put("kind", kind);
               cv.put("owner_id", ownerId);
               cv.put("name", name);
               cv.put("post", post);
               cv.put("photo", photo);
               cv.put("last_status", lastStatus);
               cv.put("permit_from", row.optLong("permit_from"));
               cv.put("permit_until", row.optLong("permit_until"));
               db.insertWithOnConflict("cards", (String)null, cv, 5);
            }
         }

      }
   }

   public void applyBoard(JSONObject board) {
      SQLiteDatabase db = this.getWritableDatabase();
      db.beginTransaction();

      try {
         this.mergeRemoteInside(db, board);
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }

      this.replayPendingInside();
      this.dedupeInside();
   }

   public void replayPendingInside() {
      JSONArray pending = this.pendingEvents();

      for(int i = 0; i < pending.length(); ++i) {
         JSONObject row = pending.optJSONObject(i);
         if (row != null) {
            String op = row.optString("op");
            if ("checkin".equals(op)) {
               this.rememberInside(row);
            } else if ("checkout".equals(op)) {
               this.forgetInside(row.optString("card"));
            }
         }
      }

      this.dedupeInside();
   }

   private void mergeRemoteInside(SQLiteDatabase db, JSONObject board) {
      JSONArray rows = board != null ? board.optJSONArray("inside") : null;
      if (rows != null) {
         for(int i = 0; i < rows.length(); ++i) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null) {
               String card = clean(row.optString("card"));
               if (card.length() >= 4 && !this.isGone(card) && !this.hasInside(db, card)) {
                  this.putInside(db, card, row);
               }
            }
         }
      }

      this.dropGone(db);
   }

   private boolean hasInside(SQLiteDatabase db, String card) {
      Cursor c = db.rawQuery("SELECT card FROM inside", (String[])null);

      try {
         while(c.moveToNext()) {
            if (clean(c.getString(0)).equals(clean(card)) || sameCard(card, c.getString(0))) {
               boolean var4 = true;
               return var4;
            }
         }

         return false;
      } finally {
         c.close();
      }
   }

   private void putInside(SQLiteDatabase db, String card, JSONObject row) {
      try {
         row.put("card", card);
      } catch (Exception var5) {
      }

      ContentValues cv = new ContentValues();
      cv.put("card", card);
      cv.put("payload", row.toString());
      db.insertWithOnConflict("inside", (String)null, cv, 5);
   }

   private void dropGone(SQLiteDatabase db) {
      Cursor c = db.rawQuery("SELECT card FROM inside", (String[])null);

      try {
         while(c.moveToNext()) {
            String card = c.getString(0);
            if (this.isGone(card)) {
               db.delete("inside", "card=?", new String[]{card});
            }
         }
      } finally {
         c.close();
      }

   }

   private boolean isGone(String card) {
      String uid = clean(card);
      if (uid.length() < 4) {
         return false;
      } else {
         JSONArray gone = this.getJsonArray("gone_cards");

         for(int i = 0; i < gone.length(); ++i) {
            if (uid.equals(clean(gone.optString(i)))) {
               return true;
            }
         }

         return false;
      }
   }

   private void markGone(String card) {
      card = clean(card);
      if (card.length() >= 4 && !this.isGone(card)) {
         JSONArray gone = this.getJsonArray("gone_cards");
         gone.put(card);
         this.putMeta(this.getWritableDatabase(), "gone_cards", gone);
      }
   }

   private void clearGone(String card) {
      String uid = clean(card);
      JSONArray gone = this.getJsonArray("gone_cards");
      JSONArray next = new JSONArray();

      for(int i = 0; i < gone.length(); ++i) {
         if (!uid.equals(clean(gone.optString(i)))) {
            next.put(gone.optString(i));
         }
      }

      this.putMeta(this.getWritableDatabase(), "gone_cards", next);
   }

   public int insideCount() {
      return this.insideList().length();
   }

   public JSONObject findCard(String raw) {
      String uid = clean(raw);
      if (uid.length() < 4) {
         return null;
      } else {
         Cursor c = this.getReadableDatabase().rawQuery("SELECT uid,kind,owner_id,name,post,photo,last_status,permit_from,permit_until FROM cards", (String[])null);

         try {
            while(c.moveToNext()) {
               if (sameCard(uid, c.getString(0))) {
                  JSONObject row = new JSONObject();
                  row.put("card", c.getString(0));
                  row.put("kind", c.getString(1));
                  row.put("id", c.getInt(2));
                  row.put("name", c.getString(3));
                  row.put("post", c.getString(4));
                  row.put("photo", c.getString(5));
                  String last = c.getString(6);
                  boolean readyOut = "READY_OUT".equalsIgnoreCase(last);
                  row.put("last_status", readyOut ? "IN" : last);
                  row.put("can_out", readyOut ? 1 : 0);
                  row.put("permit_from", c.getLong(7));
                  row.put("permit_until", c.getLong(8));
                  JSONObject var7 = row;
                  return var7;
               }
            }

            return null;
         } catch (Exception var11) {
            return null;
         } finally {
            c.close();
         }
      }
   }

   public JSONObject findInside(String raw) {
      String uid = clean(raw);
      Cursor c = this.getReadableDatabase().rawQuery("SELECT card,payload FROM inside", (String[])null);

      try {
         while(c.moveToNext()) {
            if (sameCard(uid, c.getString(0))) {
               JSONObject var4 = new JSONObject(c.getString(1));
               return var4;
            }
         }

         return null;
      } catch (Exception var8) {
         return null;
      } finally {
         c.close();
      }
   }

   public JSONObject findInsideByVisitor(JSONObject visit) {
      if (visit == null) {
         return null;
      } else {
         Cursor c = this.getReadableDatabase().rawQuery("SELECT payload FROM inside", (String[])null);

         try {
            while(c.moveToNext()) {
               JSONObject row = new JSONObject(c.getString(0));
               if (sameVisitor(visit, row)) {
                  JSONObject var4 = row;
                  return var4;
               }
            }

            return null;
         } catch (Exception var8) {
            return null;
         } finally {
            c.close();
         }
      }
   }

   public void rememberInside(JSONObject visit) {
      if (visit != null) {
         String card = clean(visit.optString("card"));
         if (card.length() >= 4) {
            this.clearGone(card);

            try {
               visit.put("card", card);
            } catch (Exception var5) {
            }

            SQLiteDatabase db = this.getWritableDatabase();
            this.removeSameVisitor(db, visit, card);
            ContentValues cv = new ContentValues();
            cv.put("card", card);
            cv.put("payload", visit.toString());
            db.insertWithOnConflict("inside", (String)null, cv, 5);
         }
      }
   }

   public void dedupeInside() {
      JSONArray rows = this.insideList();
      Set<String> seen = new HashSet();

      for(int i = 0; i < rows.length(); ++i) {
         JSONObject row = rows.optJSONObject(i);
         if (row != null) {
            String key = visitorKey(row);
            if (!key.isEmpty()) {
               if (seen.contains(key)) {
                  this.deleteInsideCard(row.optString("card"));
               } else {
                  seen.add(key);
               }
            }
         }
      }

   }

   private void removeSameVisitor(SQLiteDatabase db, JSONObject visit, String keepCard) {
      Cursor c = db.rawQuery("SELECT card,payload FROM inside", (String[])null);

      try {
         while(c.moveToNext()) {
            JSONObject row = new JSONObject(c.getString(1));
            if (sameVisitor(visit, row) && !sameCard(keepCard, c.getString(0))) {
               db.delete("inside", "card=?", new String[]{c.getString(0)});
            }
         }
      } catch (Exception var9) {
      } finally {
         c.close();
      }

   }

   public static boolean sameVisitor(JSONObject a, JSONObject b) {
      if (a != null && b != null) {
         String na = normName(a.optString("names"));
         String nb = normName(b.optString("names"));
         if (!na.isEmpty() && na.equals(nb)) {
            String pa = digitsOnly(a.optString("phone"));
            String pb = digitsOnly(b.optString("phone"));
            if (!pa.isEmpty() && pa.equals(pb)) {
               return true;
            } else {
               String ia = clean(a.optString("id_number"));
               String ib = clean(b.optString("id_number"));
               return !ia.isEmpty() && ia.equals(ib);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static String visitorKey(JSONObject row) {
      String name = normName(row.optString("names"));
      String phone = digitsOnly(row.optString("phone"));
      String id = clean(row.optString("id_number"));
      if (name.isEmpty()) {
         return "";
      } else if (!phone.isEmpty()) {
         return name + "|p|" + phone;
      } else {
         return !id.isEmpty() ? name + "|i|" + id : name;
      }
   }

   private static String normName(String name) {
      return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase();
   }

   private static String digitsOnly(String raw) {
      return raw == null ? "" : raw.replaceAll("\\D", "");
   }

   private void deleteInsideCard(String raw) {
      String uid = clean(raw);
      if (uid.length() >= 4) {
         SQLiteDatabase db = this.getWritableDatabase();
         Cursor c = db.rawQuery("SELECT card FROM inside", (String[])null);

         try {
            while(c.moveToNext()) {
               if (uid.equals(clean(c.getString(0))) || sameCard(uid, c.getString(0))) {
                  db.delete("inside", "card=?", new String[]{c.getString(0)});
               }
            }
         } finally {
            c.close();
         }

      }
   }

   public void forgetInside(String raw) {
      this.markGone(raw);
      this.deleteInsideCard(raw);
   }

   public JSONArray insideList() {
      JSONArray out = new JSONArray();
      Cursor c = this.getReadableDatabase().rawQuery("SELECT payload FROM inside", (String[])null);

      try {
         while(c.moveToNext()) {
            try {
               out.put(new JSONObject(c.getString(0)));
            } catch (Exception var7) {
            }
         }
      } finally {
         c.close();
      }

      return out;
   }

   public void enqueue(String op, JSONObject payload, long time) {
      ContentValues cv = new ContentValues();
      cv.put("op", op);
      cv.put("payload", payload == null ? "{}" : payload.toString());
      cv.put("event_time", time);
      cv.put("created_at", System.currentTimeMillis() / 1000L);
      this.getWritableDatabase().insert("queue", (String)null, cv);
   }

   public JSONArray pendingEvents() {
      JSONArray out = new JSONArray();
      Cursor c = this.getReadableDatabase().rawQuery("SELECT id,op,payload,event_time FROM queue ORDER BY event_time ASC, id ASC", (String[])null);

      try {
         while(c.moveToNext()) {
            try {
               JSONObject row = new JSONObject(c.getString(2));
               row.put("op", c.getString(1));
               row.put("time", c.getLong(3));
               row.put("_id", c.getLong(0));
               out.put(row);
            } catch (Exception var7) {
            }
         }
      } finally {
         c.close();
      }

      return out;
   }

   public void clearQueue() {
      this.getWritableDatabase().delete("queue", (String)null, (String[])null);
   }

   public int pendingCount() {
      Cursor c = this.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM queue", (String[])null);

      int var2;
      try {
         var2 = c.moveToFirst() ? c.getInt(0) : 0;
      } finally {
         c.close();
      }

      return var2;
   }

   public JSONObject kpi() {
      return this.getJsonObject("kpi");
   }

   public JSONArray recent() {
      return this.getJsonArray("recent");
   }

   private void putMeta(SQLiteDatabase db, String key, Object value) {
      ContentValues cv = new ContentValues();
      cv.put("k", key);
      cv.put("v", value == null ? "" : value.toString());
      db.insertWithOnConflict("meta", (String)null, cv, 5);
   }

   private JSONObject getJsonObject(String key) {
      String raw = this.getMeta(key);
      if (raw != null && !raw.isEmpty()) {
         try {
            return new JSONObject(raw);
         } catch (Exception var4) {
            return new JSONObject();
         }
      } else {
         return new JSONObject();
      }
   }

   private JSONArray getJsonArray(String key) {
      String raw = this.getMeta(key);
      if (raw != null && !raw.isEmpty()) {
         try {
            return new JSONArray(raw);
         } catch (Exception var4) {
            return new JSONArray();
         }
      } else {
         return new JSONArray();
      }
   }

   private String getMeta(String key) {
      Cursor c = this.getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?", new String[]{key});

      String var3;
      try {
         var3 = c.moveToFirst() ? c.getString(0) : "";
      } finally {
         c.close();
      }

      return var3;
   }
}
