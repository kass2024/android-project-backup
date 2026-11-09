package com.qonics.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * School conduct codes bundled for offline use (same laws as the web catalog).
 * Server ids are filled in when a sync can reach discipline_codes.
 */
public final class ConductCatalog {
    private static final String TAG = "ConductCatalog";
    private static List<Code> cache;

    private ConductCatalog() {
    }

    public static class Code {
        public int serverId;
        public String categoryKey;
        public String categoryEn;
        public String categoryRw;
        public int codeNo;
        public String titleEn;
        public String titleRw;
        public int m1, m2, m3;
        public String s1en, s1rw, s2en, s2rw, s3en, s3rw;

        public String category(boolean rw) {
            if (rw) {
                return categoryRw != null && categoryRw.length() > 0 ? categoryRw : categoryEn;
            }
            return categoryEn != null && categoryEn.length() > 0 ? categoryEn : categoryRw;
        }

        public String title(boolean rw) {
            if (rw) {
                return titleRw != null && titleRw.length() > 0 ? titleRw : titleEn;
            }
            return titleEn != null && titleEn.length() > 0 ? titleEn : titleRw;
        }

        public int marksFor(int occurrence) {
            if (occurrence <= 1) return Math.max(0, m1);
            if (occurrence == 2) return Math.max(0, m2);
            return Math.max(0, m3);
        }

        public String sanction(boolean rw, int occurrence) {
            if (occurrence <= 1) return rw ? s1rw : s1en;
            if (occurrence == 2) return rw ? s2rw : s2en;
            return rw ? s3rw : s3en;
        }

        /** Same marks and sanction every time. No 1st/2nd/3rd ladder. */
        public boolean isFlat() {
            return m1 == m2 && m2 == m3
                    && same(s1en, s2en) && same(s2en, s3en)
                    && same(s1rw, s2rw) && same(s2rw, s3rw);
        }

        private static boolean same(String a, String b) {
            String left = a == null ? "" : a;
            String right = b == null ? "" : b;
            return left.equals(right);
        }
    }

    public static List<Code> load(Context context) {
        if (cache != null) {
            return cache;
        }
        ArrayList<Code> list = new ArrayList<>();
        try {
            InputStream in = context.getAssets().open("conduct_codes.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            JSONArray cats = new JSONArray(sb.toString());
            for (int i = 0; i < cats.length(); i++) {
                JSONObject cat = cats.getJSONObject(i);
                String key = cat.optString("key", "");
                String en = cat.optString("en", "");
                String rw = cat.optString("rw", "");
                JSONArray items = cat.optJSONArray("items");
                if (items == null) continue;
                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.getJSONObject(j);
                    Code code = new Code();
                    code.categoryKey = key;
                    code.categoryEn = en;
                    code.categoryRw = rw;
                    code.codeNo = item.optInt("no", j + 1);
                    code.titleEn = item.optString("en", "");
                    code.titleRw = item.optString("rw", "");
                    JSONArray marks = item.optJSONArray("marks");
                    JSONArray se = item.optJSONArray("se");
                    JSONArray sr = item.optJSONArray("sr");
                    code.m1 = marks != null ? marks.optInt(0, 0) : 0;
                    code.m2 = marks != null ? marks.optInt(1, 0) : 0;
                    code.m3 = marks != null ? marks.optInt(2, 0) : 0;
                    code.s1en = se != null ? se.optString(0, "") : "";
                    code.s2en = se != null ? se.optString(1, "") : "";
                    code.s3en = se != null ? se.optString(2, "") : "";
                    code.s1rw = sr != null ? sr.optString(0, "") : "";
                    code.s2rw = sr != null ? sr.optString(1, "") : "";
                    code.s3rw = sr != null ? sr.optString(2, "") : "";
                    list.add(code);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "load catalog", e);
        }
        cache = list;
        return cache;
    }

    public static void ensureIdTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_conduct_ids(" +
                "category_key TEXT, code_no INTEGER, server_id INTEGER, PRIMARY KEY(category_key, code_no))");
    }

    public static void applyStoredIds(Context context, SQLiteDatabase db) {
        ensureIdTable(db);
        ensureSyncTable(db);
        List<Code> codes = load(context);
        overlaySynced(db, codes);
        Cursor c = db.rawQuery("SELECT category_key, code_no, server_id FROM tb_conduct_ids", null);
        while (c.moveToNext()) {
            String key = c.getString(0);
            int no = c.getInt(1);
            int id = c.getInt(2);
            for (Code code : codes) {
                if (code.codeNo == no && key != null && key.equals(code.categoryKey)) {
                    code.serverId = id;
                }
            }
        }
        c.close();
    }

    public static void rememberServerId(SQLiteDatabase db, String key, int codeNo, int serverId) {
        if (key == null || serverId < 1) return;
        ensureIdTable(db);
        db.execSQL("INSERT OR REPLACE INTO tb_conduct_ids (category_key, code_no, server_id) VALUES (?,?,?)",
                new Object[]{key, codeNo, serverId});
        if (cache != null) {
            for (Code code : cache) {
                if (code.codeNo == codeNo && key.equals(code.categoryKey)) {
                    code.serverId = serverId;
                }
            }
        }
    }

