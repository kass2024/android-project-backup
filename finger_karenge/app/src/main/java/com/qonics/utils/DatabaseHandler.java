package com.qonics.utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Administrator on 8/21/2017.
 */

public class DatabaseHandler extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 10;
    public static final String DATABASE_NAME = "somanet_attendance_db";
    //protected String TABLE_NAME[] = {"Visit","Dept", "ClassLevel", "Student", "records"};

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String STAFF = "CREATE TABLE IF NOT EXISTS tb_staff "
                + "(id INTEGER PRIMARY KEY, "
                + "name TEXT,"
                + "phone TEXT,"
                + "dept TEXT,"
                + "photo TEXT,"
                + "post_title TEXT,"
                + "email TEXT,"
                + "enlcon1 BLOB,"
                + "enlcon2 BLOB,"
                + "enlcon3 BLOB,"
                + "fp1 BLOB,"
                + "fp2 BLOB,"
                + "fp3 BLOB,"
                + "enllNO BLOB,"
                + "face1 BLOB,"
                + "face2 BLOB,"
                + "face_dim1 TEXT,"
                + "face_dim2 TEXT"
                + ")";

        String STUDENT = "CREATE TABLE IF NOT EXISTS tb_student" +
                "(id INTEGER PRIMARY KEY, " +
                "classe TEXT, " +
                "dept TEXT, " +
                "regno TEXT unique, " +
                "name TEXT, " +
                "card TEXT, " +
                "studying_mode INTEGER, " +
                "photo TEXT )";

        //user_type = 0:student,1:staff
        String records = "CREATE TABLE IF NOT EXISTS tb_records "
                + "( id INTEGER PRIMARY KEY autoincrement, "
                + "user_id TEXT, "
                + "user_type TEXT, "
                + "timee TEXT )";
        db.execSQL(records);
        db.execSQL(STAFF);
        db.execSQL(STUDENT);
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /*
        for(int i=0 ; i<TABLE_NAME.length; i++) {
            String sql = "DROP TABLE IF EXISTS " + TABLE_NAME[i];
            db.execSQL(sql);
        }
        onCreate(db);
        */
    }
}
