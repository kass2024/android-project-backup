package com.qonics.somanet;

import android.app.DatePickerDialog;
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

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public class LatenessReportActivity extends AppCompatActivity {
    private String mode = "day";
    private String day;
    private String week;
    private String month;
    private String from;
    private String to;
    private int loadGen;
    private LinearLayout dates;
    private LinearLayout rows;
    private TextView summary;
    private TextView empty;
    private TextView btnDay;
    private TextView btnWeek;
    private TextView btnMonth;
    private TextView btnRange;
    private TextView statStudents;
    private TextView statLate;
    private TextView statMarks;
    private EditText search;
    private Spinner classSpinner;
    private JSONArray records = new JSONArray();
    private String selectedClass = "";
    private boolean ignoreSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lateness_report);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
        day = today;
        week = today;
        month = today.substring(0, 7);
        from = today.substring(0, 8) + "01";
        to = today;
        dates = findViewById(R.id.lnl_dates);
        rows = findViewById(R.id.lnl_rows);
        summary = findViewById(R.id.txt_summary);
        empty = findViewById(R.id.txt_empty);
        btnDay = findViewById(R.id.btn_day);
        btnWeek = findViewById(R.id.btn_week);
        btnMonth = findViewById(R.id.btn_month);
        btnRange = findViewById(R.id.btn_range);
        statStudents = findViewById(R.id.txt_stat_students);
        statLate = findViewById(R.id.txt_stat_late);
        statMarks = findViewById(R.id.txt_stat_marks);
        search = findViewById(R.id.edt_late_search);
        classSpinner = findViewById(R.id.sp_late_class);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });
        classSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (ignoreSpinner) return;
                selectedClass = position <= 0 ? "" : String.valueOf(parent.getItemAtPosition(position));
                applyFilter();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        btnDay.setOnClickListener(v -> setMode("day"));
        btnWeek.setOnClickListener(v -> setMode("week"));
        btnMonth.setOnClickListener(v -> setMode("month"));
        btnRange.setOnClickListener(v -> setMode("range"));
        paintModes();
        paintDates();
        load();
    }

    private void setMode(String next) {
        mode = next;
        paintModes();
        paintDates();
        load();
    }

    private void paintModes() {
        style(btnDay, "day".equals(mode));
        style(btnWeek, "week".equals(mode));
        style(btnMonth, "month".equals(mode));
        style(btnRange, "range".equals(mode));
    }

    private void style(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.mode_tab_active_bg : R.drawable.mode_tab_bg);
        tab.setTextColor(active ? Color.WHITE : getResources().getColor(R.color.textPrimary));
    }

    private void paintDates() {
        dates.removeAllViews();
        if ("range".equals(mode)) {
            dates.addView(dateButton(getString(R.string.late_from) + " " + pretty(from), from, value -> {
                from = value;
                if (from.compareTo(to) > 0) to = from;
                paintDates();
                load();
            }));
            dates.addView(dateButton(getString(R.string.late_to) + " " + pretty(to), to, value -> {
                to = value;
                if (to.compareTo(from) < 0) from = to;
                paintDates();
                load();
            }));
            return;
        }
        if ("month".equals(mode)) {
            dates.addView(dateButton(prettyMonth(month), month + "-01", value -> {
                month = value.substring(0, 7);
                paintDates();
                load();
            }));
            return;
        }
        if ("week".equals(mode)) {
            dates.addView(dateButton(pretty(week), week, value -> {
                week = value;
                paintDates();
                load();
            }));
            return;
        }
        dates.addView(dateButton(pretty(day), day, value -> {
            day = value;
            paintDates();
            load();
        }));
    }

    private TextView dateButton(String label, String current, DateChosen chosen) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(getResources().getColor(R.color.textPrimary));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setBackgroundResource(R.drawable.mode_tab_bg);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = pad / 2;
        view.setLayoutParams(lp);
        view.setOnClickListener(v -> pick(current, chosen));
        return view;
    }

    private void pick(String current, DateChosen chosen) {
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(current));
        } catch (Exception ignored) {
        }
        new DatePickerDialog(this, (picker, year, monthOfYear, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, monthOfYear, dayOfMonth);
            chosen.onDate(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(picked.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void load() {
        final int gen = ++loadGen;
        rows.removeAllViews();
        records = new JSONArray();
        empty.setText(R.string.late_loading);
        empty.setVisibility(View.VISIBLE);
        summary.setText("");
        paintStats(0, 0, 0);
        if (!MainActivity.isNetworkAvailable(this)) {
            empty.setText(R.string.late_offline);
            return;
        }
        int schoolId = PreferenceManager.getDefaultSharedPreferences(this).getInt(MainActivity.SK_ID, 0);
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId);
        map.put("mode", mode);
        map.put("date", day);
        map.put("week", week);
        map.put("month", month);
        map.put("from", from);
        map.put("to", to);
        new HttpRequest(this).post(MainActivity.SERVER + "lateness_records", map, (object, code, status) -> {
            if (gen != loadGen) return;
            if (object == null || object.optInt("success") != 1) {
                empty.setText(object != null && object.optString("error").length() > 0
                        ? object.optString("error") : getString(R.string.error_occurred));
                return;
            }
            summary.setText(object.optString("period", ""));
            JSONArray loaded = object.optJSONArray("records");
            records = loaded == null ? new JSONArray() : loaded;
            fillClasses();
            applyFilter();
        });
    }

    private void fillClasses() {
        String keep = selectedClass;
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.optJSONObject(i);
            if (row == null) continue;
            String classe = row.optString("class", "").trim();
            if (classe.length() == 0 || isHoliday(classe)) continue;
            names.add(classe);
        }
        ArrayList<String> options = new ArrayList<>();
        options.add(getString(R.string.late_all_classes));
        options.addAll(names);
        ignoreSpinner = true;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        classSpinner.setAdapter(adapter);
        int index = Math.max(0, options.indexOf(keep));
        if (index == 0) selectedClass = "";
        classSpinner.setSelection(index);
        classSpinner.post(() -> ignoreSpinner = false);
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.US);
        JSONArray shown = new JSONArray();
        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.optJSONObject(i);
            if (row == null || isHoliday(row.optString("class", ""))) continue;
            String classe = row.optString("class", "").trim();
            if (selectedClass.length() > 0 && !selectedClass.equalsIgnoreCase(classe)) continue;
            if (query.length() > 0) {
                String hay = (row.optString("name", "") + " " + row.optString("regno", "") + " "
                        + classe + " " + row.optString("place", "")).toLowerCase(Locale.US);
                if (!hay.contains(query)) continue;
            }
            shown.put(row);
        }
        paint(shown);
    }

    private boolean isHoliday(String classe) {
        String n = classe == null ? "" : classe.toLowerCase(Locale.US);
        return n.contains("holiday") || n.contains("vacation") || n.contains("vacances");
    }

    private void paintStats(int students, int late, int marks) {
        statStudents.setText(String.valueOf(students));
        statLate.setText(String.valueOf(late));
        statMarks.setText(String.valueOf(marks));
    }

    private void paint(JSONArray shown) {
        rows.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(12 * density);
        HashSet<String> people = new HashSet<>();
        int marks = 0;
        for (int i = 0; i < shown.length(); i++) {
            JSONObject row = shown.optJSONObject(i);
            if (row == null) continue;
            String name = row.optString("name", "Student");
            String regno = row.optString("regno", "");
            String classe = row.optString("class", "").trim();
            people.add(regno.length() > 0 ? regno : name);
            marks += row.optInt("marks");

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackgroundResource(R.drawable.menu_tile_bg);
            card.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Math.round(8 * density);
            card.setLayoutParams(lp);

            TextView avatar = new TextView(this);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackgroundResource(R.drawable.late_avatar_bg);
            avatar.setTextColor(getResources().getColor(R.color.colorAccent));
            avatar.setTypeface(Typeface.DEFAULT_BOLD);
            avatar.setTextSize(16);
            String initial = name.trim().length() == 0 ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.US);
            avatar.setText(initial);
            int size = Math.round(42 * density);
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(size, size);
            avatarLp.rightMargin = pad;
            avatar.setLayoutParams(avatarLp);

            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView who = new TextView(this);
            who.setTypeface(Typeface.DEFAULT_BOLD);
            who.setTextSize(15);
            who.setTextColor(getResources().getColor(R.color.textPrimary));
            who.setText(name);

            TextView meta = new TextView(this);
            meta.setTextColor(getResources().getColor(R.color.textMuted));
            meta.setTextSize(12);
            meta.setPadding(0, Math.round(2 * density), 0, 0);
            meta.setText(regno);

            text.addView(who);
            if (regno.length() > 0) text.addView(meta);
            if (classe.length() > 0) {
                TextView chip = new TextView(this);
                chip.setText(classe);
                chip.setTextColor(getResources().getColor(R.color.colorBlue));
                chip.setTextSize(11);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
                chip.setBackgroundResource(R.drawable.late_class_bg);
                int chipPad = Math.round(6 * density);
                chip.setPadding(chipPad, chipPad / 2, chipPad, chipPad / 2);
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                chipLp.topMargin = Math.round(6 * density);
                chip.setLayoutParams(chipLp);
                text.addView(chip);
            }

            TextView body = new TextView(this);
            body.setTextColor(getResources().getColor(R.color.textMuted));
            body.setTextSize(12);
            body.setPadding(0, Math.round(6 * density), 0, 0);
            body.setText(getString(
                    R.string.late_line,
                    pretty(row.optString("date", "")),
                    row.optString("place", ""),
                    row.optString("arrived", ""),
                    row.optString("cutoff", "")
            ));
            text.addView(body);

            TextView mark = new TextView(this);
            mark.setText("-" + row.optInt("marks"));
            mark.setTextColor(Color.parseColor("#C2410C"));
            mark.setTypeface(Typeface.DEFAULT_BOLD);
            mark.setTextSize(14);
            mark.setBackgroundResource(R.drawable.late_marks_bg);
            int markPad = Math.round(8 * density);
            mark.setPadding(markPad, markPad / 2, markPad, markPad / 2);
            LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            markLp.leftMargin = pad / 2;
            mark.setLayoutParams(markLp);

            card.addView(avatar);
            card.addView(text);
            card.addView(mark);
            rows.addView(card);
        }
        paintStats(people.size(), shown.length(), marks);
        boolean filtered = (search.getText() != null && search.getText().toString().trim().length() > 0)
                || selectedClass.length() > 0;
        empty.setText(records.length() == 0 || !filtered ? R.string.late_empty : R.string.late_no_match);
        empty.setVisibility(shown.length() == 0 ? View.VISIBLE : View.GONE);
    }

    private String pretty(String ymd) {
        try {
            return new SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(ymd));
        } catch (Exception e) {
            return ymd;
        }
    }

    private String prettyMonth(String ym) {
        try {
            return new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    .format(new SimpleDateFormat("yyyy-MM", Locale.US).parse(ym));
        } catch (Exception e) {
            return ym;
        }
    }

    private interface DateChosen {
        void onDate(String value);
    }
}
