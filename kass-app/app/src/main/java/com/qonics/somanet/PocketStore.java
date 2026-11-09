package com.qonics.somanet;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Pocket balances downloaded for offline card taps. */
final class PocketStore {
    private PocketStore() {}

    static void replaceAll(SQLiteDatabase db, JSONArray rows, String date) {
        if (db == null || rows == null) return;
        String day = date == null || date.length() < 8
                ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())
                : date;
        db.beginTransaction();
        try {
            db.execSQL("delete from tb_pocket_balance");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                int studentId = row.optInt("student_id", 0);
                if (studentId < 1) continue;
                db.execSQL(
                        "insert or replace into tb_pocket_balance (student_id,balance,received,canteen,cash,card_disabled,kpi_date) values (?,?,?,?,?,?,?)",
                        new Object[]{
                                studentId,
                                row.optInt("balance", 0),
                                row.optInt("received", 0),
                                row.optInt("canteen", 0),
                                row.optInt("cash", 0),
                                row.optInt("card_disabled", 0),
                                day
                        });
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    static void saveOne(SQLiteDatabase db, int studentId, int balance, int received, int canteen, int cash, int disabled, String date) {
        if (db == null || studentId < 1) return;
        String day = date == null || date.length() < 8
                ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())
                : date;
        db.execSQL(
                "insert or replace into tb_pocket_balance (student_id,balance,received,canteen,cash,card_disabled,kpi_date) values (?,?,?,?,?,?,?)",
                new Object[]{studentId, balance, received, canteen, cash, disabled, day});
    }

    static int[] read(SQLiteDatabase db, int studentId) {
        int[] empty = new int[]{0, 0, 0, 0, 0};
        if (db == null || studentId < 1) return empty;
        Cursor c = db.rawQuery(
                "select balance, received, canteen, cash, card_disabled, kpi_date from tb_pocket_balance where student_id=?",
                new String[]{String.valueOf(studentId)});
        if (!c.moveToFirst()) {
            c.close();
            return null;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        boolean sameDay = today.equals(c.getString(5));
        int[] out = new int[]{
                c.getInt(0),
                sameDay ? c.getInt(1) : 0,
                sameDay ? c.getInt(2) : 0,
                sameDay ? c.getInt(3) : 0,
                c.getInt(4)
        };
        c.close();
        return out;
    }
}
