package com.qonics.dbhelper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import com.qonics.model.OfflineCourseModel;
import com.qonics.model.OfflineMarksModel;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Administrator on 8/21/2017.
 */

public class DatabaseHandler extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 17;
    public static final String DATABASE_NAME = "somanet_attendance_app_db";
    //protected String TABLE_NAME[] = {"Visit","Dept", "ClassLevel", "Student", "records"};

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static String TABLE_ID       = "id";
    public static String CREATED_AT     = "created_at";
    public static String UPDATED_AT     = "updated_at";

    public static abstract class SOMA_MARKS implements BaseColumns {
        public static final String COL_STUDENT_ID ="student_id";
        public static final String COL_STUDENT_NAME ="name";
        public static final String COL_STUDENT_REGNO ="regno";
        public static final String COL_COURSE_ID ="course_id";
        public static final String COL_COURSE_NAME ="course_name";
        public static final String COL_CLASS_ID ="class_id";
        public static final String COL_MARK_TYPE ="mark_type";
        public static final String COL_MARK_TYPE_NAME ="mark_type_name";
        public static final String COL_MARK ="marks";
        public static final String COL_OUT_OF ="out_of";
        public static final String COL_CAT_TYPE ="cat_type";
        public static final String COL_PERIOD ="period";
        public static final String COL_EXAM_DATE ="examDate";
        public static final String COL_CREATED_BY ="created_by";

    }


    //GETTING ALL OFFLINE MARKS
    public List<OfflineMarksModel> getAllOffline(int courseId) {
        List<OfflineMarksModel> marksModels = new LinkedList<>();
        String query =  "SELECT  tb_marks.*, ts.name, ts.regno FROM " + "tb_marks" + " INNER JOIN " + " tb_student ts" + " ON ts.id = tb_marks.student_id " + " WHERE tb_marks.course_id = ?";
        // 2. get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(courseId)});
        // 3. go over each row, build event and add it to list
        OfflineMarksModel marks = null;

        Log.e("OFFDATA", DatabaseUtils.dumpCursorToString(cursor));
        if (cursor.moveToFirst()) {

            do {
                marks = new OfflineMarksModel();
                marks.setId(cursor.getInt(cursor.getColumnIndex(TABLE_ID)));
                marks.setStudentId(cursor.getInt(cursor.getColumnIndex(SOMA_MARKS.COL_STUDENT_ID)));
                marks.setStudentName(cursor.getString(cursor.getColumnIndex(SOMA_MARKS.COL_STUDENT_NAME)));
                marks.setRegno(cursor.getString(cursor.getColumnIndex(SOMA_MARKS.COL_STUDENT_REGNO)));
                marks.setCourseId(cursor.getInt(cursor.getColumnIndex(SOMA_MARKS.COL_COURSE_ID)));
                marks.setClassId(cursor.getInt(cursor.getColumnIndex(SOMA_MARKS.COL_CLASS_ID)));
                marks.setMarkType(cursor.getInt(cursor.getColumnIndex(SOMA_MARKS.COL_MARK_TYPE)));
                marks.setMarks(cursor.getDouble(cursor.getColumnIndex(SOMA_MARKS.COL_MARK)));
                marks.setOutOf(cursor.getInt(cursor.getColumnIndex(SOMA_MARKS.COL_OUT_OF)));
                marks.setCatType(cursor.getString(cursor.getColumnIndex(SOMA_MARKS.COL_CAT_TYPE)));
                marks.setPeriod(cursor.getInt(cursor.getColumnIndex(SOMA_MARKS.COL_PERIOD)));
                marks.setExamDate(cursor.getString(cursor.getColumnIndex(SOMA_MARKS.COL_EXAM_DATE)));
                marks.setCreatedBy(cursor.getInt(cursor.getColumnIndex(SOMA_MARKS.COL_CREATED_BY)));
//                marks.setCreatedAt(cursor.getString(cursor.getColumnIndex(CREATED_AT)));
//                marks.setUpdatedAt(cursor.getString(cursor.getColumnIndex(UPDATED_AT)));
                marksModels.add(marks);
            } while (cursor.moveToNext());
        }
        Log.d("Inserted Row:","GetRowTest "+marksModels);
        return marksModels;
    }

    //GETTING ALL OFFLINE MARKS
    public List<OfflineCourseModel> getAllOfflineCourse() {
        List<OfflineCourseModel> courseModels = new LinkedList<>();
        String query =  "SELECT  tb_marks.*, ts.name, ts.regno FROM " + "tb_marks" + " INNER JOIN " + " tb_student ts" + " ON ts.id = tb_marks.student_id"+ " GROUP BY out_of,mark_type";
        // 2. get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        // 3. go over each row, build event and add it to list
        OfflineCourseModel marks = null;
        if (cursor.moveToFirst()) {

            do {
                marks = new OfflineCourseModel();
                marks.setId(cursor.getInt(cursor.getColumnIndex(TABLE_ID)));
                marks.setName(cursor.getString(cursor.getColumnIndex(SOMA_MARKS.COL_COURSE_NAME)));
                marks.setCourse_id(cursor.getInt(cursor.getColumnIndex(SOMA_MARKS.COL_COURSE_ID)));
                marks.setCat(cursor.getString(cursor.getColumnIndex(SOMA_MARKS.COL_MARK_TYPE_NAME)));
                marks.setMark_type_id(cursor.getInt(cursor.getColumnIndex(SOMA_MARKS.COL_MARK_TYPE)));
                marks.setMarks(cursor.getDouble(cursor.getColumnIndex(SOMA_MARKS.COL_OUT_OF)));
                courseModels.add(marks);
            } while (cursor.moveToNext());
        }
        Log.d("Inserted Row:","GetRowTest "+courseModels);
        return courseModels;
    }

    //GETTING ALL OFFLINE MARKS
    public int countAllOfflineMarks() {
        String query =  "SELECT  COUNT(*) FROM " + "tb_marks" + " GROUP BY out_of";
        // 2. get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor mCount = db.rawQuery(query, null);

//        mCount.moveToFirst();
        int result = mCount.getCount();
        mCount.close();

        Log.d("Inserted Row:","GetRowTest "+result);
        return result;
    }


    @Override
    public void onCreate(SQLiteDatabase db) {



        String STUDENT = "CREATE TABLE IF NOT EXISTS tb_student" +
                "(id INTEGER PRIMARY KEY, " +
                "classe TEXT, " +
                "class_id INTEGER, " +
                "dept TEXT, " +
                "sex TEXT, " +
                "regno TEXT unique, " +
                "name TEXT, " +
                "card TEXT, " +
                "phone TEXT, " +
                "studying_mode INTEGER, " +
                "photo TEXT, " +
                "record_id INTEGER DEFAULT 0, " +
                "status INTEGER DEFAULT 1 )";

        String CLASS = "CREATE TABLE IF NOT EXISTS tb_class" +
                "(id INTEGER PRIMARY KEY, " +
                "title TEXT )";

        String COURSE = "CREATE TABLE IF NOT EXISTS tb_course" +
                "(id INTEGER, " +
                "title TEXT, " +
                "code TEXT, " +
                "class_id INTEGER, " +
                "marks INTEGER )";


        String DISCIPLINE = "CREATE TABLE IF NOT EXISTS tb_discipline" +
                "(id INTEGER PRIMARY KEY, " +
                "type TEXT, " +
                "student_id TEXT, " +
                "notify_parent INTEGER, " +
                "comment TEXT, " +
                "school_id INTEGER, " +
                "operator INTEGER, " +
                "marks INTEGER )";

        //table that combine assessment_type and assessment_records
        String ASSESSMENT_RECORDS = "CREATE TABLE IF NOT EXISTS tb_assessment_records" +
                "(id INTEGER PRIMARY KEY, " +
                "title TEXT, " +
                "assessment_type_id  INTEGER, " +
                "academic_type_id  INTEGER, " +
                "academic_type_title  TEXT, " +
                "multiple_marks_per_term INTEGER, " +
                "percentage INTEGER)";

        String MARKS = "CREATE TABLE IF NOT EXISTS tb_marks" +
                "(id INTEGER PRIMARY KEY AUTOINCREMENT , " +
                "student_id TEXT, " +
                "student_name TEXT, " +
                "course_id TEXT, " +
                "course_name TEXT, " +
                "class_id INTEGER, " +
                "mark_type INTEGER, " +
                "mark_type_name TEXT, " +
                "marks TEXT, " +
                "out_of INTEGER, " +
                "cat_type INTEGER, " +
                "period INTEGER, " +
                "examDate TEXT, " +
                "created_by INTEGER )";
        String MARKS_TYPE = "CREATE TABLE IF NOT EXISTS tb_marks_type" +
                "(id INTEGER PRIMARY KEY, " +
                "academic_type INTEGER, " +
                "title TEXT )";

        String FEES = "CREATE TABLE IF NOT EXISTS tb_fees" +
                "(id TEXT PRIMARY KEY, " +
                "type_id TEXT, " +
                "type TEXT, " + // if 0: class_id, 1: student_id
                "fee_title TEXT, " +
                "amount TEXT, " +
                "academic_year_id INTEGER, " +
                "academic_term INTEGER )";

        String FEE_RECORDS = "CREATE TABLE IF NOT EXISTS tb_fee_records" +
                "(id TEXT PRIMARY KEY, " +
                "student_id TEXT, " +
                "fee_id INTEGER, " +
                "fee_type INTEGER, " +
                "paid_amount TEXT, " +
                "payment_mode TEXT, " +
                "academic_term INTEGER, " +
                "sync INTEGER, " +
                "created_by INTEGER,"+
                "created_at TEXT )";



        String ACADEMIC_TERM = "CREATE TABLE IF NOT EXISTS tb_academic_term" +
                "(id INTEGER PRIMARY KEY, " +
                "title TEXT )";

        String ACADEMIC_YEAR = "CREATE TABLE IF NOT EXISTS tb_academic_year" +
                "(id INTEGER PRIMARY KEY, " +
                "title TEXT )";

        db.execSQL(FEE_RECORDS);
        db.execSQL(ACADEMIC_YEAR);
        db.execSQL(ACADEMIC_TERM);
        db.execSQL(FEES);
        db.execSQL(MARKS);
        db.execSQL(CLASS);
        db.execSQL(COURSE);
        db.execSQL(STUDENT);
        db.execSQL(DISCIPLINE);
        db.execSQL(ASSESSMENT_RECORDS);
        db.execSQL(MARKS_TYPE);
        ensureNewTables(db);
    }

    /** Materials + hostel tables (v11). Safe to call repeatedly (IF NOT EXISTS). */
    private void ensureNewTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_required_materials(" +
                "id INTEGER PRIMARY KEY, " +
                "name TEXT, " +
                "unit TEXT, " +
                "sort_order INTEGER, " +
                "active INTEGER, " +
                "updated_at INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS tb_class_required_materials(" +
                "id INTEGER PRIMARY KEY, " +
                "class_id INTEGER, " +
                "material_id INTEGER, " +
                "academic_year INTEGER, " +
                "quantity REAL, " +
                "updated_at INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS tb_student_material_checks(" +
                "id TEXT PRIMARY KEY, " +
                "server_id INTEGER, " +
                "student_id INTEGER, " +
                "class_id INTEGER, " +
                "material_id INTEGER, " +
                "academic_year INTEGER, " +
                "quantity_required REAL, " +
                "quantity_brought REAL, " +
                "notes TEXT, " +
                "checked_by INTEGER, " +
                "checked_at TEXT, " +
                "sync INTEGER, " +
                "updated_at INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS tb_hostels(" +
                "id INTEGER PRIMARY KEY, " +
                "name TEXT, " +
                "max_beds INTEGER, " +
                "gender TEXT, " +
                "level_group TEXT, " +
                "sort_order INTEGER, " +
                "active INTEGER, " +
                "updated_at INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS tb_hostel_allocations(" +
                "id TEXT PRIMARY KEY, " +
                "server_id INTEGER, " +
                "hostel_id INTEGER, " +
                "student_id INTEGER, " +
                "academic_year INTEGER, " +
                "allocated_by INTEGER, " +
                "action TEXT, " +
                "sync INTEGER, " +
                "updated_at INTEGER)");

        ensureColumn(db, "tb_student_material_checks", "checker_name", "ALTER TABLE tb_student_material_checks ADD COLUMN checker_name TEXT");
        ensureColumn(db, "tb_student_material_checks", "checker_post", "ALTER TABLE tb_student_material_checks ADD COLUMN checker_post TEXT");
        ensureColumn(db, "tb_hostels", "level_group", "ALTER TABLE tb_hostels ADD COLUMN level_group TEXT");
        ensureColumn(db, "tb_hostels", "in_charge_staff_id", "ALTER TABLE tb_hostels ADD COLUMN in_charge_staff_id INTEGER DEFAULT 0");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_dormitory_attendance_pending(" +
                "id TEXT PRIMARY KEY, " +
                "hostel_id INTEGER, " +
                "term INTEGER, " +
                "year INTEGER, " +
                "teacher INTEGER, " +
                "school_id INTEGER, " +
                "students_json TEXT, " +
                "sync INTEGER, " +
                "created_at INTEGER)");
        ensureColumn(db, "tb_dormitory_attendance_pending", "roll_date", "ALTER TABLE tb_dormitory_attendance_pending ADD COLUMN roll_date TEXT");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_dorm_sheet(" +
                "hostel_id INTEGER PRIMARY KEY, " +
                "name TEXT, " +
                "in_charge INTEGER, " +
                "students_json TEXT, " +
                "present_json TEXT, " +
                "taken INTEGER, " +
                "roster_date TEXT)");
        ensureColumn(db, "tb_discipline", "code_id", "ALTER TABLE tb_discipline ADD COLUMN code_id INTEGER DEFAULT 0");
        ensureColumn(db, "tb_discipline", "code_key", "ALTER TABLE tb_discipline ADD COLUMN code_key TEXT");
        ensureColumn(db, "tb_discipline", "code_no", "ALTER TABLE tb_discipline ADD COLUMN code_no INTEGER DEFAULT 0");
        ensureColumn(db, "tb_discipline", "occurrence", "ALTER TABLE tb_discipline ADD COLUMN occurrence INTEGER DEFAULT 1");
        ensureColumn(db, "tb_discipline", "active_term", "ALTER TABLE tb_discipline ADD COLUMN active_term INTEGER DEFAULT 0");
        ensureColumn(db, "tb_discipline", "sync", "ALTER TABLE tb_discipline ADD COLUMN sync INTEGER DEFAULT 1");
        ensureColumn(db, "tb_discipline", "lang", "ALTER TABLE tb_discipline ADD COLUMN lang TEXT DEFAULT 'en'");
        ensureColumn(db, "tb_discipline", "voided", "ALTER TABLE tb_discipline ADD COLUMN voided INTEGER DEFAULT 0");
        ensureColumn(db, "tb_discipline", "void_reason", "ALTER TABLE tb_discipline ADD COLUMN void_reason TEXT");
        ensureColumn(db, "tb_discipline", "voided_name", "ALTER TABLE tb_discipline ADD COLUMN voided_name TEXT");
        ensureColumn(db, "tb_discipline", "server_id", "ALTER TABLE tb_discipline ADD COLUMN server_id INTEGER DEFAULT 0");
        ensureColumn(db, "tb_marks", "cat_code", "ALTER TABLE tb_marks ADD COLUMN cat_code TEXT");
        ensureColumn(db, "tb_student", "status", "ALTER TABLE tb_student ADD COLUMN status INTEGER DEFAULT 1");
        ensureColumn(db, "tb_student", "record_id", "ALTER TABLE tb_student ADD COLUMN record_id INTEGER DEFAULT 0");
        ensureColumn(db, "tb_course", "lecturer", "ALTER TABLE tb_course ADD COLUMN lecturer TEXT");
        ensureColumn(db, "tb_course", "marks_locked", "ALTER TABLE tb_course ADD COLUMN marks_locked INTEGER DEFAULT 0");
        ensureColumn(db, "tb_class", "is_nursery", "ALTER TABLE tb_class ADD COLUMN is_nursery INTEGER DEFAULT 0");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_fee_balance(" +
                "student_id INTEGER PRIMARY KEY, " +
                "year INTEGER, " +
                "term INTEGER, " +
                "expected INTEGER, " +
                "paid INTEGER, " +
                "left_amount INTEGER, " +
                "phone TEXT, " +
                "payload TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_staff_login(" +
                "id INTEGER PRIMARY KEY, " +
                "school_id INTEGER, " +
                "email TEXT, " +
                "phone TEXT, " +
                "password_hash TEXT, " +
                "name TEXT, " +
                "post INTEGER, " +
                "post_title TEXT, " +
                "status INTEGER, " +
                "menu_keys TEXT, " +
                "app_menus TEXT, " +
                "menu_full INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_staff_course(" +
                "staff_id INTEGER, " +
                "id INTEGER, " +
                "title TEXT, " +
                "code TEXT, " +
                "marks TEXT, " +
                "class_id INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_staff_class(" +
                "staff_id INTEGER, " +
                "id INTEGER, " +
                "title TEXT, " +
                "is_nursery INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_filled_cat(" +
                "course_id INTEGER, " +
                "class_id INTEGER, " +
                "period INTEGER, " +
                "cat_type TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_mark_cache(" +
                "student_id INTEGER, " +
                "course_id INTEGER, " +
                "class_id INTEGER, " +
                "mark_type INTEGER, " +
                "cat_type TEXT, " +
                "period INTEGER, " +
                "marks TEXT, " +
                "out_of TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_daily_attendance(" +
                "student_id INTEGER, " +
                "datee TEXT, " +
                "class_id INTEGER, " +
                "term INTEGER, " +
                "PRIMARY KEY (student_id, datee))");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_daily_attendance_pending(" +
                "id TEXT PRIMARY KEY, " +
                "class_id INTEGER, " +
                "term INTEGER, " +
                "teacher INTEGER, " +
                "students_json TEXT, " +
                "sync INTEGER, " +
                "created_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_permission_cover(" +
                "student_id INTEGER, " +
                "class_id INTEGER, " +
                "reason TEXT, " +
                "day TEXT, " +
                "PRIMARY KEY (student_id, day))");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_manual_present_pending(" +
                "id TEXT PRIMARY KEY, " +
                "student_id INTEGER, " +
                "class_id INTEGER, " +
                "term INTEGER, " +
                "school_id INTEGER, " +
                "approved_by INTEGER, " +
                "approved_name TEXT, " +
                "reason TEXT, " +
                "datee TEXT, " +
                "sync INTEGER, " +
                "created_at INTEGER)");
        ensureColumn(db, "tb_daily_attendance", "source", "ALTER TABLE tb_daily_attendance ADD COLUMN source TEXT");
        ensureColumn(db, "tb_daily_attendance", "manual_reason", "ALTER TABLE tb_daily_attendance ADD COLUMN manual_reason TEXT");
        ensureColumn(db, "tb_daily_attendance", "approved_by", "ALTER TABLE tb_daily_attendance ADD COLUMN approved_by INTEGER");
        ensureColumn(db, "tb_daily_attendance", "approved_name", "ALTER TABLE tb_daily_attendance ADD COLUMN approved_name TEXT");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_student_dismiss_pending(" +
                "id TEXT PRIMARY KEY, " +
                "student_id INTEGER, " +
                "record_id INTEGER, " +
                "class_id INTEGER, " +
                "year INTEGER, " +
                "school_id INTEGER, " +
                "sync INTEGER, " +
                "created_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_loc_area(" +
                "id INTEGER PRIMARY KEY, " +
                "name TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_loc_clock(" +
                "student_id INTEGER, " +
                "area_id INTEGER, " +
                "day TEXT, " +
                "status TEXT, " +
                "event_time INTEGER, " +
                "PRIMARY KEY (student_id, area_id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_loc_pending(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "student_id INTEGER, " +
                "area_id INTEGER, " +
                "card TEXT, " +
                "event_time INTEGER, " +
                "status TEXT, " +
                "sync INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_loc_fee(" +
                "student_id INTEGER PRIMARY KEY, " +
                "studying_mode INTEGER, " +
                "feeding_amount INTEGER, " +
                "transport_amount INTEGER, " +
                "transport_required INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_pocket_balance(" +
                "student_id INTEGER PRIMARY KEY, " +
                "balance INTEGER, " +
                "received INTEGER, " +
                "canteen INTEGER, " +
                "cash INTEGER, " +
                "card_disabled INTEGER, " +
                "kpi_date TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_vernacular_pending(" +
                "id TEXT PRIMARY KEY, " +
                "student_id INTEGER, " +
                "school_id INTEGER, " +
                "operator INTEGER, " +
                "sync INTEGER, " +
                "created_at INTEGER)");
    }

    private void ensureColumn(SQLiteDatabase db, String table, String column, String ddl) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean exists = false;
        while (cursor.moveToNext()) {
            if (column.equalsIgnoreCase(cursor.getString(cursor.getColumnIndex("name")))) {
                exists = true;
                break;
            }
        }
        cursor.close();
        if (!exists) {
            db.execSQL(ddl);
        }
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 11) {
            ensureNewTables(db);
        }
        if (oldVersion < 12) {
            ensureNewTables(db);
        }
        if (oldVersion < 13) {
            ensureNewTables(db);
        }
        if (oldVersion < 14) {
            ensureNewTables(db);
        }
        if (oldVersion < 15) {
            ensureNewTables(db);
        }
        if (oldVersion < 16) {
            ensureNewTables(db);
        }
        if (oldVersion < 17) {
            ensureNewTables(db);
        }
    }


    public int GetUnixTime()
    {
        int utc  = (int)(new Date().getTime()/1000);
        return (utc);

    }
}