    public static int lookupServerId(SQLiteDatabase db, String key, int codeNo) {
        if (cache != null) {
            for (Code code : cache) {
                if (code.codeNo == codeNo && key != null && key.equals(code.categoryKey) && code.serverId > 0) {
                    return code.serverId;
                }
            }
        }
        ensureIdTable(db);
        Cursor c = db.rawQuery(
                "SELECT server_id FROM tb_conduct_ids WHERE category_key=? AND code_no=? LIMIT 1",
                new String[]{key == null ? "" : key, String.valueOf(codeNo)});
        int id = 0;
        if (c.moveToFirst()) id = c.getInt(0);
        c.close();
        return id;
    }

    /** Pull server laws into the phone. New codes are kept for offline use. */
    public static void mergeServerGroups(Context context, SQLiteDatabase db, JSONArray groups) {
        if (groups == null) return;
        List<Code> codes = load(context);
        ensureIdTable(db);
        ensureSyncTable(db);
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            String key = group.optString("key", "");
            String catEn = group.optString("en", group.optString("title", ""));
            String catRw = group.optString("rw", "");
            JSONArray items = group.optJSONArray("items");
            if (items == null || key.length() == 0) continue;
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item == null) continue;
                int no = item.optInt("code_no", 0);
                int id = item.optInt("id", 0);
                if (no < 1) continue;
                Code code = find(codes, key, no);
                if (code == null) {
                    code = new Code();
                    code.categoryKey = key;
                    code.codeNo = no;
                    codes.add(code);
                }
                if (catEn.length() > 0) code.categoryEn = catEn;
                if (catRw.length() > 0) code.categoryRw = catRw;
                String titleEn = item.optString("title_en", item.optString("title", ""));
                String titleRw = item.optString("title_rw", "");
                if (titleEn.length() > 0) code.titleEn = titleEn;
                if (titleRw.length() > 0) code.titleRw = titleRw;
                if (item.has("first_marks")) code.m1 = item.optInt("first_marks", code.m1);
                if (item.has("second_marks")) code.m2 = item.optInt("second_marks", code.m2);
                if (item.has("third_marks")) code.m3 = item.optInt("third_marks", code.m3);
                code.s1en = pick(item, "first_sanction_en", code.s1en);
                code.s1rw = pick(item, "first_sanction_rw", code.s1rw);
                code.s2en = pick(item, "second_sanction_en", code.s2en);
                code.s2rw = pick(item, "second_sanction_rw", code.s2rw);
                code.s3en = pick(item, "third_sanction_en", code.s3en);
                code.s3rw = pick(item, "third_sanction_rw", code.s3rw);
                if (id > 0) {
                    code.serverId = id;
                    rememberServerId(db, key, no, id);
                }
                db.execSQL("INSERT OR REPLACE INTO tb_conduct_sync " +
                                "(category_key,code_no,category_en,category_rw,title_en,title_rw,m1,m2,m3," +
                                "s1en,s1rw,s2en,s2rw,s3en,s3rw,server_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{
                                key, no,
                                nz(code.categoryEn), nz(code.categoryRw), nz(code.titleEn), nz(code.titleRw),
                                code.m1, code.m2, code.m3,
                                nz(code.s1en), nz(code.s1rw), nz(code.s2en), nz(code.s2rw), nz(code.s3en), nz(code.s3rw),
                                code.serverId
                        });
            }
        }
    }

    private static void ensureSyncTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_conduct_sync(" +
                "category_key TEXT, code_no INTEGER, category_en TEXT, category_rw TEXT, " +
                "title_en TEXT, title_rw TEXT, m1 INTEGER, m2 INTEGER, m3 INTEGER, " +
                "s1en TEXT, s1rw TEXT, s2en TEXT, s2rw TEXT, s3en TEXT, s3rw TEXT, server_id INTEGER, " +
                "PRIMARY KEY(category_key, code_no))");
    }

    private static void overlaySynced(SQLiteDatabase db, List<Code> codes) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT category_key,code_no,category_en,category_rw,title_en,title_rw,m1,m2,m3," +
                    "s1en,s1rw,s2en,s2rw,s3en,s3rw,server_id FROM tb_conduct_sync", null);
        } catch (Exception e) {
            return;
        }
        while (c.moveToNext()) {
            String key = c.getString(0);
            int no = c.getInt(1);
            Code code = find(codes, key, no);
            if (code == null) {
                code = new Code();
                code.categoryKey = key;
                code.codeNo = no;
                codes.add(code);
            }
            code.categoryEn = text(c, 2, code.categoryEn);
            code.categoryRw = text(c, 3, code.categoryRw);
            code.titleEn = text(c, 4, code.titleEn);
            code.titleRw = text(c, 5, code.titleRw);
            code.m1 = c.getInt(6);
            code.m2 = c.getInt(7);
            code.m3 = c.getInt(8);
            code.s1en = text(c, 9, code.s1en);
            code.s1rw = text(c, 10, code.s1rw);
            code.s2en = text(c, 11, code.s2en);
            code.s2rw = text(c, 12, code.s2rw);
            code.s3en = text(c, 13, code.s3en);
            code.s3rw = text(c, 14, code.s3rw);
            int serverId = c.getInt(15);
            if (serverId > 0) code.serverId = serverId;
        }
        c.close();
    }

    private static Code find(List<Code> codes, String key, int no) {
        for (Code code : codes) {
            if (code.codeNo == no && key != null && key.equals(code.categoryKey)) return code;
        }
        return null;
    }

    private static String pick(JSONObject item, String name, String current) {
        if (!item.has(name)) return current;
        String value = item.optString(name, "");
        return value.length() > 0 ? value : current;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String text(Cursor c, int index, String current) {
        String value = c.getString(index);
        return value != null && value.length() > 0 ? value : current;
    }
}
