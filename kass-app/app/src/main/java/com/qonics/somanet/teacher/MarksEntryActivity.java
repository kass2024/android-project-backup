package com.qonics.somanet.teacher;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.model.Dual_data;
import com.qonics.somanet.ClassAttendanceActivity;
import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.qonics.utils.Callback;
import com.qonics.utils.CatSequence;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Marks entry setup — aligned with web marks_entry.php:
 * marktype (CAT/Exam/…) → class → course → period/cat (CAT only) → max → date → student list.
 */
public class MarksEntryActivity extends AppCompatActivity {

    TextView txtlecturer, txtstatus, txterror, txtretry, txtexit, txttitle, txtViewOffline, txtCountOffline, txtRefresh;
    Button btnstart;
    Spinner spclass, spcourse, spMarkType, spPeriod, spCatType, spAcademicType, spSemester;
    ImageView imgerror;
    RelativeLayout rlvloading;
    LinearLayout lnlclasses, lnlcourse, lnlPeriod, lnlCat, lnlSemester;
    TextView txtPeriodLabel, txtLockHint, txtEntryMode;
    Map<Integer, Boolean> classNursery = new HashMap<>();
    Set<Integer> lockedCourseIds = new HashSet<>();
    ProgressBar prb;

    SharedPreferences preferences;
    SQLiteDatabase db;
    boolean usePeriod = false;
    String courseMaximum = "";

    ArrayList<Dual_data> markType = new ArrayList<>();
    ArrayList<Dual_data> periodList = new ArrayList<>();
    ArrayList<Dual_data> catTypeList = new ArrayList<>();
    ArrayList<Dual_data> academicTypeList = new ArrayList<>();
    ArrayList<Dual_data> semesterList = new ArrayList<>();
    ArrayList<Dual_data> classes = new ArrayList<>();
    ArrayList<Dual_data> courses = new ArrayList<>();
    Map<Integer, String> courseLecturer = new HashMap<>();

    ArrayAdapter<Dual_data> adapter;
    ArrayAdapter<Dual_data> adapterCourse;
    ArrayAdapter<Dual_data> markTypeAdapter;
    ArrayAdapter<Dual_data> periodAdapter;
    ArrayAdapter<Dual_data> catTypeAdapter;
    ArrayAdapter<Dual_data> academicTypeAdapter;
    ArrayAdapter<Dual_data> semesterAdapter;

