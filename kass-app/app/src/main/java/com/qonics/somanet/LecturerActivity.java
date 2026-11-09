package com.qonics.somanet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.qonics.model.Dual_data;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class LecturerActivity extends AppCompatActivity {
    TextView txtlecturer, txtstatus, txterror, txtretry, txtexit, txttitle;
    Button btnstart;
    Spinner spclass, spcourse;
    EditText etClassSearch;
    ImageView imgerror;
    RelativeLayout rlvloading;
    LinearLayout lnlclasses, lnlcourse;
    ProgressBar prb;
    HttpRequest req;
    String server = MainActivity.SERVER;
    SharedPreferences preferences;
    /** Full list (holiday filtered + level ordered). */
    ArrayList<Dual_data> allClasses = new ArrayList<>();
    /** Spinner list (search filtered). */
    ArrayList<Dual_data> classes = new ArrayList<>();
    ArrayList<Dual_data> courses = new ArrayList<>();
    ArrayAdapter<Dual_data> adapter;
    ArrayAdapter<Dual_data> adapterCourse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lecturer);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        adapter = new ClassAdapter();
        adapterCourse = new CourseAdapter();
        populateView();
        loadData();
    }

    private void populateView() {
        txtexit = findViewById(R.id.txtexit);
        txttitle = findViewById(R.id.txttitle);
        txtretry = findViewById(R.id.txtretry);
        txterror = findViewById(R.id.txterror);
        lnlclasses = findViewById(R.id.lnlclasses);
        lnlcourse = findViewById(R.id.lnlcourse);
        txterror.setText("");
        txtlecturer = findViewById(R.id.txtlecturer);
        txtstatus = findViewById(R.id.txtstatus);
        prb = findViewById(R.id.prb);
        rlvloading = findViewById(R.id.rlvloading);
        imgerror = findViewById(R.id.imgerror);
        btnstart = findViewById(R.id.btnstart);
        spcourse = findViewById(R.id.spcourse);
        spclass = findViewById(R.id.spclass);
        etClassSearch = findViewById(R.id.et_class_search);
        if (getIntent().getIntExtra("who", 0) == 1) {
            lnlcourse.setVisibility(View.GONE);
        }
        rlvloading.setVisibility(View.VISIBLE);
        prb.setVisibility(View.VISIBLE);
        imgerror.setVisibility(View.INVISIBLE);
        txtstatus.setText(getString(R.string.please_wait));

        txtretry.setVisibility(View.INVISIBLE);
        txtretry.setOnClickListener(view -> loadData());

        txtexit.setOnClickListener(view -> onBackPressed());

        txttitle.setText(getIntent().getIntExtra("who", 0) == 0
                ? getString(R.string.das_course)
                : "Class Daily attendance");
        spclass.setAdapter(adapter);
        spcourse.setAdapter(adapterCourse);

        if (etClassSearch != null) {
            etClassSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyClassFilter(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        spclass.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i > 0 && getIntent().getIntExtra("who", 0) == 0)
                    loadCourse(classes.get(i).getId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        btnstart.setOnClickListener(view -> {
            if (spclass.getSelectedItemPosition() == -1 || spclass.getSelectedItemPosition() == 0) {
                Snackbar.make(spcourse, getString(R.string.choose_class), Snackbar.LENGTH_LONG).show();
            } else if ((spcourse.getSelectedItemPosition() == -1 || spcourse.getSelectedItemPosition() == 0)
                    && getIntent().getIntExtra("who", 0) == 0) {
                Snackbar.make(spcourse, getString(R.string.choose_course), Snackbar.LENGTH_LONG).show();
            } else {
                if (getIntent().getIntExtra("who", 0) == 0) {
                    Intent intent = new Intent(this, AttendanceActivity.class);
                    intent.putExtra("courseId", courses.get(spcourse.getSelectedItemPosition()).getId());
                    intent.putExtra("courseTitle", courses.get(spcourse.getSelectedItemPosition()).getText1());
                    intent.putExtra("classId", classes.get(spclass.getSelectedItemPosition()).getId());
                    intent.putExtra("classTitle", classes.get(spclass.getSelectedItemPosition()).getText1());
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(this, ClassAttendanceActivity.class);
                    intent.putExtra("classId", classes.get(spclass.getSelectedItemPosition()).getId());
                    intent.putExtra("classTitle", classes.get(spclass.getSelectedItemPosition()).getText1());
                    startActivity(intent);
                }
            }
        });
    }

    private void loadData() {
        allClasses.clear();
        classes.clear();
        txterror.setText("");
        imgerror.setVisibility(View.INVISIBLE);
        txtstatus.setText(getString(R.string.please_wait));
        prb.setVisibility(View.VISIBLE);
        txtretry.setVisibility(View.INVISIBLE);
        txtlecturer.setText(getString(R.string.teacher) + " " + preferences.getString("soma_name", ""));

        // Daily attendance (who=1): fully offline from synced local DB (fast)
        if (getIntent().getIntExtra("who", 0) == 1) {
            loadDailyClassesOffline();
            return;
        }

        req = new HttpRequest(this);
        int teacher = preferences.getInt("soma_id", 0);
        req.get(server + "get_class/" + teacher + "/" + preferences.getInt(MainActivity.SK_ID, 0),
                JSONObject.class, new Callback() {
                    @Override
                    public void HttpCallback(JSONObject object, int status_code, String status) {
                        if (object == null) {
                            imgerror.setVisibility(View.VISIBLE);
                            prb.setVisibility(View.INVISIBLE);
                            txtstatus.setText("Oops! " + status);
                            txtretry.setVisibility(View.VISIBLE);
                            rlvloading.setVisibility(View.VISIBLE);
                        } else {
                            try {
                                if (object.has("error")) {
                                    imgerror.setVisibility(View.VISIBLE);
                                    prb.setVisibility(View.INVISIBLE);
                                    rlvloading.setVisibility(View.VISIBLE);
                                    txtretry.setVisibility(View.VISIBLE);
                                    txtstatus.setText(getString(R.string.oops) + object.getString("error"));
                                } else {
                                    rlvloading.setVisibility(View.GONE);
                                    if (object.has("classes")) {
                                        lnlclasses.setVisibility(View.VISIBLE);
                                        txterror.setText("");
                                        JSONArray jar = object.getJSONArray("classes");
                                        ArrayList<Dual_data> loaded = new ArrayList<>();
                                        for (int a = 0; a < jar.length(); a++) {
                                            JSONObject data = jar.getJSONObject(a);
                                            String levelName = data.optString("level_name", "");
                                            String deptCode = data.optString("dept_code", "");
                                            String classTitle = data.optString("title", "");
                                            String facultyCode = data.optString("faculty_code", "");
                                            int facultyType = data.optInt("type", 0);
                                            String title = buildClassLabel(levelName, deptCode, classTitle);
                                            if (data.getInt("id") < 1 || title.isEmpty() || looksLikeHoliday(title)
                                                    || looksLikeHoliday(levelName)) {
                                                continue;
                                            }
                                            int rank = classLevelRank(levelName, classTitle, deptCode,
                                                    facultyType, facultyCode);
                                            String searchHay = (title + " " + levelName + " " + deptCode
                                                    + " " + facultyCode).toLowerCase(Locale.US);
                                            loaded.add(new Dual_data(data.getInt("id"), title,
                                                    rank + "|" + searchHay));
                                        }
                                        Collections.sort(loaded, classComparator());
                                        allClasses.clear();
                                        allClasses.addAll(loaded);
                                        String q = etClassSearch != null && etClassSearch.getText() != null
                                                ? etClassSearch.getText().toString() : "";
                                        applyClassFilter(q);
                                    } else {
                                        lnlclasses.setVisibility(View.GONE);
                                        txterror.setText(getString(R.string.lect_cls_not));
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
    }

    /** Build class picker from local synced students/classes — no network. */
    private void loadDailyClassesOffline() {
        try {
            android.database.sqlite.SQLiteDatabase db =
                    new com.qonics.dbhelper.DatabaseHandler(this).getWritableDatabase();
            // Ensure schema (status column / pending tables)
            new com.qonics.dbhelper.DatabaseHandler(this).onCreate(db);

            ArrayList<Dual_data> loaded = new ArrayList<>();
            java.util.HashSet<Integer> seen = new java.util.HashSet<>();

            android.database.Cursor c = db.rawQuery(
                    "SELECT DISTINCT class_id, classe FROM tb_student " +
                            "WHERE IFNULL(class_id,0)>0 AND IFNULL(status,1)=1 " +
                            "AND IFNULL(classe,'')<>'' ORDER BY classe ASC",
                    null);
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String label = c.getString(1);
                addOfflineClass(loaded, seen, id, label);
            }
            c.close();

            c = db.rawQuery("SELECT id, title FROM tb_class WHERE IFNULL(title,'')<>'' ORDER BY title ASC", null);
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String label = c.getString(1);
                addOfflineClass(loaded, seen, id, label);
            }
            c.close();
            db.close();

            Collections.sort(loaded, classComparator());
            allClasses.clear();
            allClasses.addAll(loaded);
            rlvloading.setVisibility(View.GONE);
            prb.setVisibility(View.INVISIBLE);
            if (allClasses.isEmpty()) {
                lnlclasses.setVisibility(View.GONE);
                txterror.setText(getString(R.string.lect_cls_not));
            } else {
                lnlclasses.setVisibility(View.VISIBLE);
                txterror.setText("");
                String q = etClassSearch != null && etClassSearch.getText() != null
                        ? etClassSearch.getText().toString() : "";
                applyClassFilter(q);
            }
        } catch (Exception e) {
            Log.e("LecturerActivity", "Offline class load failed", e);
            imgerror.setVisibility(View.VISIBLE);
            prb.setVisibility(View.INVISIBLE);
            txtstatus.setText(getString(R.string.oops) + e.getMessage());
            txtretry.setVisibility(View.VISIBLE);
            rlvloading.setVisibility(View.VISIBLE);
        }
    }

    private void addOfflineClass(ArrayList<Dual_data> target, java.util.HashSet<Integer> seen,
                                 int id, String rawLabel) {
        String title = rawLabel == null ? "" : rawLabel.trim().replaceAll("\\s+", " ");
        if (id < 1 || title.isEmpty() || looksLikeHoliday(title) || seen.contains(id)) {
            return;
        }
        seen.add(id);
        int rank = classLevelRank(title, title, "", 0, "");
        String searchHay = title.toLowerCase(Locale.US);
        target.add(new Dual_data(id, title, rank + "|" + searchHay));
    }

    private void applyClassFilter(String query) {
        String term = query == null ? "" : query.trim().toLowerCase(Locale.US);
        int selectedId = 0;
        int selectedPos = spclass != null ? spclass.getSelectedItemPosition() : -1;
        if (selectedPos > 0 && selectedPos < classes.size()) {
            selectedId = classes.get(selectedPos).getId();
        }
        classes.clear();
        classes.add(new Dual_data(0, getString(R.string.select_class)));
        for (Dual_data item : allClasses) {
            if (item == null) continue;
            String hay = item.getText2() != null ? item.getText2() : "";
            int pipe = hay.indexOf('|');
            String searchHay = pipe >= 0 ? hay.substring(pipe + 1) : item.getText1().toLowerCase(Locale.US);
            if (!term.isEmpty() && !searchHay.contains(term)) {
                continue;
            }
            classes.add(item);
        }
        adapter.notifyDataSetChanged();
        int restorePos = 0;
        for (int i = 1; i < classes.size(); i++) {
            if (classes.get(i).getId() == selectedId) {
                restorePos = i;
                break;
            }
        }
        spclass.setSelection(restorePos);
    }

    private static Comparator<Dual_data> classComparator() {
        return (a, b) -> {
            int ra = parseRank(a != null ? a.getText2() : null);
            int rb = parseRank(b != null ? b.getText2() : null);
            if (ra != rb) return Integer.compare(ra, rb);
            String ta = a != null && a.getText1() != null ? a.getText1() : "";
            String tb = b != null && b.getText1() != null ? b.getText1() : "";
            return ta.compareToIgnoreCase(tb);
        };
    }

    private static int parseRank(String text2) {
        if (text2 == null || text2.isEmpty()) return 90;
        int pipe = text2.indexOf('|');
        String num = pipe >= 0 ? text2.substring(0, pipe) : text2;
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 90;
        }
    }

    private static String buildClassLabel(String levelName, String deptCode, String title) {
        return (String.valueOf(levelName == null ? "" : levelName).trim() + " "
                + String.valueOf(deptCode == null ? "" : deptCode).trim() + " "
                + String.valueOf(title == null ? "" : title).trim()).trim().replaceAll("\\s+", " ");
    }

    private static boolean looksLikeHoliday(String label) {
        return label != null && label.toLowerCase(Locale.US).contains("holiday");
    }

    /**
     * Nursery → Primary → O Level → A Level → RTB → Special.
     */
    private static int classLevelRank(String levelName, String title, String deptCode,
                                      int facultyType, String facultyCode) {
        String hay = (safe(levelName) + " " + safe(title) + " " + safe(deptCode) + " "
                + safe(facultyCode)).toLowerCase(Locale.US);

        // Special last (faculty type 3 or name)
        if (facultyType == 3 || hay.contains("special") || hay.contains(" nursing")
                || hay.matches(".*\\banp\\b.*")) {
            return 60;
        }
        // RTB / TVET (faculty type 1)
        if (facultyType == 1 || hay.contains("rtb") || hay.contains("tvet")
                || (hay.matches(".*\\b(level\\s*[1-5]|year\\s*[1-3])\\b.*")
                && !hay.matches(".*\\b(p[1-6]|s[1-6]|n[1-3]|primary|senior|ordinary)\\b.*"))) {
            return 50;
        }
        // Nursery
        if (hay.matches(".*\\bn[1-3]\\b.*") || hay.contains("nursery")
                || hay.matches(".*\\b(baby|middle|top)\\s*class\\b.*")) {
            return 10;
        }
        // Primary
        if (hay.matches(".*\\bp[1-6]\\b.*") || hay.contains("primary")) {
            return 20;
        }
        // O Level
        if (hay.matches(".*\\bs[1-3]\\b.*") || hay.contains("o level") || hay.contains("o-level")
                || hay.contains("ordinary")) {
            return 30;
        }
        // A Level
        if (hay.matches(".*\\bs[4-6]\\b.*") || hay.contains("a level") || hay.contains("a-level")
                || hay.matches(".*\\bsenior\\s*[4-6]\\b.*") || hay.contains("advanced")) {
            return 40;
        }
        return 90;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private void loadCourse(int classe) {
        courses.clear();
        txterror.setText("");
        imgerror.setVisibility(View.INVISIBLE);
        txtstatus.setText(getString(R.string.please_wait));
        prb.setVisibility(View.VISIBLE);
        req = new HttpRequest(this);
        Log.e("SSS", server + "get_course/" + preferences.getInt("soma_id", 0) + "/" + classe + "/"
                + preferences.getInt("soma_term", 0));
        req.get(server + "get_course/" + preferences.getInt("soma_id", 0) + "/" + classe + "/"
                + preferences.getInt("soma_term", 0), JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                rlvloading.setVisibility(View.GONE);
                if (object == null) {
                    prb.setVisibility(View.INVISIBLE);
                    Toast.makeText(LecturerActivity.this, getString(R.string.oops) + status, Toast.LENGTH_LONG).show();
                } else {
                    try {
                        if (object.has("error")) {
                            prb.setVisibility(View.INVISIBLE);
                            Toast.makeText(LecturerActivity.this,
                                    getString(R.string.oops) + object.getString("error"), Toast.LENGTH_LONG).show();
                        } else {
                            if (object.has("courses")) {
                                lnlclasses.setVisibility(View.VISIBLE);
                                JSONArray jar = object.getJSONArray("courses");
                                courses.add(new Dual_data(0, getString(R.string.select_course)));
                                for (int a = 0; a < jar.length(); a++) {
                                    JSONObject data = jar.getJSONObject(a);
                                    String title = data.getString("title");
                                    courses.add(new Dual_data(data.getInt("id"), title));
                                }
                                adapterCourse.notifyDataSetChanged();
                                spcourse.setSelection(-1);
                            } else {
                                Toast.makeText(LecturerActivity.this, getString(R.string.lect_not_active),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private class ClassAdapter extends ArrayAdapter<Dual_data> {
        public ClassAdapter() {
            super(LecturerActivity.this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, classes);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
                        parent, false);

            TextView txt1 = v.findViewById(android.R.id.text1);
            txt1.setText(classes.get(position).getText1());
            return v;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
                        parent, false);

            TextView txt1 = v.findViewById(android.R.id.text1);
            txt1.setText(classes.get(position).getText1());
            return v;
        }
    }

    private class CourseAdapter extends ArrayAdapter<Dual_data> {
        public CourseAdapter() {
            super(LecturerActivity.this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, courses);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
                        parent, false);

            TextView txt1 = v.findViewById(android.R.id.text1);
            txt1.setText(courses.get(position).getText1());
            return v;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
                        parent, false);

            TextView txt1 = v.findViewById(android.R.id.text1);
            txt1.setText(courses.get(position).getText1());
            return v;
        }
    }
}
