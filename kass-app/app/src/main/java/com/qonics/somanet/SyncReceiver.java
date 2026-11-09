package com.qonics.somanet;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.utils.Callback;
import com.qonics.utils.ConductCatalog;
import com.qonics.utils.MenuClearanceHelper;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StaffLoginStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class SyncReceiver extends BroadcastReceiver {
    HttpRequest req;
    String server = "";
    SharedPreferences preferences;
    SQLiteDatabase db;
    ProgressDialog progressDialog;
    private static final int PROGRESS = 33;
    NotificationCompat.Builder mBuilder;
    NotificationManager mNotifyManager;
    int success = 0;
    /** Prevents overlapping sync cycles that duplicate upload_data posts. */
    private static final Object SYNC_LOCK = new Object();
    private static volatile boolean syncInProgress = false;
    private final Set<String> dismissSkipThisCycle = new HashSet<>();
    private boolean studentFullResyncTried = false;
    private int studentPagesThisCycle = 0;

    /** Used by menus to show live Syncing… status. */
    public static boolean isSyncing() {
        return syncInProgress;
    }

    /** Trigger a background two-way sync (uploads first, then downloads). */
    public static void requestSync(Context context) {
        Intent action = new Intent("sync.qonics.com.somanet");
        action.setPackage(context.getPackageName());
        context.sendBroadcast(action);
        Intent explicit = new Intent(context, SyncReceiver.class);
        explicit.setAction("sync.qonics.com.somanet");
        context.sendBroadcast(explicit);
    }

    private void broadcastProgress(Context context, boolean syncing) {
        Intent intent = new Intent("sync.qonics.com.somanet.progress");
        intent.setPackage(context.getPackageName());
        intent.putExtra("syncing", syncing);
        context.sendBroadcast(intent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.

        db = context.openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(context).onCreate(db);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        //server = "http://" + preferences.getString(SettingsActivity.KEY_WEB_ADDR, "") + "/smartschool/api/";
        //server = preferences.getString(SettingsActivity.KEY_WEB_ADDR, "") + "/api/";
        server = MainActivity.SERVER;
        progressDialog = new ProgressDialog(context);
        try {
            // Never use TYPE_SYSTEM_ALERT from a receiver — it crashes on modern Android.
            progressDialog.setMessage("Syncing....");
            progressDialog.setIndeterminate(false);
            progressDialog.setCancelable(false);
            progressDialog.setMax(100);
        } catch (Exception ignored) {
            progressDialog = null;
        }

        String action = intent != null ? intent.getAction() : null;
        if (action == null || "sync.qonics.com.somanet".equals(action)) {
            Log.e(TAG, "Requested sync");
            checkServer(context);
            return;
        }
        if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();

            if (networkInfo != null &&
                    networkInfo.isConnected()) {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ssid = wifiInfo.getSSID();

                Log.e(TAG, " -- Wifi connected --- " + " SSID " + ssid);
                if (!preferences.getBoolean("FIRST_RUN", true))
                    checkServer(context);
                LocationAttendanceActivity.uploadPending(context);

            }
        } else if (action.equalsIgnoreCase(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                Log.e(TAG, " ----- Wifi  Disconnected ----- ");
            }
        }
    }

    private void checkServer(final Context context) {
        synchronized (SYNC_LOCK) {
            if (syncInProgress) {
                Log.e(TAG, "Sync already in progress — skipping");
                broadcastProgress(context, true);
                return;
            }
            syncInProgress = true;
        }
        broadcastProgress(context, true);
        req = new HttpRequest(context);
        req.get(server + "check_server", JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    //Toast.makeText(context,"Error: "+status.getMessage(),Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error:" + status);
                    //notify school sync did not complete
                    Intent intent = new Intent("sync.qonics.com.somanet.success");
                    intent.setPackage(context.getPackageName());
                    intent.putExtra("status", 1);
                    intent.putExtra("msg", status);
                    context.sendBroadcast(intent);//send force sync receiver
                    releaseSyncLock();
                    broadcastProgress(context, false);
                } else {
                    //server reached try sync — upload local attendance/dismiss FIRST so download cannot overwrite them
                    if (object.has("success")) {
                        beginUploadPipeline(context);
                    } else {
                        releaseSyncLock();
                        broadcastProgress(context, false);
                    }
                }
            }
        });
    }

    private void releaseSyncLock() {
        synchronized (SYNC_LOCK) {
            syncInProgress = false;
        }
    }

    private void releaseSyncLock(Context context) {
        releaseSyncLock();
        if (context != null) {
            broadcastProgress(context, false);
        }
    }

    /** Uploads offline queues once per sync cycle. */
    private void beginUploadPipeline(final Context context) {
        dismissSkipThisCycle.clear();
        enqueueLocalDismissals();
        syncMenuClearance(context);
        uploadDailyAttendance(context);
    }

    /**
     * Queue any locally inactive students that were never added to the dismiss upload table
     * (older app versions only flipped tb_student.status).
     */
    private void enqueueLocalDismissals() {
        try {
            int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
            int year = preferences.getInt("soma_academic", 0);
            Cursor c = db.rawQuery("select id, class_id, record_id from tb_student where ifnull(status,1)=0", null);
            int queued = 0;
            while (c.moveToNext()) {
                int studentId = c.getInt(c.getColumnIndex("id"));
                Cursor existing = db.rawQuery(
                        "select id from tb_student_dismiss_pending where student_id=? limit 1",
                        new String[]{String.valueOf(studentId)});
                boolean hasRow = existing.moveToNext();
                existing.close();
                if (hasRow) {
                    continue;
                }
                db.execSQL(
                        "insert into tb_student_dismiss_pending (id,student_id,record_id,class_id,year,school_id,sync,created_at) values (?,?,?,?,?,?,?,?)",
                        new Object[]{
                                UUID.randomUUID().toString(),
                                studentId,
                                c.getInt(c.getColumnIndex("record_id")),
                                c.getInt(c.getColumnIndex("class_id")),
                                year,
                                schoolId,
                                1,
                                System.currentTimeMillis() / 1000L
                        });
                queued++;
            }
            c.close();
            if (queued > 0) {
                Log.e(TAG, "Queued " + queued + " local dismissed student(s) for upload");
            }
        } catch (Exception e) {
            Log.e(TAG, "enqueueLocalDismissals", e);
        }
    }

    private void uploadDailyAttendance(final Context context) {
        uploadManualPresent(context);
    }

    private void uploadManualPresent(final Context context) {
        Log.e(TAG, "Uploading manual present approvals");
        try {
            req = new HttpRequest(context);
            Cursor c = db.rawQuery("select * from tb_manual_present_pending where sync=1 order by created_at limit 1", null);
            if (!c.moveToNext()) {
                c.close();
                uploadClassAttendance(context);
                return;
            }
            final String rowId = c.getString(c.getColumnIndex("id"));
            Map map = new HashMap();
            map.put("school_id", c.getInt(c.getColumnIndex("school_id")));
            map.put("class", c.getInt(c.getColumnIndex("class_id")));
            map.put("student_id", c.getInt(c.getColumnIndex("student_id")));
            map.put("term", c.getInt(c.getColumnIndex("term")));
            map.put("approved_by", c.getInt(c.getColumnIndex("approved_by")));
            map.put("reason", c.getString(c.getColumnIndex("reason")));
            c.close();
            req.post(server + "approve_absent_present", map, (object, status_code, status) -> {
                String err = object != null ? object.optString("error", "") : "";
                String errLower = err.toLowerCase();
                boolean done = object != null && object.has("success") && !object.has("error");
                boolean already = errLower.contains("already");
                if (done || already) {
                    db.execSQL("update tb_manual_present_pending set sync=0 where id=?", new Object[]{rowId});
                    uploadManualPresent(context);
                } else {
                    Log.e(TAG, "Manual present upload failed: " + (err.length() > 0 ? err : status));
                    uploadClassAttendance(context);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Manual present upload error", e);
            uploadClassAttendance(context);
        }
    }

    private void uploadClassAttendance(final Context context) {
        Log.e(TAG, "Uploading daily class attendance");
        try {
            req = new HttpRequest(context);
            Cursor c = db.rawQuery("select * from tb_daily_attendance_pending where sync=1 limit 1", null);
            if (!c.moveToNext()) {
                c.close();
                uploadDormitoryAttendance(context);
                return;
            }
            final String rowId = c.getString(c.getColumnIndex("id"));
            Map map = new HashMap();
            map.put("teacher", c.getInt(c.getColumnIndex("teacher")));
            map.put("term", c.getInt(c.getColumnIndex("term")));
            map.put("class", c.getInt(c.getColumnIndex("class_id")));
            map.put("students", c.getString(c.getColumnIndex("students_json")));
            c.close();
            req.post(server + "save_class_attendance", map, (object, status_code, status) -> {
                if (object != null && object.has("success")) {
                    db.execSQL("update tb_daily_attendance_pending set sync=0 where id=?", new Object[]{rowId});
                    uploadClassAttendance(context);
                } else if (object != null && object.has("error")
                        && object.optString("error", "").toLowerCase().contains("already")) {
                    db.execSQL("update tb_daily_attendance_pending set sync=0 where id=?", new Object[]{rowId});
                    uploadClassAttendance(context);
                } else {
                    Log.e(TAG, "Daily attendance upload failed: " + status);
                    uploadDormitoryAttendance(context);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Daily attendance upload error", e);
            uploadDormitoryAttendance(context);
        }
    }

    private void uploadDormitoryAttendance(final Context context) {
        Log.e(TAG, "Uploading dormitory attendance");
        try {
            req = new HttpRequest(context);
            Cursor c = db.rawQuery("select * from tb_dormitory_attendance_pending where sync=1 limit 1", null);
            if (!c.moveToNext()) {
                c.close();
                uploadDismissPending(context);
                return;
            }
            final String rowId = c.getString(c.getColumnIndex("id"));
            Map map = new HashMap();
            map.put("school", c.getInt(c.getColumnIndex("school_id")));
            map.put("hostel", c.getInt(c.getColumnIndex("hostel_id")));
            map.put("teacher", c.getInt(c.getColumnIndex("teacher")));
            map.put("term", c.getInt(c.getColumnIndex("term")));
            map.put("year", c.getInt(c.getColumnIndex("year")));
            map.put("students", c.getString(c.getColumnIndex("students_json")));
            int dateCol = c.getColumnIndex("roll_date");
            String rollDate = dateCol >= 0 ? c.getString(dateCol) : "";
            if (rollDate == null || rollDate.length() < 8) {
                long created = c.getLong(c.getColumnIndex("created_at"));
                rollDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(new java.util.Date(created > 0 ? created * 1000L : System.currentTimeMillis()));
            }
            map.put("date", rollDate);
            c.close();
            req.post(server + "save_dormitory_attendance_api", map, (object, status_code, status) -> {
                boolean ok = object != null && object.has("success");
                if (ok) {
                    db.execSQL("update tb_dormitory_attendance_pending set sync=0 where id=?", new Object[]{rowId});
                    uploadDormitoryAttendance(context);
                } else {
                    Log.e(TAG, "Dormitory attendance upload failed: " + status);
                    uploadDismissPending(context);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Dormitory attendance upload error", e);
            uploadDismissPending(context);
        }
    }

    private void uploadNextDismissOrContinue(Context context, String skipId) {
        if (skipId != null) {
            dismissSkipThisCycle.add(skipId);
        }
        uploadDismissPending(context);
    }

    private void uploadDismissPending(final Context context) {
        Log.e(TAG, "Uploading dismiss pending");
        try {
            req = new HttpRequest(context);
            Cursor c = db.rawQuery("select * from tb_student_dismiss_pending where sync=1", null);
            String rowId = null;
            Map map = new HashMap();
            while (c.moveToNext()) {
                String id = c.getString(c.getColumnIndex("id"));
                if (dismissSkipThisCycle.contains(id)) {
                    continue;
                }
                rowId = id;
                int schoolId = c.getInt(c.getColumnIndex("school_id"));
                if (schoolId < 1) {
                    schoolId = preferences.getInt(MainActivity.SK_ID, 0);
                }
                int year = c.getInt(c.getColumnIndex("year"));
                if (year < 1) {
                    year = preferences.getInt("soma_academic", 0);
                }
                map.put("school_id", schoolId);
                map.put("student_id", c.getInt(c.getColumnIndex("student_id")));
                map.put("record_id", c.getInt(c.getColumnIndex("record_id")));
                map.put("year", year);
                map.put("class", c.getInt(c.getColumnIndex("class_id")));
                break;
            }
            c.close();
            if (rowId == null) {
                dismissSkipThisCycle.clear();
                startDownloadsThenRemainingUploads(context);
                return;
            }
            final String pendingId = rowId;
            Log.e(TAG, "Dismiss upload student=" + map.get("student_id") + " school=" + map.get("school_id"));
            req.post(server + "dismiss_student", map, (object, status_code, status) -> {
                if (object != null && object.has("success")) {
                    db.execSQL("update tb_student_dismiss_pending set sync=0 where id=?", new Object[]{pendingId});
                    uploadDismissPending(context);
                } else {
                    String err = object != null ? object.optString("error", status) : status;
                    Log.e(TAG, "Dismiss upload failed id=" + pendingId + " : " + err);
                    // Drop rows the server will never accept (wrong school / deleted student)
                    if (err != null && err.toLowerCase().contains("not found")) {
                        db.execSQL("update tb_student_dismiss_pending set sync=0 where id=?", new Object[]{pendingId});
                        uploadDismissPending(context);
                    } else {
                        uploadNextDismissOrContinue(context, pendingId);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Dismiss upload error", e);
            startDownloadsThenRemainingUploads(context);
        }
    }

    /** After local attendance/dismiss uploads, pull server state then continue other queues. */
    private void startDownloadsThenRemainingUploads(final Context context) {
        syncStudent(context);
        syncDailyAttendance(context);
        syncAcademicYears(context);
        syncFees(context);
        syncAssessmentRecords(context);
        syncFeesRecords(context);
        syncFeeBalances(context, 0);
        syncPocketMoney(context);
        uploadVernacularPending(context);
        syncRequiredMaterials(context);
        syncClassRequiredMaterials(context);
        uploadFeesRecord(context);
    }

    private boolean hasPendingDismiss(int studentId) {
        try {
            Cursor c = db.rawQuery(
                    "select id from tb_student_dismiss_pending where student_id=? and sync=1 limit 1",
                    new String[]{String.valueOf(studentId)});
            boolean found = c.moveToNext();
            c.close();
            return found;
        } catch (Exception e) {
            return false;
        }
    }

    private void syncDailyAttendance(final Context context) {
        Log.e(TAG, "Syncing daily attendance");
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        req = new HttpRequest(context);
        req.get(server + "sync/daily_attendance/" + preferences.getInt(MainActivity.SK_ID, 0)
                        + "?date=" + today,
                JSONObject.class, (object, status_code, status) -> {
                    if (object == null || object.has("error") || !object.has("attendance")) {
                        Log.e(TAG, "daily attendance download: " + status);
                        return;
                    }
                    try {
                        JSONArray jarr = object.getJSONArray("attendance");
                        HashSet<Integer> pendingManual = new HashSet<>();
                        Cursor pending = db.rawQuery(
                                "select student_id from tb_manual_present_pending where sync=1 and datee=?",
                                new String[]{today});
                        while (pending.moveToNext()) {
                            pendingManual.add(pending.getInt(0));
                        }
                        pending.close();
                        db.execSQL("delete from tb_daily_attendance where datee=? and student_id not in (select student_id from tb_manual_present_pending where sync=1 and datee=?)",
                                new Object[]{today, today});
                        for (int a = 0; a < jarr.length(); a++) {
                            JSONObject row = jarr.getJSONObject(a);
                            int studentId = row.optInt("student_id", 0);
                            if (studentId < 1 || pendingManual.contains(studentId)) {
                                continue;
                            }
                            String source = row.optString("source", "record");
                            boolean masterSchool = preferences.getInt("soma_is_master", 0) == 1;
                            if (!masterSchool && "device".equals(source)) {
                                continue;
                            }
                            db.execSQL(
                                    "insert or replace into tb_daily_attendance (student_id,datee,class_id,term,source,manual_reason,approved_by,approved_name) values (?,?,?,?,?,?,?,?)",
                                    new Object[]{
                                            studentId,
                                            row.optString("datee", today),
                                            row.optInt("class_id", 0),
                                            row.optInt("active_term", 0),
                                            source,
                                            row.optString("manual_reason", ""),
                                            row.optInt("approved_by", 0),
                                            row.optString("approved_name", "")
                                    });
                        }
                        if (preferences.getInt("soma_is_master", 0) == 1) {
                            preferences.edit().putString("attendance_synced_day", today).apply();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "daily attendance download error", e);
                    }
                });
    }

    private void syncStudent(final Context context) {
        Log.e(TAG, "Syncing students");
        showNotification(context);
        if (progressDialog != null) {
            progressDialog.setProgress(PROGRESS * 1);
        }
        if (mBuilder != null && mNotifyManager != null) {
            mBuilder.setProgress(100, PROGRESS * 1, false);
            mBuilder.setContentText("Sync Students");
            mNotifyManager.notify(1112, mBuilder.build());
        }
        studentFullResyncTried = false;
        studentPagesThisCycle = 0;
        // One-time full catch-up so large schools (e.g. Wisdom) are not stuck on a partial cursor.
        if (!preferences.getBoolean("student_sync_cursor_v34", false)) {
            preferences.edit()
                    .putBoolean("student_sync_cursor_v34", true)
                    .putLong("updated_date_student", 0)
                    .putInt("updated_date_student_id", 0)
                    .apply();
        }
        fetchStudentPage(context);
    }

    private void fetchStudentPage(final Context context) {
        if (studentPagesThisCycle >= 40) {
            Log.e(TAG, "Student sync page cap reached");
            notifyStudentRosterUpdated(context);
            return;
        }
        studentPagesThisCycle++;
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        long updatedAt = preferences.getLong("updated_date_student", 0);
        int afterId = preferences.getInt("updated_date_student_id", 0);
        String url = server + "sync/student_v2/" + schoolId
                + "?updatedAt=" + updatedAt
                + "&afterId=" + afterId
                + "&limit=500";
        Log.e("EEEEE", url);
        new HttpRequest(context).timeouts(20, 90).get(url, JSONObject.class, (object, status_code, status) -> {
            if (object == null) {
                Log.e(TAG, "Error:" + status);
                return;
            }
            try {
                success = success + 1;
                if (object.has("error") && !object.has("students")) {
                    Log.e(TAG, "Student sync: " + object.optString("error"));
                    return;
                }
                JSONArray jarr = object.optJSONArray("students");
                if (jarr == null) {
                    jarr = new JSONArray();
                }
                applyStudentRows(jarr);
                boolean hasMore = object.optBoolean("has_more", jarr.length() >= 200);
                int enrolled = object.optInt("enrolled_count", 0);
                if (jarr.length() > 0) {
                    int nextTs = object.optInt("next_updated_at", 0);
                    int nextId = object.optInt("next_id", 0);
                    JSONObject last = jarr.getJSONObject(jarr.length() - 1);
                    if (nextTs <= 0) {
                        nextTs = last.optInt("updated_at", (int) updatedAt);
                    }
                    if (nextId <= 0) {
                        nextId = last.optInt("id", afterId);
                    }
                    preferences.edit()
                            .putLong("updated_date_student", nextTs)
                            .putInt("updated_date_student_id", nextId)
                            .apply();
                    notifyStudentRosterUpdated(context);
                }
                if (hasMore && jarr.length() > 0) {
                    fetchStudentPage(context);
                    return;
                }
                int localCount = countLocalStudents();
                if (enrolled > 0 && localCount < enrolled && !studentFullResyncTried) {
                    studentFullResyncTried = true;
                    Log.e(TAG, "Student roster behind (" + localCount + "/" + enrolled + ") — full resync");
                    preferences.edit()
                            .putLong("updated_date_student", 0)
                            .putInt("updated_date_student_id", 0)
                            .apply();
                    fetchStudentPage(context);
                }
            } catch (Exception e) {
                Log.e(TAG, "Student sync parse", e);
            }
        });
    }

    private void applyStudentRows(JSONArray jarr) throws JSONException {
        for (int a = 0; a < jarr.length(); a++) {
            JSONObject data = jarr.getJSONObject(a);
            int studentId = data.getInt("id");
            int recordId = data.optInt("record_id", 0);
            int stStatus = data.optInt("status", 1);
            if (hasPendingDismiss(studentId)) {
                stStatus = 0;
            }
            String classe = data.optString("class");
            int classId = data.optInt("class_id");
            if (com.qonics.utils.ClassLabels.isHoliday(classe)) {
                classe = "";
                classId = 0;
            }
            Cursor cursor = db.rawQuery("select id from tb_student where id ='" + studentId + "'", null);
            if (cursor.moveToNext()) {
                String name = escapeString(data.optString("name"));
                String dept = escapeString(data.optString("dept_title"));
                db.execSQL("update tb_student set " +
                        "name='" + name + "'," +
                        "card='" + data.optString("card").toUpperCase().replaceAll("[^A-F0-9]", "") + "'," +
                        "classe='" + classe + "'," +
                        "phone='" + escapeString(data.optString("phone")) + "'," +
                        "class_id='" + classId + "'," +
                        "sex='" + data.optString("sex") + "'," +
                        "dept='" + dept + "'," +
                        "studying_mode='" + data.optString("studying_mode") + "'," +
                        "photo='" + data.optString("photo") + "'," +
                        "record_id='" + recordId + "'," +
                        "status='" + stStatus + "'" +
                        " where id='" + studentId + "'");
            } else {
                try {
                    String name = escapeString(data.optString("name"));
                    String sql = "insert into tb_student (id,regno,name,card,classe,dept,studying_mode,photo,class_id,phone,sex,status,record_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)";
                    Object[] args = new Object[]{studentId, data.optString("regno"), name,
                            data.optString("card").toUpperCase().replaceAll("[^A-F0-9]", ""), classe, data.optString("dept_title").trim()
                            , data.optString("studying_mode"), data.optString("photo"), classId, data.optString("phone"), data.optString("sex"), stStatus, recordId};
                    db.execSQL(sql, args);
                } catch (Exception ex) {
                    Log.e(TAG, "insert student " + studentId, ex);
                }
            }
            cursor.close();
        }
        Log.e(TAG, "Applied " + jarr.length() + " student row(s)");
    }

    private int countLocalStudents() {
        try {
            Cursor c = db.rawQuery("select count(*) from tb_student", null);
            int n = 0;
            if (c.moveToNext()) {
                n = c.getInt(0);
            }
            c.close();
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    private void notifyStudentRosterUpdated(Context context) {
        Intent intent = new Intent("sync.qonics.com.somanet.success");
        intent.setPackage(context.getPackageName());
        intent.putExtra("status", 2);
        intent.putExtra("students", true);
        context.sendBroadcast(intent);
    }

    private void syncPocketMoney(final Context context) {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId < 1) return;
        Log.e(TAG, "Syncing pocket money");
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId);
        new HttpRequest(context).timeouts(20, 60).post(server + "pocket_money_sync", map, (object, statusCode, status) -> {
            if (object == null || !object.optBoolean("success", false)) {
                Log.e(TAG, "Pocket money sync failed: " + status);
                return;
            }
            PocketStore.replaceAll(db, object.optJSONArray("rows"), object.optString("date", ""));
            Log.e(TAG, "Pocket money saved: " + object.optInt("count"));
        });
    }

    private void uploadVernacularPending(final Context context) {
        try {
            Cursor c = db.rawQuery("select id, student_id, school_id, operator from tb_vernacular_pending where sync=1 limit 1", null);
            if (!c.moveToNext()) {
                c.close();
                return;
            }
            final String rowId = c.getString(0);
            Map<String, Object> map = new HashMap<>();
            map.put("student_id", c.getInt(1));
            map.put("school_id", c.getInt(2));
            map.put("operator", c.getInt(3));
            c.close();
            new HttpRequest(context).timeouts(12, 25).post(server + "vernacular_confirm", map, (object, statusCode, status) -> {
                boolean ok = object != null && object.optBoolean("success", false);
                String err = object == null ? "" : object.optString("error", "").toLowerCase();
                boolean already = err.contains("just confirmed") || err.contains("wait a moment");
                if (ok || already) {
                    db.execSQL("update tb_vernacular_pending set sync=0 where id=?", new Object[]{rowId});
                    uploadVernacularPending(context);
                } else {
                    Log.e(TAG, "Vernacular upload failed: " + status);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Vernacular upload error", e);
        }
    }

    private void syncFees(final Context context) {
        Log.e(TAG, "Syncing fees");
        showNotification(context);
        progressDialog.setProgress(PROGRESS * 1);
        mBuilder.setProgress(100, PROGRESS * 1, false);
        mBuilder.setContentText("Sync fees");
        mNotifyManager.notify(1112, mBuilder.build());
        req = new HttpRequest(context);
        Log.e("EEEEE", server + "sync/fees/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + preferences.getLong("updated_fees", 0));
        req.get(server + "sync/fees/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + preferences.getLong("updated_fees", 0), JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {

                if (object == null) {
//                    Toast.makeText(context,"Error: "+status,Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error:" + status);
                } else {
                    try {
                        success = success + 1;
                        if (object.has("error")) {
                            if (object.getString("error").equals("0")) {
                                Log.e(TAG, "No new fees");
                            } else {
                                Log.e(TAG, "Fatal error: " + object.getString("error"));
                            }
                        } else {
                            //new data found
                            JSONArray jarr = object.getJSONArray("fees");
                            int updatedAt = 0;
                            for (int a = 0; jarr.length() > a; a++) {
                                JSONObject data = jarr.getJSONObject(a);
                                Cursor cursor = db.rawQuery("select id from tb_fees where id ='" + data.getInt("id") + "'", null);
                                if (cursor.moveToNext()) {
                                    db.execSQL("update tb_fees set " +
                                            "type_id='" + data.getString("type_id") + "'," +
                                            "type='" + data.getInt("type") + "'," +
                                            "fee_title='" + escapeString(data.getString("title")) + "'," +
                                            "amount='" + data.getString("amount") + "'," +
                                            "academic_year_id='" + data.getInt("academic_year") + "'," +
                                            "academic_term='" + data.getString("term") + "'" +
                                            " where id='" + data.getString("id") + "'");
                                    Log.e("Fees", data.getString("title") + " Updated!!!");
                                } else {
                                    //i.e new
                                    try {
                                        String sql = "insert into tb_fees (id,type,type_id,fee_title,amount,academic_year_id,academic_term) values (?,?,?,?,?,?,?)";
                                        Object[] args = new Object[]{
                                                data.getInt("id"),
                                                data.getInt("type"),
                                                data.getString("type_id"),
                                                data.getString("title"),
                                                data.getString("amount"),
                                                data.getString("academic_year"),
                                                data.getString("term"),
                                        };
                                        db.execSQL(sql, args);
                                        Log.e("Fees", data.getString("title"));
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                }
                                if (updatedAt < data.getInt("updated_at1"))
                                    updatedAt = data.getInt("updated_at1");
                            }
                            preferences.edit().putLong("updated_fees", updatedAt).apply();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void syncFeesRecords(final Context context) {
        Log.e(TAG, "Syncing fees records");
        showNotification(context);
        progressDialog.setProgress(PROGRESS * 1);
        mBuilder.setProgress(100, PROGRESS * 1, false);
        mBuilder.setContentText("Sync fees records");
        mNotifyManager.notify(1112, mBuilder.build());
        req = new HttpRequest(context);
        Log.e("EEEEE", server + "sync/fees_records/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + preferences.getLong("updated_fees_records", 0));
        req.get(server + "sync/fees_records/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + preferences.getLong("updated_fees_records", 0), JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                // Uploads run only via beginUploadPipeline()
                if (object == null) {
//                    Toast.makeText(context,"Error: "+status,Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error:" + status);
                } else {
                    try {
                        success = success + 1;
                        if (object.has("error")) {
                            if (object.getString("error").equals("0")) {
                                Log.e(TAG, "No new fees_records");
                            } else {
                                Log.e(TAG, "Fatal error: " + object.getString("error"));
                            }
                        } else {
                            //new data found
                            JSONArray jarr = object.getJSONArray("fees");
                            int updatedAt = 0;
                            for (int a = 0; jarr.length() > a; a++) {
                                JSONObject data = jarr.getJSONObject(a);
                                String uuid = data.optString("uuid", "");
                                if (uuid.isEmpty()) {
                                    continue;
                                }
                                Cursor cursor = db.rawQuery("select id from tb_fee_records where id =?", new String[]{uuid});
                                if (cursor.moveToNext()) {
                                    db.execSQL("update tb_fee_records set " +
                                            "student_id='" + data.getString("student_id") + "'," +
                                            "fee_id='" + data.getInt("fees_id") + "'," +
                                            "fee_type='" + data.getInt("fees_type") + "'," +
                                            "paid_amount='" + data.getString("amount") + "'," +
                                            "payment_mode='" + data.getInt("payment_mode") + "'," +
                                            "created_by='" + data.getInt("created_by") + "'," +
                                            "created_at='" + data.getString("created_at") + "'" +
                                            " where id='" + uuid + "'");
                                    Log.e("fees_records", data.getString("fees_id") + " Updated!!!");
                                } else {
                                    //i.e new
                                    try {
                                        String sql = "insert into tb_fee_records (id,student_id,fee_id,fee_type,payment_mode,paid_amount,academic_term,created_by,created_at) values (?,?,?,?,?,?,?,?,?)";
                                        Object[] args = new Object[]{
                                                UUID.randomUUID().toString(),
                                                data.getInt("student_id"),
                                                data.getInt("fees_id"),
                                                data.getInt("fees_type"),
                                                data.getString("payment_mode"),
                                                data.getString("amount"),
                                                data.getString("term"),
                                                data.getString("created_by"),
                                                data.getString("created_at")
                                        };
                                        db.execSQL(sql, args);
                                        Log.e("fees_records", data.getString("fees_id"));
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                }
                                if (updatedAt < data.getInt("updated_at1"))
                                    updatedAt = data.getInt("updated_at1");
                            }
                            preferences.edit().putLong("updated_fees_records", updatedAt).apply();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
    private void syncAssessmentRecords(final Context context) {
        Log.e(TAG, "Syncing assessment_records");
        showNotification(context);
        progressDialog.setProgress(PROGRESS * 1);
        mBuilder.setProgress(100, PROGRESS * 1, false);
        mBuilder.setContentText("Sync assessment_records");
        mNotifyManager.notify(1112, mBuilder.build());
        req = new HttpRequest(context);
        Log.e("EEEEE", server + "sync/assessment_records/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + preferences.getLong("updated_assessment_records", 0));
        req.get(server + "sync/assessment_records/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + preferences.getLong("updated_assessment_records", 0), JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
//                    Toast.makeText(context,"Error: "+status,Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error:" + status);
                } else {
                    try {
                        success = success + 1;
                        if (object.has("error")) {
                            if (object.getString("error").equals("0")) {
                                Log.e(TAG, "No new assessment_records");
                            } else {
                                Log.e(TAG, "Fatal error: " + object.getString("error"));
                            }
                        } else {
                            //new data found
                            JSONArray jarr = object.getJSONArray("records");
                            int updatedAt = 0;
                            for (int a = 0; jarr.length() > a; a++) {
                                JSONObject data = jarr.getJSONObject(a);
                                Cursor cursor = db.rawQuery("select id from tb_assessment_records where id ='" + data.getInt("id") + "'", null);
                                if (cursor.moveToNext()) {
                                    db.execSQL("update tb_assessment_records set " +
                                            "title='" + data.getString("title") + "'," +
                                            "academic_type_title='" + data.getString("academic_type_title") + "'," +
                                            "percentage='" + data.getInt("percentage") + "'," +
                                            "multiple_marks_per_term='" + data.getInt("multiple_marks_per_term") + "'" +
                                            " where id='" + data.getString("id") + "'");
                                    Log.e("assessment_records", data.getString("title") + " Updated!!!");
                                } else {
                                    //i.e new
                                    try {
                                        String sql = "insert into tb_assessment_records (id,title,assessment_type_id,academic_type_id,academic_type_title,multiple_marks_per_term,percentage)" +
                                                " values (?,?,?,?,?,?,?)";
                                        Object[] args = new Object[]{
                                                data.getInt("id"),
                                                data.getString("title"),
                                                data.getInt("assessment_type_id"),
                                                data.getInt("academic_type_id"),
                                                data.getString("academic_type_title"),
                                                data.getInt("multiple_marks_per_term"),
                                                data.getInt("percentage"),
                                        };
                                        db.execSQL(sql, args);
                                        Log.e("assessment_records", data.getString("title"));
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                }
                                if (updatedAt < data.getInt("updated_at1"))
                                    updatedAt = data.getInt("updated_at1");
                            }
                            preferences.edit().putLong("updated_assessment_records", updatedAt).apply();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void syncAcademicYears(final Context context) {
        Log.e(TAG, "Syncing academic year");
        showNotification(context);
        progressDialog.setProgress(PROGRESS * 1);
        mBuilder.setProgress(100, PROGRESS * 1, false);
        mBuilder.setContentText("Sync Academic Year");
        mNotifyManager.notify(1112, mBuilder.build());
        req = new HttpRequest(context);
        Log.e("EEEEE", server + "sync/sync_year/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + preferences.getLong("updated_academic_year", 0));
        req.get(server + "sync/sync_year/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + preferences.getLong("updated_academic_year", 0), JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
//                    Toast.makeText(context,"Error: "+status,Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error:" + status);
                } else {
                    try {
                        success = success + 1;
                        if (object.has("error")) {
                            if (object.getString("error").equals("0")) {
                                Log.e(TAG, "No new academic year");
                            } else {
                                Log.e(TAG, "Fatal error: " + object.getString("error"));
                            }
                        } else {
                            //new data found
                            JSONArray jarr = object.getJSONArray("years");
                            int updatedAt = 0;
                            for (int a = 0; jarr.length() > a; a++) {
                                JSONObject data = jarr.getJSONObject(a);
                                Cursor cursor = db.rawQuery("select id from tb_academic_year where id ='" + data.getInt("id") + "'", null);
                                if (cursor.moveToNext()) {
                                    db.execSQL("update tb_academic_year set " +
                                            "title='" + data.getString("title") + "'" +
                                            " where id='" + data.getString("id") + "'");
                                    Log.e("Academic Year", data.getString("title") + " Updated!!!");
                                } else {
                                    //i.e new
                                    try {
                                        String sql = "insert into tb_academic_year (id,title) values (?,?)";
                                        Object[] args = new Object[]{
                                                data.getInt("id"),
                                                data.getString("title"),
                                        };
                                        db.execSQL(sql, args);
                                        Log.e("Academic Year", data.getString("title"));
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                }
                                if (updatedAt < data.getInt("updated_at1"))
                                    updatedAt = data.getInt("updated_at1");
                            }
                            preferences.edit().putLong("updated_academic_year", updatedAt).apply();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void uploadMarks(final Context context) {
        Log.e(TAG, "Uploading marks records");
        progressDialog.setProgress(PROGRESS * 3);
        mBuilder.setProgress(100, 50, false);
        mBuilder.setContentText("Uploading marks");
        mNotifyManager.notify(1112, mBuilder.build());
        req = new HttpRequest(context);
        Cursor c = db.rawQuery("select * from tb_marks limit 10", null);
        JSONArray jsonArray = new JSONArray();
        int id = 0;
        boolean recordFound = false;
        while (c.moveToNext()) {
            recordFound = true;
            Log.e("Marks", c.getInt(1) + " " + c.getString(2));
            Map mmp = new HashMap();
            mmp.put("id", c.getInt(0));
            mmp.put("student_id", c.getString(1));
            mmp.put("course_id", c.getString(3));
            mmp.put("class_id", c.getString(5));
            mmp.put("mark_type", c.getString(6));
            mmp.put("marks", c.getString(8));
            mmp.put("out_of", c.getString(9));
            int catCodeIdx = c.getColumnIndex("cat_code");
            String catCode = catCodeIdx >= 0 ? c.getString(catCodeIdx) : null;
            if (catCode == null || catCode.length() == 0) {
                catCode = c.getString(10);
            }
            mmp.put("cat_type", catCode == null ? "" : catCode);
            mmp.put("period", c.getString(11));
            mmp.put("examDate", c.getString(12));
            mmp.put("created_by", c.getString(13));
            JSONObject data = new JSONObject(mmp);
            jsonArray.put(data);
            id = c.getInt(0);
        }
        Map map = new HashMap();
        map.put("records", jsonArray.toString());
        map.put("school", preferences.getInt(MainActivity.SK_ID, 0));
        Log.e("records", jsonArray.toString());
        Log.e("LAST ID", id + "");
        if (recordFound) {
            req.post(server + "upload_data/marks", map, new Callback() {
                @Override
                public void HttpCallback(JSONObject object, int status_code, String status) {
                    progressDialog.dismiss();
                    mNotifyManager.cancelAll();
                    if (object == null) {
                        Log.e("records", "Error:" + status);
                        uploadDiscipline(context);
                    } else {
                        try {
                            if (object.has("error")) {
                                Log.e("REC ERROR", object.getString("error") + "");
                                uploadDiscipline(context);
                            } else if (object.has("success")) {
                                Log.e("LAST SAVED ID", object.getInt("last_id") + "");
                                db.execSQL("delete from tb_marks where id <=" + object.getInt("last_id"));
                                Log.e("records", object.getString("success") + " Success");
                                uploadMarks(context);
                            } else {
                                Log.e("REC ERROR", object.getString("msg") + "");
                                uploadDiscipline(context);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            uploadDiscipline(context);
                        }
                    }
                }
            });
        } else {
            uploadDiscipline(context);
        }
    }

    private void syncFeeBalances(final Context context, final int afterId) {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId < 1) return;
        req = new HttpRequest(context);
        req.get(server + "sync/fee_balance/" + schoolId + "?afterId=" + afterId + "&limit=40", JSONObject.class, (object, status_code, status) -> {
            if (object == null || object.has("error")) {
                Log.e(TAG, "fee balance " + status);
                return;
            }
            JSONArray rows = object.optJSONArray("balances");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) continue;
                    int studentId = row.optInt("student_id", 0);
                    if (studentId < 1) continue;
                    String phone = row.optString("phone", "");
                    String payload = row.optJSONArray("lines") == null ? "[]" : row.optJSONArray("lines").toString();
                    db.execSQL("INSERT OR REPLACE INTO tb_fee_balance (student_id,year,term,expected,paid,left_amount,phone,payload) VALUES (?,?,?,?,?,?,?,?)",
                            new Object[]{
                                    studentId,
                                    object.optInt("year", 0),
                                    object.optInt("term", 0),
                                    (int) Math.round(row.optDouble("expected", 0)),
                                    (int) Math.round(row.optDouble("paid", 0)),
                                    (int) Math.round(row.optDouble("left", 0)),
                                    phone,
                                    payload
                            });
                    if (phone != null && phone.trim().length() > 3) {
                        db.execSQL("UPDATE tb_student SET phone=? WHERE id=? AND (phone IS NULL OR TRIM(phone)='')",
                                new Object[]{phone, studentId});
                    }
                }
            }
            if (object.optBoolean("has_more", false) && rows != null && rows.length() > 0) {
                syncFeeBalances(context, object.optInt("next_id", afterId));
            }
        });
    }

    private void uploadFeesRecord(final Context context) {
        try {
            db.execSQL("delete from tb_fee_records where sync = 1");
        } catch (Exception ignored) {
        }
        uploadMarks(context);
    }

    private void uploadOneConduct(final Context context, Cursor coded) {
        final int localId = coded.getInt(coded.getColumnIndex("id"));
        final String type = coded.getString(coded.getColumnIndex("type"));
        final String studentId = coded.getString(coded.getColumnIndex("student_id"));
        final String notify = coded.getString(coded.getColumnIndex("notify_parent"));
        final String operator = coded.getString(coded.getColumnIndex("operator"));
        int codeId = coded.getInt(coded.getColumnIndex("code_id"));
        final String key = coded.getString(coded.getColumnIndex("code_key"));
        final int codeNo = coded.getInt(coded.getColumnIndex("code_no"));
        int langCol = coded.getColumnIndex("lang");
        final String lang = langCol >= 0 && coded.getString(langCol) != null ? coded.getString(langCol) : "en";
        coded.close();
        if (codeId < 1) {
            codeId = ConductCatalog.lookupServerId(db, key, codeNo);
        }
        if (codeId < 1) {
            int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
            req = new HttpRequest(context);
            req.get(server + "discipline_codes?school_id=" + schoolId + "&lang=en", JSONObject.class, new Callback() {
                @Override
                public void HttpCallback(JSONObject object, int status_code, String status) {
                    if (object != null) {
                        ConductCatalog.mergeServerGroups(context, db, object.optJSONArray("groups"));
                    }
                    int resolved = ConductCatalog.lookupServerId(db, key, codeNo);
                    if (resolved < 1) {
                        Log.e(TAG, "Conduct code has no server id yet");
                        uploadMaterialChecks(context);
                        return;
                    }
                    postConduct(context, localId, type, studentId, notify, operator, resolved, lang);
                }
            });
            return;
        }
        postConduct(context, localId, type, studentId, notify, operator, codeId, lang);
    }

    private void postConduct(final Context context, final int localId, String type, String studentId,
                             String notify, String operator, final int codeId, final String lang) {
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("student_id", studentId);
        map.put("type", type);
        map.put("code_id", codeId);
        map.put("notify_parent", notify);
        map.put("operator", operator);
        map.put("marks", "0");
        map.put("reason", "");
        map.put("lang", lang == null || lang.length() == 0 ? "en" : lang);
        req = new HttpRequest(context);
        req.post(server + "save_discipline", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object != null && object.has("success")) {
                    db.execSQL("UPDATE tb_discipline SET sync=0, code_id=? WHERE id=?", new Object[]{codeId, localId});
                    uploadDiscipline(context);
                } else {
                    Log.e(TAG, "save_discipline failed: " + status);
                    uploadMaterialChecks(context);
                }
            }
        });
    }

    private void uploadDiscipline(final Context context) {
        Log.e(TAG, "Uploading discipline records");
        progressDialog.setProgress(PROGRESS * 3);
        mBuilder.setProgress(100, 50, false);
        mBuilder.setContentText("Uploading discipline");
        mNotifyManager.notify(1112, mBuilder.build());
        req = new HttpRequest(context);
        Cursor coded = db.rawQuery(
                "SELECT id, type, student_id, notify_parent, operator, code_id, code_key, code_no, lang " +
                        "FROM tb_discipline WHERE IFNULL(sync,1)=1 AND IFNULL(voided,0)=0 AND IFNULL(code_no,0)>0 LIMIT 1",
                null);
        if (coded.moveToNext()) {
            uploadOneConduct(context, coded);
            return;
        }
        coded.close();
        Cursor c = db.rawQuery(
                "select * from tb_discipline where IFNULL(sync,1)=1 AND IFNULL(voided,0)=0 AND IFNULL(code_no,0)=0 limit 10",
                null);
        JSONArray jsonArray = new JSONArray();
        int id = 0;
        boolean recordFound = false;
        while (c.moveToNext()) {
            recordFound = true;
            Log.e("Marks", c.getInt(1) + " " + c.getString(2));
            Map mmp = new HashMap();
            mmp.put("id", c.getInt(c.getColumnIndex("id")));
            mmp.put("type", c.getString(c.getColumnIndex("type")));
            mmp.put("student_id", c.getString(c.getColumnIndex("student_id")));
            mmp.put("notify_parent", c.getString(c.getColumnIndex("notify_parent")));
            mmp.put("comment", c.getString(c.getColumnIndex("comment")));
            mmp.put("school_id", c.getString(c.getColumnIndex("school_id")));
            mmp.put("operator", c.getString(c.getColumnIndex("operator")));
            mmp.put("marks", c.getString(c.getColumnIndex("marks")));
            int langIdx = c.getColumnIndex("lang");
            mmp.put("lang", langIdx >= 0 && c.getString(langIdx) != null ? c.getString(langIdx) : "en");
            JSONObject data = new JSONObject(mmp);
            jsonArray.put(data);
            id = c.getInt(c.getColumnIndex("id"));
        }
        c.close();
        Map map = new HashMap();
        map.put("records", jsonArray.toString());
        map.put("school", preferences.getInt(MainActivity.SK_ID, 0));
        Log.e("discipline", jsonArray.toString());
        Log.e("LAST ID", id + "");
        if (recordFound) {
            req.post(server + "upload_data/discipline", map, new Callback() {
                @Override
                public void HttpCallback(JSONObject object, int status_code, String status) {
                    progressDialog.dismiss();
                    mNotifyManager.cancelAll();
                    if (object == null) {
                        Log.e("records", "Error:" + status);
                        uploadMaterialChecks(context);
                    } else {
                        try {
                            if (object.has("error")) {
                                Log.e("REC ERROR", object.getString("error") + "");
                                uploadMaterialChecks(context);
                            } else if (object.has("success")) {
                                Log.e("LAST SAVED ID", object.getInt("last_id") + "");
                                db.execSQL("delete from tb_discipline where IFNULL(code_no,0)=0 AND IFNULL(sync,1)=1 AND id <=" + object.getInt("last_id"));
                                Log.e("records", object.getString("success") + " Success");
                                uploadDiscipline(context);
                            } else {
                                Log.e("REC ERROR", object.optString("msg"));
                                uploadMaterialChecks(context);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            uploadMaterialChecks(context);
                        }
                    }
                }
            });
        } else {
            uploadMaterialChecks(context);
        }
    }

    private void syncRequiredMaterials(final Context context) {
        Log.e(TAG, "Syncing required materials");
        req = new HttpRequest(context);
        long updatedAtPref = preferences.getLong("updated_required_materials", 0);
        req.get(server + "sync/required_materials/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + updatedAtPref, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) {
                    Log.e(TAG, "required_materials sync: " + status);
                    return;
                }
                try {
                    if (!object.has("materials")) return;
                    JSONArray jarr = object.getJSONArray("materials");
                    int updatedAt = 0;
                    for (int a = 0; a < jarr.length(); a++) {
                        JSONObject data = jarr.getJSONObject(a);
                        Cursor cursor = db.rawQuery("select id from tb_required_materials where id=?", new String[]{String.valueOf(data.getInt("id"))});
                        if (cursor.moveToNext()) {
                            db.execSQL("update tb_required_materials set name=?, unit=?, sort_order=?, active=?, updated_at=? where id=?",
                                    new Object[]{
                                            data.optString("name"),
                                            data.optString("unit", "pcs"),
                                            data.optInt("sort_order", 0),
                                            data.optInt("active", 1),
                                            data.optInt("updated_at1", 0),
                                            data.getInt("id")
                                    });
                        } else {
                            db.execSQL("insert into tb_required_materials (id,name,unit,sort_order,active,updated_at) values (?,?,?,?,?,?)",
                                    new Object[]{
                                            data.getInt("id"),
                                            data.optString("name"),
                                            data.optString("unit", "pcs"),
                                            data.optInt("sort_order", 0),
                                            data.optInt("active", 1),
                                            data.optInt("updated_at1", 0)
                                    });
                        }
                        cursor.close();
                        if (updatedAt < data.optInt("updated_at1", 0))
                            updatedAt = data.optInt("updated_at1", 0);
                    }
                    if (updatedAt > 0) {
                        preferences.edit().putLong("updated_required_materials", updatedAt).apply();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void syncClassRequiredMaterials(final Context context) {
        Log.e(TAG, "Syncing class required materials");
        req = new HttpRequest(context);
        long updatedAtPref = preferences.getLong("updated_class_required_materials", 0);
        req.get(server + "sync/class_required_materials/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + updatedAtPref, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) {
                    Log.e(TAG, "class_required_materials sync: " + status);
                    return;
                }
                try {
                    if (!object.has("assignments")) return;
                    JSONArray jarr = object.getJSONArray("assignments");
                    int updatedAt = 0;
                    for (int a = 0; a < jarr.length(); a++) {
                        JSONObject data = jarr.getJSONObject(a);
                        Cursor cursor = db.rawQuery("select id from tb_class_required_materials where id=?", new String[]{String.valueOf(data.getInt("id"))});
                        if (cursor.moveToNext()) {
                            db.execSQL("update tb_class_required_materials set class_id=?, material_id=?, academic_year=?, quantity=?, updated_at=? where id=?",
                                    new Object[]{
                                            data.optInt("class_id", 0),
                                            data.optInt("material_id", 0),
                                            data.optInt("academic_year", 0),
                                            data.optDouble("quantity", 0),
                                            data.optInt("updated_at1", 0),
                                            data.getInt("id")
                                    });
                        } else {
                            db.execSQL("insert into tb_class_required_materials (id,class_id,material_id,academic_year,quantity,updated_at) values (?,?,?,?,?,?)",
                                    new Object[]{
                                            data.getInt("id"),
                                            data.optInt("class_id", 0),
                                            data.optInt("material_id", 0),
                                            data.optInt("academic_year", 0),
                                            data.optDouble("quantity", 0),
                                            data.optInt("updated_at1", 0)
                                    });
                        }
                        cursor.close();
                        if (updatedAt < data.optInt("updated_at1", 0))
                            updatedAt = data.optInt("updated_at1", 0);
                    }
                    if (updatedAt > 0) {
                        preferences.edit().putLong("updated_class_required_materials", updatedAt).apply();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void syncStudentMaterialChecks(final Context context) {
        Log.e(TAG, "Syncing student material checks");
        req = new HttpRequest(context);
        // Full reconcile each sync so app↔web mismatches are healed via conflict merge
        long updatedAtPref = 0;
        req.get(server + "sync/student_material_checks/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + updatedAtPref, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) {
                    Log.e(TAG, "student_material_checks sync: " + status);
                    syncHostels(context);
                    return;
                }
                try {
                    if (object.has("checks")) {
                        JSONArray jarr = object.getJSONArray("checks");
                        int updatedAt = 0;
                        for (int a = 0; a < jarr.length(); a++) {
                            JSONObject data = jarr.getJSONObject(a);
                            int serverId = data.getInt("id");
                            String localId = String.valueOf(serverId);
                            int studentId = data.optInt("student_id", 0);
                            int materialId = data.optInt("material_id", 0);
                            int yearId = data.optInt("academic_year", 0);
                            double serverBrought = data.optDouble("quantity_brought", 0);
                            int serverUpdated = data.optInt("updated_at1", 0);
                            Cursor cursor = db.rawQuery(
                                    "select id, sync, quantity_brought, updated_at from tb_student_material_checks where server_id=? OR (student_id=? AND material_id=? AND academic_year=?)",
                                    new String[]{String.valueOf(serverId), String.valueOf(studentId), String.valueOf(materialId), String.valueOf(yearId)});
                            if (cursor.moveToNext()) {
                                int localSync = cursor.getInt(cursor.getColumnIndex("sync"));
                                String existingId = cursor.getString(cursor.getColumnIndex("id"));
                                double localBrought = cursor.getDouble(cursor.getColumnIndex("quantity_brought"));
                                int localUpdated = cursor.getInt(cursor.getColumnIndex("updated_at"));
                                if (localSync == 1) {
                                    // Keep offline pending edits; only attach server_id
                                    db.execSQL("update tb_student_material_checks set server_id=? where id=?",
                                            new Object[]{serverId, existingId});
                                } else if (localBrought > serverBrought + 0.0001) {
                                    // App is ahead of web — re-queue upload so web catches up
                                    db.execSQL("update tb_student_material_checks set server_id=?, sync=1 where id=?",
                                            new Object[]{serverId, existingId});
                                } else {
                                    db.execSQL("update tb_student_material_checks set server_id=?, student_id=?, class_id=?, material_id=?, academic_year=?, quantity_required=?, quantity_brought=?, notes=?, checked_by=?, checker_name=?, checker_post=?, checked_at=?, sync=0, updated_at=? where id=?",
                                            new Object[]{
                                                    serverId,
                                                    studentId,
                                                    data.optInt("class_id", 0),
                                                    materialId,
                                                    yearId,
                                                    data.optDouble("quantity_required", 0),
                                                    serverBrought,
                                                    data.optString("notes", ""),
                                                    data.optInt("checked_by", 0),
                                                    cleanSyncLabel(data.optString("checker_name", "")),
                                                    cleanSyncLabel(data.optString("checker_post", "")),
                                                    data.optString("checked_at", ""),
                                                    serverUpdated,
                                                    existingId
                                            });
                                }
                            } else {
                                db.execSQL("insert into tb_student_material_checks (id,server_id,student_id,class_id,material_id,academic_year,quantity_required,quantity_brought,notes,checked_by,checker_name,checker_post,checked_at,sync,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                        new Object[]{
                                                localId,
                                                serverId,
                                                studentId,
                                                data.optInt("class_id", 0),
                                                materialId,
                                                yearId,
                                                data.optDouble("quantity_required", 0),
                                                serverBrought,
                                                data.optString("notes", ""),
                                                data.optInt("checked_by", 0),
                                                cleanSyncLabel(data.optString("checker_name", "")),
                                                cleanSyncLabel(data.optString("checker_post", "")),
                                                data.optString("checked_at", ""),
                                                0,
                                                serverUpdated
                                        });
                            }
                            cursor.close();
                            if (updatedAt < serverUpdated)
                                updatedAt = serverUpdated;
                        }
                        if (updatedAt > 0) {
                            preferences.edit().putLong("updated_student_material_checks", updatedAt).apply();
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // Push any rows re-queued as sync=1, then hostels
                uploadMaterialChecksAfterDownload(context);
            }
        });
    }

    /** Second-pass upload after download conflict detection (app ahead of web). */
    private void uploadMaterialChecksAfterDownload(final Context context) {
        req = new HttpRequest(context);
        Cursor c = db.rawQuery("select * from tb_student_material_checks where sync=1 limit 20", null);
        JSONArray jsonArray = new JSONArray();
        boolean recordFound = false;
        while (c.moveToNext()) {
            recordFound = true;
            Map mmp = new HashMap();
            mmp.put("id", c.getString(c.getColumnIndex("id")));
            mmp.put("student_id", c.getInt(c.getColumnIndex("student_id")));
            mmp.put("class_id", c.getInt(c.getColumnIndex("class_id")));
            mmp.put("material_id", c.getInt(c.getColumnIndex("material_id")));
            mmp.put("academic_year", c.getInt(c.getColumnIndex("academic_year")));
            mmp.put("quantity_required", c.getDouble(c.getColumnIndex("quantity_required")));
            mmp.put("quantity_brought", c.getDouble(c.getColumnIndex("quantity_brought")));
            String notes = c.getString(c.getColumnIndex("notes"));
            mmp.put("notes", notes == null || "null".equalsIgnoreCase(notes) ? "" : notes);
            mmp.put("checked_by", c.getInt(c.getColumnIndex("checked_by")));
            mmp.put("checker_name", cleanSyncLabel(c.getString(c.getColumnIndex("checker_name"))));
            mmp.put("checker_post", cleanSyncLabel(c.getString(c.getColumnIndex("checker_post"))));
            mmp.put("checked_at", c.getString(c.getColumnIndex("checked_at")));
            mmp.put("updated_at", c.getInt(c.getColumnIndex("updated_at")));
            jsonArray.put(new JSONObject(mmp));
        }
        c.close();
        if (!recordFound) {
            syncHostels(context);
            return;
        }
        Map map = new HashMap();
        map.put("records", jsonArray.toString());
        map.put("school", preferences.getInt(MainActivity.SK_ID, 0));
        req.post(server + "upload_data/material_checks", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object != null && object.has("success")) {
                    try {
                        JSONArray jarr = object.getJSONArray("ids");
                        for (int a = 0; a < jarr.length(); a++) {
                            db.execSQL("update tb_student_material_checks set sync=0 where id=?",
                                    new Object[]{jarr.getString(a)});
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                syncHostels(context);
            }
        });
    }

    private void syncHostels(final Context context) {
        Log.e(TAG, "Syncing hostels");
        req = new HttpRequest(context);
        long updatedAtPref = preferences.getLong("updated_hostels", 0);
        int yearId = preferences.getInt("soma_academic", 0);
        req.get(server + "sync/hostels/" + preferences.getInt(MainActivity.SK_ID, 0)
                + "?updatedAt=" + updatedAtPref + "&year=" + yearId, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) {
                    Log.e(TAG, "hostels sync: " + status);
                    syncHostelAllocations(context);
                    return;
                }
                try {
                    if (object.has("hostels")) {
                    JSONArray jarr = object.getJSONArray("hostels");
                    int updatedAt = 0;
                    for (int a = 0; a < jarr.length(); a++) {
                        JSONObject data = jarr.getJSONObject(a);
                        Cursor cursor = db.rawQuery("select id from tb_hostels where id=?", new String[]{String.valueOf(data.getInt("id"))});
                        if (cursor.moveToNext()) {
                            db.execSQL("update tb_hostels set name=?, max_beds=?, gender=?, level_group=?, sort_order=?, active=?, in_charge_staff_id=?, updated_at=? where id=?",
                                    new Object[]{
                                            data.optString("name"),
                                            data.optInt("max_beds", 0),
                                            data.optString("gender", "M"),
                                            data.optString("level_group", ""),
                                            data.optInt("sort_order", 0),
                                            data.optInt("active", 1),
                                            data.optInt("in_charge_staff_id", 0),
                                            data.optInt("updated_at1", 0),
                                            data.getInt("id")
                                    });
                        } else {
                            db.execSQL("insert into tb_hostels (id,name,max_beds,gender,level_group,sort_order,active,in_charge_staff_id,updated_at) values (?,?,?,?,?,?,?,?,?)",
                                    new Object[]{
                                            data.getInt("id"),
                                            data.optString("name"),
                                            data.optInt("max_beds", 0),
                                            data.optString("gender", "M"),
                                            data.optString("level_group", ""),
                                            data.optInt("sort_order", 0),
                                            data.optInt("active", 1),
                                            data.optInt("in_charge_staff_id", 0),
                                            data.optInt("updated_at1", 0)
                                    });
                        }
                        cursor.close();
                        if (updatedAt < data.optInt("updated_at1", 0))
                            updatedAt = data.optInt("updated_at1", 0);
                    }
                    if (updatedAt > 0) {
                        preferences.edit().putLong("updated_hostels", updatedAt).apply();
                    }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                syncHostelAllocations(context);
            }
        });
    }

    private void syncHostelAllocations(final Context context) {
        Log.e(TAG, "Syncing hostel allocations");
        req = new HttpRequest(context);
        long updatedAtPref = preferences.getLong("updated_hostel_allocations", 0);
        req.get(server + "sync/hostel_allocations/" + preferences.getInt(MainActivity.SK_ID, 0) + "?updatedAt=" + updatedAtPref, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) {
                    Log.e(TAG, "hostel_allocations sync: " + status);
                    finishSync(context, false);
                    return;
                }
                try {
                    if (object.has("allocations")) {
                    JSONArray jarr = object.getJSONArray("allocations");
                    int updatedAt = 0;
                    for (int a = 0; a < jarr.length(); a++) {
                        JSONObject data = jarr.getJSONObject(a);
                        int serverId = data.getInt("id");
                        String localId = String.valueOf(serverId);
                        int studentId = data.optInt("student_id", 0);
                        int yearId = data.optInt("academic_year", 0);
                        Cursor cursor = db.rawQuery(
                                "select id, sync from tb_hostel_allocations where server_id=? OR (student_id=? AND academic_year=? AND sync=0)",
                                new String[]{String.valueOf(serverId), String.valueOf(studentId), String.valueOf(yearId)});
                        if (cursor.moveToNext()) {
                            int localSync = cursor.getInt(cursor.getColumnIndex("sync"));
                            String existingId = cursor.getString(cursor.getColumnIndex("id"));
                            if (localSync == 1) {
                                db.execSQL("update tb_hostel_allocations set server_id=? where id=?",
                                        new Object[]{serverId, existingId});
                            } else {
                                db.execSQL("update tb_hostel_allocations set server_id=?, hostel_id=?, student_id=?, academic_year=?, allocated_by=?, action='assign', sync=0, updated_at=? where id=?",
                                        new Object[]{
                                                serverId,
                                                data.optInt("hostel_id", 0),
                                                studentId,
                                                yearId,
                                                data.optInt("allocated_by", 0),
                                                data.optInt("updated_at1", 0),
                                                existingId
                                        });
                            }
                        } else {
                            // Replace any older synced row for same student+year then insert
                            db.execSQL("delete from tb_hostel_allocations where student_id=? AND academic_year=? AND sync=0",
                                    new Object[]{studentId, yearId});
                            db.execSQL("insert into tb_hostel_allocations (id,server_id,hostel_id,student_id,academic_year,allocated_by,action,sync,updated_at) values (?,?,?,?,?,?,?,?,?)",
                                    new Object[]{
                                            localId,
                                            serverId,
                                            data.optInt("hostel_id", 0),
                                            studentId,
                                            yearId,
                                            data.optInt("allocated_by", 0),
                                            "assign",
                                            0,
                                            data.optInt("updated_at1", 0)
                                    });
                        }
                        cursor.close();
                        if (updatedAt < data.optInt("updated_at1", 0))
                            updatedAt = data.optInt("updated_at1", 0);
                    }
                    if (updatedAt > 0) {
                        preferences.edit().putLong("updated_hostel_allocations", updatedAt).apply();
                    }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                syncDormSheets(context);
            }
        });
    }

    private void syncDormSheets(final Context context) {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        int staffId = preferences.getInt("soma_id", 0);
        int yearId = preferences.getInt("soma_academic", 0);
        if (schoolId < 1 || staffId < 1) {
            syncConductCodes(context);
            return;
        }
        String url = server + "dormitory_attendance_sync/" + schoolId + "/" + staffId + "?year=" + yearId;
        new HttpRequest(context).timeouts(20, 60).get(url, JSONObject.class, (object, statusCode, status) -> {
            if (object != null && object.optBoolean("success", false)) {
                DormRosterStore.replaceAll(db, object.optJSONArray("dorms"), object.optString("date", ""));
                Log.e(TAG, "Dorm rosters saved: " + object.optInt("count"));
            } else {
                Log.e(TAG, "Dorm roster sync failed: " + status);
            }
            syncConductCodes(context);
        });
    }

    /** Save Level Clearance on this phone so menus stay correct offline. */
    private void syncMenuClearance(final Context context) {
        int postId = preferences.getInt("soma_post", 0);
        int staffId = preferences.getInt("soma_id", 0);
        if (postId <= 0 && staffId <= 0) return;
        String url = server + "menu_clearance/" + postId + (staffId > 0 ? ("?staff_id=" + staffId) : "");
        new HttpRequest(context).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) return;
                SharedPreferences.Editor ed = preferences.edit();
                MenuClearanceHelper.saveFromLogin(ed, object);
                ed.apply();
            }
        });
    }

    private void syncConductCodes(final Context context) {
        Log.e(TAG, "Syncing conduct codes");
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        req = new HttpRequest(context);
        req.get(server + "discipline_codes?school_id=" + schoolId + "&lang=en", JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object != null) {
                    ConductCatalog.mergeServerGroups(context, db, object.optJSONArray("groups"));
                } else {
                    Log.e(TAG, "conduct codes sync: " + status);
                }
                syncStaffAccounts(context);
            }
        });
    }

    /** Last step of Sync. Staff logins must be on the phone before Sync complete. */
    private void syncStaffAccounts(final Context context) {
        Log.e(TAG, "Syncing staff accounts");
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (mBuilder != null && mNotifyManager != null) {
            mBuilder.setProgress(100, 95, false);
            mBuilder.setContentText("Saving staff accounts");
            mNotifyManager.notify(1112, mBuilder.build());
        }
        if (schoolId < 1) {
            finishSync(context, false, "Sync failed. This phone has no school yet.");
            return;
        }
        req = new HttpRequest(context).timeouts(30, 90);
        req.get(server + "sync/staff_login/" + schoolId, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error") || object.optJSONArray("staff_accounts") == null) {
                    Log.e(TAG, "staff account sync: " + status);
                    finishSync(context, false, "Sync failed while saving staff accounts");
                    return;
                }
                int saved = StaffLoginStore.save(db, object);
                if (saved < 1) {
                    finishSync(context, false, "Sync failed. No staff accounts were saved.");
                    return;
                }
                StaffLoginStore.saveDevice(preferences, object.optJSONObject("device"));
                preferences.edit().putInt("staff_login_saved", saved).apply();
                finishSync(context, true, "Sync complete. " + saved + " staff accounts saved.");
            }
        });
    }

    private static String cleanSyncLabel(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private void uploadMaterialChecks(final Context context) {
        Log.e(TAG, "Uploading material checks");
        // One-time heal: push all local brought totals to web (fixes app Complete / web Missing)
        if (!preferences.getBoolean("material_heal_v42", false)) {
            db.execSQL("update tb_student_material_checks set sync=1 where quantity_brought > 0");
            preferences.edit().putBoolean("material_heal_v42", true).apply();
            Log.e(TAG, "Material heal: re-queued local brought totals for upload");
        }
        progressDialog.setProgress(PROGRESS * 3);
        if (mBuilder != null && mNotifyManager != null) {
            mBuilder.setProgress(100, 70, false);
            mBuilder.setContentText("Uploading material checks");
            mNotifyManager.notify(1112, mBuilder.build());
        }
        req = new HttpRequest(context);
        Cursor c = db.rawQuery("select * from tb_student_material_checks where sync=1 limit 10", null);
        JSONArray jsonArray = new JSONArray();
        boolean recordFound = false;
        while (c.moveToNext()) {
            recordFound = true;
            Map mmp = new HashMap();
            mmp.put("id", c.getString(c.getColumnIndex("id")));
            mmp.put("student_id", c.getInt(c.getColumnIndex("student_id")));
            mmp.put("class_id", c.getInt(c.getColumnIndex("class_id")));
            mmp.put("material_id", c.getInt(c.getColumnIndex("material_id")));
            mmp.put("academic_year", c.getInt(c.getColumnIndex("academic_year")));
            mmp.put("quantity_required", c.getDouble(c.getColumnIndex("quantity_required")));
            mmp.put("quantity_brought", c.getDouble(c.getColumnIndex("quantity_brought")));
            String notes = c.getString(c.getColumnIndex("notes"));
            mmp.put("notes", notes == null || "null".equalsIgnoreCase(notes) ? "" : notes);
            mmp.put("checked_by", c.getInt(c.getColumnIndex("checked_by")));
            mmp.put("checker_name", cleanSyncLabel(c.getString(c.getColumnIndex("checker_name"))));
            mmp.put("checker_post", cleanSyncLabel(c.getString(c.getColumnIndex("checker_post"))));
            mmp.put("checked_at", c.getString(c.getColumnIndex("checked_at")));
            mmp.put("updated_at", c.getInt(c.getColumnIndex("updated_at")));
            jsonArray.put(new JSONObject(mmp));
        }
        c.close();
        Map map = new HashMap();
        map.put("records", jsonArray.toString());
        map.put("school", preferences.getInt(MainActivity.SK_ID, 0));
        if (recordFound) {
            req.post(server + "upload_data/material_checks", map, new Callback() {
                @Override
                public void HttpCallback(JSONObject object, int status_code, String status) {
                    if (object == null) {
                        Log.e("material_checks", "Error:" + status);
                        uploadHostelAllocations(context);
                    } else {
                        try {
                            if (object.has("error")) {
                                Log.e("material_checks", object.getString("error"));
                                uploadHostelAllocations(context);
                            } else if (object.has("success")) {
                                JSONArray jarr = object.getJSONArray("ids");
                                for (int a = 0; a < jarr.length(); a++) {
                                    String savedId = jarr.getString(a);
                                    db.execSQL("update tb_student_material_checks set sync=0 where id=?", new Object[]{savedId});
                                }
                                uploadMaterialChecks(context);
                            } else {
                                uploadHostelAllocations(context);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            uploadHostelAllocations(context);
                        }
                    }
                }
            });
        } else {
            uploadHostelAllocations(context);
        }
    }

    private void uploadHostelAllocations(final Context context) {
        Log.e(TAG, "Uploading hostel allocations");
        progressDialog.setProgress(PROGRESS * 3);
        if (mBuilder != null && mNotifyManager != null) {
            mBuilder.setProgress(100, 85, false);
            mBuilder.setContentText("Uploading hostel allocations");
            mNotifyManager.notify(1112, mBuilder.build());
        }
        req = new HttpRequest(context);
        Cursor c = db.rawQuery("select * from tb_hostel_allocations where sync=1 limit 10", null);
        JSONArray jsonArray = new JSONArray();
        boolean recordFound = false;
        while (c.moveToNext()) {
            recordFound = true;
            Map mmp = new HashMap();
            mmp.put("id", c.getString(c.getColumnIndex("id")));
            mmp.put("hostel_id", c.getInt(c.getColumnIndex("hostel_id")));
            mmp.put("student_id", c.getInt(c.getColumnIndex("student_id")));
            mmp.put("academic_year", c.getInt(c.getColumnIndex("academic_year")));
            mmp.put("allocated_by", c.getInt(c.getColumnIndex("allocated_by")));
            mmp.put("action", c.getString(c.getColumnIndex("action")));
            jsonArray.put(new JSONObject(mmp));
        }
        c.close();
        Map map = new HashMap();
        map.put("records", jsonArray.toString());
        map.put("school", preferences.getInt(MainActivity.SK_ID, 0));
        if (recordFound) {
            req.post(server + "upload_data/hostel_allocations", map, new Callback() {
                @Override
                public void HttpCallback(JSONObject object, int status_code, String status) {
                    if (object == null) {
                        Log.e("hostel_allocations", "Error:" + status);
                        syncStudentMaterialChecks(context);
                    } else {
                        try {
                            if (object.has("error")) {
                                Log.e("hostel_allocations", object.getString("error"));
                                syncStudentMaterialChecks(context);
                            } else if (object.has("success")) {
                                JSONArray jarr = object.getJSONArray("ids");
                                for (int a = 0; a < jarr.length(); a++) {
                                    String savedId = jarr.getString(a);
                                    db.execSQL("update tb_hostel_allocations set sync=0 where id=?", new Object[]{savedId});
                                }
                                uploadHostelAllocations(context);
                            } else {
                                syncStudentMaterialChecks(context);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            syncStudentMaterialChecks(context);
                        }
                    }
                }
            });
        } else {
            // Uploads done — download materials/hostels and heal any app-ahead rows
            syncStudentMaterialChecks(context);
        }
    }

    private void finishSync(Context context, boolean ok) {
        finishSync(context, ok, ok ? "Sync complete" : "Sync finished with errors");
    }

    private void finishSync(Context context, boolean ok, String message) {
        success = success + 1;
        if (mBuilder != null && mNotifyManager != null) {
            mBuilder.setProgress(0, 0, false);
            mBuilder.setContentText(message);
            mNotifyManager.notify(1112, mBuilder.build());
        }
        Intent intent = new Intent("sync.qonics.com.somanet.success");
        intent.setPackage(context.getPackageName());
        intent.putExtra("status", ok ? 2 : 1);
        context.sendBroadcast(intent);
        releaseSyncLock();
        broadcastProgress(context, false);
    }

    private void showNotification(Context context) {
        mNotifyManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        final String channelId = "smartsms_sync";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "SmartSMS Sync",
                    NotificationManager.IMPORTANCE_LOW
            );
            mNotifyManager.createNotificationChannel(channel);
        }
        mBuilder = new NotificationCompat.Builder(context, channelId);
        mBuilder.setContentTitle("SmartSMS sync")
                .setContentText("Upload records")
                .setSmallIcon(R.mipmap.ic_launcher);
        mBuilder.setProgress(100, PROGRESS, false);
        mBuilder.setAutoCancel(true);
        mNotifyManager.notify(1112, mBuilder.build());

    }

    private String escapeString(String s) {
        if (s.contains("'")) {
            s = s.replaceAll("'", " ");
        }
        return s;
    }
}