    private int mYear, mMonth, mDay;
    EditText etfrom, etMaximum;
    DatabaseHandler db_helper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_marks_entry);

        db = this.openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        db_helper = new DatabaseHandler(this);

        adapter = new GenericAdapter(this, classes);
        adapterCourse = new GenericAdapter(this, courses);
        markTypeAdapter = new GenericAdapter(this, markType);
        periodAdapter = new GenericAdapter(this, periodList);
        catTypeAdapter = new GenericAdapter(this, catTypeList);
        academicTypeAdapter = new GenericAdapter(this, academicTypeList);
        semesterAdapter = new GenericAdapter(this, semesterList);

        populateView();
        bindSpinners();
        seedWebMarkTypes();
        rebuildPeriods();
        setDefaultDate();
        refreshEntryMode();

        txtCountOffline.setText(String.valueOf(db_helper.countAllOfflineMarks()));
        showTeacherLine(null);

        loadLocalData();
        uploadPendingIfOnline();
        if (hasNetwork()) {
            refreshFromServer(classes.size() <= 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (txtCountOffline != null && db_helper != null) {
            txtCountOffline.setText(String.valueOf(db_helper.countAllOfflineMarks()));
        }
        uploadPendingIfOnline();
        refreshEntryMode();
        if (spcourse != null) {
            rebuildCatTypes();
            refreshLockHint();
        }
    }

    private void uploadPendingIfOnline() {
        if (!hasNetwork() || db_helper == null || db_helper.countAllOfflineMarks() < 1) return;
        Intent syncIntent = new Intent("sync.qonics.com.somanet");
        syncIntent.setPackage(getPackageName());
        sendBroadcast(syncIntent);
    }

    private boolean fullAccessMarks() {
        return preferences.getBoolean("marks_full_access", false);
    }

    private void showTeacherLine(String lecturer) {
        if (fullAccessMarks()) {
            if (lecturer == null || lecturer.trim().isEmpty()) {
                txtlecturer.setText("—");
            } else {
                txtlecturer.setText(lecturer.trim());
            }
        } else {
            txtlecturer.setText(preferences.getString("soma_name", "Teacher"));
        }
    }

    private boolean isHolidayLabel(String title) {
        return title != null && title.toLowerCase(Locale.US).contains("holiday");
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private void showLoading(boolean show) {
        if (rlvloading == null) return;
        rlvloading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            prb.setVisibility(View.VISIBLE);
            imgerror.setVisibility(View.INVISIBLE);
            txtretry.setVisibility(View.INVISIBLE);
            txtstatus.setText(getString(R.string.please_wait));
        }
    }

    private void showEmptyState(String message) {
        rlvloading.setVisibility(View.VISIBLE);
        prb.setVisibility(View.GONE);
        imgerror.setVisibility(View.VISIBLE);
        txtretry.setVisibility(View.VISIBLE);
        txtstatus.setText(message);
    }

    /** Web mark types: 1 CAT, 2 Exam, 3 Second sitting, 9 Re-assess */
    private void seedWebMarkTypes() {
        markType.clear();
        markType.add(new Dual_data(0, "Select examination category"));
        Cursor c = db.rawQuery("select id,title from tb_marks_type order by id", null);
        while (c.moveToNext()) {
            markType.add(new Dual_data(c.getInt(0), c.getString(1)));
        }
        c.close();
        if (markType.size() <= 1) {
            markType.add(new Dual_data(1, "CAT"));
            markType.add(new Dual_data(2, "Exam"));
            markType.add(new Dual_data(3, "Second sitting"));
            markType.add(new Dual_data(9, "Re-assess"));
        }
        if (currentClassIsNursery()) {
            Dual_data exams = new Dual_data(1, "Exams");
            markType.clear();
            markType.add(new Dual_data(0, "Select examination category"));
            markType.add(exams);
        }
        markTypeAdapter.notifyDataSetChanged();
        if (currentClassIsNursery() && spMarkType != null && markType.size() > 1) {
            spMarkType.setSelection(1);
        }
    }

    /** Web nursery exams: End of Month 1, End of Month 2, Midterm, End of Term. */
    private static final String[] NURSERY_EXAMS = {
            "",
            "End of Month 1 Exam",
            "End of Month 2 Exam",
            "Midterm",
            "End of Term Exam"
    };

    private boolean nurseryOnlyTeacher() {
        return preferences != null && preferences.getBoolean("nursery_marks_only", false);
    }

    private boolean titleLooksNursery(String title) {
        if (title == null) return false;
        String hay = title.toLowerCase(Locale.US);
        return hay.contains("nursery")
                || hay.contains("baby class")
                || hay.contains("middle class")
                || hay.contains("top class")
                || hay.matches(".*\\bn\\s*[1-3]\\b.*");
    }

    private boolean currentClassIsNursery() {
        int classId = selectedId(spclass, classes);
        if (classId < 1) return nurseryOnlyTeacher();
        if (Boolean.TRUE.equals(classNursery.get(classId))) return true;
        int pos = spclass.getSelectedItemPosition();
        if (pos > 0 && pos < classes.size() && titleLooksNursery(classes.get(pos).getText1())) return true;
        return nurseryOnlyTeacher();
    }

    private Set<Integer> lockedPeriodIds() {
        Set<Integer> locked = new HashSet<>();
        if (preferences == null) return locked;
        String raw = preferences.getString("locked_periods", "");
        if (raw == null || raw.length() == 0) return locked;
        for (String part : raw.split(",")) {
            try {
                int n = Integer.parseInt(part.trim());
                if (n > 0) locked.add(n);
            } catch (NumberFormatException ignored) {
            }
        }
        return locked;
    }

    private boolean selectedCourseLocked() {
        int courseId = selectedId(spcourse, courses);
        return courseId > 0 && lockedCourseIds.contains(courseId);
    }

    private boolean selectedPeriodLocked() {
        int periodId = selectedId(spPeriod, periodList);
        return periodId > 0 && lockedPeriodIds().contains(periodId);
    }

    private void rebuildPeriods() {
        int keep = selectedId(spPeriod, periodList);
        boolean nursery = currentClassIsNursery();
        Set<Integer> locked = lockedPeriodIds();
        periodList.clear();
        periodList.add(new Dual_data(0, nursery ? "Select exam" : "Select period"));
        for (int i = 1; i <= 4; i++) {
            String label = nursery ? NURSERY_EXAMS[i] : ("Period " + i);
            if (locked.contains(i)) label = label + " (Locked)";
            periodList.add(new Dual_data(i, label));
        }
        periodAdapter.notifyDataSetChanged();
        if (spPeriod != null && keep > 0) {
            for (int i = 0; i < periodList.size(); i++) {
                if (periodList.get(i).getId() == keep) {
                    spPeriod.setSelection(i);
                    break;
                }
            }
        }
        if (txtPeriodLabel != null) {
            txtPeriodLabel.setText(nursery ? "Exam" : getString(R.string.lbl_select_mark_period));
        }
        rebuildCatTypes();
    }

    private void refreshEntryMode() {
        if (txtEntryMode == null) return;
        if (hasNetwork()) {
            txtEntryMode.setText("Online — marks save straight to the server");
        } else {
            txtEntryMode.setText("Offline — marks stay on this phone until internet returns");
        }
    }

    private void refreshLockHint() {
        if (txtLockHint == null) return;
        String msg = "";
        if (selectedCourseLocked()) {
            msg = "Marks editing is locked for you on this course. Quizzes, tests, homework, and exams stay blocked.";
        } else if (selectedPeriodLocked()) {
            msg = "This period is locked. No marks can be entered until it is unlocked.";
        }
        txtLockHint.setText(msg);
        txtLockHint.setVisibility(msg.length() == 0 ? View.GONE : View.VISIBLE);
        if (btnstart != null) {
            btnstart.setEnabled(msg.length() == 0);
            btnstart.setAlpha(msg.length() == 0 ? 1f : 0.45f);
        }
    }

    /** Finished CAT numbers stay selectable. Only the next number is added. */
    private void rebuildCatTypes() {
        int keepCodePos = spCatType != null ? spCatType.getSelectedItemPosition() : 0;
        String keepCode = keepCodePos > 0 && keepCodePos < catTypeList.size()
                ? catTypeList.get(keepCodePos).getText2() : "";
        Set<String> filled = filledCatCodes();
        catTypeList.clear();
        catTypeList.add(new Dual_data(0, "Select CAT type", ""));
        String advance = preferences == null ? "" : preferences.getString("marks_advance_cat", "");
        String advanceNext = nextCatCode(advance);
        int n = 1;
        int restore = 0;
        int advancePos = 0;
        int lastQuiz = 0;
        for (CatSequence.Option option : CatSequence.options(filled)) {
            catTypeList.add(new Dual_data(n, option.label, option.code));
            if (option.code.equals(keepCode)) restore = n;
            if (advanceNext.length() > 0 && option.code.equals(advanceNext)) advancePos = n;
            if (option.code.startsWith("Q")) lastQuiz = n;
            n++;
        }
        if (advance.length() > 0 && preferences != null) {
            preferences.edit().remove("marks_advance_cat").apply();
        }
        if (advancePos > 0) restore = advancePos;
        else if (restore == 0 && lastQuiz > 0) restore = lastQuiz;
        catTypeAdapter.notifyDataSetChanged();
        if (spCatType != null && restore > 0 && restore < catTypeList.size()) {
            spCatType.setSelection(restore);
        }
        if (spCatType != null) {
            spCatType.post(this::applyExistingSheetMaximum);
        } else {
            applyExistingSheetMaximum();
        }
    }

    private String nextCatCode(String code) {
        if (code == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([QTH])(\\d+)$")
                .matcher(code.trim().toUpperCase(Locale.US));
        if (!m.matches()) return "";
        return m.group(1) + (Integer.parseInt(m.group(2)) + 1);
    }

    private Set<String> filledCatCodes() {
        Set<String> filled = new HashSet<>();
        int classId = selectedId(spclass, classes);
        int courseId = selectedId(spcourse, courses);
        int periodId = usePeriod ? selectedId(spPeriod, periodList) : 0;
        if (classId < 1 || courseId < 1) return filled;
        String[] args = new String[]{
                String.valueOf(classId), String.valueOf(courseId), String.valueOf(periodId)
        };
        Cursor saved = db.rawQuery(
                "select cat_type from tb_filled_cat where class_id=? and course_id=? and period=?", args);
        while (saved.moveToNext()) {
            String code = saved.getString(0);
            if (code != null && code.length() > 0) filled.add(code.toUpperCase(Locale.US));
        }
        saved.close();
        Cursor pending = db.rawQuery(
                "select distinct ifnull(nullif(cat_code,''), cat_type) from tb_marks where class_id=? and course_id=? and period=? and mark_type=1",
                args);
        while (pending.moveToNext()) {
            String code = pending.getString(0);
            if (code != null && code.length() > 1) filled.add(code.toUpperCase(Locale.US));
        }
        pending.close();
        return filled;
    }

    /**
     * Same as the website: an assessment that already has marks keeps its own total
     * (for example 10), instead of the course maximum (for example 70).
     */
    private void applyExistingSheetMaximum() {
        if (etMaximum == null) return;
        String saved = lookupSavedOutOf();
        if (saved != null) {
            etMaximum.setText(saved);
            return;
        }
        if (courseMaximum != null && courseMaximum.length() > 0 && !"0".equals(courseMaximum)) {
            etMaximum.setText(courseMaximum);
        }
    }

    private String lookupSavedOutOf() {
        if (db == null) return null;
        int classId = selectedId(spclass, classes);
        int courseId = selectedId(spcourse, courses);
        int markTypeId = selectedId(spMarkType, markType);
        if (classId < 1 || courseId < 1 || markTypeId < 1) return null;
        int periodId = usePeriod ? selectedId(spPeriod, periodList) : 0;
        String cat = "";
        if (markTypeId == 1 && spCatType != null) {
            int pos = spCatType.getSelectedItemPosition();
            if (pos > 0 && pos < catTypeList.size()) {
                String code = catTypeList.get(pos).getText2();
                cat = code == null ? "" : code;
            }
        }
        Cursor c = db.rawQuery(
                "select out_of from tb_mark_cache where course_id=? and class_id=? and mark_type=? and ifnull(cat_type,'')=? and period=? and cast(out_of as real) > 0 limit 1",
                new String[]{
                        String.valueOf(courseId),
                        String.valueOf(classId),
                        String.valueOf(markTypeId),
                        cat,
                        String.valueOf(periodId)
                });
        String outOf = null;
        if (c.moveToFirst()) {
            outOf = c.getString(0);
        }
        c.close();
        if (outOf == null) return null;
        try {
            double value = Double.parseDouble(outOf.trim());
            if (value <= 0) return null;
            if (Math.abs(value - Math.rint(value)) < 0.0001) {
                return String.valueOf((long) Math.rint(value));
            }
            return outOf.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private int selectedId(Spinner spinner, ArrayList<Dual_data> list) {
        if (spinner == null || list == null) return 0;
        int pos = spinner.getSelectedItemPosition();
        if (pos <= 0 || pos >= list.size()) return 0;
        return list.get(pos).getId();
    }

    private void setDefaultDate() {
        Calendar c = Calendar.getInstance();
        etfrom.setText(String.format(Locale.US, "%02d-%02d-%d",
                c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.YEAR)));
    }

    private void populateView() {
        lnlPeriod = findViewById(R.id.lnlperiod);
        lnlCat = findViewById(R.id.lnlcattype);
        txtPeriodLabel = findViewById(R.id.txtPeriodLabel);
        txtLockHint = findViewById(R.id.txtLockHint);
        txtEntryMode = findViewById(R.id.txtEntryMode);
        txtexit = findViewById(R.id.txtexit);
        txttitle = findViewById(R.id.txttitle);
        txtretry = findViewById(R.id.txtretry);
        txterror = findViewById(R.id.txterror);
        lnlclasses = findViewById(R.id.lnlclasses);
        lnlcourse = findViewById(R.id.lnlcourse);
        lnlSemester = findViewById(R.id.lnlSemester);
        txtRefresh = findViewById(R.id.txtRefresh);

        txterror.setText("");
        txtlecturer = findViewById(R.id.txtlecturer);
        txtstatus = findViewById(R.id.txtstatus);
        prb = findViewById(R.id.prb);
        rlvloading = findViewById(R.id.rlvloading);
        imgerror = findViewById(R.id.imgerror);
        btnstart = findViewById(R.id.btnstart);
        spcourse = findViewById(R.id.spcourse);
        spclass = findViewById(R.id.spclass);
        spAcademicType = findViewById(R.id.spAcademicType);
        spSemester = findViewById(R.id.spSemester);
        spMarkType = findViewById(R.id.marktype);
        spPeriod = findViewById(R.id.period);
        spCatType = findViewById(R.id.cat_type);
        txtViewOffline = findViewById(R.id.viewOfflineMarkId);
        txtCountOffline = findViewById(R.id.countOfflineMarks);
        etMaximum = findViewById(R.id.maximum);
        etfrom = findViewById(R.id.lay_date_from);

        if (getIntent().getIntExtra("who", 0) == 1) {
            lnlcourse.setVisibility(View.GONE);
            txttitle.setText("Class Daily attendance");
        }

        lnlPeriod.setVisibility(View.GONE);

        txtretry.setOnClickListener(v -> {
            if (hasNetwork()) refreshFromServer(true);
            else {
                loadLocalData();
                if (classes.size() <= 1) {
                    showEmptyState("No classes offline. Connect to internet and tap Retry.");
                } else {
                    showLoading(false);
                }
            }
        });

        txtexit.setOnClickListener(v -> onBackPressed());

        txtViewOffline.setOnClickListener(v ->
                startActivity(new Intent(this, ViewOfflineMarks.class)));

        txtRefresh.setOnClickListener(v -> refreshFromServer(true));

        etfrom.setOnClickListener(v -> openDatePicker());
        etfrom.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) openDatePicker();
        });
    }

    private void openDatePicker() {
        Calendar c = Calendar.getInstance();
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, monthOfYear, dayOfMonth) ->
                        etfrom.setText(String.format(Locale.US, "%02d-%02d-%d", dayOfMonth, monthOfYear + 1, year)),
                mYear, mMonth, mDay);
        dialog.show();
    }

    private void bindSpinners() {
        spMarkType.setAdapter(markTypeAdapter);
        spPeriod.setAdapter(periodAdapter);
        spCatType.setAdapter(catTypeAdapter);
        spAcademicType.setAdapter(academicTypeAdapter);
        spSemester.setAdapter(semesterAdapter);
        spclass.setAdapter(adapter);
        spcourse.setAdapter(adapterCourse);

        spMarkType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePeriodVisibility();
                refreshLockHint();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spclass.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i > 0 && getIntent().getIntExtra("who", 0) == 0) {
                    seedWebMarkTypes();
                    loadCourse(classes.get(i).getId());
                    ensureClassRoster(classes.get(i).getId());
                    updatePeriodVisibility();
                } else {
                    showTeacherLine(null);
                    seedWebMarkTypes();
                    updatePeriodVisibility();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        spPeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                rebuildCatTypes();
                refreshLockHint();
                applyExistingSheetMaximum();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spcourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    String max = courses.get(position).getText2();
                    courseMaximum = max == null ? "" : max.trim();
                    if (courseMaximum.length() > 0 && !courseMaximum.equals("0")) {
                        etMaximum.setText(courseMaximum);
                    }
                    showTeacherLine(courseLecturer.get(courses.get(position).getId()));
                    applyExistingSheetMaximum();
                } else {
                    courseMaximum = "";
                    showTeacherLine(null);
                }
                rebuildCatTypes();
                refreshLockHint();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnstart.setOnClickListener(view -> startMarksEntry());
    }

    private void updatePeriodVisibility() {
        int pos = spMarkType.getSelectedItemPosition();
        int markTypeId = pos > 0 ? markType.get(pos).getId() : 0;
        boolean nursery = currentClassIsNursery();
        boolean schoolUsesPeriod = preferences.getInt("soma_use_period", 0) == 1;
        boolean holiday = markTypeId == 11;
        // Web: period is required while the periodic system is on. Nursery always picks a named exam.
        usePeriod = !holiday && (nursery || (schoolUsesPeriod && markTypeId > 0));
        lnlPeriod.setVisibility(usePeriod ? View.VISIBLE : View.GONE);
        if (lnlCat != null) {
            lnlCat.setVisibility(!nursery && markTypeId == 1 ? View.VISIBLE : View.GONE);
        }
        rebuildPeriods();
    }

    private void startMarksEntry() {
        if (spclass.getSelectedItemPosition() <= 0) {
            Snackbar.make(btnstart, getString(R.string.choose_class), Snackbar.LENGTH_LONG).show();
            return;
        }
        if (getIntent().getIntExtra("who", 0) == 0) {
            if (spcourse.getSelectedItemPosition() <= 0) {
                Snackbar.make(btnstart, getString(R.string.choose_course), Snackbar.LENGTH_LONG).show();
                return;
            }
            if (spMarkType.getSelectedItemPosition() <= 0) {
                Snackbar.make(btnstart, getString(R.string.choose_mark_type), Snackbar.LENGTH_LONG).show();
                return;
            }
            int markTypeId = markType.get(spMarkType.getSelectedItemPosition()).getId();
            boolean nursery = currentClassIsNursery();
            if (selectedCourseLocked()) {
                Snackbar.make(btnstart, "Marks editing is locked for you on this course", Snackbar.LENGTH_LONG).show();
                refreshLockHint();
                return;
            }
            if (nursery && markTypeId != 1) {
                Snackbar.make(btnstart, "Nursery classes record exams only", Snackbar.LENGTH_LONG).show();
                return;
            }
            if (!nursery && markTypeId == 1 && spCatType.getSelectedItemPosition() <= 0) {
                Snackbar.make(btnstart, "Select the quiz, test, or homework", Snackbar.LENGTH_LONG).show();
                return;
            }
            if (usePeriod && spPeriod.getSelectedItemPosition() <= 0) {
                Snackbar.make(btnstart, nursery ? "Select the exam" : "Select the period", Snackbar.LENGTH_LONG).show();
                return;
            }
            if (selectedPeriodLocked()) {
                Snackbar.make(btnstart, "This period is locked. No marks can be entered until it is unlocked.", Snackbar.LENGTH_LONG).show();
                refreshLockHint();
                return;
            }
            if (!nursery && markTypeId == 1) {
                String chosen = catTypeList.get(spCatType.getSelectedItemPosition()).getText2();
                if (!CatSequence.isAllowed(chosen, filledCatCodes())) {
                    Snackbar.make(btnstart, "That item is locked until the previous one is saved", Snackbar.LENGTH_LONG).show();
                    rebuildCatTypes();
                    return;
                }
            }
        }
        if (etMaximum.getText().toString().trim().isEmpty()) {
            Snackbar.make(etMaximum, "Please enter maximum marks", Snackbar.LENGTH_LONG).show();
            return;
        }
        if (etfrom.getText().toString().trim().isEmpty()) {
            Snackbar.make(etfrom, "Please select date", Snackbar.LENGTH_LONG).show();
            return;
        }

        if (getIntent().getIntExtra("who", 0) == 0) {
            Intent intent = new Intent(this, StudentListActivity.class);
            intent.putExtra("courseId", courses.get(spcourse.getSelectedItemPosition()).getId());
            intent.putExtra("courseTitle", courses.get(spcourse.getSelectedItemPosition()).getText1());
            intent.putExtra("classId", classes.get(spclass.getSelectedItemPosition()).getId());
            intent.putExtra("classTitle", classes.get(spclass.getSelectedItemPosition()).getText1());
            intent.putExtra("markTypeId", markType.get(spMarkType.getSelectedItemPosition()).getId());
            intent.putExtra("markTypeTitle", markType.get(spMarkType.getSelectedItemPosition()).getText1());
            int periodId = 0;
            String periodTitle = "";
            if (usePeriod && spPeriod.getSelectedItemPosition() > 0) {
                periodId = periodList.get(spPeriod.getSelectedItemPosition()).getId();
                periodTitle = periodList.get(spPeriod.getSelectedItemPosition()).getText1();
            }
            intent.putExtra("periodId", periodId);
            intent.putExtra("periodTitle", periodTitle);
            intent.putExtra("nursery", currentClassIsNursery());
            intent.putExtra("marksLocked", selectedCourseLocked());
            String catCode = "";
            int catId = 0;
            boolean nurseryEntry = currentClassIsNursery();
            if (!nurseryEntry && markType.get(spMarkType.getSelectedItemPosition()).getId() == 1
                    && spCatType.getSelectedItemPosition() > 0) {
                catId = catTypeList.get(spCatType.getSelectedItemPosition()).getId();
                catCode = catTypeList.get(spCatType.getSelectedItemPosition()).getText2();
            }
            intent.putExtra("catTypeId", catId);
            intent.putExtra("catTypeTitle", catCode);
            intent.putExtra("maximum", etMaximum.getText().toString().trim());
            intent.putExtra("date", etfrom.getText().toString().trim());
            intent.putExtra("lecturerName", courseLecturer.get(courses.get(spcourse.getSelectedItemPosition()).getId()));
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, ClassAttendanceActivity.class);
            intent.putExtra("classId", classes.get(spclass.getSelectedItemPosition()).getId());
            intent.putExtra("classTitle", classes.get(spclass.getSelectedItemPosition()).getText1());
            startActivity(intent);
        }
    }

    private void loadLocalData() {
        classes.clear();
        classNursery.clear();
        classes.add(new Dual_data(0, getString(R.string.select_class)));
        Cursor c = db.rawQuery("select id,title,ifnull(is_nursery,0) from tb_class order by title", null);
        while (c.moveToNext()) {
            String title = c.getString(1);
            if (isHolidayLabel(title)) continue;
            int id = c.getInt(0);
            classes.add(new Dual_data(id, title));
            classNursery.put(id, c.getInt(2) == 1 || titleLooksNursery(title));
        }
        c.close();
        adapter.notifyDataSetChanged();
        if (classes.size() == 2) {
            spclass.setSelection(1);
        }

        seedWebMarkTypes();
        updatePeriodVisibility();

        if (classes.size() <= 1) {
            showEmptyState("No classes assigned. Tap Refresh while online (same as web teacher courses).");
        } else {
            showLoading(false);
            txterror.setText("");
        }
    }

    private void loadCourse(int classId) {
        courses.clear();
        courseLecturer.clear();
        lockedCourseIds.clear();
        courses.add(new Dual_data(0, getString(R.string.select_course), ""));
        Cursor c = db.rawQuery("select id,title,marks,lecturer,ifnull(marks_locked,0) from tb_course where class_id = ? order by title",
                new String[]{String.valueOf(classId)});
        while (c.moveToNext()) {
            int id = c.getInt(0);
            String title = c.getString(1);
            if (c.getInt(4) == 1) {
                lockedCourseIds.add(id);
                title = title + " (Locked)";
            }
            courses.add(new Dual_data(id, title, c.getString(2)));
            courseLecturer.put(id, c.getString(3));
        }
        c.close();
        adapterCourse.notifyDataSetChanged();
        if (courses.size() == 2) {
            spcourse.setSelection(1);
        }
        showTeacherLine(null);
        rebuildCatTypes();
        if (courses.size() <= 1) {
            txterror.setText("No courses for this class");
        } else {
            txterror.setText("");
        }
    }

    private void refreshFromServer(boolean showOverlay) {
        if (!hasNetwork()) {
            Snackbar.make(btnstart, "No network — using offline data", Snackbar.LENGTH_LONG).show();
            loadLocalData();
            return;
        }
        if (showOverlay) showLoading(true);

        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("teacher_id", preferences.getInt("soma_id", 0));

        HttpRequest req = new HttpRequest(this);
        req.post(MainActivity.SERVER + "marks_setup", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) {
                    String err = object != null ? object.optString("error", status) : status;
                    Log.e("MarksEntry", "marks_setup failed: " + err);
                    loadLocalData();
                    if (classes.size() <= 1) {
                        showEmptyState("Could not load classes: " + err);
                    }
                    return;
                }
                try {
                    SharedPreferences.Editor editor = preferences.edit();
                    if (object.has("use_period")) {
                        editor.putInt("soma_use_period", object.getInt("use_period"));
                    }
                    editor.putBoolean("marks_full_access", object.optInt("full_access", 0) == 1);
                    if (object.has("nursery_only")) {
                        editor.putBoolean("nursery_marks_only", object.optInt("nursery_only", 0) == 1);
                    }
                    if (object.has("locked_periods")) {
                        editor.putString("locked_periods", joinInts(object.optJSONArray("locked_periods")));
                    }
                    if (object.has("nursery_bands")) {
                        JSONArray bands = object.optJSONArray("nursery_bands");
                        editor.putString("nursery_grade_bands", bands == null ? "[]" : bands.toString());
                    }
                    editor.apply();
                    persistClasses(object.optJSONArray("classes"));
                    persistCourses(object.optJSONArray("courses"));
                    persistMarkTypes(object.optJSONArray("assessmentTypes"));
                    persistFilledCats(object.optJSONArray("filled_cats"));
                    loadLocalData();
                    showTeacherLine(null);
                    updatePeriodVisibility();
                    Snackbar.make(btnstart, "Classes & courses updated", Snackbar.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    loadLocalData();
                }
            }
        });
    }

    private void persistClasses(JSONArray jarr) {
        if (jarr == null) return;
        db.execSQL("delete from tb_class");
        for (int i = 0; i < jarr.length(); i++) {
            try {
                JSONObject o = jarr.getJSONObject(i);
                String title = o.optString("title", "").trim().replaceAll("\\s+", " ");
                if (title.length() == 0) title = "Class " + o.getInt("id");
                if (isHolidayLabel(title)) continue;
                int nursery = (o.optInt("is_nursery", 0) == 1 || titleLooksNursery(title)) ? 1 : 0;
                db.execSQL("insert into tb_class (id,title,is_nursery) values (?,?,?)",
                        new Object[]{o.getInt("id"), title, nursery});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void persistCourses(JSONArray jarr) {
        if (jarr == null) return;
        db.execSQL("delete from tb_course");
        for (int i = 0; i < jarr.length(); i++) {
            try {
                JSONObject o = jarr.getJSONObject(i);
                db.execSQL("insert into tb_course (id,title,code,marks,class_id,lecturer,marks_locked) values (?,?,?,?,?,?,?)",
                        new Object[]{
                                o.getInt("id"),
                                o.getString("title"),
                                o.optString("code", ""),
                                o.optString("marks", "0"),
                                o.getInt("class_id"),
                                o.optString("lecturer", ""),
                                o.optInt("marks_locked", 0)
                        });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        db.execSQL("delete from tb_course where class_id not in (select id from tb_class)");
    }

    private void persistFilledCats(JSONArray jarr) {
        db.execSQL("delete from tb_filled_cat");
        if (jarr == null) return;
        for (int i = 0; i < jarr.length(); i++) {
            try {
                JSONObject o = jarr.getJSONObject(i);
                db.execSQL("insert into tb_filled_cat (course_id,class_id,period,cat_type) values (?,?,?,?)",
                        new Object[]{
                                o.optInt("course_id"),
                                o.optInt("class_id"),
                                o.optInt("period"),
                                o.optString("cat_type", "").toUpperCase(Locale.US)
                        });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void ensureClassRoster(int classId) {
        if (!hasNetwork() || classId < 1) return;
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("class_id", classId);
        map.put("year", preferences.getInt("soma_academic", 0));
        HttpRequest req = new HttpRequest(this);
        req.post(MainActivity.SERVER + "get_class_roster", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || !object.has("students")) return;
                JSONArray students = object.optJSONArray("students");
                if (students == null) return;
                for (int i = 0; i < students.length(); i++) {
                    try {
                        JSONObject st = students.getJSONObject(i);
                        int id = st.getInt("id");
                        Cursor existing = db.rawQuery("select id from tb_student where id=?",
                                new String[]{String.valueOf(id)});
                        boolean found = existing.moveToNext();
                        existing.close();
                        if (found) {
                            db.execSQL("update tb_student set name=?, regno=?, class_id=?, status=? where id=?",
                                    new Object[]{
                                            st.optString("name"),
                                            st.optString("regno"),
                                            classId,
                                            st.optInt("status", 1),
                                            id
                                    });
                        } else {
                            db.execSQL("insert into tb_student (id,regno,name,card,classe,dept,studying_mode,photo,class_id,phone,sex,status,record_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                    new Object[]{
                                            id,
                                            st.optString("regno"),
                                            st.optString("name"),
                                            st.optString("card"),
                                            st.optString("class"),
                                            st.optString("dept_title"),
                                            st.optString("studying_mode"),
                                            st.optString("photo"),
                                            classId,
                                            st.optString("phone"),
                                            st.optString("sex"),
                                            st.optInt("status", 1),
                                            st.optInt("record_id")
                                    });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private String joinInts(JSONArray arr) {
        if (arr == null || arr.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            int n = arr.optInt(i, 0);
            if (n < 1) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(n);
        }
        return sb.toString();
    }

    private void persistMarkTypes(JSONArray jarr) {
        if (jarr == null) return;
        db.execSQL("delete from tb_marks_type");
        for (int i = 0; i < jarr.length(); i++) {
            try {
                JSONObject o = jarr.getJSONObject(i);
                db.execSQL("insert into tb_marks_type (id,academic_type,title) values (?,?,?)",
                        new Object[]{
                                o.getInt("id"),
                                o.optInt("academic_type_id", 1),
                                o.getString("title")
                        });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class GenericAdapter extends ArrayAdapter<Dual_data> {
        public GenericAdapter(Context context, ArrayList<Dual_data> list) {
            super(context, android.R.layout.simple_spinner_dropdown_item, list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = View.inflate(getContext(), android.R.layout.simple_spinner_dropdown_item, null);
            }
            TextView txt = v.findViewById(android.R.id.text1);
            String label = getItem(position).getText1();
            txt.setText(label);
            txt.setTextColor(label != null && label.contains("(Locked)") ? 0xFFB91C1C : 0xFF0F172A);
            txt.setTextSize(15);
            return v;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View v = getView(position, convertView, parent);
            v.setPadding(24, 28, 24, 28);
            return v;
        }
    }
}
