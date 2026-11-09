package com.qonics.somanet.teacher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.model.StudentModel;
import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class StudentListActivity extends AppCompatActivity {
    FloatingActionButton fab;
    ImageView imgerror;
    RelativeLayout rlvloading;
    LinearLayout lnlstudents, lnlbuttons;
    ScrollView scrollView;
    ProgressBar prb;
    Button btnEdit, btnSaveMarks;
    SearchView searchStudent;
    TextView txtstatus, txtcount, txtretry, txtTeacher, txtClass, txtCourse, txtMaxTotal, txtBack;
    String server = MainActivity.SERVER;
    String catTypeId, maximum, examinationDate, courseName;
    int subjectId, classId, teacher, examinationType, period;
    String teacherName;
    boolean nurseryEntry;
    boolean entryLocked;
    boolean serverCheckDone;
    boolean serverEditAllowed;
    boolean marksUploadRunning;
    boolean lastOnline;
    final Map<Integer, Double> savedMarks = new HashMap<>();
    final Map<Integer, Double> unsavedNew = new HashMap<>();
    org.json.JSONArray nurseryBands = new org.json.JSONArray();
    TextView txtLockBanner;
    SQLiteDatabase db;
    ArrayList<StudentModel> studentList = new ArrayList<>();
    ListView lvStudent;
    StudentListAdapter adapater;

    DatabaseHandler db_helper;
    SharedPreferences preferences;
    int stTotal = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_teacher_student_list);
        db = this.openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        classId = getIntent().getIntExtra("classId", 0);
        subjectId = getIntent().getIntExtra("courseId", 0);
        examinationType = getIntent().getIntExtra("markTypeId", 0);
        period = getIntent().getIntExtra("periodId", 0);
        catTypeId = getIntent().getStringExtra("catTypeTitle");
        maximum = getIntent().getStringExtra("maximum");
        examinationDate = getIntent().getStringExtra("date");
        teacher = preferences.getInt("soma_id", 0);
        teacherName = preferences.getString("soma_name", "");
        courseName = getIntent().getStringExtra("courseTitle");
        nurseryEntry = getIntent().getBooleanExtra("nursery", false);
        if (getIntent().getBooleanExtra("marksLocked", false) && !onlineNow()) {
            entryLocked = true;
        }
        try {
            nurseryBands = new JSONArray(preferences.getString("nursery_grade_bands", "[]"));
        } catch (Exception ignored) {
            nurseryBands = new JSONArray();
        }
        adapater = new StudentListAdapter();

        populateView();
        applyBottomInset();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean now = onlineNow();
        if (lnlstudents != null && lnlstudents.getChildCount() > 0) {
            stashUnsavedNew();
        }
        if (now) {
            serverCheckDone = false;
            serverEditAllowed = false;
        } else {
            serverCheckDone = false;
            serverEditAllowed = false;
            if (getIntent().getBooleanExtra("marksLocked", false)) {
                entryLocked = true;
            }
        }
        lastOnline = now;
        loadStudent();
        if (now) {
            uploadPendingMarksThenRefresh();
        } else {
            applySaveButtonLabel();
        }
    }

    private void applyBottomInset() {
        View root = findViewById(R.id.rootMarksList);
        if (root == null || lnlbuttons == null) return;
        final int baseBottom = getResources().getDimensionPixelSize(R.dimen.bottom_safe);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            lnlbuttons.setPadding(
                    lnlbuttons.getPaddingLeft(),
                    lnlbuttons.getPaddingTop(),
                    lnlbuttons.getPaddingRight(),
                    Math.max(baseBottom, bars.bottom + 12)
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void populateView() {
        db_helper = new DatabaseHandler(this);
        txtretry = findViewById(R.id.txtretry);
        fab = findViewById(R.id.fab);
        scrollView = findViewById(R.id.scrollView);
        searchStudent = findViewById(R.id.srchstudent);
        lnlbuttons = findViewById(R.id.lnlbuttons);
        lnlstudents = findViewById(R.id.lnlstudents);
        txtstatus = findViewById(R.id.txtstatus);
        txtcount = findViewById(R.id.txtcount);
        txtClass = findViewById(R.id.txtclass);
        txtCourse = findViewById(R.id.txtcourse);
        txtMaxTotal = findViewById(R.id.txtmaxtotal);
        txtTeacher = findViewById(R.id.txtteacher);
        txtBack = findViewById(R.id.txtBack);
        btnEdit = findViewById(R.id.btnedit);
        btnSaveMarks = findViewById(R.id.btnsave);
        prb = findViewById(R.id.prb);
        rlvloading = findViewById(R.id.rlvloading);
        imgerror = findViewById(R.id.imgerror);
        lvStudent = findViewById(R.id.lvstudent);
        if (lvStudent != null) {
            lvStudent.setAdapter(adapater);
            lvStudent.setVisibility(View.GONE);
        }
        if (fab != null) {
            fab.hide();
            fab.setVisibility(View.GONE);
        }

        txtCourse.setText("Course: " + nullToEmpty(getIntent().getStringExtra("courseTitle")));
        txtClass.setText("Class: " + nullToEmpty(getIntent().getStringExtra("classTitle")));
        String periodTitle = nullToEmpty(getIntent().getStringExtra("periodTitle"));
        String maxLine = "Max: " + maximum;
        if (periodTitle.length() > 0 && !periodTitle.toLowerCase(Locale.US).contains("select")) {
            maxLine = maxLine + "  ·  " + periodTitle;
        }
        txtMaxTotal.setText(maxLine);
        txtLockBanner = findViewById(R.id.txtlockbanner);
        String lecturer = getIntent().getStringExtra("lecturerName");
        if (preferences.getBoolean("marks_full_access", false) && lecturer != null && lecturer.trim().length() > 0) {
            txtTeacher.setText("Teacher: " + lecturer.trim());
        } else {
            txtTeacher.setText("Teacher: " + teacherName);
        }
        txtretry.setVisibility(View.INVISIBLE);
        txtretry.setOnClickListener(view -> loadStudent());
        if (txtBack != null) {
            txtBack.setOnClickListener(v -> onBackPressed());
        }

        // Always show sticky footer
        lnlbuttons.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.VISIBLE);

        searchStudent.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.trim().length() == 0) {
                    loadStudent();
                    return false;
                }
                if (newText.trim().length() < 2) {
                    return false;
                }
                loadStudent(newText.trim());
                return true;
            }
        });

        // Review = jump to first empty mark field
        btnEdit.setOnClickListener(v -> {
            commitVisibleMarks();
            int filled = 0;
            for (StudentModel st : studentList) {
                if (st.getMarks() != null) filled++;
            }
            View firstEmpty = null;
            for (int i = 0; i < lnlstudents.getChildCount(); i++) {
                View row = lnlstudents.getChildAt(i);
                EditText et = row.findViewById(R.id.studentMarksValue);
                if (et != null && et.getText().toString().trim().isEmpty()) {
                    firstEmpty = et;
                    break;
                }
            }
            if (firstEmpty != null) {
                firstEmpty.requestFocus();
                scrollView.smoothScrollTo(0, firstEmpty.getTop());
                Toast.makeText(this,
                        String.format(Locale.US, "Filled %d / %d — next empty highlighted", filled, stTotal),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        String.format(Locale.US, "All %d students have marks. Tap Save.", stTotal),
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnSaveMarks.setOnClickListener(v -> beginSave());
        if (entryLocked) {
            lockEditing("Marks editing is locked for you on this course.");
        } else {
            applySaveButtonLabel();
        }
    }

    private boolean onlineNow() {
        return MainActivity.isNetworkAvailable(this);
    }

    private boolean previouslySaved(int studentId) {
        return savedMarks.containsKey(studentId);
    }

    /** Existing marks change only after the server says this course is open. */
    private boolean fieldEnabled(int studentId) {
        if (entryLocked) return false;
        if (onlineNow()) return serverCheckDone && serverEditAllowed;
        return !previouslySaved(studentId);
    }

    private void applySaveButtonLabel() {
        if (btnSaveMarks == null || entryLocked) return;
        applySheetNotice();
        if (onlineNow()) {
            btnSaveMarks.setEnabled(!marksUploadRunning && serverCheckDone && serverEditAllowed);
            if (marksUploadRunning) {
                btnSaveMarks.setText("Syncing marks...");
            } else if (!serverCheckDone) {
                btnSaveMarks.setText("Checking...");
            } else if (!serverEditAllowed) {
                btnSaveMarks.setText("Not confirmed");
            } else {
                btnSaveMarks.setText("Save to server");
            }
            return;
        }
        boolean anyOpen = false;
        for (StudentModel st : studentList) {
            if (!previouslySaved(st.getId())) {
                anyOpen = true;
                break;
            }
        }
        if (!anyOpen) {
            btnSaveMarks.setEnabled(false);
            btnSaveMarks.setText("Connect to edit");
        } else {
            btnSaveMarks.setEnabled(true);
            btnSaveMarks.setText("Save offline");
        }
    }

    private void applySheetNotice() {
        if (txtLockBanner == null || entryLocked) return;
        if (onlineNow()) {
            if (marksUploadRunning) {
                txtLockBanner.setText("Sending saved marks to the server before editing.");
                txtLockBanner.setVisibility(View.VISIBLE);
            } else if (!serverCheckDone) {
                txtLockBanner.setText("Checking whether this course allows marks editing...");
                txtLockBanner.setVisibility(View.VISIBLE);
            } else if (serverEditAllowed) {
                txtLockBanner.setVisibility(View.GONE);
            }
            return;
        }
        int saved = 0;
        int open = 0;
        for (StudentModel st : studentList) {
            if (previouslySaved(st.getId())) saved++;
            else open++;
        }
        if (saved > 0 && open == 0) {
            txtLockBanner.setText("These marks are already saved. Connect to the internet to change them, and only if this course is open for editing.");
            txtLockBanner.setVisibility(View.VISIBLE);
        } else if (saved > 0) {
            txtLockBanner.setText("Students who already have marks cannot be changed offline. You can enter marks only where none are saved yet.");
            txtLockBanner.setVisibility(View.VISIBLE);
        } else {
            txtLockBanner.setVisibility(View.GONE);
        }
    }

    private void unlockFromServer() {
        entryLocked = false;
        serverCheckDone = true;
        serverEditAllowed = true;
        if (txtLockBanner != null) txtLockBanner.setVisibility(View.GONE);
        if (btnSaveMarks != null) btnSaveMarks.setEnabled(true);
    }

    private void lockEditing(String reason) {
        entryLocked = true;
        if (txtLockBanner != null) {
            txtLockBanner.setText(reason);
            txtLockBanner.setVisibility(View.VISIBLE);
        }
        if (btnSaveMarks != null) {
            btnSaveMarks.setEnabled(false);
            btnSaveMarks.setText("Locked");
        }
        if (lnlstudents != null) {
            for (int i = 0; i < lnlstudents.getChildCount(); i++) {
                EditText et = lnlstudents.getChildAt(i).findViewById(R.id.studentMarksValue);
                if (et != null) et.setEnabled(false);
            }
        }
    }

    private void beginSave() {
        if (entryLocked) {
            Toast.makeText(this, txtLockBanner != null ? txtLockBanner.getText() : "Marks editing is locked", Toast.LENGTH_LONG).show();
            return;
        }
        commitVisibleMarks();
        if (onlineNow()) {
            if (!serverCheckDone || !serverEditAllowed || entryLocked) {
                Toast.makeText(this, "Marks can be changed only after the server confirms this course is open for editing.", Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            for (StudentModel st : studentList) {
                if (previouslySaved(st.getId())) {
                    st.setMarks(savedMarks.get(st.getId()));
                }
            }
        }
        int count = 0;
        for (StudentModel st : studentList) {
            if (st.getMarks() == null) continue;
            if (!onlineNow() && previouslySaved(st.getId())) continue;
            count++;
        }
        if (count == 0) {
            Toast.makeText(this, onlineNow()
                    ? "Enter at least one mark before saving"
                    : "Saved marks can only be changed online. Enter marks only for students who have none yet.", Toast.LENGTH_LONG).show();
            return;
        }
        final boolean live = onlineNow();
        final int saveCount = count;
        SweetAlertDialog sa = new SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE);
        sa.setTitle("Save " + saveCount + " mark(s)?");
        sa.setContentText(live
                ? "Internet is on and this course is open. Marks go straight to the server."
                : "No internet. Only students with no saved mark are stored on this phone, then sent when you are back online.")
                .setCancelButton("Cancel", SweetAlertDialog::dismissWithAnimation)
                .setConfirmButton("Save", sweetAlertDialog -> {
                    sweetAlertDialog.changeAlertType(SweetAlertDialog.PROGRESS_TYPE);
                    sweetAlertDialog.setTitle(live ? "Saving to server..." : "Saving on this phone...");
                    sweetAlertDialog.setContentText("Please wait");
                    sweetAlertDialog.setConfirmButton(null, null);
                    sweetAlertDialog.setCancelButton(null, null);
                    if (live) {
                        postMarksLive(sweetAlertDialog);
                    } else {
                        queueMarksOffline();
                        showQueuedDialog(sweetAlertDialog);
                    }
                })
                .setCancelable(false);
        sa.show();
    }

    private String catCode() {
        if (nurseryEntry) return "";
        return catTypeId == null ? "" : catTypeId;
    }

    private void rememberLocalCache(StudentModel st, String cat) {
        String[] key = new String[]{
                String.valueOf(st.getId()),
                String.valueOf(subjectId),
                String.valueOf(classId),
                String.valueOf(examinationType),
                cat,
                String.valueOf(period)
        };
        db.execSQL("delete from tb_mark_cache where student_id=? and course_id=? and class_id=? and mark_type=? and ifnull(cat_type,'')=? and period=?", key);
        db.execSQL("insert into tb_mark_cache (student_id,course_id,class_id,mark_type,cat_type,period,marks,out_of) values (?,?,?,?,?,?,?,?)",
                new Object[]{st.getId(), subjectId, classId, examinationType, cat, period, st.getMarks(), maximum});
    }

    private void markCatFilled(String cat) {
        if (examinationType != 1 || nurseryEntry || cat == null || cat.length() == 0) return;
        String code = cat.toUpperCase(Locale.US);
        Cursor filled = db.rawQuery(
                "select 1 from tb_filled_cat where class_id=? and course_id=? and period=? and upper(cat_type)=? limit 1",
                new String[]{String.valueOf(classId), String.valueOf(subjectId), String.valueOf(period), code});
        boolean already = filled.moveToFirst();
        filled.close();
        if (!already) {
            db.execSQL("insert into tb_filled_cat (course_id,class_id,period,cat_type) values (?,?,?,?)",
                    new Object[]{subjectId, classId, period, code});
        }
        preferences.edit().putString("marks_advance_cat", code).apply();
    }

    private int queueMarksOffline() {
        String cat = catCode();
        int queued = 0;
        for (StudentModel st : studentList) {
            if (st.getMarks() == null) continue;
            if (previouslySaved(st.getId())) continue;
            try {
                Cursor existing = db.rawQuery(
                        "select id from tb_marks where student_id=? and course_id=? and class_id=? and mark_type=? and period=? and (upper(ifnull(cat_code,''))=upper(?) or ifnull(cast(cat_type as text),'')=?) limit 1",
                        new String[]{
                                String.valueOf(st.getId()),
                                String.valueOf(subjectId),
                                String.valueOf(classId),
                                String.valueOf(examinationType),
                                String.valueOf(period),
                                cat,
                                cat
                        });
                boolean exists = existing.moveToNext();
                int queuedId = exists ? existing.getInt(0) : 0;
                existing.close();
                if (exists) {
                    db.execSQL("update tb_marks set marks=?, out_of=?, examDate=?, created_by=?, cat_code=? where id=?",
                            new Object[]{st.getMarks(), maximum, examinationDate, teacher, cat, queuedId});
                } else {
                    db.execSQL("insert into tb_marks (student_id,student_name,course_id,course_name,class_id,mark_type,mark_type_name,marks,out_of,cat_type,period,examDate,created_by,cat_code) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            new Object[]{
                                    st.getId(),
                                    st.getName(),
                                    subjectId,
                                    getIntent().getStringExtra("courseTitle"),
                                    classId,
                                    examinationType,
                                    getIntent().getStringExtra("markTypeTitle"),
                                    st.getMarks(),
                                    maximum,
                                    cat,
                                    period,
                                    examinationDate,
                                    teacher,
                                    cat
                            });
                }
                rememberLocalCache(st, cat);
                savedMarks.put(st.getId(), st.getMarks());
                queued++;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (queued > 0) markCatFilled(cat);
        Intent syncIntent = new Intent("sync.qonics.com.somanet");
        syncIntent.setPackage(getPackageName());
        sendBroadcast(syncIntent);
        return queued;
    }

    private void showQueuedDialog(SweetAlertDialog sweetAlertDialog) {
        sweetAlertDialog.changeAlertType(SweetAlertDialog.SUCCESS_TYPE);
        sweetAlertDialog.setTitle("Saved on this phone");
        sweetAlertDialog.setContentText("They will send to the server when internet is back.");
        sweetAlertDialog.setCancelButton("Close", d -> {
            d.dismiss();
            finish();
        });
        sweetAlertDialog.setConfirmButton("Close", d -> {
            d.dismiss();
            finish();
        });
    }

    private JSONArray liveRecords() {
        JSONArray jsonArray = new JSONArray();
        String cat = catCode();
        int n = 1;
        for (StudentModel st : studentList) {
            if (st.getMarks() == null) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("id", n++);
            row.put("student_id", st.getId());
            row.put("course_id", subjectId);
            row.put("class_id", classId);
            row.put("mark_type", nurseryEntry ? 1 : examinationType);
            row.put("marks", st.getMarks());
            row.put("out_of", maximum);
            row.put("cat_type", cat);
            row.put("period", period);
            row.put("examDate", examinationDate);
            row.put("created_by", teacher);
            jsonArray.put(new JSONObject(row));
        }
        return jsonArray;
    }

    private void postMarksLive(SweetAlertDialog sweetAlertDialog) {
        Map<String, Object> map = new HashMap<>();
        map.put("records", liveRecords().toString());
        map.put("school", preferences.getInt(MainActivity.SK_ID, 0));
        HttpRequest req = new HttpRequest(this);
        req.post(server + "upload_data/marks", map, (object, status_code, status) -> {
            if (isFinishing()) return;
            if (object != null && object.has("success") && !object.has("error")) {
                String cat = catCode();
                for (StudentModel st : studentList) {
                    if (st.getMarks() == null) continue;
                    db.execSQL("delete from tb_marks where student_id=? and course_id=? and class_id=? and mark_type=? and period=?",
                            new Object[]{st.getId(), subjectId, classId, examinationType, period});
                    rememberLocalCache(st, cat);
                }
                markCatFilled(cat);
                sweetAlertDialog.changeAlertType(SweetAlertDialog.SUCCESS_TYPE);
                sweetAlertDialog.setTitle("Saved to the server");
                sweetAlertDialog.setContentText("Marks were entered live. Nothing was left in the offline queue.");
                sweetAlertDialog.setCancelButton("Close", d -> {
                    d.dismiss();
                    finish();
                });
                sweetAlertDialog.setConfirmButton("Close", d -> {
                    d.dismiss();
                    finish();
                });
                return;
            }
            if (object == null) {
                int queued = queueMarksOffline();
                sweetAlertDialog.changeAlertType(SweetAlertDialog.NORMAL_TYPE);
                sweetAlertDialog.setTitle(queued > 0 ? "Network dropped" : "Not saved");
                sweetAlertDialog.setContentText(queued > 0
                        ? "New marks were kept on this phone and will send when internet returns. Marks that were already saved were not changed."
                        : "Saved marks can only be changed while you are online. Nothing was stored offline.");
                sweetAlertDialog.setConfirmButton("Close", d -> {
                    d.dismiss();
                    if (queued > 0) finish();
                });
                return;
            }
            String err = object.optString("error", status == null ? "Could not save marks" : status);
            sweetAlertDialog.changeAlertType(SweetAlertDialog.ERROR_TYPE);
            sweetAlertDialog.setTitle("Not saved");
            sweetAlertDialog.setContentText(err);
            sweetAlertDialog.setConfirmButton("OK", SweetAlertDialog::dismissWithAnimation);
        });
    }

    /** Persist typed values from visible EditTexts into studentList before save/review. */
    private void commitVisibleMarks() {
        for (int i = 0; i < lnlstudents.getChildCount(); i++) {
            View row = lnlstudents.getChildAt(i);
            EditText et = row.findViewById(R.id.studentMarksValue);
            Object tag = row.getTag();
            if (et == null || !(tag instanceof Integer)) continue;
            int studentId = (Integer) tag;
            String raw = et.getText().toString().trim();
            if (raw.isEmpty()) continue;
            try {
                double marks = Double.parseDouble(raw);
                double max = Double.parseDouble(maximum);
                if (marks > max) continue;
                int index = checkMarksExists(studentId);
                if (index >= 0) {
                    studentList.get(index).setMarks(marks);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Whole marks show as 4, not 4.0. A stored -1 is "did not sit" and is not a score. */
    private static String formatMark(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(value);
    }

    private static String firstPositiveOutOf(String current, String candidate) {
        if (current != null) return current;
        if (candidate == null) return null;
        try {
            double value = Double.parseDouble(candidate.trim());
            if (value > 0) return formatMark(value);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Web sets Total Marks from the saved assessment (outof), not the course maximum.
     * Editing Quiz 1 / Period 2 must stay out of 10 when the course itself is out of 70.
     */
    private void applyAssessmentMaximum(String outOf) {
        if (outOf == null || outOf.length() == 0 || txtMaxTotal == null) return;
        if (outOf.equals(maximum)) return;
        maximum = outOf;
        String periodTitle = nullToEmpty(getIntent().getStringExtra("periodTitle"));
        String maxLine = "Max: " + maximum;
        if (periodTitle.length() > 0 && !periodTitle.toLowerCase(Locale.US).contains("select")) {
            maxLine = maxLine + "  ·  " + periodTitle;
        }
        txtMaxTotal.setText(maxLine);
    }

    private void loadStudent() {
        loadStudent(null);
    }

    private void stashUnsavedNew() {
        commitVisibleMarks();
        unsavedNew.clear();
        for (StudentModel st : studentList) {
            if (st.getMarks() != null && !savedMarks.containsKey(st.getId())) {
                unsavedNew.put(st.getId(), st.getMarks());
            }
        }
    }

    private void loadStudent(String searchQuery) {
        if (unsavedNew.isEmpty() && lnlstudents != null && lnlstudents.getChildCount() > 0) {
            stashUnsavedNew();
        }
        studentList.clear();
        lnlstudents.removeAllViews();
        changeTitle(getString(R.string.please_wait));
        imgerror.setVisibility(View.INVISIBLE);
        txtstatus.setText(getString(R.string.please_wait));
        prb.setVisibility(View.VISIBLE);
        txtretry.setVisibility(View.INVISIBLE);
        rlvloading.setVisibility(View.VISIBLE);

        String filter = "";
        if (searchQuery != null) {
            filter = " and (name like '%" + searchQuery.replace("'", "''") + "%' or regno like '%" + searchQuery.replace("'", "''") + "%')";
        }
        String cat = catCode();
        Map<Integer, Double> cached = new HashMap<>();
        Set<Integer> absentSaved = new HashSet<>();
        String sheetOutOf = null;
        Cursor cache = db.rawQuery(
                "select student_id, marks, out_of from tb_mark_cache where course_id=? and class_id=? and mark_type=? and ifnull(cat_type,'')=? and period=?",
                new String[]{String.valueOf(subjectId), String.valueOf(classId), String.valueOf(examinationType), cat, String.valueOf(period)});
        while (cache.moveToNext()) {
            sheetOutOf = firstPositiveOutOf(sheetOutOf, cache.getString(2));
            try {
                double mark = Double.parseDouble(cache.getString(1));
                if (mark < 0) absentSaved.add(cache.getInt(0));
                else cached.put(cache.getInt(0), mark);
            } catch (Exception ignored) {
            }
        }
        cache.close();
        Cursor pending = db.rawQuery(
                "select student_id, marks, out_of from tb_marks where course_id=? and class_id=? and mark_type=? and period=? and (ifnull(cat_type,'')=? or ifnull(cat_code,'')=?)",
                new String[]{String.valueOf(subjectId), String.valueOf(classId), String.valueOf(examinationType), String.valueOf(period), cat, cat});
        while (pending.moveToNext()) {
            if (sheetOutOf == null) {
                sheetOutOf = firstPositiveOutOf(null, pending.getString(2));
            }
            try {
                int studentId = Integer.parseInt(pending.getString(0));
                double mark = Double.parseDouble(pending.getString(1));
                absentSaved.remove(studentId);
                if (mark < 0) absentSaved.add(studentId);
                else cached.put(studentId, mark);
            } catch (Exception ignored) {
            }
        }
        pending.close();
        applyAssessmentMaximum(sheetOutOf);
        savedMarks.clear();
        savedMarks.putAll(cached);
        for (Integer absentId : absentSaved) {
            if (!savedMarks.containsKey(absentId)) savedMarks.put(absentId, -1d);
        }

        Cursor c = db.rawQuery(
                "select * from tb_student where class_id = ? " + filter + " order by name asc limit 200",
                new String[]{classId + ""});
        boolean recordFound = false;
        while (c.moveToNext()) {
            recordFound = true;
            String name = c.getString(c.getColumnIndex("name"));
            String regno = c.getString(c.getColumnIndex("regno"));
            int student_id = c.getInt(c.getColumnIndex("id"));
            if (name == null || name.trim().isEmpty()) {
                name = regno != null ? regno : ("Student #" + student_id);
            }
            StudentModel model = new StudentModel(student_id, name, regno, c.getString(c.getColumnIndex("card")));
            if (cached.containsKey(student_id)) {
                model.setMarks(cached.get(student_id));
            } else if (unsavedNew.containsKey(student_id)) {
                model.setMarks(unsavedNew.get(student_id));
            }
            studentList.add(model);
            addStudentRow(student_id, name, regno);
        }
        c.close();
        adapater.notifyDataSetChanged();

        if (!recordFound) {
            rlvloading.setVisibility(View.VISIBLE);
            imgerror.setVisibility(View.VISIBLE);
            prb.setVisibility(View.INVISIBLE);
            txtretry.setVisibility(View.VISIBLE);
            txtstatus.setText(getString(R.string.atte_err_one));
            changeTitle(getString(R.string.no_student));
        } else {
            rlvloading.setVisibility(View.GONE);
            stTotal = studentList.size();
            changeTitle(stTotal + " students");
        }
        unsavedNew.clear();
        applySaveButtonLabel();
    }

    /** Push every queued mark, then ask the server if this course may be edited. */
    private void uploadPendingMarksThenRefresh() {
        if (!onlineNow()) {
            applySaveButtonLabel();
            return;
        }
        if (marksUploadRunning) return;
        Cursor c = db.rawQuery("select * from tb_marks order by id limit 40", null);
        JSONArray jsonArray = new JSONArray();
        boolean recordFound = false;
        while (c.moveToNext()) {
            recordFound = true;
            Map<String, Object> row = new HashMap<>();
            row.put("id", c.getInt(0));
            row.put("student_id", c.getString(1));
            row.put("course_id", c.getString(3));
            row.put("class_id", c.getString(5));
            row.put("mark_type", c.getString(6));
            row.put("marks", c.getString(8));
            row.put("out_of", c.getString(9));
            int catCodeIdx = c.getColumnIndex("cat_code");
            String cat = catCodeIdx >= 0 ? c.getString(catCodeIdx) : null;
            if (cat == null || cat.length() == 0) {
                cat = c.getString(10);
            }
            row.put("cat_type", cat == null ? "" : cat);
            row.put("period", c.getString(11));
            row.put("examDate", c.getString(12));
            row.put("created_by", c.getString(13));
            jsonArray.put(new JSONObject(row));
        }
        c.close();
        if (!recordFound) {
            refreshSheetFromServer();
            return;
        }
        marksUploadRunning = true;
        applySaveButtonLabel();
        Map<String, Object> map = new HashMap<>();
        map.put("records", jsonArray.toString());
        map.put("school", preferences.getInt(MainActivity.SK_ID, 0));
        HttpRequest req = new HttpRequest(this);
        req.post(server + "upload_data/marks", map, (object, status_code, status) -> {
            marksUploadRunning = false;
            if (isFinishing()) return;
            if (object != null && object.has("success") && !object.has("error") && object.has("last_id")) {
                try {
                    db.execSQL("delete from tb_marks where id <= " + object.getInt("last_id"));
                } catch (Exception ignored) {
                }
                uploadPendingMarksThenRefresh();
                return;
            }
            refreshSheetFromServer();
        });
    }

    private void refreshSheetFromServer() {
        if (!MainActivity.isNetworkAvailable(this)) return;
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("teacher_id", teacher);
        map.put("class_id", classId);
        map.put("course_id", subjectId);
        map.put("mark_type", examinationType);
        map.put("cat_type", catTypeId == null ? "" : catTypeId);
        map.put("period", period);
        HttpRequest req = new HttpRequest(this);
        req.post(server + "marks_sheet", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (isFinishing()) return;
                if (object == null) {
                    runOnUiThread(() -> {
                        serverCheckDone = true;
                        serverEditAllowed = false;
                        if (!entryLocked && txtLockBanner != null) {
                            txtLockBanner.setText("Could not confirm that this course is open for editing. Saved marks stay locked.");
                            txtLockBanner.setVisibility(View.VISIBLE);
                        }
                        loadStudent();
                    });
                    return;
                }
                if (object.has("error")) {
                    String err = object.optString("error", "Marks cannot be entered");
                    runOnUiThread(() -> lockEditing(err));
                    return;
                }
                final boolean locked = object.optInt("marks_locked", 0) == 1 || object.optInt("editable", 1) == 0;
                if (object.optInt("is_nursery", 0) == 1) {
                    nurseryEntry = true;
                }
                if (object.has("nursery_bands")) {
                    JSONArray bands = object.optJSONArray("nursery_bands");
                    if (bands != null && bands.length() > 0) {
                        nurseryBands = bands;
                        preferences.edit().putString("nursery_grade_bands", bands.toString()).apply();
                    }
                }
                if (object.has("marks")) {
                    JSONArray rows = object.optJSONArray("marks");
                    if (rows != null) {
                        String cat = catCode();
                        db.execSQL("delete from tb_mark_cache where course_id=? and class_id=? and mark_type=? and ifnull(cat_type,'')=? and period=?",
                                new Object[]{subjectId, classId, examinationType, cat, period});
                        for (int i = 0; i < rows.length(); i++) {
                            try {
                                JSONObject row = rows.getJSONObject(i);
                                db.execSQL("insert into tb_mark_cache (student_id,course_id,class_id,mark_type,cat_type,period,marks,out_of) values (?,?,?,?,?,?,?,?)",
                                        new Object[]{
                                                row.optInt("student_id"),
                                                subjectId,
                                                classId,
                                                examinationType,
                                                cat,
                                                period,
                                                row.optString("marks"),
                                                row.optString("out_of", maximum)
                                        });
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                runOnUiThread(() -> {
                    if (locked) {
                        lockEditing("Marks editing is locked for you on this course.");
                    } else {
                        unlockFromServer();
                    }
                    loadStudent();
                });
            }
        });
    }

    private void addStudentRow(final int student_id, final String name, String regno) {
        View v = getLayoutInflater().inflate(R.layout.student_marks_row, lnlstudents, false);
        v.setTag(student_id);
        TextView txtname = v.findViewById(R.id.txtname);
        TextView txtregno = v.findViewById(R.id.txtregno);
        TextView txtmarks = v.findViewById(R.id.txtmarks);
        EditText studentMarks = v.findViewById(R.id.studentMarksValue);

        txtmarks.setVisibility(View.GONE);
        studentMarks.setVisibility(View.VISIBLE);
        txtname.setText(name);
        txtregno.setText(regno != null ? regno : "");
        TextView txtmention = v.findViewById(R.id.txtmention);
        if (nurseryEntry && txtmention != null) {
            txtmention.setVisibility(View.VISIBLE);
        }

        int index0 = checkMarksExists(student_id);
        StudentModel studentData0 = studentList.get(index0);
        studentMarks.setHint("-");
        if (studentData0.getMarks() != null && studentData0.getMarks() >= 0) {
            studentMarks.setText(formatMark(studentData0.getMarks()));
            studentMarks.setBackground(ContextCompat.getDrawable(this, R.drawable.marks_field_bg));
            if (txtmention != null) txtmention.setText(mentionFor(studentData0.getMarks()));
        } else {
            studentMarks.setText("");
        }
        boolean enabled = fieldEnabled(student_id);
        studentMarks.setEnabled(enabled);
        studentMarks.setFocusable(enabled);
        studentMarks.setFocusableInTouchMode(enabled);

        studentMarks.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String raw = s.toString().trim();
                if (raw.isEmpty()) return;
                try {
                    double val = Double.parseDouble(raw);
                    double max = Double.parseDouble(maximum);
                    if (val > max) {
                        Toast.makeText(StudentListActivity.this,
                                "Max for " + name + " is " + maximum, Toast.LENGTH_SHORT).show();
                        s.clear();
                        if (txtmention != null) txtmention.setText("");
                        return;
                    }
                    if (txtmention != null) txtmention.setText(mentionFor(val));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        studentMarks.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                String raw = studentMarks.getText().toString().trim();
                if (raw.isEmpty()) return;
                try {
                    double marks = Double.parseDouble(raw);
                    double max = Double.parseDouble(maximum);
                    if (marks > max) {
                        Toast.makeText(this, "Invalid marks for " + name, Toast.LENGTH_SHORT).show();
                        studentMarks.setText("");
                        return;
                    }
                    int index = checkMarksExists(student_id);
                    if (index >= 0) {
                        studentList.get(index).setMarks(marks);
                    }
                } catch (NumberFormatException e) {
                    studentMarks.setText("");
                }
            }
        });
        lnlstudents.addView(v);
    }

    private String mentionFor(double mark) {
        if (!nurseryEntry || nurseryBands == null) return "";
        double max;
        try {
            max = Double.parseDouble(maximum);
        } catch (Exception e) {
            return "";
        }
        if (max <= 0) return "";
        double pct = mark / max * 100.0;
        for (int i = 0; i < nurseryBands.length(); i++) {
            JSONObject band = nurseryBands.optJSONObject(i);
            if (band == null) continue;
            double min = band.optDouble("min_point", Double.NaN);
            double hi = band.optDouble("max_point", Double.NaN);
            if (Double.isNaN(min) || Double.isNaN(hi)) continue;
            if (pct + 0.001 >= min && pct - 0.001 <= hi) {
                return band.optString("color_title", "");
            }
        }
        return "";
    }

    private int checkMarksExists(int studentId) {
        int c = 0;
        for (StudentModel a : studentList) {
            if (studentId == a.getId()) {
                return c;
            }
            c++;
        }
        return -1;
    }

    private void changeTitle(String title) {
        txtcount.setText(title);
    }

    private class StudentListAdapter extends ArrayAdapter<StudentModel> {
        public StudentListAdapter() {
            super(StudentListActivity.this, R.layout.student_marks_row, studentList);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.student_marks_row, parent, false);
            }
            TextView txtname = v.findViewById(R.id.txtname);
            TextView txtregno = v.findViewById(R.id.txtregno);
            TextView txtmarks = v.findViewById(R.id.txtmarks);
            EditText studentMarks = v.findViewById(R.id.studentMarksValue);
            txtmarks.setVisibility(View.VISIBLE);
            studentMarks.setVisibility(View.GONE);
            StudentModel student = studentList.get(position);
            txtname.setText(student.getName());
            txtregno.setText(student.getRegno());
            Double shown = student.getMarks();
            txtmarks.setText(shown == null || shown < 0 ? "-" : formatMark(shown));
            return v;
        }
    }
}
