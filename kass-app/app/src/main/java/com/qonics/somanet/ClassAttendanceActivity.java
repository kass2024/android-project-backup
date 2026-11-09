package com.qonics.somanet;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Calendar;
import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.SyncStatusHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class ClassAttendanceActivity extends AppCompatActivity {
    private static final String TAG = "ClassAttendance";

    FloatingActionButton fab;
    Toolbar toolbar;
    ImageView imgerror;
    RelativeLayout rlvloading;
    ProgressBar prb;
    TextView txtstatus, txtcount, txtretry, txtClassName, txtDeviceHint, txtAttendanceDate, btnDateNext;
    LinearLayout lnlstudents;
    SearchView searchStudent;
    NestedScrollView scrollStudents;
    String server = MainActivity.SERVER;
    ActionBar actionBar;
    SharedPreferences preferences;
    int stTotal = 0;
    SQLiteDatabase db;
    private boolean saving = false;
    private String selectedDate = "";

    private final ArrayList<StudentRow> students = new ArrayList<>();
    private final Set<Integer> absentIds = new HashSet<>();
    private final HashMap<Integer, String> permissionCover = new HashMap<>();
    private BroadcastReceiver syncReloadReceiver;
    private boolean awaitingInitialSync = false;
    private boolean initialSyncRequested = false;
    private boolean rosterFetched = false;
    private String searchQuery = "";
    private boolean deviceLocked = false;

    private static class StudentRow {
        final int id;
        final int recordId;
        String name;
        final String regno;
        int studyingMode;
        View view;
        boolean inactive;
        boolean devicePresent;
        boolean manualPresent;
        String manualReason;
        String approvedName;

        StudentRow(int id, int recordId, String name, String regno, int studyingMode) {
            this.id = id;
            this.recordId = recordId;
            this.name = name;
            this.regno = regno;
            this.studyingMode = studyingMode == 0 ? 0 : 1;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_attendance);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        deviceLocked = preferences.getInt("soma_is_master", 0) == 1;
        db = new DatabaseHandler(this).getWritableDatabase();
        new DatabaseHandler(this).onCreate(db);
        registerSyncReloadReceiver();
        populateView();
        loadStudent(false);
    }

    @Override
    protected void onDestroy() {
        unregisterSyncReloadReceiver();
        super.onDestroy();
    }

    private void populateView() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        actionBar = getSupportActionBar();
        txtretry = findViewById(R.id.txtretry);
        txtClassName = findViewById(R.id.txtClassName);
        txtDeviceHint = findViewById(R.id.txtDeviceHint);
        String classTitle = resolveClassTitle();
        actionBar.setDisplayShowTitleEnabled(false);
        if (txtClassName != null) {
            txtClassName.setText(getString(R.string.atte_class_line, classTitle));
        }
        if (txtDeviceHint != null) {
            if (deviceLocked) {
                txtDeviceHint.setText(R.string.atte_device_hint);
            } else {
                txtDeviceHint.setText(R.string.atte_manual_register_hint);
            }
            txtDeviceHint.setVisibility(View.VISIBLE);
        }
        fab = findViewById(R.id.fab);
        txtstatus = findViewById(R.id.txtstatus);
        txtcount = findViewById(R.id.txtcount);
        prb = findViewById(R.id.prb);
        rlvloading = findViewById(R.id.rlvloading);
        imgerror = findViewById(R.id.imgerror);
        lnlstudents = findViewById(R.id.lnlstudents);
        searchStudent = findViewById(R.id.srchstudent);
        scrollStudents = findViewById(R.id.scrollStudents);

        txtretry.setVisibility(View.INVISIBLE);
        txtretry.setOnClickListener(view -> {
            initialSyncRequested = false;
            rosterFetched = false;
            loadStudent(false);
        });

        if (searchStudent != null) {
            searchStudent.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchQuery = query == null ? "" : query.trim();
                    applySearchFilter();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    searchQuery = newText == null ? "" : newText.trim();
                    applySearchFilter();
                    return true;
                }
            });
        }

        fab.hide();
        fab.setOnClickListener(view -> {
            if (deviceLocked || viewingPast()) return;
            sendAttendance();
        });
        bindDateFilter();
    }

    private void bindDateFilter() {
        selectedDate = todayDate();
        txtAttendanceDate = findViewById(R.id.txtAttendanceDate);
        btnDateNext = findViewById(R.id.btnDateNext);
        TextView prev = findViewById(R.id.btnDatePrev);
        if (prev != null) {
            prev.setOnClickListener(view -> shiftDate(-1));
        }
        if (btnDateNext != null) {
            btnDateNext.setOnClickListener(view -> shiftDate(1));
        }
        if (txtAttendanceDate != null) {
            txtAttendanceDate.setOnClickListener(view -> pickDate());
        }
        paintDateChrome();
    }

    private boolean viewingPast() {
        return selectedDate != null && selectedDate.length() == 10 && !selectedDate.equals(todayDate());
    }

    private void paintDateChrome() {
        boolean past = viewingPast();
        if (txtAttendanceDate != null) {
            txtAttendanceDate.setText(past ? selectedDate : getString(R.string.atte_date_today, selectedDate));
        }
        if (btnDateNext != null) {
            btnDateNext.setEnabled(past);
            btnDateNext.setAlpha(past ? 1f : 0.35f);
        }
        if (txtDeviceHint != null) {
            if (past) {
                txtDeviceHint.setText(R.string.atte_past_hint);
            } else if (deviceLocked) {
                txtDeviceHint.setText(R.string.atte_device_hint);
            } else {
                txtDeviceHint.setText(R.string.atte_manual_register_hint);
            }
        }
        if (fab != null) {
            if (past || deviceLocked || students.isEmpty()) {
                fab.hide();
            } else {
                fab.show();
            }
        }
    }

    private Calendar calendarFor(String day) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        try {
            String[] parts = day.split("-");
            cal.set(Calendar.YEAR, Integer.parseInt(parts[0]));
            cal.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[2]));
        } catch (Exception ignored) {
            cal.setTime(new Date());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }
        return cal;
    }

    private void shiftDate(int days) {
        Calendar cal = calendarFor(selectedDate);
        cal.add(Calendar.DATE, days);
        Calendar today = calendarFor(todayDate());
        if (cal.after(today)) {
            return;
        }
        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
        showSelectedDay();
    }

    private void pickDate() {
        Calendar cal = calendarFor(selectedDate);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, day, 0, 0, 0);
            picked.set(Calendar.MILLISECOND, 0);
            if (picked.after(calendarFor(todayDate()))) {
                picked = calendarFor(todayDate());
            }
            selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(picked.getTime());
            showSelectedDay();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void showSelectedDay() {
        paintDateChrome();
        if (students.isEmpty()) {
            return;
        }
        int classId = getIntent().getIntExtra("classId", 0);
        resetSheetLooks();
        loadPermissionCover(classId);
        applyCachedAttendance(classId);
        if (viewingPast()) {
            fetchAttendanceDay(classId);
        } else if (deviceLocked) {
            fetchDeviceAttendance(classId);
        }
        fetchPermissionCover(classId);
        applySearchFilter();
        refreshCount();
    }

    private void resetSheetLooks() {
        absentIds.clear();
        for (StudentRow row : students) {
            if (row == null || row.inactive) {
                continue;
            }
            row.devicePresent = false;
            row.manualPresent = false;
            applyRowPresentLook(row);
        }
    }

    private void sendAttendance() {
        if (saving || deviceLocked || viewingPast()) {
            return;
        }
        saving = true;
        if (fab != null) {
            fab.setEnabled(false);
        }

        JSONArray jar = new JSONArray();
        for (StudentRow row : students) {
            if (row.inactive) continue;
            if (absentIds.contains(row.id)) continue;
            jar.put(row.id);
        }

        String pendingId = UUID.randomUUID().toString();
        try {
            db.execSQL(
                    "insert into tb_daily_attendance_pending (id,class_id,term,teacher,students_json,sync,created_at) values (?,?,?,?,?,?,?)",
                    new Object[]{
                            pendingId,
                            getIntent().getIntExtra("classId", 0),
                            preferences.getInt("soma_term", 0),
                            preferences.getInt("soma_id", 0),
                            jar.toString(),
                            1,
                            System.currentTimeMillis() / 1000L
                    }
            );
            cacheTodayAttendance(jar);
        } catch (Exception e) {
            Log.e(TAG, "Failed to queue attendance", e);
            saving = false;
            if (fab != null) {
                fab.setEnabled(true);
            }
            Toast.makeText(this, getString(R.string.oops) + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        // Always confirm locally first — do not wait on the network or a Please wait overlay.
        showSavedDialog(getString(R.string.atte_save_msg));

        if (MainActivity.isNetworkAvailable(this)) {
            Map map = new HashMap();
            map.put("teacher", preferences.getInt("soma_id", 0));
            map.put("term", preferences.getInt("soma_term", 0));
            map.put("class", getIntent().getIntExtra("classId", 0));
            map.put("students", jar.toString());
            new HttpRequest(this).post(server + "save_class_attendance", map, (object, status_code, status) -> {
                boolean ok = object != null && (object.has("success")
                        || (object.has("error") && object.optString("error").toLowerCase().contains("already")));
                if (ok) {
                    try {
                        db.execSQL("update tb_daily_attendance_pending set sync=0 where id=?", new Object[]{pendingId});
                    } catch (Exception e) {
                        Log.e(TAG, "mark attendance synced", e);
                    }
                } else {
                    Log.e(TAG, "Immediate attendance upload failed: " + status);
                }
            });
        }
        SyncReceiver.requestSync(this);
    }

    private void showSavedDialog(String msg) {
        rlvloading.setVisibility(View.GONE);
        lnlstudents.setVisibility(View.VISIBLE);
        if (isFinishing() || isDestroyed()) {
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            SweetAlertDialog sa = new SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE);
            sa.setTitleText(getString(R.string.good_job));
            sa.setContentText(msg);
            sa.setConfirmText(getString(R.string.ok));
            sa.setConfirmClickListener(dialog -> {
                dialog.dismissWithAnimation();
                if (!isFinishing()) {
                    finish();
                }
            });
            sa.setCancelable(false);
            sa.show();
        } catch (Exception e) {
            Log.e(TAG, "Success dialog failed", e);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            fab.postDelayed(() -> {
                if (!isFinishing()) {
                    finish();
                }
            }, 1200);
        }
    }

    private void loadStudent() {
        loadStudent(false);
    }

    private void loadStudent(boolean fromRoster) {
        HashSet<Integer> keepAbsents = fromRoster ? new HashSet<>(absentIds) : new HashSet<>();
        lnlstudents.removeAllViews();
        students.clear();
        absentIds.clear();
        changeTitle(getString(R.string.please_wait));
        imgerror.setVisibility(View.INVISIBLE);
        txtstatus.setText(getString(R.string.please_wait));
        prb.setVisibility(View.VISIBLE);
        txtretry.setVisibility(View.INVISIBLE);
        fab.hide();

        int classId = getIntent().getIntExtra("classId", 0);
        try {
            Cursor c = db.rawQuery(
                    "SELECT id, name, regno, IFNULL(record_id,0), IFNULL(studying_mode,1) FROM tb_student " +
                            "WHERE class_id=? AND IFNULL(status,1)=1 ORDER BY name ASC",
                    new String[]{String.valueOf(classId)}
            );
            rlvloading.setVisibility(View.GONE);
            lnlstudents.setVisibility(View.VISIBLE);
            stTotal = 0;
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String name = c.getString(1);
                String regno = c.getString(2);
                int recordId = c.getInt(3);
                int studyingMode = c.getInt(4);
                bindStudentRow(id, recordId, name, regno, studyingMode);
                stTotal++;
            }
            c.close();
            for (Integer id : keepAbsents) {
                StudentRow row = findStudent(id);
                if (row != null && !absentIds.contains(row.id)) {
                    absentIds.add(row.id);
                    applyRowAbsentLook(row);
                }
            }
            if (stTotal == 0) {
                imgerror.setVisibility(View.VISIBLE);
                prb.setVisibility(View.INVISIBLE);
                rlvloading.setVisibility(View.VISIBLE);
                txtstatus.setText(getString(R.string.atte_err_one));
                txtretry.setVisibility(View.VISIBLE);
                changeTitle(getString(R.string.no_student));
                awaitingInitialSync = true;
            } else if (viewingPast()) {
                showSelectedDay();
                prb.setVisibility(View.INVISIBLE);
                awaitingInitialSync = false;
            } else {
                loadPermissionCover(classId);
                applyCachedAttendance(classId);
                applySearchFilter();
                refreshCount();
                prb.setVisibility(View.INVISIBLE);
                awaitingInitialSync = false;
                if (deviceLocked) {
                    fab.hide();
                } else {
                    fab.show();
                }
                if (deviceLocked && MainActivity.isNetworkAvailable(this)) {
                    fetchDeviceAttendance(classId);
                }
            }
            if (!fromRoster && !rosterFetched && MainActivity.isNetworkAvailable(this)) {
                fetchLiveClassRoster();
            }
            if (MainActivity.isNetworkAvailable(this)) {
                fetchPermissionCover(classId);
            }
            if (!initialSyncRequested) {
                initialSyncRequested = true;
                SyncReceiver.requestSync(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Offline student load failed", e);
            imgerror.setVisibility(View.VISIBLE);
            prb.setVisibility(View.INVISIBLE);
            txtstatus.setText(getString(R.string.oops) + e.getMessage());
            txtretry.setVisibility(View.VISIBLE);
            changeTitle(getString(R.string.error_occurred));
        }
    }

    private void fetchLiveClassRoster() {
        rosterFetched = true;
        int classId = getIntent().getIntExtra("classId", 0);
        Map map = new HashMap();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("class_id", classId);
        map.put("year", preferences.getInt("soma_academic", 0));
        new HttpRequest(this).timeouts(15, 45).post(server + "get_class_roster", map, (object, status_code, status) -> {
            if (object == null || !object.has("students")) {
                Log.e(TAG, "Live class roster: " + status);
                rosterFetched = false;
                return;
            }
            try {
                applyLiveRoster(classId, object.getJSONArray("students"));
                loadStudent(true);
            } catch (Exception e) {
                Log.e(TAG, "Live class roster apply", e);
            }
        });
    }

    private void applyLiveRoster(int classId, JSONArray jarr) throws Exception {
        HashSet<Integer> liveIds = new HashSet<>();
        for (int i = 0; i < jarr.length(); i++) {
            JSONObject data = jarr.getJSONObject(i);
            int studentId = data.optInt("id", 0);
            if (studentId < 1) continue;
            liveIds.add(studentId);
            int recordId = data.optInt("record_id", 0);
            int stStatus = data.optInt("status", 1);
            if (stStatus != 1 && stStatus != 2) {
                stStatus = 1;
            }
            Cursor cursor = db.rawQuery("select id from tb_student where id=?",
                    new String[]{String.valueOf(studentId)});
            boolean exists = cursor.moveToNext();
            cursor.close();
            if (exists) {
                db.execSQL("update tb_student set name=?, regno=?, card=?, classe=?, phone=?, class_id=?, sex=?, dept=?, studying_mode=?, photo=?, record_id=?, status=? where id=?",
                        new Object[]{
                                data.optString("name"),
                                data.optString("regno"),
                                data.optString("card"),
                                data.optString("class"),
                                data.optString("phone"),
                                classId,
                                data.optString("sex"),
                                data.optString("dept_title"),
                                data.optInt("studying_mode", 1),
                                data.optString("photo"),
                                recordId,
                                stStatus,
                                studentId
                        });
            } else {
                try {
                    db.execSQL("insert into tb_student (id,regno,name,card,classe,dept,studying_mode,photo,class_id,phone,sex,status,record_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            new Object[]{
                                    studentId,
                                    data.optString("regno"),
                                    data.optString("name"),
                                    data.optString("card"),
                                    data.optString("class"),
                                    data.optString("dept_title"),
                                    data.optInt("studying_mode", 1),
                                    data.optString("photo"),
                                    classId,
                                    data.optString("phone"),
                                    data.optString("sex"),
                                    stStatus,
                                    recordId
                            });
                } catch (Exception e) {
                    Log.e(TAG, "insert live roster student " + studentId, e);
                }
            }
        }
        Cursor extra = db.rawQuery("select id from tb_student where class_id=?",
                new String[]{String.valueOf(classId)});
        ArrayList<Integer> prune = new ArrayList<>();
        while (extra.moveToNext()) {
            int id = extra.getInt(0);
            if (!liveIds.contains(id)) {
                prune.add(id);
            }
        }
        extra.close();
        for (Integer id : prune) {
            db.execSQL("update tb_student set class_id=0 where id=?", new Object[]{id});
        }
        Log.e(TAG, "Live roster class=" + classId + " kept=" + liveIds.size() + " pruned=" + prune.size());
    }

    private StudentRow findStudent(int id) {
        for (StudentRow row : students) {
            if (row.id == id) return row;
        }
        return null;
    }

    private void applySearchFilter() {
        String q = searchQuery == null ? "" : searchQuery.toLowerCase(Locale.US);
        for (StudentRow row : students) {
            if (row.view == null || row.inactive) continue;
            if (q.isEmpty()) {
                row.view.setVisibility(View.VISIBLE);
                continue;
            }
            String name = row.name == null ? "" : row.name.toLowerCase(Locale.US);
            String regno = row.regno == null ? "" : row.regno.toLowerCase(Locale.US);
            boolean match = name.contains(q) || regno.contains(q);
            row.view.setVisibility(match ? View.VISIBLE : View.GONE);
        }
    }

    private void bindStudentRow(int id, int recordId, String name, String regno, int studyingMode) {
        View v = getLayoutInflater().inflate(R.layout.student_row, null);
        TextView txtname = v.findViewById(R.id.txtname);
        TextView txtregno = v.findViewById(R.id.txtregno);
        TextView txtRowStatus = v.findViewById(R.id.txtrowstatus);
        ImageView imgabsent = v.findViewById(R.id.imgabsent);
        ImageView imgpresent = v.findViewById(R.id.imgpresent);
        ImageView imgdelete = v.findViewById(R.id.imgdelete);
        txtname.setText(name);
        if (imgdelete != null) imgdelete.setVisibility(View.GONE);

        StudentRow row = new StudentRow(id, recordId, name, regno, studyingMode);
        row.view = v;
        students.add(row);
        bindRegnoLine(row, txtregno);

        imgabsent.setOnClickListener(view -> onAbsentClick(row, txtname, txtRowStatus, imgabsent));
        if (imgpresent != null) {
            imgpresent.setOnClickListener(view -> onPresentClick(row, txtname, txtRowStatus, imgabsent));
        }
        applyRowPresentLook(row);
        lnlstudents.addView(v);
    }

    private void bindRegnoLine(StudentRow row, TextView txtregno) {
        if (txtregno == null) return;
        txtregno.setText(row.regno + "  ·  " + modeLabel(row.studyingMode));
    }

    private String modeLabel(int mode) {
        return getString(mode == 0 ? R.string.atte_mode_boarding : R.string.atte_mode_day);
    }

    private void attachNameGestures(View rowView, StudentRow row, TextView txtname) {
        View nameTarget = rowView.findViewById(R.id.lnlStudentInfo);
        if (nameTarget == null) {
            nameTarget = txtname;
        }
        final GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                promptEditName(row, txtname);
                return true;
            }
        });
        nameTarget.setOnTouchListener((view, event) -> detector.onTouchEvent(event));
    }

    private void promptEditName(StudentRow row, TextView txtname) {
        if (!MainActivity.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.atte_edit_need_online, Toast.LENGTH_LONG).show();
            return;
        }
        String[] parts = splitName(row.name);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        final EditText edtFname = new EditText(this);
        edtFname.setHint(R.string.staff_fname);
        edtFname.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        edtFname.setText(parts[0]);
        box.addView(edtFname);

        final EditText edtLname = new EditText(this);
        edtLname.setHint(R.string.staff_lname);
        edtLname.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        edtLname.setText(parts[1]);
        box.addView(edtLname);

        TextView modeLbl = new TextView(this);
        modeLbl.setText(R.string.atte_edit_mode);
        modeLbl.setPadding(0, pad / 2, 0, 0);
        box.addView(modeLbl);

        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbBoard = new RadioButton(this);
        rbBoard.setId(View.generateViewId());
        rbBoard.setText(R.string.atte_mode_boarding);
        RadioButton rbDay = new RadioButton(this);
        rbDay.setId(View.generateViewId());
        rbDay.setText(R.string.atte_mode_day);
        rg.addView(rbBoard);
        rg.addView(rbDay);
        if (row.studyingMode == 1) {
            rbDay.setChecked(true);
        } else {
            rbBoard.setChecked(true);
        }
        box.addView(rg);
        final int boardingId = rbBoard.getId();

        new AlertDialog.Builder(this)
                .setTitle(R.string.atte_edit_title)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, which) -> {
                    String fname = edtFname.getText().toString().trim();
                    String lname = edtLname.getText().toString().trim();
                    if (fname.isEmpty() || lname.isEmpty()) {
                        Toast.makeText(this, R.string.staff_name_required, Toast.LENGTH_LONG).show();
                        return;
                    }
                    int mode = rg.getCheckedRadioButtonId() == boardingId ? 0 : 1;
                    saveStudentNameLive(row, txtname, fname, lname, mode);
                })
                .show();
    }

    private void saveStudentNameLive(StudentRow row, TextView txtname, String fname, String lname, int studyingMode) {
        if (!MainActivity.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.atte_edit_need_online, Toast.LENGTH_LONG).show();
            return;
        }
        if (!studentExistsLocally(row.id)) {
            Toast.makeText(this, R.string.atte_delete_missing_local, Toast.LENGTH_LONG).show();
            return;
        }
        SweetAlertDialog wait = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
        wait.setTitleText(getString(R.string.please_wait));
        wait.setCancelable(false);
        Map map = new HashMap();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("student_id", row.id);
        map.put("fname", fname);
        map.put("lname", lname);
        map.put("studying_mode", studyingMode);
        new HttpRequest(this).progress(wait).post(server + "update_student_name", map, (object, status_code, status) -> {
            if (object != null && object.has("success")) {
                String full = object.optString("name", (fname + " " + lname).trim());
                row.name = full;
                row.studyingMode = object.optInt("studying_mode", studyingMode);
                txtname.setText(full);
                if (row.view != null) {
                    bindRegnoLine(row, row.view.findViewById(R.id.txtregno));
                }
                try {
                    db.execSQL("update tb_student set name=?, studying_mode=? where id=?",
                            new Object[]{full, row.studyingMode, row.id});
                } catch (Exception e) {
                    Log.e(TAG, "local name/mode update", e);
                }
                Toast.makeText(this, R.string.atte_edit_saved, Toast.LENGTH_SHORT).show();
                SyncReceiver.requestSync(this);
            } else {
                String err = object != null ? object.optString("error", status) : status;
                Toast.makeText(this, getString(R.string.atte_edit_failed, err), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String[] splitName(String full) {
        String raw = full == null ? "" : full.trim().replaceAll("\\s+", " ");
        int space = raw.indexOf(' ');
        if (space < 0) {
            return new String[]{raw, "."};
        }
        return new String[]{raw.substring(0, space), raw.substring(space + 1).trim()};
    }

    private boolean studentExistsLocally(int studentId) {
        try {
            Cursor c = db.rawQuery("select id from tb_student where id=? limit 1",
                    new String[]{String.valueOf(studentId)});
            boolean found = c.moveToNext();
            c.close();
            return found;
        } catch (Exception e) {
            Log.e(TAG, "local student check", e);
            return false;
        }
    }

    private void fetchDeviceAttendance(int classId) {
        if (!MainActivity.isNetworkAvailable(this)) {
            if (!attendanceSheetReady(classId) && txtstatus != null) {
                txtstatus.setText("Connect once to save today's attendance on this phone. After that, checking and recording work offline.");
            }
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("class", classId);
        new HttpRequest(this).timeouts(8, 25).post(server + "class_device_attendance", map, (object, code, status) -> runOnUiThread(() -> {
            if (viewingPast() || object == null || object.optInt("locked") != 1) {
                return;
            }
            deviceLocked = true;
            if (fab != null) fab.hide();
            HashSet<Integer> present = new HashSet<>();
            JSONArray ids = object.optJSONArray("present");
            if (ids != null) {
                for (int i = 0; i < ids.length(); i++) {
                    present.add(ids.optInt(i));
                }
            }
            HashMap<Integer, JSONObject> manual = new HashMap<>();
            JSONArray manualRows = object.optJSONArray("manual");
            if (manualRows != null) {
                for (int i = 0; i < manualRows.length(); i++) {
                    JSONObject mark = manualRows.optJSONObject(i);
                    if (mark == null) continue;
                    int studentId = mark.optInt("student_id");
                    if (studentId > 0) manual.put(studentId, mark);
                }
            }
            absentIds.clear();
            for (StudentRow row : students) {
                if (row.inactive) continue;
                if (present.contains(row.id)) {
                    row.devicePresent = true;
                    row.manualPresent = false;
                    applyRowDevicePresentLook(row);
                } else if (manual.containsKey(row.id)) {
                    JSONObject mark = manual.get(row.id);
                    row.manualPresent = true;
                    row.devicePresent = false;
                    row.manualReason = mark == null ? "" : mark.optString("reason");
                    row.approvedName = mark == null ? "" : mark.optString("approved_name");
                    applyRowManualPresentLook(row);
                } else {
                    absentIds.add(row.id);
                    row.devicePresent = false;
                    row.manualPresent = false;
                    applyRowAbsentLook(row);
                }
            }
            cacheDeviceSheet(present, manual);
            refreshCount();
        }));
    }

    private void fetchAttendanceDay(int classId) {
        final String requested = selectedDate;
        if (!viewingPast()) {
            return;
        }
        if (!MainActivity.isNetworkAvailable(this)) {
            if (txtstatus != null) {
                txtstatus.setText(R.string.atte_past_offline);
            }
            changeTitle(getString(R.string.atte_past_offline));
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("class", classId);
        map.put("year", preferences.getInt("soma_academic", 0));
        map.put("date", requested);
        new HttpRequest(this).timeouts(12, 25).post(server + "class_attendance_day", map, (object, code, status) -> runOnUiThread(() -> {
            if (isFinishing() || !requested.equals(selectedDate)) {
                return;
            }
            if (object == null || object.has("error") || object.optJSONArray("present") == null) {
                if (txtstatus != null) {
                    txtstatus.setText(object == null ? getString(R.string.atte_past_offline) : object.optString("error", getString(R.string.atte_past_offline)));
                }
                return;
            }
            HashSet<Integer> present = new HashSet<>();
            JSONArray ids = object.optJSONArray("present");
            for (int i = 0; i < ids.length(); i++) {
                present.add(ids.optInt(i));
            }
            HashMap<Integer, JSONObject> manual = new HashMap<>();
            JSONArray manualRows = object.optJSONArray("manual");
            if (manualRows != null) {
                for (int i = 0; i < manualRows.length(); i++) {
                    JSONObject mark = manualRows.optJSONObject(i);
                    if (mark == null) continue;
                    int studentId = mark.optInt("student_id");
                    if (studentId > 0) manual.put(studentId, mark);
                }
            }
            absentIds.clear();
            for (StudentRow row : students) {
                if (row.inactive) continue;
                if (present.contains(row.id)) {
                    row.devicePresent = true;
                    row.manualPresent = false;
                    applyRowDevicePresentLook(row);
                } else if (manual.containsKey(row.id)) {
                    JSONObject mark = manual.get(row.id);
                    row.manualPresent = true;
                    row.devicePresent = false;
                    row.manualReason = mark == null ? "" : mark.optString("reason");
                    row.approvedName = mark == null ? "" : mark.optString("approved_name");
                    applyRowManualPresentLook(row);
                } else {
                    absentIds.add(row.id);
                    row.devicePresent = false;
                    row.manualPresent = false;
                    applyRowAbsentLook(row);
                }
            }
            cacheDeviceSheet(present, manual);
            if (txtstatus != null) {
                txtstatus.setText("");
            }
            refreshCount();
        }));
    }

    private void onAbsentClick(StudentRow row, TextView txtname, TextView txtRowStatus, ImageView imgabsent) {
        if (row.inactive) return;
        if (viewingPast()) {
            Toast.makeText(this, R.string.atte_past_locked, Toast.LENGTH_SHORT).show();
            return;
        }
        if (deviceLocked) {
            if (row.devicePresent) {
                Toast.makeText(this, R.string.atte_manual_from_device, Toast.LENGTH_SHORT).show();
                return;
            }
            if (row.manualPresent) {
                Toast.makeText(this, getString(R.string.atte_manual_already, nullToEmpty(row.manualReason)), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.atte_manual_tap_present, Toast.LENGTH_LONG).show();
            return;
        }
        toggleAbsent(row, txtname, txtRowStatus, imgabsent);
    }

    private void onPresentClick(StudentRow row, TextView txtname, TextView txtRowStatus, ImageView imgabsent) {
        if (row.inactive) return;
        if (viewingPast()) {
            Toast.makeText(this, R.string.atte_past_locked, Toast.LENGTH_SHORT).show();
            return;
        }
        if (deviceLocked) {
            if (row.devicePresent) {
                Toast.makeText(this, R.string.atte_manual_from_device, Toast.LENGTH_SHORT).show();
                return;
            }
            if (row.manualPresent) {
                Toast.makeText(this, getString(R.string.atte_manual_already, nullToEmpty(row.manualReason)), Toast.LENGTH_LONG).show();
                return;
            }
            promptManualPresent(row);
            return;
        }
        toggleAbsent(row, txtname, txtRowStatus, imgabsent);
    }

    private void promptManualPresent(StudentRow row) {
        if (!absentIds.contains(row.id) || row.devicePresent || row.manualPresent) {
            return;
        }
        String approver = preferences.getString("soma_name", "");
        final EditText reason = new EditText(this);
        reason.setHint(R.string.atte_manual_hint);
        reason.setMinLines(3);
        reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        reason.setPadding(pad, pad, pad, pad);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.atte_manual_title)
                .setMessage(getString(R.string.atte_manual_msg, row.name, approver))
                .setView(reason)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = reason.getText().toString().trim();
            if (text.length() < 10) {
                Toast.makeText(this, R.string.atte_manual_need_reason, Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            approveManualPresent(row, text);
        }));
        dialog.show();
    }

    private void approveManualPresent(StudentRow row, String reason) {
        String approver = preferences.getString("soma_name", "");
        int staffId = preferences.getInt("soma_id", 0);
        queueManualPresent(row, reason, approver, staffId);
        markManualPresent(row, reason, approver);
        if (!MainActivity.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.atte_manual_queued, Toast.LENGTH_LONG).show();
            SyncReceiver.requestSync(this);
            return;
        }
        SweetAlertDialog wait = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
        wait.setTitleText(getString(R.string.please_wait));
        wait.setCancelable(false);
        wait.show();
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("class", getIntent().getIntExtra("classId", 0));
        map.put("student_id", row.id);
        map.put("term", preferences.getInt("soma_term", 0));
        map.put("approved_by", preferences.getInt("soma_id", 0));
        map.put("reason", reason);
        new HttpRequest(this).post(server + "approve_absent_present", map, (object, code, status) -> runOnUiThread(() -> {
            if (!isFinishing()) {
                try {
                    wait.dismissWithAnimation();
                } catch (Exception ignored) {
                }
            }
            if (object != null && object.has("success") && !object.has("error")) {
                clearManualPending(row.id);
                row.manualReason = object.optString("reason", reason);
                row.approvedName = object.optString("approved_name", approver);
                rememberManualCache(row, row.manualReason, row.approvedName, staffId);
                applyRowManualPresentLook(row);
                Toast.makeText(this, getString(R.string.atte_manual_saved, row.name), Toast.LENGTH_LONG).show();
                return;
            }
            String err = object != null ? object.optString("error", "") : "";
            if (err.toLowerCase(Locale.US).contains("already")) {
                clearManualPending(row.id);
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, getString(R.string.atte_manual_queued), Toast.LENGTH_LONG).show();
            SyncReceiver.requestSync(this);
        }));
    }

    private void markManualPresent(StudentRow row, String reason, String approver) {
        row.manualPresent = true;
        row.devicePresent = false;
        row.manualReason = reason;
        row.approvedName = approver;
        absentIds.remove(row.id);
        applyRowManualPresentLook(row);
        refreshCount();
    }

    private void queueManualPresent(StudentRow row, String reason, String approver, int staffId) {
        int classId = getIntent().getIntExtra("classId", 0);
        String today = todayDate();
        try {
            db.execSQL("delete from tb_manual_present_pending where student_id=? and class_id=? and datee=? and sync=1",
                    new Object[]{row.id, classId, today});
            db.execSQL("insert into tb_manual_present_pending (id,student_id,class_id,term,school_id,approved_by,approved_name,reason,datee,sync,created_at) values (?,?,?,?,?,?,?,?,?,?,?)",
                    new Object[]{
                            UUID.randomUUID().toString(),
                            row.id,
                            classId,
                            preferences.getInt("soma_term", 0),
                            preferences.getInt(MainActivity.SK_ID, 0),
                            staffId,
                            approver,
                            reason,
                            today,
                            1,
                            System.currentTimeMillis() / 1000L
                    });
            rememberManualCache(row, reason, approver, staffId);
        } catch (Exception e) {
            Log.e(TAG, "queue manual present", e);
        }
    }

    private void rememberManualCache(StudentRow row, String reason, String approver, int staffId) {
        int classId = getIntent().getIntExtra("classId", 0);
        try {
            db.execSQL("insert or replace into tb_daily_attendance (student_id,datee,class_id,term,source,manual_reason,approved_by,approved_name) values (?,?,?,?,?,?,?,?)",
                    new Object[]{
                            row.id,
                            todayDate(),
                            classId,
                            preferences.getInt("soma_term", 0),
                            "manual",
                            reason,
                            staffId,
                            approver
                    });
        } catch (Exception e) {
            Log.e(TAG, "cache manual present", e);
        }
    }

    private void clearManualPending(int studentId) {
        try {
            db.execSQL("update tb_manual_present_pending set sync=0 where student_id=? and datee=? and sync=1",
                    new Object[]{studentId, todayDate()});
        } catch (Exception e) {
            Log.e(TAG, "clear manual pending", e);
        }
    }

    private void cacheDeviceSheet(Set<Integer> present, Map<Integer, JSONObject> manual) {
        int classId = getIntent().getIntExtra("classId", 0);
        String today = viewingPast() ? selectedDate : todayDate();
        try {
            db.execSQL("delete from tb_daily_attendance where datee=? and class_id=? and student_id not in (select student_id from tb_manual_present_pending where sync=1 and datee=? and class_id=?)",
                    new Object[]{today, classId, today, classId});
            for (Integer id : present) {
                if (id == null || manual.containsKey(id)) continue;
                db.execSQL("insert or replace into tb_daily_attendance (student_id,datee,class_id,term,source,manual_reason,approved_by,approved_name) values (?,?,?,?,?,?,?,?)",
                        new Object[]{id, today, classId, preferences.getInt("soma_term", 0), "device", "", 0, ""});
            }
            for (Map.Entry<Integer, JSONObject> entry : manual.entrySet()) {
                JSONObject mark = entry.getValue();
                db.execSQL("insert or replace into tb_daily_attendance (student_id,datee,class_id,term,source,manual_reason,approved_by,approved_name) values (?,?,?,?,?,?,?,?)",
                        new Object[]{
                                entry.getKey(),
                                today,
                                classId,
                                preferences.getInt("soma_term", 0),
                                "manual",
                                mark == null ? "" : mark.optString("reason"),
                                mark == null ? 0 : mark.optInt("approved_by"),
                                mark == null ? "" : mark.optString("approved_name")
                        });
            }
            if (!viewingPast()) {
                preferences.edit()
                        .putString("attendance_synced_day", today)
                        .putString("attendance_saved_" + classId, today)
                        .apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "cacheDeviceSheet", e);
        }
    }

    private String resolveClassTitle() {
        String title = getIntent().getStringExtra("classTitle");
        if (title != null) {
            title = title.trim();
            if (title.length() > 0 && !title.toLowerCase(Locale.US).contains("select")) {
                return title;
            }
        }
        int classId = getIntent().getIntExtra("classId", 0);
        if (classId > 0 && db != null) {
            try {
                Cursor c = db.rawQuery("select title from tb_class where id=? limit 1", new String[]{String.valueOf(classId)});
                if (c.moveToNext()) {
                    String local = c.getString(0);
                    c.close();
                    if (local != null && local.trim().length() > 0) {
                        return local.trim();
                    }
                } else {
                    c.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "class title", e);
            }
        }
        return "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void toggleAbsent(StudentRow row, TextView txtname, TextView txtRowStatus, ImageView imgabsent) {
        if (row.inactive || deviceLocked) return;
        if (absentIds.contains(row.id)) {
            absentIds.remove(row.id);
            applyRowPresentLook(row);
            Toast.makeText(this, row.name + " " + getString(R.string.atte_is_present), Toast.LENGTH_SHORT).show();
        } else {
            absentIds.add(row.id);
            applyRowAbsentLook(row);
            Toast.makeText(this, row.name + " " + getString(R.string.atte_is_absent), Toast.LENGTH_SHORT).show();
        }
        refreshCount();
    }

    private void confirmDelete(StudentRow row) {
        if (row.inactive) return;
        if (!MainActivity.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.atte_delete_need_online, Toast.LENGTH_LONG).show();
            return;
        }
        if (!studentExistsLocally(row.id)) {
            Toast.makeText(this, R.string.atte_delete_missing_local, Toast.LENGTH_LONG).show();
            return;
        }
        SweetAlertDialog sa = new SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE);
        sa.setTitle(getString(R.string.atte_delete_confirm_title));
        sa.setContentText(getString(R.string.atte_delete_confirm_msg, row.name))
                .setConfirmButton(getString(R.string.yes), dialog -> {
                    dialog.dismiss();
                    deleteStudentLive(row);
                })
                .setCancelButton(getString(R.string.no), SweetAlertDialog::dismiss)
                .setCancelable(true);
        sa.show();
    }

    private void deleteStudentLive(StudentRow row) {
        if (!MainActivity.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.atte_delete_need_online, Toast.LENGTH_LONG).show();
            return;
        }
        if (!studentExistsLocally(row.id)) {
            Toast.makeText(this, R.string.atte_delete_missing_local, Toast.LENGTH_LONG).show();
            return;
        }
        SweetAlertDialog wait = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
        wait.setTitleText(getString(R.string.please_wait));
        wait.setCancelable(false);
        Map map = new HashMap();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("student_id", row.id);
        map.put("class_id", getIntent().getIntExtra("classId", 0));
        map.put("record_id", row.recordId);
        new HttpRequest(this).progress(wait).post(server + "delete_student", map, (object, status_code, status) -> {
            if (object != null && object.has("success")) {
                removeStudentLocally(row);
            } else {
                String err = object != null ? object.optString("error", status) : status;
                Toast.makeText(this, getString(R.string.atte_delete_failed, err), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void removeStudentLocally(StudentRow row) {
        try {
            db.execSQL("delete from tb_student where id=?", new Object[]{row.id});
            db.execSQL("delete from tb_student_dismiss_pending where student_id=?", new Object[]{row.id});
            db.execSQL("delete from tb_daily_attendance where student_id=?", new Object[]{row.id});
        } catch (Exception e) {
            Log.e(TAG, "local delete after server success", e);
        }
        row.inactive = true;
        absentIds.remove(row.id);
        if (row.view != null) {
            lnlstudents.removeView(row.view);
        }
        students.remove(row);
        stTotal = students.size();
        refreshCount();
        Toast.makeText(this, getString(R.string.atte_delete_done, row.name), Toast.LENGTH_SHORT).show();
        if (students.isEmpty()) fab.hide();
        SyncReceiver.requestSync(this);
    }

    private String todayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void cacheTodayAttendance(JSONArray presentIds) {
        int classId = getIntent().getIntExtra("classId", 0);
        int term = preferences.getInt("soma_term", 0);
        String today = todayDate();
        try {
            db.execSQL("delete from tb_daily_attendance where datee=? and class_id=?",
                    new Object[]{today, classId});
            for (int i = 0; i < presentIds.length(); i++) {
                db.execSQL(
                        "insert or replace into tb_daily_attendance (student_id,datee,class_id,term,source) values (?,?,?,?,?)",
                        new Object[]{presentIds.getInt(i), today, classId, term, "record"});
            }
            preferences.edit().putString("attendance_saved_" + classId, today).apply();
        } catch (Exception e) {
            Log.e(TAG, "cacheTodayAttendance", e);
        }
    }

    private boolean attendanceSheetReady(int classId) {
        String today = todayDate();
        return today.equals(preferences.getString("attendance_synced_day", ""))
                || today.equals(preferences.getString("attendance_saved_" + classId, ""));
    }

    /** Show the last synced register. A complete snapshot also marks everyone else absent. */
    private void applyCachedAttendance(int classId) {
        String today = viewingPast() ? selectedDate : todayDate();
        boolean savedThisClass = !viewingPast() && today.equals(preferences.getString("attendance_saved_" + classId, ""));
        boolean complete = viewingPast() ? false : (deviceLocked ? attendanceSheetReady(classId) : savedThisClass);
        HashMap<Integer, String> source = new HashMap<>();
        HashMap<Integer, String> reason = new HashMap<>();
        HashMap<Integer, String> approver = new HashMap<>();
        try {
            Cursor c = db.rawQuery(
                    "SELECT student_id, IFNULL(source,'record'), IFNULL(manual_reason,''), IFNULL(approved_name,'') FROM tb_daily_attendance WHERE datee=? AND class_id=?",
                    new String[]{today, String.valueOf(classId)});
            while (c.moveToNext()) {
                String kind = c.getString(1);
                if (!deviceLocked && "device".equals(kind)) {
                    continue;
                }
                source.put(c.getInt(0), kind);
                reason.put(c.getInt(0), c.getString(2));
                approver.put(c.getInt(0), c.getString(3));
            }
            c.close();
            Cursor pending = db.rawQuery(
                    "SELECT student_id, IFNULL(reason,''), IFNULL(approved_name,'') FROM tb_manual_present_pending WHERE sync=1 AND datee=? AND class_id=?",
                    new String[]{today, String.valueOf(classId)});
            while (pending.moveToNext()) {
                int id = pending.getInt(0);
                source.put(id, "manual");
                reason.put(id, pending.getString(1));
                approver.put(id, pending.getString(2));
            }
            pending.close();
        } catch (Exception e) {
            Log.e(TAG, "applyCachedAttendance", e);
            return;
        }
        if (viewingPast() && source.isEmpty()) {
            return;
        }
        if (!viewingPast() && !deviceLocked && classHasSavedPresent(source)) {
            complete = true;
        }
        if (viewingPast() && !source.isEmpty()) {
            complete = true;
        }
        if (!deviceLocked && !complete && !classHasSavedPresent(source)) {
            return;
        }
        if (source.isEmpty() && !complete) {
            return;
        }
        for (StudentRow row : students) {
            if (row.inactive) continue;
            String kind = source.get(row.id);
            if (kind == null) {
                if (complete && !absentIds.contains(row.id)) {
                    absentIds.add(row.id);
                    row.devicePresent = false;
                    row.manualPresent = false;
                    applyRowAbsentLook(row);
                }
                continue;
            }
            absentIds.remove(row.id);
            if ("manual".equals(kind)) {
                row.manualPresent = true;
                row.devicePresent = false;
                row.manualReason = reason.get(row.id);
                row.approvedName = approver.get(row.id);
                applyRowManualPresentLook(row);
            } else if (deviceLocked && "device".equals(kind)) {
                row.devicePresent = true;
                row.manualPresent = false;
                applyRowDevicePresentLook(row);
            } else {
                row.devicePresent = false;
                row.manualPresent = false;
                applyRowPresentLook(row);
            }
        }
    }

    private boolean classHasSavedPresent(HashMap<Integer, String> source) {
        for (StudentRow row : students) {
            String kind = source.get(row.id);
            if (kind != null && !"device".equals(kind)) {
                return true;
            }
        }
        return false;
    }

    private void loadPermissionCover(int classId) {
        permissionCover.clear();
        try {
            Cursor c = db.rawQuery(
                    "SELECT student_id, IFNULL(reason,'') FROM tb_permission_cover WHERE class_id=? AND day=?",
                    new String[]{String.valueOf(classId), viewingPast() ? selectedDate : todayDate()});
            while (c.moveToNext()) {
                permissionCover.put(c.getInt(0), c.getString(1));
            }
            c.close();
        } catch (Exception e) {
            Log.e(TAG, "loadPermissionCover", e);
        }
    }

    private void fetchPermissionCover(int classId) {
        Map<String, String> map = new HashMap<>();
        map.put("school_id", String.valueOf(preferences.getInt(MainActivity.SK_ID, 0)));
        map.put("class_id", String.valueOf(classId));
        map.put("year", String.valueOf(preferences.getInt("soma_academic", 0)));
        final String requested = selectedDate == null || selectedDate.length() != 10 ? todayDate() : selectedDate;
        map.put("date", requested);
        new HttpRequest(this).timeouts(12, 25).post(server + "class_absence_permissions", map, (object, status_code, status) -> {
            if (isFinishing() || object == null || object.optJSONArray("covers") == null) {
                return;
            }
            if (!requested.equals(selectedDate == null || selectedDate.length() != 10 ? todayDate() : selectedDate)) {
                return;
            }
            JSONArray covers = object.optJSONArray("covers");
            permissionCover.clear();
            String day = requested;
            try {
                db.execSQL("DELETE FROM tb_permission_cover WHERE class_id=? AND day=?", new Object[]{classId, day});
                for (int i = 0; i < covers.length(); i++) {
                    JSONObject row = covers.optJSONObject(i);
                    if (row == null) continue;
                    int studentId = row.optInt("student_id");
                    if (studentId < 1) continue;
                    String reason = row.optString("reason");
                    permissionCover.put(studentId, reason);
                    db.execSQL(
                            "INSERT OR REPLACE INTO tb_permission_cover (student_id, class_id, reason, day) VALUES (?,?,?,?)",
                            new Object[]{studentId, classId, reason, day});
                }
            } catch (Exception e) {
                Log.e(TAG, "savePermissionCover", e);
            }
            repaintAbsents();
        });
    }

    private void repaintAbsents() {
        for (StudentRow row : students) {
            if (row != null && absentIds.contains(row.id)) {
                applyRowAbsentLook(row);
            }
        }
    }

    private String absentLine(String detail) {
        return getString(R.string.atte_absent_first) + "\n" + detail;
    }

    private void paintStatusChip(TextView status, String label, int background, int foreground) {
        status.setSingleLine(false);
        status.setMaxLines(3);
        status.setText(label);
        status.setTextColor(foreground);
        status.setBackgroundColor(background);
        int padH = Math.round(8 * getResources().getDisplayMetrics().density);
        int padV = Math.round(2 * getResources().getDisplayMetrics().density);
        status.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = padV;
        status.setLayoutParams(lp);
    }

    private void clearStatusChip(TextView status) {
        if (status == null) return;
        status.setSingleLine(true);
        status.setMaxLines(1);
        status.setBackgroundColor(Color.TRANSPARENT);
        status.setPadding(0, 0, 0, 0);
        status.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void applyRowAbsentLook(StudentRow row) {
        if (row.view == null) return;
        if (permissionCover.containsKey(row.id)) {
            applyRowPermittedLook(row);
            return;
        }
        TextView txtname = row.view.findViewById(R.id.txtname);
        TextView txtRowStatus = row.view.findViewById(R.id.txtrowstatus);
        ImageView imgabsent = row.view.findViewById(R.id.imgabsent);
        ImageView imgpresent = row.view.findViewById(R.id.imgpresent);
        txtRowStatus.setVisibility(View.VISIBLE);
        paintStatusChip(txtRowStatus, absentLine(getString(R.string.atte_absent_label)), Color.parseColor("#DC2626"), Color.WHITE);
        txtname.setTextColor(Color.parseColor("#B91C1C"));
        row.view.setBackgroundColor(Color.parseColor("#FEE2E2"));
        if (imgabsent != null) {
            imgabsent.setVisibility(View.VISIBLE);
            imgabsent.setAlpha(1f);
            imgabsent.clearColorFilter();
        }
        if (imgpresent != null) {
            imgpresent.setVisibility(deviceLocked ? View.VISIBLE : View.GONE);
            imgpresent.setAlpha(1f);
        }
    }

    private void applyRowPermittedLook(StudentRow row) {
        TextView txtname = row.view.findViewById(R.id.txtname);
        TextView txtRowStatus = row.view.findViewById(R.id.txtrowstatus);
        ImageView imgabsent = row.view.findViewById(R.id.imgabsent);
        ImageView imgpresent = row.view.findViewById(R.id.imgpresent);
        txtRowStatus.setVisibility(View.VISIBLE);
        String reason = permissionCover.get(row.id);
        if (reason == null || reason.trim().length() == 0) {
            reason = getString(R.string.atte_absent_reason);
        }
        paintStatusChip(txtRowStatus, absentLine(reason.trim()), Color.parseColor("#F59E0B"), Color.parseColor("#78350F"));
        txtname.setTextColor(Color.parseColor("#B45309"));
        row.view.setBackgroundColor(Color.parseColor("#FEF3C7"));
        if (imgabsent != null) {
            imgabsent.setVisibility(View.VISIBLE);
            imgabsent.setAlpha(1f);
            imgabsent.setColorFilter(Color.parseColor("#B45309"));
        }
        if (imgpresent != null) {
            imgpresent.setVisibility(deviceLocked ? View.VISIBLE : View.GONE);
            imgpresent.setAlpha(1f);
        }
    }

    private void applyRowManualPresentLook(StudentRow row) {
        if (row.view == null) return;
        TextView txtname = row.view.findViewById(R.id.txtname);
        TextView txtRowStatus = row.view.findViewById(R.id.txtrowstatus);
        ImageView imgabsent = row.view.findViewById(R.id.imgabsent);
        ImageView imgpresent = row.view.findViewById(R.id.imgpresent);
        txtRowStatus.setVisibility(View.VISIBLE);
        clearStatusChip(txtRowStatus);
        String reason = nullToEmpty(row.manualReason);
        txtRowStatus.setText(reason.length() == 0 ? getString(R.string.atte_present_label) : reason);
        txtRowStatus.setTextColor(Color.parseColor("#166534"));
        txtname.setTextColor(Color.parseColor("#166534"));
        row.view.setBackgroundColor(Color.parseColor("#DCFCE7"));
        if (imgabsent != null) imgabsent.setVisibility(View.GONE);
        if (imgpresent != null) {
            imgpresent.setVisibility(View.VISIBLE);
            imgpresent.setAlpha(1f);
        }
    }

    private void applyRowDevicePresentLook(StudentRow row) {
        if (row.view == null) return;
        TextView txtname = row.view.findViewById(R.id.txtname);
        TextView txtRowStatus = row.view.findViewById(R.id.txtrowstatus);
        ImageView imgabsent = row.view.findViewById(R.id.imgabsent);
        ImageView imgpresent = row.view.findViewById(R.id.imgpresent);
        txtRowStatus.setVisibility(View.VISIBLE);
        clearStatusChip(txtRowStatus);
        txtRowStatus.setText(R.string.atte_present_label);
        txtRowStatus.setTextColor(Color.parseColor("#166534"));
        txtname.setTextColor(Color.parseColor("#166534"));
        row.view.setBackgroundColor(Color.parseColor("#DCFCE7"));
        if (imgabsent != null) imgabsent.setVisibility(View.GONE);
        if (imgpresent != null) {
            imgpresent.setVisibility(View.VISIBLE);
            imgpresent.setAlpha(1f);
        }
    }

    private void applyRowPresentLook(StudentRow row) {
        if (row.view == null) return;
        TextView txtname = row.view.findViewById(R.id.txtname);
        TextView txtRowStatus = row.view.findViewById(R.id.txtrowstatus);
        ImageView imgabsent = row.view.findViewById(R.id.imgabsent);
        ImageView imgpresent = row.view.findViewById(R.id.imgpresent);
        txtRowStatus.setVisibility(View.GONE);
        clearStatusChip(txtRowStatus);
        txtname.setTextColor(Color.BLACK);
        row.view.setBackgroundColor(Color.TRANSPARENT);
        if (imgabsent != null) {
            imgabsent.setVisibility(View.VISIBLE);
            imgabsent.setAlpha(1f);
            imgabsent.clearColorFilter();
        }
        if (imgpresent != null) {
            imgpresent.setVisibility(View.VISIBLE);
            imgpresent.setAlpha(1f);
        }
    }

    private void refreshCount() {
        changeTitle(getString(R.string.atte_absent_count, absentIds.size(), stTotal));
    }

    private void changeTitle(String title) {
        txtcount.setText(title);
    }

    private void registerSyncReloadReceiver() {
        if (syncReloadReceiver != null) return;
        syncReloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !SyncStatusHelper.ACTION_SUCCESS.equals(intent.getAction())) {
                    return;
                }
                if (saving) {
                    return;
                }
                if (intent.getBooleanExtra("students", false)) {
                    rosterFetched = false;
                    if (MainActivity.isNetworkAvailable(ClassAttendanceActivity.this)) {
                        fetchLiveClassRoster();
                    } else if (awaitingInitialSync || absentIds.isEmpty()) {
                        loadStudent(true);
                    }
                    return;
                }
                if (awaitingInitialSync || absentIds.isEmpty()) {
                    awaitingInitialSync = false;
                    loadStudent(true);
                }
            }
        };
        IntentFilter filter = new IntentFilter(SyncStatusHelper.ACTION_SUCCESS);
        ContextCompat.registerReceiver(this, syncReloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterSyncReloadReceiver() {
        if (syncReloadReceiver == null) return;
        try {
            unregisterReceiver(syncReloadReceiver);
        } catch (Exception ignored) {
        }
        syncReloadReceiver = null;
    }
}
