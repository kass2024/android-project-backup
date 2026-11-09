package com.qonics.somanet;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.model.Dual_data;
import com.qonics.utils.SyncStatusHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dormitory assignment from synced students and dormitories. No live server reads.
 */
public class HostelMenuActivity extends AppCompatActivity {
    private static final String FILTER_ALL = "all";
    private static final String FILTER_OPEN = "open";
    private static final String FILTER_ASSIGNED = "assigned";

    private SQLiteDatabase db;
    private SharedPreferences preferences;
    private SyncStatusHelper syncHelper;

    private Spinner spYear;
    private LinearLayout lnlKpi;
    private LinearLayout lnlDorms;
    private LinearLayout lnlClasses;
    private EditText edtSearch;
    private TextView txtResult;
    private TextView btnAll;
    private TextView btnOpen;
    private TextView btnAssigned;

    private final ArrayList<Dual_data> years = new ArrayList<>();
    private final ArrayList<Dorm> dorms = new ArrayList<>();
    private final ArrayList<StudentBed> students = new ArrayList<>();
    private final Map<Integer, Boolean> openClasses = new HashMap<>();

    private int selectedYearId;
    private String filter = FILTER_ALL;
    private boolean bindingYear;

    private static class Dorm {
        int id;
        String name;
        String gender;
        int maxBeds;
        int occupied;
    }

    private static class StudentBed {
        int id;
        String name;
        String regno;
        String sex;
        int classId;
        String classLabel;
        int hostelId;
    }

    private static class ClassGroup {
        int id;
        String label;
        int[] rank;
        ArrayList<StudentBed> students = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hostel_menu);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        db = openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        selectedYearId = preferences.getInt("soma_academic", 0);

        spYear = findViewById(R.id.sp_year);
        lnlKpi = findViewById(R.id.lnl_kpi);
        lnlDorms = findViewById(R.id.lnl_dorms);
        lnlClasses = findViewById(R.id.lnl_classes);
        edtSearch = findViewById(R.id.edt_search);
        txtResult = findViewById(R.id.txt_result);
        btnAll = findViewById(R.id.btn_filter_all);
        btnOpen = findViewById(R.id.btn_filter_open);
        btnAssigned = findViewById(R.id.btn_filter_assigned);
        TextView btnSync = findViewById(R.id.btn_sync_now);
        syncHelper = new SyncStatusHelper(this, btnSync);
        btnSync.setOnClickListener(v -> syncHelper.triggerSync());

        btnAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        btnOpen.setOnClickListener(v -> setFilter(FILTER_OPEN));
        btnAssigned.setOnClickListener(v -> setFilter(FILTER_ASSIGNED));
        findViewById(R.id.btn_expand).setOnClickListener(v -> setAllOpen(true));
        findViewById(R.id.btn_fold).setOnClickListener(v -> setAllOpen(false));
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { renderClasses(); }
        });

        loadYears();
        spYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bindingYear) return;
                if (position >= 0 && position < years.size()) {
                    selectedYearId = years.get(position).getId();
                    reload();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        reload();
    }

    private void loadYears() {
        years.clear();
        Cursor c = db.rawQuery("SELECT id, title FROM tb_academic_year ORDER BY title DESC", null);
        int selectPos = 0;
        int i = 0;
        while (c.moveToNext()) {
            int id = c.getInt(0);
            String title = c.getString(1);
            years.add(new Dual_data(id, title != null ? title : ("Year " + id)));
            if (id == selectedYearId) selectPos = i;
            i++;
        }
        c.close();
        if (years.isEmpty()) {
            years.add(new Dual_data(selectedYearId, getString(R.string.select_year)));
        }
        bindingYear = true;
        spYear.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years));
        spYear.setSelection(Math.min(selectPos, years.size() - 1));
        selectedYearId = years.get(spYear.getSelectedItemPosition()).getId();
        bindingYear = false;
    }

    private void reload() {
        loadDorms();
        loadStudents();
        renderKpis();
        renderDormChips();
        renderClasses();
    }

    private void loadDorms() {
        dorms.clear();
        Cursor c = db.rawQuery(
                "SELECT id, name, max_beds, gender FROM tb_hostels WHERE active=1 ORDER BY sort_order ASC, name ASC",
                null);
        while (c.moveToNext()) {
            Dorm dorm = new Dorm();
            dorm.id = c.getInt(0);
            dorm.name = c.getString(1);
            dorm.maxBeds = c.getInt(2);
            dorm.gender = normGender(c.getString(3));
            dorm.occupied = countOccupied(dorm.id);
            dorms.add(dorm);
        }
        c.close();
    }

    private int countOccupied(int hostelId) {
        Cursor c = db.rawQuery(
                "SELECT COUNT(DISTINCT student_id) FROM tb_hostel_allocations WHERE hostel_id=? AND academic_year=? AND action='assign'",
                new String[]{String.valueOf(hostelId), String.valueOf(selectedYearId)});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    private void loadStudents() {
        students.clear();
        Cursor c = db.rawQuery(
                "SELECT s.id, s.name, s.regno, s.sex, s.class_id, IFNULL(s.classe,'') " +
                        "FROM tb_student s " +
                        "WHERE IFNULL(s.studying_mode,1)=0 " +
                        "AND IFNULL(s.status,1) IN (1,2) " +
                        "AND IFNULL(s.classe,'') NOT LIKE '%Holiday%' " +
                        "ORDER BY s.classe ASC, s.name ASC",
                null);
        while (c.moveToNext()) {
            StudentBed st = new StudentBed();
            st.id = c.getInt(0);
            st.name = c.getString(1) != null ? c.getString(1) : "Student";
            st.regno = c.getString(2) != null ? c.getString(2) : "";
            st.sex = normSex(c.getString(3));
            st.classId = c.getInt(4);
            st.classLabel = c.getString(5);
            if (st.classLabel == null || st.classLabel.trim().isEmpty()) {
                st.classLabel = "Class";
            }
            st.hostelId = currentHostel(st.id);
            students.add(st);
        }
        c.close();
    }

    private int currentHostel(int studentId) {
        Cursor c = db.rawQuery(
                "SELECT hostel_id FROM tb_hostel_allocations WHERE student_id=? AND academic_year=? AND action='assign' ORDER BY updated_at DESC LIMIT 1",
                new String[]{String.valueOf(studentId), String.valueOf(selectedYearId)});
        int id = 0;
        if (c.moveToFirst()) id = c.getInt(0);
        c.close();
        return id;
    }

    private void renderKpis() {
        lnlKpi.removeAllViews();
        int assigned = 0;
        for (StudentBed st : students) if (st.hostelId > 0) assigned++;
        int beds = 0;
        int used = 0;
        for (Dorm d : dorms) {
            beds += Math.max(0, d.maxBeds);
            used += d.occupied;
        }
        int free = Math.max(0, beds - used);
        addKpi(getString(R.string.drm_kpi_boarding), String.valueOf(students.size()), getString(R.string.drm_this_year), "#0EA5E9");
        addKpi(getString(R.string.drm_kpi_assigned), String.valueOf(assigned), getString(R.string.drm_have_bed), "#05bc42");
        addKpi(getString(R.string.drm_kpi_open), String.valueOf(Math.max(0, students.size() - assigned)), getString(R.string.drm_need_bed), "#ea950c");
        addKpi(getString(R.string.drm_kpi_dorms), String.valueOf(dorms.size()), getString(R.string.drm_from_settings), "#0284C7");
        addKpi(getString(R.string.drm_kpi_used), String.valueOf(used), getString(R.string.drm_of_beds, beds), "#0F172A");
        addKpi(getString(R.string.drm_kpi_free), String.valueOf(free), getString(R.string.drm_across), "#059669");
    }

    private void addKpi(String label, String value, String sub, String color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.menu_tile_bg);
        int pad = dp(10);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(132), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        TextView lbl = small(label, "#64748B");
        TextView val = new TextView(this);
        val.setText(value);
        val.setTextSize(20);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setTextColor(Color.parseColor(color));
        TextView note = small(sub, "#64748B");
        card.addView(lbl);
        card.addView(val);
        card.addView(note);
        lnlKpi.addView(card);
    }

    private void renderDormChips() {
        lnlDorms.removeAllViews();
        if (dorms.isEmpty()) {
            TextView empty = small(getString(R.string.drm_no_dorms), "#64748B");
            empty.setPadding(dp(8), dp(4), dp(8), dp(8));
            lnlDorms.addView(empty);
            return;
        }
        for (Dorm dorm : dorms) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setBackgroundResource(R.drawable.menu_tile_bg);
            chip.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(168), LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            lp.bottomMargin = dp(8);
            chip.setLayoutParams(lp);
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(dorm.name != null ? dorm.name : "Dormitory");
            name.setTextColor(getResources().getColor(R.color.textPrimary));
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView gender = new TextView(this);
            boolean female = "F".equals(dorm.gender);
            gender.setText(female ? R.string.gender_female : R.string.gender_male);
            gender.setTextSize(11);
            gender.setTextColor(Color.WHITE);
            gender.setBackgroundColor(Color.parseColor(female ? "#DB2777" : "#0284C7"));
            gender.setPadding(dp(6), dp(2), dp(6), dp(2));
            top.addView(name);
            top.addView(gender);
            int max = Math.max(1, dorm.maxBeds);
            int pct = (int) Math.min(100, Math.round((dorm.occupied * 100.0) / max));
            TextView bar = new TextView(this);
            bar.setBackgroundColor(Color.parseColor("#0EA5E9"));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
            barLp.topMargin = dp(8);
            barLp.width = (int) (dp(148) * (pct / 100f));
            if (barLp.width < dp(4) && dorm.occupied > 0) barLp.width = dp(4);
            bar.setLayoutParams(barLp);
            TextView count = small(dorm.occupied + " / " + dorm.maxBeds + " beds", "#64748B");
            count.setPadding(0, dp(4), 0, 0);
            chip.addView(top);
            chip.addView(bar);
            chip.addView(count);
            lnlDorms.addView(chip);
        }
    }

    private void renderClasses() {
        lnlClasses.removeAllViews();
        if (students.isEmpty()) {
            lnlClasses.addView(small(getString(R.string.drm_no_boarding), "#64748B"));
            txtResult.setVisibility(View.GONE);
            return;
        }
        String q = edtSearch.getText() == null ? "" : edtSearch.getText().toString().trim().toLowerCase(Locale.US);
        ArrayList<ClassGroup> groups = groupStudents();
        int shownStudents = 0;
        int shownClasses = 0;
        for (ClassGroup group : groups) {
            boolean classHit = q.length() > 0 && group.label.toLowerCase(Locale.US).contains(q);
            ArrayList<StudentBed> visible = new ArrayList<>();
            for (StudentBed st : group.students) {
                String hay = (st.name + " " + st.regno + " " + group.label).toLowerCase(Locale.US);
                boolean textHit = q.length() == 0 || classHit || hay.contains(q);
                boolean state = FILTER_ALL.equals(filter)
                        || (FILTER_ASSIGNED.equals(filter) && st.hostelId > 0)
                        || (FILTER_OPEN.equals(filter) && st.hostelId <= 0);
                if (textHit && state) visible.add(st);
            }
            if (visible.isEmpty()) continue;
            shownClasses++;
            shownStudents += visible.size();
            if (q.length() > 0) openClasses.put(group.id, true);
            boolean open = openClasses.containsKey(group.id) && openClasses.get(group.id);
            lnlClasses.addView(buildClass(group, visible, open));
        }
        if (q.length() > 0 || !FILTER_ALL.equals(filter)) {
            txtResult.setVisibility(View.VISIBLE);
            txtResult.setText(getString(R.string.drm_result_fmt, shownStudents, shownClasses));
        } else {
            txtResult.setVisibility(View.GONE);
        }
    }

    private ArrayList<ClassGroup> groupStudents() {
        HashMap<Integer, ClassGroup> map = new HashMap<>();
        for (StudentBed st : students) {
            ClassGroup group = map.get(st.classId);
            if (group == null) {
                group = new ClassGroup();
                group.id = st.classId;
                group.label = st.classLabel;
                group.rank = classRank(st.classLabel);
                map.put(st.classId, group);
            }
            group.students.add(st);
        }
        ArrayList<ClassGroup> groups = new ArrayList<>(map.values());
        Collections.sort(groups, new Comparator<ClassGroup>() {
            @Override
            public int compare(ClassGroup a, ClassGroup b) {
                for (int i = 0; i < a.rank.length && i < b.rank.length; i++) {
                    int c = Integer.compare(a.rank[i], b.rank[i]);
                    if (c != 0) return c;
                }
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return groups;
    }

    private View buildClass(final ClassGroup group, ArrayList<StudentBed> visible, boolean open) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.menu_tile_bg);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        int assigned = 0;
        for (StudentBed st : group.students) if (st.hostelId > 0) assigned++;
        TextView head = new TextView(this);
        head.setText(group.label + "    " + getString(R.string.drm_class_meta, group.students.size(), assigned));
        head.setTextColor(getResources().getColor(R.color.textPrimary));
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextSize(15);
        head.setPadding(0, dp(6), 0, dp(6));
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        head.setOnClickListener(v -> {
            boolean next = body.getVisibility() != View.VISIBLE;
            body.setVisibility(next ? View.VISIBLE : View.GONE);
            openClasses.put(group.id, next);
        });
        card.addView(head);
        for (StudentBed st : visible) {
            body.addView(buildStudent(st));
        }
        card.addView(body);
        return card;
    }

    private View buildStudent(final StudentBed st) {
        View row = getLayoutInflater().inflate(R.layout.item_dorm_student, lnlClasses, false);
        TextView avatar = row.findViewById(R.id.txt_avatar);
        TextView name = row.findViewById(R.id.txt_name);
        TextView regno = row.findViewById(R.id.txt_regno);
        TextView sex = row.findViewById(R.id.txt_sex);
        Spinner sp = row.findViewById(R.id.sp_dorm);
        String letter = st.name.length() > 0 ? st.name.substring(0, 1).toUpperCase(Locale.US) : "S";
        avatar.setText(letter);
        avatar.setBackgroundColor(Color.parseColor("F".equals(st.sex) ? "#DB2777" : "#0284C7"));
        name.setText(st.name);
        regno.setText(st.regno);
        if ("F".equals(st.sex) || "M".equals(st.sex)) {
            sex.setText("F".equals(st.sex) ? R.string.gender_female : R.string.gender_male);
            sex.setTextColor(Color.WHITE);
            sex.setBackgroundColor(Color.parseColor("F".equals(st.sex) ? "#DB2777" : "#0284C7"));
        } else {
            sex.setVisibility(View.GONE);
        }

        final ArrayList<DormOption> options = optionsFor(st);
        ArrayAdapter<DormOption> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        sp.setAdapter(adapter);
        int select = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id == st.hostelId) select = i;
        }
        sp.setTag(Boolean.TRUE);
        sp.setSelection(select);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (Boolean.TRUE.equals(sp.getTag())) {
                    sp.setTag(Boolean.FALSE);
                    return;
                }
                if (position < 0 || position >= options.size()) return;
                int next = options.get(position).id;
                if (next == st.hostelId) return;
                String error = assign(st, next);
                if (error != null) {
                    Toast.makeText(HostelMenuActivity.this, error, Toast.LENGTH_LONG).show();
                    int back = 0;
                    for (int i = 0; i < options.size(); i++) {
                        if (options.get(i).id == st.hostelId) back = i;
                    }
                    sp.setSelection(back);
                    return;
                }
                Toast.makeText(HostelMenuActivity.this, R.string.drm_saved, Toast.LENGTH_SHORT).show();
                sp.post(() -> reload());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        return row;
    }

    private ArrayList<DormOption> optionsFor(StudentBed st) {
        ArrayList<DormOption> options = new ArrayList<>();
        options.add(new DormOption(0, getString(R.string.drm_unassigned_option)));
        for (Dorm dorm : dorms) {
            if (st.sex.length() > 0 && !st.sex.equals(dorm.gender)) continue;
            int free = Math.max(0, dorm.maxBeds - dorm.occupied);
            String gender = "F".equals(dorm.gender) ? getString(R.string.gender_female) : getString(R.string.gender_male);
            options.add(new DormOption(dorm.id, getString(R.string.drm_option_free, dorm.name, gender, free)));
        }
        return options;
    }

    private String assign(StudentBed st, int hostelId) {
        if (hostelId > 0) {
            Dorm target = null;
            for (Dorm dorm : dorms) if (dorm.id == hostelId) target = dorm;
            if (target == null) return getString(R.string.drm_no_dorms);
            boolean movingIn = st.hostelId != hostelId;
            if (movingIn && target.occupied >= target.maxBeds) {
                return getString(R.string.drm_full);
            }
        }
        int now = (int) (System.currentTimeMillis() / 1000);
        int staff = preferences.getInt("soma_id", 0);
        db.execSQL("DELETE FROM tb_hostel_allocations WHERE student_id=? AND academic_year=?",
                new Object[]{st.id, selectedYearId});
        String action = hostelId > 0 ? "assign" : "unassign";
        db.execSQL("INSERT INTO tb_hostel_allocations (id,server_id,hostel_id,student_id,academic_year,allocated_by,action,sync,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                new Object[]{UUID.randomUUID().toString(), 0, hostelId, st.id, selectedYearId, staff, action, 1, now});
        st.hostelId = hostelId;
        triggerSync();
        return null;
    }

    private void setFilter(String next) {
        filter = next;
        styleFilter(btnAll, FILTER_ALL.equals(filter));
        styleFilter(btnOpen, FILTER_OPEN.equals(filter));
        styleFilter(btnAssigned, FILTER_ASSIGNED.equals(filter));
        renderClasses();
    }

    private void styleFilter(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.mode_tab_active_bg : R.drawable.mode_tab_bg);
        tab.setTextColor(active ? Color.WHITE : getResources().getColor(R.color.textPrimary));
    }

    private void setAllOpen(boolean open) {
        for (StudentBed st : students) openClasses.put(st.classId, open);
        renderClasses();
    }

    private void triggerSync() {
        Intent syncIntent = new Intent("sync.qonics.com.somanet");
        syncIntent.setPackage(getPackageName());
        sendBroadcast(syncIntent);
    }

    private TextView small(String text, String color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(Color.parseColor(color));
        return tv;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String normSex(String sex) {
        if (sex == null) return "";
        String g = sex.trim().toUpperCase(Locale.US);
        if (g.startsWith("F") || g.equals("GIRL") || g.equals("WOMAN")) return "F";
        if (g.startsWith("M") || g.equals("BOY") || g.equals("MAN")) return "M";
        return "";
    }

    private String normGender(String gender) {
        if (gender == null) return "M";
        String g = gender.trim().toUpperCase(Locale.US);
        return g.startsWith("F") ? "F" : "M";
    }

    private int[] classRank(String label) {
        String l = label == null ? "" : label.toLowerCase(Locale.US).trim();
        if (l.contains("baby")) return new int[]{0, 1};
        if (l.contains("middle")) return new int[]{0, 2};
        if (l.contains("top")) return new int[]{0, 3};
        if (l.contains("nursery") || l.matches(".*\\bn\\s*[1-3]\\b.*")) return new int[]{0, 4};
        if (l.matches(".*level\\s*(\\d+).*")) {
            int n = digitsAfter(l, "level");
            return new int[]{1, n};
        }
        if (l.matches("^p\\s*\\d+.*")) return new int[]{2, digitsAfter(l, "p")};
        if (l.matches("^s\\s*\\d+.*")) return new int[]{3, digitsAfter(l, "s")};
        return new int[]{9, 0};
    }

    private int digitsAfter(String text, String token) {
        int i = text.indexOf(token);
        if (i < 0) return 0;
        i += token.length();
        int n = 0;
        boolean found = false;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') {
                n = n * 10 + (ch - '0');
                found = true;
            } else if (found) {
                break;
            }
            i++;
        }
        return n;
    }

    private static class DormOption {
        final int id;
        final String label;
        DormOption(int id, String label) {
            this.id = id;
            this.label = label;
        }
        @Override public String toString() { return label; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (syncHelper != null) syncHelper.onResume(false);
        reload();
    }

    @Override
    protected void onPause() {
        if (syncHelper != null) syncHelper.onPause();
        super.onPause();
    }
}
