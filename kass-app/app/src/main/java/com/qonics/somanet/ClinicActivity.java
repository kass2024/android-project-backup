package com.qonics.somanet;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.HttpRequest;
import com.qonics.utils.MenuClearanceHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ClinicActivity extends AppCompatActivity {
    private static final String[] TRANSPORT = {
            "Choose", "Parent pickup", "School car", "Ambulance", "Motorcycle", "Public bus", "Walked with staff"
    };
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private String day;
    private int studentId;
    private boolean fillingFind;
    private boolean canAdd;
    private int loadGen;
    private String chosen = "here";
    private JSONArray dayRecords = new JSONArray();
    private TextView dateLabel;
    private TextView emptyTitle;
    private TextView empty;
    private TextView picked;
    private TextView noteCount;
    private TextView kpiReceived;
    private TextView kpiHere;
    private TextView kpiHospital;
    private TextView kpiHome;
    private TextView kpiCost;
    private TextView saveButton;
    private LinearLayout rows;
    private LinearLayout hits;
    private LinearLayout form;
    private LinearLayout listPane;
    private LinearLayout emptyPane;
    private LinearLayout costWrap;
    private View dotHere;
    private View dotHospital;
    private View dotHome;
    private View cardHere;
    private View cardHospital;
    private View cardHome;
    private EditText find;
    private EditText disease;
    private EditText note;
    private EditText cost;
    private EditText filter;
    private Spinner transport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (MenuClearanceHelper.blockUnlessClinic(this)) return;
        setContentView(R.layout.activity_clinic);
        day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
        dateLabel = findViewById(R.id.txt_clinic_date);
        emptyTitle = findViewById(R.id.txt_clinic_empty_title);
        empty = findViewById(R.id.txt_clinic_empty);
        picked = findViewById(R.id.txt_clinic_picked);
        noteCount = findViewById(R.id.txt_note_count);
        kpiReceived = findViewById(R.id.txt_kpi_received);
        kpiHere = findViewById(R.id.txt_kpi_here);
        kpiHospital = findViewById(R.id.txt_kpi_hospital);
        kpiHome = findViewById(R.id.txt_kpi_home);
        kpiCost = findViewById(R.id.txt_kpi_cost);
        saveButton = findViewById(R.id.btn_clinic_save);
        rows = findViewById(R.id.lnl_clinic_rows);
        hits = findViewById(R.id.lnl_clinic_hits);
        form = findViewById(R.id.lnl_clinic_form);
        listPane = findViewById(R.id.lnl_clinic_list);
        emptyPane = findViewById(R.id.lnl_clinic_empty);
        costWrap = findViewById(R.id.lnl_clinic_cost);
        find = findViewById(R.id.edt_clinic_find);
        disease = findViewById(R.id.edt_clinic_disease);
        note = findViewById(R.id.edt_clinic_note);
        cost = findViewById(R.id.edt_clinic_cost);
        filter = findViewById(R.id.edt_clinic_filter);
        transport = findViewById(R.id.spn_clinic_transport);
        cardHere = findViewById(R.id.card_here);
        cardHospital = findViewById(R.id.card_hospital);
        cardHome = findViewById(R.id.card_home);
        dotHere = findViewById(R.id.dot_here);
        dotHospital = findViewById(R.id.dot_hospital);
        dotHome = findViewById(R.id.dot_home);
        transport.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TRANSPORT));
        findViewById(R.id.btn_clinic_date).setOnClickListener(v -> pickDate());
        findViewById(R.id.btn_clinic_back).setOnClickListener(v -> closeOrLeave());
        findViewById(R.id.btn_clinic_add).setOnClickListener(v -> showForm(true));
        findViewById(R.id.btn_clinic_add_empty).setOnClickListener(v -> showForm(true));
        cardHere.setOnClickListener(v -> choose("here"));
        cardHospital.setOnClickListener(v -> choose("hospital"));
        cardHome.setOnClickListener(v -> choose("home"));
        choose("here");
        note.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                noteCount.setText(s.length() + "/200");
            }
        });
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { paint(dayRecords); }
        });
        find.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (fillingFind) return;
                studentId = 0;
                picked.setText("");
                searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(() -> searchStudents(s.toString().trim()), 300);
            }
        });
        saveButton.setOnClickListener(v -> save());
        canAdd = isSchoolNurse();
        applyAddButtons();
        paintDate();
        load();
    }

    private boolean isSchoolNurse() {
        String title = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("soma_post_title", "");
        if (title == null) return false;
        String t = title.toLowerCase(Locale.US);
        return t.contains("nurse") || t.contains("nuse");
    }

    private void applyAddButtons() {
        int show = canAdd ? View.VISIBLE : View.GONE;
        findViewById(R.id.btn_clinic_add).setVisibility(show);
        findViewById(R.id.btn_clinic_add_empty).setVisibility(show);
        if (!canAdd && form.getVisibility() == View.VISIBLE) {
            showForm(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (form.getVisibility() == View.VISIBLE) {
            showForm(false);
            return;
        }
        super.onBackPressed();
    }

    private void closeOrLeave() {
        if (form.getVisibility() == View.VISIBLE) {
            showForm(false);
            return;
        }
        finish();
    }

    private void showForm(boolean open) {
        if (open && !canAdd) return;
        form.setVisibility(open ? View.VISIBLE : View.GONE);
        listPane.setVisibility(open ? View.GONE : View.VISIBLE);
        saveButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    private void choose(String value) {
        chosen = value;
        cardHere.setBackgroundResource("here".equals(value) ? R.drawable.bg_clinic_card_on : R.drawable.bg_clinic_card);
        cardHospital.setBackgroundResource("hospital".equals(value) ? R.drawable.bg_clinic_card_on : R.drawable.bg_clinic_card);
        cardHome.setBackgroundResource("home".equals(value) ? R.drawable.bg_clinic_card_on : R.drawable.bg_clinic_card);
        dotHere.setBackgroundResource("here".equals(value) ? R.drawable.bg_radio_on : R.drawable.bg_clinic_dot);
        dotHospital.setBackgroundResource("hospital".equals(value) ? R.drawable.bg_radio_on : R.drawable.bg_clinic_dot);
        dotHome.setBackgroundResource("home".equals(value) ? R.drawable.bg_radio_on : R.drawable.bg_clinic_dot);
        costWrap.setVisibility("hospital".equals(value) ? View.VISIBLE : View.GONE);
        transport.setVisibility("home".equals(value) ? View.VISIBLE : View.GONE);
    }

    private void paintDate() {
        try {
            dateLabel.setText(new SimpleDateFormat("EEE d MMM yyyy", Locale.US)
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)));
        } catch (Exception e) {
            dateLabel.setText(day);
        }
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day));
        } catch (Exception ignored) {
        }
        new DatePickerDialog(this, (picker, year, month, dayOfMonth) -> {
            Calendar pickedDay = Calendar.getInstance();
            pickedDay.set(year, month, dayOfMonth);
            day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(pickedDay.getTime());
            paintDate();
            load();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private Map<String, Object> base() {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", prefs.getInt(MainActivity.SK_ID, 0));
        map.put("post_id", prefs.getInt("soma_post", 0));
        map.put("staff_id", prefs.getInt("soma_id", 0));
        map.put("date", day);
        return map;
    }

    private void load() {
        final int gen = ++loadGen;
        rows.removeAllViews();
        emptyTitle.setText(R.string.clinic_loading);
        empty.setText("");
        emptyPane.setVisibility(View.VISIBLE);
        if (!MainActivity.isNetworkAvailable(this)) {
            emptyTitle.setText(R.string.clinic_offline);
            return;
        }
        new HttpRequest(this).post(MainActivity.SERVER + "clinic_day", base(), (object, code, status) -> {
            if (gen != loadGen || isFinishing()) return;
            if (object == null || object.optInt("success") != 1) {
                emptyTitle.setText(object != null && object.optString("error").length() > 0
                        ? object.optString("error") : getString(R.string.error_occurred));
                empty.setText("");
                return;
            }
            NumberFormat money = NumberFormat.getIntegerInstance(Locale.US);
            kpiReceived.setText(String.valueOf(object.optInt("received")));
            kpiHere.setText(String.valueOf(object.optInt("here")));
            kpiHospital.setText(String.valueOf(object.optInt("hospital")));
            kpiHome.setText(String.valueOf(object.optInt("home")));
            kpiCost.setText(money.format(object.optDouble("cost")));
            canAdd = object.optInt("can_add") == 1;
            applyAddButtons();
            dayRecords = object.optJSONArray("records");
            if (dayRecords == null) dayRecords = new JSONArray();
            paint(dayRecords);
        });
    }

    private void paint(JSONArray records) {
        rows.removeAllViews();
        String needle = filter.getText().toString().trim().toLowerCase(Locale.US);
        int shown = 0;
        if (records != null) {
            for (int i = 0; i < records.length(); i++) {
                JSONObject row = records.optJSONObject(i);
                if (row == null) continue;
                String blob = (row.optString("name") + " " + row.optString("regno") + " " + row.optString("disease")).toLowerCase(Locale.US);
                if (needle.length() > 0 && !blob.contains(needle)) continue;
                shown++;
                addCard(row);
            }
        }
        if (shown == 0) {
            emptyPane.setVisibility(View.VISIBLE);
            emptyTitle.setText(needle.length() > 0 ? R.string.clinic_empty : R.string.clinic_empty);
            empty.setText(getString(R.string.clinic_empty_sub, dateLabel.getText()));
            return;
        }
        emptyPane.setVisibility(View.GONE);
    }

    private void addCard(JSONObject row) {
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_clinic_card);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = pad / 2;
        card.setLayoutParams(lp);
        TextView who = new TextView(this);
        who.setTypeface(Typeface.DEFAULT_BOLD);
        who.setTextColor(getResources().getColor(R.color.textPrimary));
        String name = row.optString("name", "Student");
        String regno = row.optString("regno", "");
        String classe = row.optString("class", "");
        who.setText(name + (regno.length() > 0 ? " · " + regno : "") + (classe.length() > 0 ? " · " + classe : ""));
        TextView body = new TextView(this);
        body.setTextColor(getResources().getColor(R.color.textMuted));
        body.setPadding(0, pad / 4, 0, 0);
        body.setText(detail(row));
        card.addView(who);
        card.addView(body);
        int id = row.optInt("id");
        card.setOnLongClickListener(v -> {
            if (!canAdd) return false;
            confirmRemove(id);
            return true;
        });
        rows.addView(card);
    }

    private String detail(JSONObject row) {
        String outcome = row.optString("outcome", "here");
        String disease = row.optString("disease", "");
        String note = row.optString("note", "");
        String line;
        if ("hospital".equals(outcome)) {
            line = getString(R.string.clinic_hospital) + " · "
                    + NumberFormat.getIntegerInstance(Locale.US).format(row.optDouble("hospital_cost")) + " RWF";
        } else if ("home".equals(outcome)) {
            line = getString(R.string.clinic_home) + " · " + row.optString("transport", "");
        } else {
            line = getString(R.string.clinic_here);
        }
        if (disease.length() > 0) line = disease + " · " + line;
        if (note.length() > 0) line = line + "\n" + note;
        return line;
    }

    private void searchStudents(String q) {
        hits.removeAllViews();
        if (q.length() < 2 || !MainActivity.isNetworkAvailable(this)) return;
        Map<String, Object> map = base();
        map.put("q", q);
        new HttpRequest(this).post(MainActivity.SERVER + "clinic_student_search", map, (object, code, status) -> {
            if (isFinishing() || object == null) return;
            hits.removeAllViews();
            JSONArray students = object.optJSONArray("students");
            if (students == null) return;
            int pad = Math.round(8 * getResources().getDisplayMetrics().density);
            for (int i = 0; i < students.length(); i++) {
                JSONObject st = students.optJSONObject(i);
                if (st == null) continue;
                TextView hit = new TextView(this);
                hit.setPadding(0, pad, 0, pad);
                hit.setTextColor(getResources().getColor(R.color.textPrimary));
                String label = st.optString("name");
                if (st.optString("regno").length() > 0) label += " · " + st.optString("regno");
                if (st.optString("class").length() > 0) label += " · " + st.optString("class");
                hit.setText(label);
                int id = st.optInt("id");
                String name = st.optString("name");
                final String shown = label;
                hit.setOnClickListener(v -> {
                    fillingFind = true;
                    studentId = id;
                    picked.setText(shown);
                    find.setText(name);
                    find.setSelection(name.length());
                    fillingFind = false;
                    hits.removeAllViews();
                });
                hits.addView(hit);
            }
        });
    }

    private void save() {
        if (studentId < 1) {
            Toast.makeText(this, R.string.clinic_pick_student, Toast.LENGTH_LONG).show();
            return;
        }
        if (!MainActivity.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.clinic_offline, Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, Object> map = base();
        map.put("student_id", studentId);
        map.put("disease", disease.getText().toString().trim());
        map.put("note", note.getText().toString().trim());
        map.put("outcome", chosen);
        map.put("hospital_cost", cost.getText().toString().trim());
        int transportIndex = transport.getSelectedItemPosition();
        map.put("transport", transportIndex > 0 ? TRANSPORT[transportIndex] : "");
        new HttpRequest(this).post(MainActivity.SERVER + "clinic_save", map, (object, code, status) -> {
            if (isFinishing()) return;
            if (object == null || object.optInt("success") != 1) {
                Toast.makeText(this, object != null && object.optString("error").length() > 0
                        ? object.optString("error") : getString(R.string.error_occurred), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.clinic_saved, Toast.LENGTH_SHORT).show();
            studentId = 0;
            find.setText("");
            disease.setText("");
            note.setText("");
            cost.setText("");
            picked.setText("");
            transport.setSelection(0);
            noteCount.setText("0/200");
            choose("here");
            showForm(false);
            load();
        });
    }

    private void confirmRemove(int id) {
        if (id < 1) return;
        new AlertDialog.Builder(this)
                .setMessage(R.string.clinic_remove)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Map<String, Object> map = base();
                    map.put("id", id);
                    new HttpRequest(this).post(MainActivity.SERVER + "clinic_remove", map, (object, code, status) -> {
                        if (!isFinishing()) load();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
