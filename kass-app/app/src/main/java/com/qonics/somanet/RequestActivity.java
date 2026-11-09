package com.qonics.somanet;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RequestActivity extends AppCompatActivity {
    private static final int[] TYPES = {0, 5, 1, 2, 3, 4};
    ProgressBar prb;
    EditText etreason, etaddress, etfrom, ettime, shortDay, regFrom, regTo, regDays, staffQuery, coverQuery, coverPhone;
    Spinner sptype;
    TextView txtinfo, staffPicked, txtHint, txtReasonLabel, txtLeaveNote, coverPicked;
    Button save;
    LinearLayout staffBox, shortPanel, hoursBlock, daysBlock, regularPanel, staffHits, coverHits;
    SharedPreferences preferences;
    String server = MainActivity.SERVER;
    boolean customerCare;
    boolean hoursMode = true;
    int hours = 2;
    int shortDays = 1;
    int staffId = 0;
    int coverStaffId = 0;
    String fromIso = "";
    String toIso = "";
    String shortIso = "";
    String startTime = "";
    final Handler searchHandler = new Handler(Looper.getMainLooper());
    Runnable searchTask;
    List<JSONObject> staffRows = new ArrayList<>();
    boolean pickingStaff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String postTitle = preferences.getString("soma_post_title", "").toLowerCase(Locale.US);
        customerCare = getIntent().getBooleanExtra("customer_care", false) || postTitle.contains("customer care");

        txtinfo = findViewById(R.id.txtinfo);
        txtinfo.setText(preferences.getString("soma_name", "") + "\n" + preferences.getString("soma_post_title", ""));
        txtHint = findViewById(R.id.txtHint);
        etreason = findViewById(R.id.lay_reason);
        etaddress = findViewById(R.id.lay_address);
        etfrom = findViewById(R.id.lay_date_from);
        ettime = findViewById(R.id.lay_time);
        shortDay = findViewById(R.id.shortDay);
        regFrom = findViewById(R.id.regFrom);
        regTo = findViewById(R.id.regTo);
        sptype = findViewById(R.id.lay_type);
        save = findViewById(R.id.btnSave);
        prb = findViewById(R.id.prb);
        prb.setVisibility(View.GONE);
        staffBox = findViewById(R.id.staffBox);
        shortPanel = findViewById(R.id.shortPanel);
        hoursBlock = findViewById(R.id.hoursBlock);
        daysBlock = findViewById(R.id.daysBlock);
        regularPanel = findViewById(R.id.regularPanel);
        staffQuery = findViewById(R.id.staffQuery);
        staffPicked = findViewById(R.id.staffPicked);
        staffHits = findViewById(R.id.staffHits);
        coverQuery = findViewById(R.id.coverQuery);
        coverPhone = findViewById(R.id.coverPhone);
        coverPicked = findViewById(R.id.coverPicked);
        coverHits = findViewById(R.id.coverHits);
        txtReasonLabel = findViewById(R.id.txtReasonLabel);
        txtLeaveNote = findViewById(R.id.txtLeaveNote);
        regDays = findViewById(R.id.regDays);

        sptype.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{
                getString(R.string.leave_select_type),
                getString(R.string.leave_short_option),
                getString(R.string.annual_leave),
                getString(R.string.sick_leave),
                getString(R.string.maternity_leave),
                getString(R.string.unpaid_leave)
        }));
        sptype.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                showType(TYPES[position]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        if (customerCare) {
            staffBox.setVisibility(View.VISIBLE);
            txtHint.setText(R.string.leave_pick_staff);
        }
        wireDates();
        wireHours();
        wireStaffSearch();
        wireCoverSearch();
        showType(0);
        save.setOnClickListener(view -> saveLeave());
    }

    private void showType(int type) {
        boolean chosen = type > 0;
        boolean shortLeave = type == 5;
        shortPanel.setVisibility(shortLeave ? View.VISIBLE : View.GONE);
        regularPanel.setVisibility(chosen && !shortLeave ? View.VISIBLE : View.GONE);
        txtLeaveNote.setVisibility(type == 2 || type == 3 ? View.VISIBLE : View.GONE);
        if (type == 5) {
            txtReasonLabel.setText(R.string.leave_reason_short);
            etreason.setHint(R.string.leave_reason_short);
        } else if (type == 4) {
            txtReasonLabel.setText(R.string.leave_reason_unpaid);
            etreason.setHint(R.string.leave_reason_unpaid);
        } else {
            txtReasonLabel.setText(R.string.lbl_reason);
            etreason.setHint(R.string.lbl_reason);
        }
        refreshHint();
    }

    private void wireDates() {
        etfrom.setOnClickListener(v -> pickDate(iso -> {
            fromIso = iso;
            etfrom.setText(iso);
            refreshHint();
        }));
        ettime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, hour, minute) -> {
                startTime = String.format(Locale.US, "%02d:%02d", hour, minute);
                ettime.setText(startTime);
                refreshHint();
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });
        shortDay.setOnClickListener(v -> pickDate(iso -> {
            shortIso = iso;
            shortDay.setText(iso);
            refreshHint();
        }));
        regFrom.setOnClickListener(v -> pickDate(iso -> {
            fromIso = iso;
            regFrom.setText(iso);
            refreshHint();
        }));
        regTo.setOnClickListener(v -> pickDate(iso -> {
            toIso = iso;
            regTo.setText(iso);
            refreshHint();
        }));
        findViewById(R.id.btnHours).setOnClickListener(v -> {
            hoursMode = true;
            hoursBlock.setVisibility(View.VISIBLE);
            daysBlock.setVisibility(View.GONE);
            paintChoices();
            refreshHint();
        });
        findViewById(R.id.btnDays).setOnClickListener(v -> {
            hoursMode = false;
            hoursBlock.setVisibility(View.GONE);
            daysBlock.setVisibility(View.VISIBLE);
            paintChoices();
            refreshHint();
        });
    }

    private void wireHours() {
        int[] ids = {R.id.chip1, R.id.chip2, R.id.chip3, R.id.chip4, R.id.chip6, R.id.chip8};
        int[] values = {1, 2, 3, 4, 6, 8};
        for (int i = 0; i < ids.length; i++) {
            int value = values[i];
            findViewById(ids[i]).setOnClickListener(v -> {
                hours = value;
                paintChoices();
                refreshHint();
            });
        }
        findViewById(R.id.day1).setOnClickListener(v -> { shortDays = 1; paintChoices(); refreshHint(); });
        findViewById(R.id.day2).setOnClickListener(v -> { shortDays = 2; paintChoices(); refreshHint(); });
        findViewById(R.id.day3).setOnClickListener(v -> { shortDays = 3; paintChoices(); refreshHint(); });
        paintChoices();
    }

    private void paintChoices() {
        paintOne(R.id.btnHours, hoursMode);
        paintOne(R.id.btnDays, !hoursMode);
        int[] chips = {R.id.chip1, R.id.chip2, R.id.chip3, R.id.chip4, R.id.chip6, R.id.chip8};
        int[] values = {1, 2, 3, 4, 6, 8};
        for (int i = 0; i < chips.length; i++) paintOne(chips[i], hours == values[i]);
        paintOne(R.id.day1, shortDays == 1);
        paintOne(R.id.day2, shortDays == 2);
        paintOne(R.id.day3, shortDays == 3);
    }

    private void paintOne(int id, boolean on) {
        Button button = findViewById(id);
        button.setBackgroundColor(on ? 0xFF0A8FA7 : 0xFFE2E8F0);
        button.setTextColor(on ? 0xFFFFFFFF : 0xFF0F172A);
    }

    private void wireCoverSearch() {
        coverQuery.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pickingStaff) return;
                coverStaffId = 0;
                coverPicked.setVisibility(View.GONE);
                if (searchTask != null) searchHandler.removeCallbacks(searchTask);
                String q = s.toString().trim();
                searchTask = () -> searchCover(q);
                searchHandler.postDelayed(searchTask, 180);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void pickCover(JSONObject row) {
        pickingStaff = true;
        coverStaffId = row.optInt("id");
        String name = row.optString("name");
        String phone = row.optString("phone");
        coverQuery.setText(name);
        coverQuery.setSelection(name.length());
        if (!phone.isEmpty()) coverPhone.setText(phone);
        pickingStaff = false;
        coverPicked.setText("Selected: " + name + ". They will get an SMS.");
        coverPicked.setVisibility(View.VISIBLE);
        coverHits.setVisibility(View.GONE);
    }

    private void searchCover(String q) {
        if (q.length() < 1) {
            coverHits.setVisibility(View.GONE);
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("actor_id", preferences.getInt("soma_id", 0));
        map.put("q", q);
        map.put("cover", 1);
        new HttpRequest(this).post(server + "staff_leave_search", map, (object, status_code, status) -> {
            if (!q.equals(coverQuery.getText().toString().trim())) return;
            coverHits.removeAllViews();
            if (object == null || object.has("error")) {
                coverHits.setVisibility(View.GONE);
                return;
            }
            JSONArray rows = object.optJSONArray("staffs");
            if (rows == null || rows.length() == 0) {
                coverHits.setVisibility(View.GONE);
                return;
            }
            int gap = Math.round(6 * getResources().getDisplayMetrics().density);
            int me = preferences.getInt("soma_id", 0);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || row.optInt("id") == me) continue;
                String phone = row.optString("phone");
                TextView hit = new TextView(this);
                hit.setText(phone.isEmpty() ? row.optString("name") : row.optString("name") + " · " + phone);
                hit.setTextSize(16);
                hit.setTextColor(0xFF0F172A);
                hit.setBackgroundResource(R.drawable.staff_hit_bg);
                hit.setPadding(gap * 2, gap * 2, gap * 2, gap * 2);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = gap;
                hit.setLayoutParams(lp);
                JSONObject chosen = row;
                hit.setOnClickListener(v -> pickCover(chosen));
                coverHits.addView(hit);
            }
            coverHits.setVisibility(coverHits.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        });
    }

    private void wireStaffSearch() {
        staffQuery.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pickingStaff) return;
                staffId = 0;
                staffPicked.setVisibility(View.GONE);
                if (searchTask != null) searchHandler.removeCallbacks(searchTask);
                String q = s.toString().trim();
                searchTask = () -> searchStaff(q);
                searchHandler.postDelayed(searchTask, 180);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void pickStaff(JSONObject row) {
        pickingStaff = true;
        staffId = row.optInt("id");
        String name = row.optString("name");
        String phone = row.optString("phone");
        staffQuery.setText(name);
        staffQuery.setSelection(name.length());
        pickingStaff = false;
        staffPicked.setText(phone.isEmpty() ? name : name + " · " + phone);
        staffPicked.setVisibility(View.VISIBLE);
        staffHits.setVisibility(View.GONE);
    }

    private void searchStaff(String q) {
        if (!customerCare || q.length() < 1) {
            staffHits.setVisibility(View.GONE);
            return;
        }
        showSearchStatus(getString(R.string.leave_searching));
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("actor_id", preferences.getInt("soma_id", 0));
        map.put("q", q);
        new HttpRequest(this).post(server + "staff_leave_search", map, (object, status_code, status) -> {
            if (!q.equals(staffQuery.getText().toString().trim())) return;
            staffHits.removeAllViews();
            staffRows.clear();
            if (object == null) {
                showSearchStatus(getString(R.string.lbl_error) + status);
                return;
            }
            if (object.has("error")) {
                showSearchStatus(object.optString("error"));
                return;
            }
            JSONArray rows = object.optJSONArray("staffs");
            if (rows == null || rows.length() == 0) {
                showSearchStatus("No staff found");
                return;
            }
            int gap = Math.round(6 * getResources().getDisplayMetrics().density);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                staffRows.add(row);
                String phone = row.optString("phone");
                TextView hit = new TextView(this);
                hit.setText(phone.isEmpty() ? row.optString("name") : row.optString("name") + " · " + phone);
                hit.setTextSize(16);
                hit.setTextColor(0xFF0F172A);
                hit.setBackgroundResource(R.drawable.staff_hit_bg);
                hit.setPadding(gap * 2, gap * 2, gap * 2, gap * 2);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = gap;
                hit.setLayoutParams(lp);
                JSONObject chosen = row;
                hit.setOnClickListener(v -> pickStaff(chosen));
                staffHits.addView(hit);
            }
            staffHits.setVisibility(View.VISIBLE);
        });
    }

    private void showSearchStatus(String text) {
        staffHits.removeAllViews();
        TextView hit = new TextView(this);
        hit.setText(text);
        hit.setTextColor(0xFF64748B);
        hit.setPadding(8, 12, 8, 8);
        staffHits.addView(hit);
        staffHits.setVisibility(View.VISIBLE);
    }

    private void pickDate(DateChosen chosen) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) ->
                chosen.onDate(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void refreshHint() {
        int type = TYPES[Math.max(sptype.getSelectedItemPosition(), 0)];
        if (type == 0) {
            txtHint.setText(R.string.leave_choose_type);
            return;
        }
        String text;
        if (customerCare) {
            text = "Saving this approves it in your name. The gate shows green ";
        } else {
            text = "After approval, the gate shows green ";
        }
        if (type == 5 && hoursMode) {
            text += fromIso.isEmpty() || startTime.isEmpty()
                    ? "Pick the day, start time, and how many hours."
                    : "Green for " + hours + " hour(s) from " + fromIso + " " + startTime + ".";
        } else if (type == 5) {
            text += shortIso.isEmpty()
                    ? "Pick the start day and 1, 2, or 3 days."
                    : "Green for " + shortDays + " day(s) starting " + shortIso + ".";
        } else {
            text += fromIso.isEmpty() || toIso.isEmpty()
                    ? "Pick the from and to dates."
                    : "Green from " + fromIso + " through " + toIso + ".";
        }
        text += " The tablet must be online.";
        txtHint.setText(text);
    }

    private void saveLeave() {
        int type = TYPES[Math.max(sptype.getSelectedItemPosition(), 0)];
        if (type < 1) {
            Toast.makeText(this, R.string.leave_select_type, Toast.LENGTH_LONG).show();
            return;
        }
        if (customerCare && staffId < 1) {
            Toast.makeText(this, R.string.leave_pick_staff, Toast.LENGTH_LONG).show();
            return;
        }
        String coverName = coverQuery.getText().toString().trim();
        String coverTel = coverPhone.getText().toString().trim();
        if (coverName.isEmpty() || coverTel.isEmpty()) {
            Toast.makeText(this, R.string.leave_cover_required, Toast.LENGTH_LONG).show();
            return;
        }
        int requester = customerCare ? staffId : preferences.getInt("soma_id", 0);
        if (coverStaffId > 0 && coverStaffId == requester) {
            Toast.makeText(this, R.string.leave_cover_self, Toast.LENGTH_LONG).show();
            return;
        }
        String reason = etreason.getText().toString().trim();
        if ((type == 4 || type == 5) && reason.isEmpty()) {
            etreason.setError(getString(R.string.lbl_reason));
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("actor_id", preferences.getInt("soma_id", 0));
        map.put("staff_id", staffId);
        map.put("type", type);
        map.put("reason", reason);
        map.put("address", etaddress.getText().toString().trim());
        map.put("cover_name", coverName);
        map.put("cover_phone", coverTel);
        map.put("cover_staff_id", coverStaffId);
        map.put("days", regDays.getText().toString().trim());
        if (type == 5) {
            map.put("span", hoursMode ? "hours" : "days");
            map.put("fdate", hoursMode ? fromIso : shortIso);
            map.put("ftime", startTime);
            map.put("hours", hours);
            map.put("short_days", shortDays);
        } else {
            map.put("fdate", fromIso);
            map.put("tdate", toIso);
        }
        new HttpRequest(this).progress(prb).hide(save).post(server + "staff_leave_save", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    Snackbar.make(save, getString(R.string.lbl_error) + status, Snackbar.LENGTH_LONG).show();
                    return;
                }
                try {
                    if (object.has("error")) {
                        Snackbar.make(save, object.getString("error"), Snackbar.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(RequestActivity.this, object.optString("success"), Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private interface DateChosen {
        void onDate(String iso);
    }
}
