package com.qonics.somanet;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

import cn.pedant.SweetAlert.SweetAlertDialog;

/**
 * Same roll-call screen as daily class attendance.
 * One save per dormitory per day; saving again updates that roll call.
 */
public class DormitoryAttendanceActivity extends AppCompatActivity {
    private static final String TAG = "DormAttendance";

    private SharedPreferences preferences;
    private SQLiteDatabase db;
    private LinearLayout lnlstudents;
    private TextView txtcount;
    private TextView txtClassName;
    private TextView txtDeviceHint;
    private TextView txtstatus;
    private TextView txtretry;
    private ImageView imgerror;
    private ProgressBar prb;
    private RelativeLayout rlvloading;
    private SearchView searchStudent;
    private FloatingActionButton fab;
    private final ArrayList<StudentRow> students = new ArrayList<>();
    private final HashSet<Integer> absentIds = new HashSet<>();
    private String searchQuery = "";
    private int hostelId;
    private String hostelName = "";
    private boolean saving;
    private boolean alreadyTaken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_attendance);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        db = new DatabaseHandler(this).getWritableDatabase();
        txtClassName = findViewById(R.id.txtClassName);
        txtDeviceHint = findViewById(R.id.txtDeviceHint);
        txtcount = findViewById(R.id.txtcount);
        txtstatus = findViewById(R.id.txtstatus);
        txtretry = findViewById(R.id.txtretry);
        imgerror = findViewById(R.id.imgerror);
        prb = findViewById(R.id.prb);
        rlvloading = findViewById(R.id.rlvloading);
        lnlstudents = findViewById(R.id.lnlstudents);
        searchStudent = findViewById(R.id.srchstudent);
        fab = findViewById(R.id.fab);
        fab.hide();
        fab.setOnClickListener(v -> save());
        txtretry.setOnClickListener(v -> {
            if (hostelId > 0) openSheet();
            else loadDorms();
        });
        if (searchStudent != null) {
            searchStudent.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchQuery = query == null ? "" : query.trim();
                    applySearch();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    searchQuery = newText == null ? "" : newText.trim();
                    applySearch();
                    return true;
                }
            });
        }
        hostelId = getIntent().getIntExtra("hostelId", 0);
        hostelName = getIntent().getStringExtra("hostelName");
        if (hostelName == null) hostelName = "";
        if (hostelId > 0) openSheet();
        else loadDorms();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MainActivity.isNetworkAvailable(this)) {
            SyncReceiver.requestSync(this);
        }
    }

    private int staffId() {
        return preferences.getInt("soma_id", 0);
    }

    private int schoolId() {
        return preferences.getInt(MainActivity.SK_ID, 0);
    }

    private int yearId() {
        return preferences.getInt("soma_academic", 0);
    }

    private boolean seesEveryDorm() {
        String title = preferences.getString("soma_post_title", "");
        if (title == null) return false;
        return title.toLowerCase(Locale.US).contains("customer care");
    }

    private void showWait() {
        rlvloading.setVisibility(View.VISIBLE);
        lnlstudents.setVisibility(View.GONE);
        prb.setVisibility(View.VISIBLE);
        imgerror.setVisibility(View.INVISIBLE);
        txtretry.setVisibility(View.INVISIBLE);
        txtstatus.setText(R.string.please_wait);
        fab.hide();
    }

    private void loadDorms() {
        showWait();
        if (!MainActivity.isNetworkAvailable(this)) {
            showDorms(localDorms());
            return;
        }
        String url = MainActivity.SERVER + "dormitory_attendance_sync/" + schoolId() + "/" + staffId()
                + "?year=" + yearId();
        new HttpRequest(this).timeouts(20, 60).get(url, JSONObject.class, (object, code, status) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (object == null || !object.optBoolean("success", false)) {
                showDorms(localDorms());
                return;
            }
            DormRosterStore.replaceAll(db, object.optJSONArray("dorms"), object.optString("date", ""));
            ArrayList<Dorm> dorms = new ArrayList<>();
            JSONArray arr = object.optJSONArray("dorms");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.optJSONObject(i);
                    if (row == null) continue;
                    Dorm dorm = new Dorm();
                    dorm.id = row.optInt("id");
                    dorm.name = row.optString("name");
                    if (dorm.id > 0) dorms.add(dorm);
                }
            }
            showDorms(dorms.isEmpty() ? localDorms() : dorms);
        }));
    }

    private ArrayList<Dorm> localDorms() {
        ArrayList<Dorm> mine = new ArrayList<>();
        ArrayList<Dorm> all = new ArrayList<>();
        try {
            Cursor c = db.rawQuery(
                    "SELECT id, name, IFNULL(in_charge_staff_id,0) FROM tb_hostels WHERE IFNULL(active,1)=1 ORDER BY name",
                    null);
            while (c.moveToNext()) {
                Dorm dorm = new Dorm();
                dorm.id = c.getInt(0);
                dorm.name = c.getString(1);
                all.add(dorm);
                if (c.getInt(2) == staffId() && staffId() > 0) mine.add(dorm);
            }
            c.close();
            if (all.isEmpty()) {
                Cursor sheet = db.rawQuery("SELECT hostel_id, name, IFNULL(in_charge,0) FROM tb_dorm_sheet ORDER BY name", null);
                while (sheet.moveToNext()) {
                    Dorm dorm = new Dorm();
                    dorm.id = sheet.getInt(0);
                    dorm.name = sheet.getString(1);
                    all.add(dorm);
                    if (sheet.getInt(2) == staffId() && staffId() > 0) mine.add(dorm);
                }
                sheet.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "local dorms", e);
        }
        if (seesEveryDorm() || mine.isEmpty()) {
            return all;
        }
        return mine;
    }

    private void showDorms(ArrayList<Dorm> dorms) {
        if (dorms.size() == 1) {
            hostelId = dorms.get(0).id;
            hostelName = dorms.get(0).name;
            openSheet();
            return;
        }
        rlvloading.setVisibility(View.GONE);
        lnlstudents.setVisibility(View.VISIBLE);
        lnlstudents.removeAllViews();
        txtClassName.setText(R.string.dormitory_attendance);
        txtcount.setText(R.string.dorm_att_pick);
        if (dorms.isEmpty()) {
            txtstatus.setText(R.string.dorm_att_none);
            txtretry.setVisibility(View.VISIBLE);
            rlvloading.setVisibility(View.VISIBLE);
            prb.setVisibility(View.GONE);
            return;
        }
        for (Dorm dorm : dorms) {
            TextView row = new TextView(this);
            row.setText(dorm.name);
            row.setTextSize(18);
            row.setTextColor(Color.BLACK);
            row.setPadding(28, 22, 28, 22);
            row.setOnClickListener(v -> {
                hostelId = dorm.id;
                hostelName = dorm.name;
                openSheet();
            });
            View line = new View(this);
            line.setBackgroundColor(Color.parseColor("#cdcdcd"));
            line.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            lnlstudents.addView(row);
            lnlstudents.addView(line);
        }
    }

    private void openSheet() {
        showWait();
        students.clear();
        absentIds.clear();
        txtClassName.setText(hostelName);
        boolean cached = bindCachedSheet();
        if (!cached) {
            bindLocalSheet();
        }
        if (!MainActivity.isNetworkAvailable(this)) {
            return;
        }
        String url = MainActivity.SERVER + "dormitory_attendance_sheet_api/" + schoolId() + "/" + hostelId
                + "?staff=" + staffId() + "&year=" + yearId();
        new HttpRequest(this).get(url, JSONObject.class, (object, code, status) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (object == null || object.has("error")) {
                if (!cached) bindLocalSheet();
                return;
            }
            students.clear();
            absentIds.clear();
            alreadyTaken = object.optBoolean("taken");
            JSONArray ids = object.optJSONArray("present");
            JSONArray arr = object.optJSONArray("students");
            DormRosterStore.saveOne(db, hostelId, hostelName, arr, ids, alreadyTaken, today());
            fillFromArrays(arr, ids, alreadyTaken);
            paintStudents();
        }));
    }

    private boolean bindCachedSheet() {
        JSONObject sheet = DormRosterStore.read(db, hostelId);
        if (sheet == null) return false;
        JSONArray studentsArr = sheet.optJSONArray("students");
        if (studentsArr == null || studentsArr.length() == 0) return false;
        String cachedName = sheet.optString("name", "");
        if (hostelName.length() == 0 && cachedName.length() > 0) {
            hostelName = cachedName;
            txtClassName.setText(hostelName);
        }
        boolean takenToday = sheet.optBoolean("taken") && today().equals(sheet.optString("date", ""));
        fillFromArrays(studentsArr, sheet.optJSONArray("present"), takenToday);
        if (students.isEmpty()) return false;
        paintStudents();
        return true;
    }

    private void fillFromArrays(JSONArray arr, JSONArray presentIds, boolean taken) {
        alreadyTaken = taken;
        HashSet<Integer> present = new HashSet<>();
        if (presentIds != null && taken) {
            for (int i = 0; i < presentIds.length(); i++) present.add(presentIds.optInt(i));
        }
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.optJSONObject(i);
            if (row == null) continue;
            StudentRow student = new StudentRow();
            student.id = row.optInt("id");
            student.name = row.optString("name");
            student.regno = row.optString("regno");
            student.classLabel = row.optString("class_label");
            if (student.id < 1) continue;
            students.add(student);
            if (taken && !present.contains(student.id)) absentIds.add(student.id);
        }
        applyPendingMarks();
    }

    private void applyPendingMarks() {
        try {
            Cursor c = db.rawQuery(
                    "select students_json, ifnull(roll_date,'') from tb_dormitory_attendance_pending where hostel_id=? and sync=1 order by created_at desc limit 1",
                    new String[]{String.valueOf(hostelId)});
            if (!c.moveToFirst()) {
                c.close();
                return;
            }
            String json = c.getString(0);
            String rollDate = c.getString(1);
            c.close();
            if (rollDate != null && rollDate.length() >= 8 && !today().equals(rollDate)) return;
            JSONArray present = new JSONArray(json == null ? "[]" : json);
            HashSet<Integer> ids = new HashSet<>();
            for (int i = 0; i < present.length(); i++) ids.add(present.optInt(i));
            absentIds.clear();
            for (StudentRow student : students) {
                if (!ids.contains(student.id)) absentIds.add(student.id);
            }
            alreadyTaken = true;
        } catch (Exception e) {
            Log.e(TAG, "pending marks", e);
        }
    }

    private void bindLocalSheet() {
        alreadyTaken = false;
        try {
            String year = String.valueOf(yearId());
            String hostel = String.valueOf(hostelId);
            Cursor c = db.rawQuery(
                    "SELECT s.id, s.name, s.regno, IFNULL(s.classe,'') FROM tb_hostel_allocations a " +
                            "JOIN tb_student s ON s.id = a.student_id " +
                            "WHERE a.hostel_id=? AND IFNULL(a.action,'assign')='assign' AND IFNULL(s.status,1) IN (1,2) " +
                            "AND (a.academic_year=? OR ?='0') ORDER BY s.name",
                    new String[]{hostel, year, year});
            if (!c.moveToFirst()) {
                c.close();
                c = db.rawQuery(
                        "SELECT s.id, s.name, s.regno, IFNULL(s.classe,'') FROM tb_hostel_allocations a " +
                                "JOIN tb_student s ON s.id = a.student_id " +
                                "WHERE a.hostel_id=? AND IFNULL(a.action,'assign')='assign' AND IFNULL(s.status,1) IN (1,2) " +
                                "AND a.academic_year=(SELECT MAX(academic_year) FROM tb_hostel_allocations WHERE hostel_id=?) ORDER BY s.name",
                        new String[]{hostel, hostel});
                while (c.moveToNext()) readAllocation(c);
            } else {
                do {
                    readAllocation(c);
                } while (c.moveToNext());
            }
            c.close();
        } catch (Exception e) {
            Log.e(TAG, "local sheet", e);
        }
        applyPendingMarks();
        paintStudents();
    }

    private void readAllocation(Cursor c) {
        StudentRow student = new StudentRow();
        student.id = c.getInt(0);
        student.name = c.getString(1);
        student.regno = c.getString(2);
        student.classLabel = c.getString(3);
        if (student.id < 1) return;
        for (StudentRow existing : students) {
            if (existing.id == student.id) return;
        }
        students.add(student);
    }

    /** Keep today's roll call on the phone so it can be opened again with no internet. */
    private void rememberLocalRoll(JSONArray presentIds) {
        JSONArray roster = new JSONArray();
        for (StudentRow student : students) {
            JSONObject row = new JSONObject();
            try {
                row.put("id", student.id);
                row.put("name", student.name == null ? "" : student.name);
                row.put("regno", student.regno == null ? "" : student.regno);
                row.put("class_label", student.classLabel == null ? "" : student.classLabel);
                roster.put(row);
            } catch (Exception ignored) {
            }
        }
        DormRosterStore.saveOne(db, hostelId, hostelName, roster, presentIds, true, today());
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void paintStudents() {
        rlvloading.setVisibility(View.GONE);
        lnlstudents.setVisibility(View.VISIBLE);
        lnlstudents.removeAllViews();
        if (txtDeviceHint != null) {
            if (!MainActivity.isNetworkAvailable(this)) {
                txtDeviceHint.setText(R.string.dorm_att_offline);
                txtDeviceHint.setVisibility(View.VISIBLE);
            } else if (alreadyTaken) {
                txtDeviceHint.setText(R.string.dorm_att_one_day);
                txtDeviceHint.setVisibility(View.VISIBLE);
            } else {
                txtDeviceHint.setVisibility(View.GONE);
            }
        }
        if (students.isEmpty()) {
            txtstatus.setText(R.string.dorm_att_empty);
            txtretry.setVisibility(View.VISIBLE);
            prb.setVisibility(View.GONE);
            imgerror.setVisibility(View.VISIBLE);
            rlvloading.setVisibility(View.VISIBLE);
            fab.hide();
            return;
        }
        for (StudentRow student : students) {
            View v = getLayoutInflater().inflate(R.layout.student_row, lnlstudents, false);
            TextView txtname = v.findViewById(R.id.txtname);
            TextView txtregno = v.findViewById(R.id.txtregno);
            ImageView imgabsent = v.findViewById(R.id.imgabsent);
            ImageView imgpresent = v.findViewById(R.id.imgpresent);
            ImageView imgdelete = v.findViewById(R.id.imgdelete);
            txtname.setText(student.name);
            String line = student.regno == null ? "" : student.regno;
            if (student.classLabel != null && student.classLabel.trim().length() > 0) {
                line = line + "  ·  " + student.classLabel.trim();
            }
            txtregno.setText(line);
            if (imgdelete != null) imgdelete.setVisibility(View.GONE);
            if (imgpresent != null) imgpresent.setVisibility(View.GONE);
            student.view = v;
            imgabsent.setOnClickListener(view -> toggleAbsent(student));
            applyLook(student);
            lnlstudents.addView(v);
        }
        applySearch();
        refreshCount();
        fab.show();
    }

    private void toggleAbsent(StudentRow student) {
        if (absentIds.contains(student.id)) absentIds.remove(student.id);
        else absentIds.add(student.id);
        applyLook(student);
        refreshCount();
        Toast.makeText(this, student.name + " " + getString(
                absentIds.contains(student.id) ? R.string.atte_is_absent : R.string.atte_is_present),
                Toast.LENGTH_SHORT).show();
    }

    private void applyLook(StudentRow student) {
        if (student.view == null) return;
        TextView txtname = student.view.findViewById(R.id.txtname);
        TextView txtRowStatus = student.view.findViewById(R.id.txtrowstatus);
        boolean absent = absentIds.contains(student.id);
        if (absent) {
            txtRowStatus.setVisibility(View.VISIBLE);
            txtRowStatus.setText(R.string.atte_absent_label);
            txtRowStatus.setTextColor(Color.parseColor("#DD6B55"));
            txtname.setTextColor(Color.parseColor("#DD6B55"));
            student.view.setBackgroundColor(Color.parseColor("#FFF1F2"));
        } else {
            txtRowStatus.setVisibility(View.GONE);
            txtname.setTextColor(Color.BLACK);
            student.view.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void refreshCount() {
        txtcount.setText(getString(R.string.atte_absent_count, absentIds.size(), students.size()));
    }

    private void applySearch() {
        String q = searchQuery == null ? "" : searchQuery.toLowerCase(Locale.US);
        for (StudentRow student : students) {
            if (student.view == null) continue;
            if (q.isEmpty()) {
                student.view.setVisibility(View.VISIBLE);
                continue;
            }
            String name = student.name == null ? "" : student.name.toLowerCase(Locale.US);
            String regno = student.regno == null ? "" : student.regno.toLowerCase(Locale.US);
            student.view.setVisibility(name.contains(q) || regno.contains(q) ? View.VISIBLE : View.GONE);
        }
    }

    private void save() {
        if (saving || hostelId < 1) return;
        saving = true;
        fab.setEnabled(false);
        JSONArray ids = new JSONArray();
        for (StudentRow student : students) {
            if (!absentIds.contains(student.id)) ids.put(student.id);
        }
        String pendingId = UUID.randomUUID().toString();
        rememberLocalRoll(ids);
        try {
            db.execSQL("delete from tb_dormitory_attendance_pending where hostel_id=? and sync=1", new Object[]{hostelId});
            db.execSQL(
                    "insert into tb_dormitory_attendance_pending (id,hostel_id,term,year,teacher,school_id,students_json,sync,created_at,roll_date) values (?,?,?,?,?,?,?,?,?,?)",
                    new Object[]{
                            pendingId, hostelId, preferences.getInt("soma_term", 0), yearId(),
                            staffId(), schoolId(), ids.toString(), 1, System.currentTimeMillis() / 1000L, today()
                    });
        } catch (Exception e) {
            saving = false;
            fab.setEnabled(true);
            Toast.makeText(this, getString(R.string.oops) + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (MainActivity.isNetworkAvailable(this)) {
            java.util.HashMap<String, String> map = new java.util.HashMap<>();
            map.put("school", String.valueOf(schoolId()));
            map.put("hostel", String.valueOf(hostelId));
            map.put("teacher", String.valueOf(staffId()));
            map.put("term", String.valueOf(preferences.getInt("soma_term", 0)));
            map.put("year", String.valueOf(yearId()));
            map.put("students", ids.toString());
            map.put("date", today());
            new HttpRequest(this).post(MainActivity.SERVER + "save_dormitory_attendance_api", map, (object, code, status) -> {
                if (object != null && object.has("success")) {
                    try {
                        db.execSQL("update tb_dormitory_attendance_pending set sync=0 where id=?", new Object[]{pendingId});
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        SyncReceiver.requestSync(this);
        SweetAlertDialog dialog = new SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE);
        dialog.setTitleText(getString(R.string.good_job));
        boolean online = MainActivity.isNetworkAvailable(this);
        dialog.setContentText(getString(online ? R.string.atte_save_msg : R.string.dorm_att_queued));
        dialog.setConfirmText(getString(R.string.ok));
        dialog.setConfirmClickListener(sweetAlertDialog -> {
            sweetAlertDialog.dismissWithAnimation();
            finish();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private static class Dorm {
        int id;
        String name;
    }

    private static class StudentRow {
        int id;
        String name;
        String regno;
        String classLabel;
        View view;
    }
}
