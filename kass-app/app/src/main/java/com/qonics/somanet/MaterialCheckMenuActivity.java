package com.qonics.somanet;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.model.Dual_data;
import com.qonics.model.StudentModel;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.SyncStatusHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MaterialCheckMenuActivity extends AppCompatActivity {
    private static final String TAG = "MaterialCheckMenuActivity";
    private static final int MODE_CLASS = 0;
    private static final int MODE_SEARCH = 1;
    private static final int MODE_CARD = 2;

    private SQLiteDatabase db;
    private SharedPreferences preferences;
    private SyncStatusHelper syncHelper;

    private Spinner spYear;
    private Spinner spClass;
    private EditText etSearch;
    private EditText etClassSearch;
    private EditText etRosterSearch;
    private ListView lvRoster;
    private TextView txtRosterHeader;
    private TextView txtRosterSummary;
    private View panelClass;
    private View panelSearch;
    private View panelCard;
    private TextView tabClass;
    private TextView tabSearch;
    private TextView tabCard;

    private final ArrayList<Dual_data> years = new ArrayList<>();
    private final ArrayList<Dual_data> allClasses = new ArrayList<>();
    private final ArrayList<Dual_data> classes = new ArrayList<>();
    private final ArrayList<RosterItem> classRoster = new ArrayList<>();
    private final ArrayList<RosterItem> roster = new ArrayList<>();
    private RosterAdapter rosterAdapter;

    private int mode = MODE_CLASS;
    private int selectedYearId;
    private int openClassId;
    private String openClassTitle = "";
    private int classLoadSeq = 0;
    private int searchSeq = 0;
    private int lastAutoOpenedId = 0;
    private String lastAutoOpenedQuery = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    private static class RosterItem {
        StudentModel student;
        String status; // checked | incomplete | missing
        String checkerLine;
        String liveMaterialsJson;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_material_check_menu);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        db = openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        selectedYearId = preferences.getInt("soma_academic", 0);

        spYear = findViewById(R.id.sp_year);
        spClass = findViewById(R.id.sp_class);
        etSearch = findViewById(R.id.et_search);
        etClassSearch = findViewById(R.id.et_class_search);
        etRosterSearch = findViewById(R.id.et_roster_search);
        lvRoster = findViewById(R.id.lv_roster);
        txtRosterHeader = findViewById(R.id.txt_roster_header);
        txtRosterSummary = findViewById(R.id.txt_roster_summary);
        panelClass = findViewById(R.id.panel_class);
        panelSearch = findViewById(R.id.panel_search);
        panelCard = findViewById(R.id.panel_card);
        tabClass = findViewById(R.id.tab_class);
        tabSearch = findViewById(R.id.tab_search);
        tabCard = findViewById(R.id.tab_card);
        Button btnOpenClass = findViewById(R.id.btn_open_class);
        Button btnSearch = findViewById(R.id.btn_search);
        Button btnOpenNfc = findViewById(R.id.btn_open_nfc);
        TextView btnSync = findViewById(R.id.btn_sync_now);

        syncHelper = new SyncStatusHelper(this, btnSync);
        btnSync.setOnClickListener(v -> syncHelper.triggerSync());

        rosterAdapter = new RosterAdapter();
        lvRoster.setAdapter(rosterAdapter);
        lvRoster.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= roster.size()) return;
            openMaterialCheck(roster.get(position));
        });

        tabClass.setOnClickListener(v -> setMode(MODE_CLASS));
        tabSearch.setOnClickListener(v -> setMode(MODE_SEARCH));
        tabCard.setOnClickListener(v -> setMode(MODE_CARD));

        btnOpenClass.setOnClickListener(v -> openSelectedClass());
        btnSearch.setOnClickListener(v -> runSearch());
        btnOpenNfc.setOnClickListener(v -> openCardScanner());

        loadYears();
        loadClasses();
        setMode(MODE_CLASS);
        etClassSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyClassFilter(s != null ? s.toString() : "");
            }
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleLiveSearch();
            }
        });
        etRosterSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mode == MODE_CLASS) {
                    applyRosterFilter();
                }
            }
        });

        spYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < years.size()) {
                    selectedYearId = years.get(position).getId();
                    if (openClassId > 0 && mode == MODE_CLASS) {
                        loadClassRoster(openClassId, openClassTitle);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setMode(int newMode) {
        mode = newMode;
        styleTab(tabClass, mode == MODE_CLASS);
        styleTab(tabSearch, mode == MODE_SEARCH);
        styleTab(tabCard, mode == MODE_CARD);
        panelClass.setVisibility(mode == MODE_CLASS ? View.VISIBLE : View.GONE);
        panelSearch.setVisibility(mode == MODE_SEARCH ? View.VISIBLE : View.GONE);
        panelCard.setVisibility(mode == MODE_CARD ? View.VISIBLE : View.GONE);
        syncRosterSearchVisibility();

        if (mode == MODE_CARD) {
            hideRoster();
        } else if (mode == MODE_CLASS && openClassId > 0) {
            loadClassRoster(openClassId, openClassTitle);
        } else if (mode == MODE_SEARCH) {
            scheduleLiveSearch();
        } else {
            searchHandler.removeCallbacksAndMessages(null);
            if (roster.isEmpty()) {
                hideRoster();
            }
        }
    }

    private void styleTab(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.mode_tab_active_bg : R.drawable.mode_tab_bg);
        tab.setTextColor(active ? Color.WHITE : getResources().getColor(R.color.textPrimary));
    }

    private void loadYears() {
        years.clear();
        Cursor c = db.rawQuery("SELECT id, title FROM tb_academic_year ORDER BY title DESC", null);
        int selectPos = 0;
        int i = 0;
        while (c.moveToNext()) {
            int id = c.getInt(c.getColumnIndex("id"));
            String title = c.getString(c.getColumnIndex("title"));
            years.add(new Dual_data(id, title != null ? title : ("Year " + id)));
            if (id == selectedYearId) selectPos = i;
            i++;
        }
        c.close();
        if (years.isEmpty()) {
            years.add(new Dual_data(selectedYearId, getString(R.string.select_year)));
        }
        spYear.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years));
        spYear.setSelection(Math.min(selectPos, years.size() - 1));
        selectedYearId = years.get(spYear.getSelectedItemPosition()).getId();
    }

    private void loadClasses() {
        allClasses.clear();
        Cursor c = db.rawQuery(
                "SELECT DISTINCT class_id, classe FROM tb_student " +
                        "WHERE IFNULL(classe,'') <> '' ORDER BY classe ASC",
                null);
        while (c.moveToNext()) {
            int id = c.getInt(c.getColumnIndex("class_id"));
            String label = c.getString(c.getColumnIndex("classe"));
            addClassChoice(allClasses, id, label);
        }
        c.close();
        c = db.rawQuery("SELECT id, title FROM tb_class ORDER BY title ASC", null);
        while (c.moveToNext()) {
            int id = c.getInt(c.getColumnIndex("id"));
            String label = c.getString(c.getColumnIndex("title"));
            addClassChoice(allClasses, id, label);
        }
        c.close();
        applyClassFilter(etClassSearch != null && etClassSearch.getText() != null ? etClassSearch.getText().toString() : "");
        if (MainActivity.isNetworkAvailable(this)) {
            loadClassesFromServer();
        }
    }

    private void loadClassesFromServer() {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId < 1) {
            return;
        }
        String url = MainActivity.SERVER + "get_material_classes"
                + "?school_id=" + schoolId;
        new HttpRequest(this).get(url, JSONObject.class, (object, statusCode, status) -> {
            JSONArray liveClasses = object != null && object.optBoolean("success", false)
                    ? object.optJSONArray("classes") : null;
            if (liveClasses == null) {
                Log.e(TAG, "Live class list failed: " + status);
                return;
            }
            bindLiveClasses(liveClasses);
        });
    }

    private void bindLiveClasses(JSONArray liveClasses) {
        int selectedId = selectedClassId();
        ArrayList<Dual_data> merged = new ArrayList<>();

        for (int i = 0; i < liveClasses.length(); i++) {
            JSONObject item = liveClasses.optJSONObject(i);
            if (item == null) continue;
            int id = item.optInt("id", 0);
            String title = item.optString("label", "").trim();
            if (title.isEmpty()) {
                title = buildClassLabel(
                        item.optString("level_name", ""),
                        item.optString("dept_code", ""),
                        item.optString("title", "")
                );
            }
            addClassChoice(merged, id, title);
        }

        if (merged.isEmpty()) {
            return;
        }
        allClasses.clear();
        allClasses.addAll(merged);
        applyClassFilter(etClassSearch != null && etClassSearch.getText() != null ? etClassSearch.getText().toString() : "", selectedId);
    }

    private void addClassChoice(ArrayList<Dual_data> target, int id, String rawLabel) {
        String label = rawLabel == null ? "" : rawLabel.trim().replaceAll("\\s+", " ");
        if (id < 1 || label.isEmpty() || looksLikeHoliday(label)) {
            return;
        }
        for (Dual_data item : target) {
            if (item != null && item.getId() == id) {
                return;
            }
        }
        target.add(new Dual_data(id, label, label.toLowerCase()));
    }

    private String buildClassLabel(String levelName, String deptCode, String title) {
        return (String.valueOf(levelName == null ? "" : levelName).trim() + " "
                + String.valueOf(deptCode == null ? "" : deptCode).trim() + " "
                + String.valueOf(title == null ? "" : title).trim()).trim().replaceAll("\\s+", " ");
    }

    private boolean looksLikeHoliday(String label) {
        return label != null && label.toLowerCase().contains("holiday");
    }

    private int selectedClassId() {
        int selectedPos = spClass.getSelectedItemPosition();
        if (selectedPos > 0 && selectedPos < classes.size()) {
            return classes.get(selectedPos).getId();
        }
        return openClassId > 0 ? openClassId : 0;
    }

    private void applyClassFilter(String query) {
        applyClassFilter(query, selectedClassId());
    }

    private void applyClassFilter(String query, int selectedId) {
        String term = query == null ? "" : query.trim().toLowerCase();
        classes.clear();
        classes.add(new Dual_data(0, getString(R.string.select_class)));
        for (Dual_data item : allClasses) {
            if (item == null) continue;
            String hay = item.getText2() != null ? item.getText2() : item.getText1().toLowerCase();
            if (!term.isEmpty() && !hay.contains(term)) {
                continue;
            }
            classes.add(item);
        }
        spClass.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, classes));
        int restorePos = 0;
        for (int i = 1; i < classes.size(); i++) {
            if (classes.get(i).getId() == selectedId) {
                restorePos = i;
                break;
            }
        }
        spClass.setSelection(restorePos);
    }

    private void openSelectedClass() {
        int pos = spClass.getSelectedItemPosition();
        if (pos <= 0 || pos >= classes.size()) {
            Toast.makeText(this, R.string.select_class, Toast.LENGTH_SHORT).show();
            return;
        }
        Dual_data cls = classes.get(pos);
        openClassId = cls.getId();
        openClassTitle = cls.getText1();
        loadClassRoster(openClassId, openClassTitle);
    }

    private void loadClassRoster(int classId, String classTitle) {
        if (MainActivity.isNetworkAvailable(this)) {
            showClassLoading(classTitle);
            loadClassRosterFromLocal(classId, classTitle, false);
            int seq = ++classLoadSeq;
            loadClassRosterFromServer(classId, classTitle, selectedYearId, seq);
            return;
        }
        loadClassRosterFromLocal(classId, classTitle, true);
    }

    private void loadClassRosterFromLocal(int classId, String classTitle, boolean showNoMaterialsMessage) {
        classRoster.clear();
        Cursor c = db.rawQuery(
                "SELECT id, name, regno, class_id, classe, sex, photo, card, phone FROM tb_student " +
                        "WHERE class_id=? ORDER BY name ASC",
                new String[]{String.valueOf(classId)});
        ArrayList<StudentModel> students = new ArrayList<>();
        while (c.moveToNext()) {
            StudentModel st = studentFromCursor(c);
            if (st.getClassId() < 1 || looksLikeHoliday(st.getClasse())) {
                continue;
            }
            students.add(st);
        }
        c.close();

        Set<Integer> requiredMaterials = loadRequiredMaterialIds(classId, selectedYearId);
        if (requiredMaterials.isEmpty()) {
            if (showNoMaterialsMessage) {
                showNoMaterialsForClass(classTitle);
            }
            return;
        }
        Map<Integer, Map<Integer, double[]>> checksByStudent = loadChecksForClass(classId, selectedYearId);

        int checked = 0, incomplete = 0, missing = 0;
        for (StudentModel st : students) {
            RosterItem item = new RosterItem();
            item.student = st;
            item.status = computeStatus(requiredMaterials, checksByStudent.get(st.getId()));
            item.checkerLine = localCheckerLine(st.getId());
            if ("checked".equals(item.status)) checked++;
            else if ("incomplete".equals(item.status)) incomplete++;
            else missing++;
            classRoster.add(item);
        }

        txtRosterHeader.setVisibility(View.VISIBLE);
        txtRosterSummary.setVisibility(View.VISIBLE);
        txtRosterHeader.setText(getString(R.string.roster_class_fmt, classTitle != null ? classTitle : ""));
        txtRosterSummary.setText(getString(R.string.roster_summary_fmt, roster.size(), checked, incomplete, missing));
        syncRosterSearchVisibility();
        applyRosterFilter();
    }

    private void loadClassRosterFromServer(int classId, String classTitle, int yearId, int seq) {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        String url = MainActivity.SERVER + "get_material_class_overview/" + schoolId + "/" + classId
                + "?year=" + yearId;
        new HttpRequest(this).get(url, JSONObject.class, (object, statusCode, status) -> {
            if (object == null || !object.optBoolean("success", false)) {
                Log.e(TAG, "Live material class overview failed: " + status);
                if (classRoster.isEmpty()) {
                    showClassLoadFailed(classTitle);
                }
                return;
            }
            if (seq != classLoadSeq || classId != openClassId || yearId != selectedYearId) {
                return;
            }
            JSONObject overview = object.optJSONObject("overview");
            if (overview == null) {
                return;
            }
            JSONArray students = overview.optJSONArray("students");
            JSONObject kpi = overview.optJSONObject("class_kpi");
            bindLiveClassRoster(students, kpi, overview.optInt("material_count", 0), classTitle);
        });
    }

    private void bindLiveClassRoster(JSONArray students, JSONObject kpi, int materialCount, String classTitle) {
        if (materialCount <= 0) {
            showNoMaterialsForClass(classTitle);
            return;
        }
        classRoster.clear();
        int checked = 0, incomplete = 0, missing = 0;
        if (students != null) {
            for (int i = 0; i < students.length(); i++) {
                JSONObject item = students.optJSONObject(i);
                if (item == null) continue;
                StudentModel st = new StudentModel(
                        item.optInt("id", 0),
                        item.optString("name", ""),
                        item.optString("regno", ""),
                        openClassId,
                        classTitle != null ? classTitle : "",
                        "",
                        "",
                        preferences.getInt("soma_term_number", 0),
                        "",
                        ""
                );
                RosterItem row = new RosterItem();
                row.student = st;
                row.status = mapOverallToRosterStatus(item.optString("overall", ""));
                row.checkerLine = checkerLineFromJson(item);
                JSONArray items = item.optJSONArray("items");
                row.liveMaterialsJson = items != null ? items.toString() : null;
                if ("checked".equals(row.status)) checked++;
                else if ("incomplete".equals(row.status)) incomplete++;
                else missing++;
                classRoster.add(row);
            }
        }
        txtRosterHeader.setVisibility(View.VISIBLE);
        txtRosterSummary.setVisibility(View.VISIBLE);
        txtRosterHeader.setText(getString(R.string.roster_class_fmt, classTitle != null ? classTitle : ""));
        if (kpi != null) {
            txtRosterSummary.setText(getString(
                    R.string.roster_summary_fmt,
                    kpi.optInt("total", roster.size()),
                    kpi.optInt("complete", checked),
                    kpi.optInt("partial", incomplete),
                    kpi.optInt("missing", missing) + kpi.optInt("unchecked", 0)
            ));
        } else {
            txtRosterSummary.setText(getString(R.string.roster_summary_fmt, classRoster.size(), checked, incomplete, missing));
        }
        syncRosterSearchVisibility();
        applyRosterFilter();
    }

    private void showNoMaterialsForClass(String classTitle) {
        classRoster.clear();
        roster.clear();
        rosterAdapter.notifyDataSetChanged();
        txtRosterHeader.setVisibility(View.VISIBLE);
        txtRosterSummary.setVisibility(View.VISIBLE);
        txtRosterHeader.setText(getString(R.string.roster_class_fmt, classTitle != null ? classTitle : ""));
        txtRosterSummary.setText(R.string.no_required_materials);
        syncRosterSearchVisibility();
        Toast.makeText(this, R.string.no_required_materials, Toast.LENGTH_SHORT).show();
    }

    private void showClassLoading(String classTitle) {
        roster.clear();
        rosterAdapter.notifyDataSetChanged();
        txtRosterHeader.setVisibility(View.VISIBLE);
        txtRosterSummary.setVisibility(View.VISIBLE);
        txtRosterHeader.setText(getString(R.string.roster_class_fmt, classTitle != null ? classTitle : ""));
        txtRosterSummary.setText(R.string.loading_live_class_data);
        syncRosterSearchVisibility();
    }

    private void showClassLoadFailed(String classTitle) {
        roster.clear();
        rosterAdapter.notifyDataSetChanged();
        txtRosterHeader.setVisibility(View.VISIBLE);
        txtRosterSummary.setVisibility(View.VISIBLE);
        txtRosterHeader.setText(getString(R.string.roster_class_fmt, classTitle != null ? classTitle : ""));
        txtRosterSummary.setText(R.string.live_class_load_failed);
        syncRosterSearchVisibility();
        Toast.makeText(this, R.string.live_class_load_failed, Toast.LENGTH_SHORT).show();
    }

    private void runSearch() {
        searchHandler.removeCallbacksAndMessages(null);
        lastAutoOpenedId = 0;
        lastAutoOpenedQuery = "";
        final int seq = ++searchSeq;
        String q = currentSearchQuery();
        if (q.length() < 2) {
            return;
        }
        if (MainActivity.isNetworkAvailable(this)) {
            showSearchStatus(R.string.loading_live_class_data);
            runSearchFromServer(seq);
        } else {
            runSearchFromLocal(seq);
        }
    }

    private void scheduleLiveSearch() {
        if (mode != MODE_SEARCH) {
            return;
        }
        searchHandler.removeCallbacksAndMessages(null);
        String q = currentSearchQuery();
        if (q.isEmpty()) {
            hideRoster();
            lastAutoOpenedId = 0;
            lastAutoOpenedQuery = "";
            return;
        }
        if (q.length() < 2) {
            showSearchStatus(R.string.material_search_hint);
            roster.clear();
            rosterAdapter.notifyDataSetChanged();
            return;
        }
        final int seq = ++searchSeq;
        searchHandler.postDelayed(() -> {
            if (seq != searchSeq || mode != MODE_SEARCH) {
                return;
            }
            if (MainActivity.isNetworkAvailable(this)) {
                showSearchStatus(R.string.loading_live_class_data);
                runSearchFromServer(seq);
            } else {
                runSearchFromLocal(seq);
            }
        }, 280);
    }

    private String currentSearchQuery() {
        return etSearch.getText() != null ? etSearch.getText().toString().trim() : "";
    }

    private void showSearchStatus(int messageRes) {
        txtRosterHeader.setVisibility(View.VISIBLE);
        txtRosterSummary.setVisibility(View.VISIBLE);
        txtRosterHeader.setText(R.string.roster_search_results);
        txtRosterSummary.setText(messageRes);
    }

    private void runSearchFromLocal(int seq) {
        String q = currentSearchQuery();
        if (q.isEmpty() || seq != searchSeq || mode != MODE_SEARCH) {
            return;
        }
        ArrayList<RosterItem> found = new ArrayList<>();
        String like = "%" + q + "%";
        Cursor c = db.rawQuery(
                "SELECT id, name, regno, class_id, classe, sex, photo, card, phone FROM tb_student " +
                        "WHERE name LIKE ? OR regno LIKE ? ORDER BY name ASC LIMIT 80",
                new String[]{like, like});
        while (c.moveToNext()) {
            StudentModel st = studentFromCursor(c);
            if (st.getClassId() < 1 || looksLikeHoliday(st.getClasse())) {
                continue;
            }
            RosterItem item = new RosterItem();
            item.student = st;
            item.checkerLine = localCheckerLine(st.getId());
            Set<Integer> required = loadRequiredMaterialIds(st.getClassId(), selectedYearId);
            Map<Integer, double[]> checks = loadChecksForStudent(st.getId(), selectedYearId);
            item.status = computeStatus(required, checks);
            found.add(item);
        }
        c.close();
        preferRealClass(found);

        roster.clear();
        roster.addAll(found);
        txtRosterHeader.setVisibility(View.VISIBLE);
        txtRosterSummary.setVisibility(View.VISIBLE);
        txtRosterHeader.setText(R.string.roster_search_results);
        txtRosterSummary.setText(getString(R.string.roster_count_fmt, roster.size()));
        rosterAdapter.notifyDataSetChanged();
        openFoundStudent(q);
    }

    private void runSearchFromServer(int seq) {
        String q = etSearch.getText() != null ? etSearch.getText().toString().trim() : "";
        if (q.isEmpty()) {
            return;
        }
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        String url = MainActivity.SERVER + "search_material_students"
                + "?school_id=" + schoolId
                + "&year=" + selectedYearId
                + "&q=" + Uri.encode(q);
        new HttpRequest(this).get(url, JSONObject.class, (object, statusCode, status) -> {
            if (seq != searchSeq || mode != MODE_SEARCH) {
                return;
            }
            if (object == null || !object.optBoolean("success", false)) {
                Log.e(TAG, "Live material search failed: " + status);
                runSearchFromLocal(seq);
                return;
            }
            JSONArray students = object.optJSONArray("students");
            if (students == null || students.length() == 0) {
                bindLiveSearchResults(new JSONArray(), seq);
                return;
            }
            bindLiveSearchResults(students, seq);
        });
    }

    private void bindLiveSearchResults(JSONArray students, int seq) {
        if (seq != searchSeq || mode != MODE_SEARCH) {
            return;
        }
        roster.clear();
        if (students != null) {
            for (int i = 0; i < students.length(); i++) {
                JSONObject item = students.optJSONObject(i);
                if (item == null) continue;
                String classLabel = cleanRosterLabel(item.optString("class_label", ""));
                if (item.optInt("class_id", 0) < 1 || looksLikeHoliday(classLabel)) {
                    continue;
                }
                StudentModel st = new StudentModel(
                        item.optInt("id", 0),
                        cleanRosterLabel(item.optString("name", "")),
                        cleanRosterLabel(item.optString("regno", "")),
                        item.optInt("class_id", 0),
                        classLabel,
                        "",
                        cleanRosterLabel(item.optString("photo", "")),
                        preferences.getInt("soma_term_number", 0),
                        "",
                        ""
                );
                RosterItem row = new RosterItem();
                row.student = st;
                row.status = mapOverallToRosterStatus(item.optString("overall", ""));
                row.checkerLine = checkerLineFromJson(item);
                JSONArray items = item.optJSONArray("items");
                row.liveMaterialsJson = items != null ? items.toString() : null;
                roster.add(row);
            }
        }
        txtRosterHeader.setVisibility(View.VISIBLE);
        txtRosterSummary.setVisibility(View.VISIBLE);
        txtRosterHeader.setText(R.string.roster_search_results);
        txtRosterSummary.setText(getString(R.string.roster_count_fmt, roster.size()));
        rosterAdapter.notifyDataSetChanged();
        openFoundStudent(currentSearchQuery());
    }

    private void preferRealClass(ArrayList<RosterItem> found) {
        boolean anyReal = false;
        for (RosterItem item : found) {
            if (item.student != null && !looksLikeHoliday(item.student.getClasse())) {
                anyReal = true;
                break;
            }
        }
        if (!anyReal) {
            return;
        }
        for (int i = found.size() - 1; i >= 0; i--) {
            RosterItem item = found.get(i);
            if (item.student != null && looksLikeHoliday(item.student.getClasse())) {
                found.remove(i);
            }
        }
    }

    private void openFoundStudent(String query) {
        if (mode != MODE_SEARCH || roster.isEmpty()) {
            return;
        }
        String q = query == null ? "" : query.trim();
        RosterItem match = null;
        for (RosterItem item : roster) {
            if (item.student == null || item.student.getRegno() == null) {
                continue;
            }
            if (item.student.getRegno().equalsIgnoreCase(q)) {
                match = item;
                break;
            }
        }
        if (match == null && roster.size() == 1) {
            match = roster.get(0);
        }
        if (match == null || match.student == null) {
            return;
        }
        if (match.student.getId() == lastAutoOpenedId && q.equalsIgnoreCase(lastAutoOpenedQuery)) {
            return;
        }
        lastAutoOpenedId = match.student.getId();
        lastAutoOpenedQuery = q;
        openMaterialCheck(match);
    }

    private String checkerLineFromJson(JSONObject item) {
        if (item == null) return "";
        String name = cleanRosterLabel(item.optString("checker_name", ""));
        String post = cleanRosterLabel(item.optString("checker_post", ""));
        String who = name;
        if (!post.isEmpty()) {
            who = who.isEmpty() ? post : who + " · " + post;
        }
        if (who.isEmpty()) return "";
        return getString(R.string.material_last_checked_fmt, who);
    }

    private String localCheckerLine(int studentId) {
        Cursor c = db.rawQuery(
                "SELECT checker_name, checker_post, checked_by FROM tb_student_material_checks " +
                        "WHERE student_id=? AND academic_year=? AND IFNULL(checked_by,0) > 0 " +
                        "ORDER BY checked_at DESC LIMIT 1",
                new String[]{String.valueOf(studentId), String.valueOf(selectedYearId)});
        String line = "";
        if (c.moveToFirst()) {
            String name = cleanRosterLabel(c.getString(c.getColumnIndex("checker_name")));
            String post = cleanRosterLabel(c.getString(c.getColumnIndex("checker_post")));
            int checkedBy = c.getInt(c.getColumnIndex("checked_by"));
            if (name.isEmpty() && checkedBy == preferences.getInt("soma_id", 0)) {
                name = cleanRosterLabel(preferences.getString("soma_name", ""));
                post = cleanRosterLabel(preferences.getString("soma_post_title", ""));
            }
            String who = name;
            if (!post.isEmpty()) {
                who = who.isEmpty() ? post : who + " · " + post;
            }
            if (!who.isEmpty()) {
                line = getString(R.string.material_last_checked_fmt, who);
            }
        }
        c.close();
        return line;
    }

    private static String cleanRosterLabel(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private String mapOverallToRosterStatus(String overall) {
        if ("complete".equalsIgnoreCase(overall)) return "checked";
        if ("partial".equalsIgnoreCase(overall)) return "incomplete";
        return "missing";
    }

    private void hideRoster() {
        classRoster.clear();
        roster.clear();
        rosterAdapter.notifyDataSetChanged();
        txtRosterHeader.setVisibility(View.GONE);
        txtRosterSummary.setVisibility(View.GONE);
        syncRosterSearchVisibility();
    }

    private void syncRosterSearchVisibility() {
        if (etRosterSearch == null) {
            return;
        }
        boolean visible = mode == MODE_CLASS && openClassId > 0 && !classRoster.isEmpty();
        etRosterSearch.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible && etRosterSearch.getText() != null && etRosterSearch.getText().length() > 0) {
            etRosterSearch.setText("");
        }
    }

    private void applyRosterFilter() {
        String term = etRosterSearch != null && etRosterSearch.getText() != null
                ? etRosterSearch.getText().toString().trim().toLowerCase()
                : "";
        roster.clear();
        for (RosterItem item : classRoster) {
            if (item == null || item.student == null) {
                continue;
            }
            String name = item.student.getName() != null ? item.student.getName().toLowerCase() : "";
            String regno = item.student.getRegno() != null ? item.student.getRegno().toLowerCase() : "";
            if (!term.isEmpty() && !name.contains(term) && !regno.contains(term)) {
                continue;
            }
            roster.add(item);
        }
        if (term.isEmpty()) {
            txtRosterSummary.setText(getString(
                    R.string.roster_summary_fmt,
                    classRoster.size(),
                    countStatus(classRoster, "checked"),
                    countStatus(classRoster, "incomplete"),
                    countStatus(classRoster, "missing")
            ));
        } else {
            txtRosterSummary.setText(getString(R.string.roster_filter_count_fmt, roster.size(), classRoster.size()));
        }
        rosterAdapter.notifyDataSetChanged();
    }

    private int countStatus(ArrayList<RosterItem> items, String wanted) {
        int total = 0;
        for (RosterItem item : items) {
            if (item != null && wanted.equals(item.status)) {
                total++;
            }
        }
        return total;
    }

    private void openCardScanner() {
        Intent intent = new Intent(this, SearchStudentActivity.class);
        intent.putExtra("who", 6);
        startActivity(intent);
    }

    private void openMaterialCheck(RosterItem item) {
        StudentModel st = item.student;
        Intent intent = new Intent(this, MaterialCheckActivity.class);
        intent.putExtra("student_id", st.getId());
        intent.putExtra("name", st.getName());
        intent.putExtra("class", st.getClasse());
        intent.putExtra("classId", st.getClassId());
        intent.putExtra("regno", st.getRegno());
        intent.putExtra("photo", st.getPhoto());
        intent.putExtra("card", st.getCard());
        intent.putExtra("phone", st.getPhone());
        intent.putExtra("academic_year", selectedYearId);
        intent.putExtra("live_materials_json", item.liveMaterialsJson);
        startActivity(intent);
    }

    private StudentModel studentFromCursor(Cursor c) {
        return new StudentModel(
                c.getInt(c.getColumnIndex("id")),
                c.getString(c.getColumnIndex("name")),
                c.getString(c.getColumnIndex("regno")),
                c.getInt(c.getColumnIndex("class_id")),
                c.getString(c.getColumnIndex("classe")),
                c.getString(c.getColumnIndex("sex")),
                c.getString(c.getColumnIndex("photo")),
                preferences.getInt("soma_term_number", 0),
                c.getString(c.getColumnIndex("card")),
                c.getString(c.getColumnIndex("phone"))
        );
    }

    private Set<Integer> loadRequiredMaterialIds(int classId, int yearId) {
        Set<Integer> ids = new HashSet<>();
        Cursor c = db.rawQuery(
                "SELECT material_id FROM tb_class_required_materials " +
                        "WHERE class_id=? AND academic_year=? AND quantity > 0",
                new String[]{String.valueOf(classId), String.valueOf(yearId)});
        while (c.moveToNext()) {
            ids.add(c.getInt(0));
        }
        c.close();
        return ids;
    }

    /** materialId -> [required, brought] */
    private Map<Integer, Map<Integer, double[]>> loadChecksForClass(int classId, int yearId) {
        Map<Integer, Map<Integer, double[]>> map = new HashMap<>();
        Cursor c = db.rawQuery(
                "SELECT student_id, material_id, quantity_required, quantity_brought " +
                        "FROM tb_student_material_checks WHERE class_id=? AND academic_year=?",
                new String[]{String.valueOf(classId), String.valueOf(yearId)});
        while (c.moveToNext()) {
            int sid = c.getInt(0);
            int mid = c.getInt(1);
            double req = c.getDouble(2);
            double brought = c.getDouble(3);
            Map<Integer, double[]> per = map.get(sid);
            if (per == null) {
                per = new HashMap<>();
                map.put(sid, per);
            }
            per.put(mid, new double[]{req, brought});
        }
        c.close();
        return map;
    }

    private Map<Integer, double[]> loadChecksForStudent(int studentId, int yearId) {
        Map<Integer, double[]> map = new HashMap<>();
        Cursor c = db.rawQuery(
                "SELECT material_id, quantity_required, quantity_brought " +
                        "FROM tb_student_material_checks WHERE student_id=? AND academic_year=?",
                new String[]{String.valueOf(studentId), String.valueOf(yearId)});
        while (c.moveToNext()) {
            map.put(c.getInt(0), new double[]{c.getDouble(1), c.getDouble(2)});
        }
        c.close();
        return map;
    }

    private String computeStatus(Set<Integer> required, Map<Integer, double[]> checks) {
        if (required == null || required.isEmpty()) {
            if (checks == null || checks.isEmpty()) return "missing";
            boolean allOk = true;
            for (double[] qb : checks.values()) {
                if (qb[1] < qb[0]) {
                    allOk = false;
                    break;
                }
            }
            return allOk ? "checked" : "incomplete";
        }
        if (checks == null || checks.isEmpty()) return "missing";
        boolean any = false;
        boolean allComplete = true;
        for (Integer mid : required) {
            double[] qb = checks.get(mid);
            if (qb == null) {
                allComplete = false;
                continue;
            }
            any = true;
            if (qb[1] + 0.0001 < qb[0]) {
                allComplete = false;
            }
        }
        if (!any) return "missing";
        return allComplete ? "checked" : "incomplete";
    }

    private class RosterAdapter extends ArrayAdapter<RosterItem> {
        RosterAdapter() {
            super(MaterialCheckMenuActivity.this, R.layout.item_material_roster_row, roster);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.item_material_roster_row, parent, false);
            }
            RosterItem item = roster.get(position);
            TextView name = v.findViewById(R.id.txt_name);
            TextView regno = v.findViewById(R.id.txt_regno);
            TextView status = v.findViewById(R.id.txt_status);
            name.setText(item.student.getName());
            String cls = item.student.getClasse();
            String reg = item.student.getRegno();
            String sub = (reg != null ? reg : "") + (cls != null && !cls.isEmpty() ? " · " + cls : "");
            if (item.checkerLine != null && !item.checkerLine.isEmpty()) {
                sub = sub + "\n" + item.checkerLine;
            }
            regno.setText(sub);

            if ("checked".equals(item.status)) {
                status.setText(R.string.status_checked);
                status.setTextColor(getResources().getColor(R.color.colorSuccess));
            } else if ("incomplete".equals(item.status)) {
                status.setText(R.string.status_incomplete);
                status.setTextColor(getResources().getColor(R.color.colorWarning));
            } else {
                status.setText(R.string.status_missing);
                status.setTextColor(getResources().getColor(R.color.colorError));
            }
            return v;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastAutoOpenedId = 0;
        lastAutoOpenedQuery = "";
        if (syncHelper != null) {
            syncHelper.onResume(false);
        }
        if (mode == MODE_CLASS && openClassId > 0) {
            loadClassRoster(openClassId, openClassTitle);
        }
    }

    @Override
    protected void onPause() {
        if (syncHelper != null) {
            syncHelper.onPause();
        }
        super.onPause();
    }
}
