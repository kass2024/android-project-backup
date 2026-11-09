package com.qonics.somanet;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Live Wisdom campus dashboard. Online only.
 */
public class SchoolMonitorActivity extends AppCompatActivity {
    private static final int BLUE = 0xFF1D4ED8;
    private static final int GREEN = 0xFF16A34A;
    private static final int RED = 0xFFDC2626;
    private static final int AMBER = 0xFFD97706;
    private static final int TEAL = 0xFF0A8FA7;
    private static final int INDIGO = 0xFF4F46E5;
    private static final int ROSE = 0xFFE11D48;
    private static final int INK = 0xFF0F172A;
    private static final int MUTED = 0xFF64748B;

    private LinearLayout body;
    private ProgressBar progress;
    private TextView status;
    private JSONObject last;
    private final Set<Integer> open = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F1F5F9"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(TEAL);
        bar.setPadding(dp(6), dp(8), dp(8), dp(8));
        bar.setElevation(dp(4));
        TextView back = label("‹  Back", 16, Color.WHITE, true);
        back.setPadding(dp(10), dp(8), dp(10), dp(8));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("All schools Monitoring", 16, Color.WHITE, true);
        TextView sub = label("Live campuses", 11, Color.parseColor("#D1FAE5"), false);
        titles.addView(title);
        titles.addView(sub);
        bar.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView refresh = label("Refresh", 13, Color.WHITE, true);
        refresh.setPadding(dp(10), dp(8), dp(10), dp(8));
        refresh.setOnClickListener(v -> load());
        bar.addView(refresh);
        root.addView(bar);

        progress = new ProgressBar(this);
        progress.setPadding(0, dp(8), 0, 0);
        root.addView(progress);

        status = label("Loading live campuses…", 13, MUTED, false);
        status.setPadding(dp(16), dp(8), dp(16), dp(4));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), dp(28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        load();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void load() {
        if (!MainActivity.isNetworkAvailable(this)) {
            progress.setVisibility(View.GONE);
            status.setText("This dashboard needs the internet. Connect, then tap Refresh.");
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!canOpen(preferences)) {
            progress.setVisibility(View.GONE);
            status.setText("School monitoring is open to Director, Deputy Director, Executive Principal, Director of Finance, and Customer Care.");
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("post_id", preferences.getInt("soma_post", 0));
        progress.setVisibility(View.VISIBLE);
        status.setText("Loading live campuses…");
        new HttpRequest(this).timeouts(20, 60).post(MainActivity.SERVER + "school_monitor", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int statusCode, String statusText) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (object == null) {
                        String why = statusText == null || statusText.length() == 0
                                ? "Could not reach the server"
                                : statusText;
                        status.setText(why);
                        return;
                    }
                    if (object.has("error")) {
                        status.setText(object.optString("error", "Could not load the dashboard"));
                        return;
                    }
                    last = object;
                    render(object);
                });
            }
        });
    }

    private void render(JSONObject data) {
        body.removeAllViews();
        JSONObject totals = data.optJSONObject("totals");
        JSONArray schools = data.optJSONArray("schools");
        int count = schools == null ? 0 : schools.length();
        boolean musanzeOnly = "musanze".equals(data.optString("scope")) || data.optBoolean("single", false);
        status.setText(count == 0
                ? "No campus data yet"
                : (musanzeOnly ? "Live today · Wisdom School Musanze" : "Live today · " + count + " campus" + (count == 1 ? "" : "es")));
        if (totals != null) {
            body.addView(section(musanzeOnly ? "Today" : "Today, all schools"));
            body.addView(kpiGrid(groupKpis(totals)));
        }
        if (schools == null) return;
        body.addView(section(musanzeOnly ? "This school" : "Each campus"));
        boolean anyOpen = !open.isEmpty();
        for (int i = 0; i < schools.length(); i++) {
            JSONObject school = schools.optJSONObject(i);
            if (school == null) continue;
            if (!anyOpen && i == 0) open.add(school.optInt("id"));
            body.addView(campusCard(school));
        }
    }

    private View campusCard(JSONObject school) {
        int id = school.optInt("id");
        LinearLayout card = panel();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(8));

        TextView name = label(school.optString("name", "School"), 16, INK, true);
        name.setSingleLine(false);
        name.setMaxLines(2);
        head.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (school.optBoolean("is_master")) {
            TextView badge = label(" Master ", 11, TEAL, true);
            badge.setBackground(round(Color.parseColor("#E0F7FA"), 20));
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.leftMargin = dp(6);
            badge.setLayoutParams(bp);
            head.addView(badge);
        }
        TextView chevron = label(open.contains(id) ? "▴" : "▾", 16, TEAL, true);
        chevron.setPadding(dp(8), 0, dp(2), 0);
        head.addView(chevron);
        card.addView(head);

        boolean master = school.optBoolean("is_master");
        int sid = school.optInt("id");
        JSONObject att = school.optJSONObject("att_summary");
        LinearLayout roster = new LinearLayout(this);
        roster.setOrientation(LinearLayout.VERTICAL);
        roster.setVisibility(View.GONE);
        TextView rosterStatus = label("Tap a number to see classes", 11, MUTED, false);
        rosterStatus.setPadding(0, dp(4), 0, dp(4));
        if (master) {
            card.addView(section("Attendance today"));
            card.addView(kpiGrid(new Kpi[]{
                    kpi(school, "students", "Students", BLUE),
                    link(registerKpi(att, "daily_present", "Total present", "School gate and boarding", GREEN), sid, "present", roster, rosterStatus),
                    link(registerKpi(att, "day_present", "Dayscholar present", "School gate", BLUE), sid, "day", roster, rosterStatus),
                    link(registerKpi(att, "board_present", "Boarding present", "Boarding device", TEAL), sid, "board", roster, rosterStatus),
                    link(registerKpi(att, "girls_present", "Girls present", "Of those present", ROSE), sid, "girls", roster, rosterStatus),
                    link(registerKpi(att, "boys_present", "Boys present", "Of those present", GREEN), sid, "boys", roster, rosterStatus),
                    link(registerKpi(att, "daily_absent", "Absent students", "Not recorded on the device", RED), sid, "absent", roster, rosterStatus)
            }));
            card.addView(section("Staff"));
            card.addView(kpiGrid(new Kpi[]{
                    link(kpi(school, "staff_present", "Staff in", GREEN), sid, "staff_in", roster, rosterStatus),
                    link(kpi(school, "staff_absent", "Staff absent", RED), sid, "staff_out", roster, rosterStatus)
            }));
            JSONObject visitors = school.optJSONObject("daily_visitors");
            card.addView(pulseCard("Daily visitors", "Today", new String[]{
                    format(visitors == null ? 0 : visitors.optInt("visits")),
                    format(visitors == null ? 0 : visitors.optInt("still_inside")),
                    format(visitors == null ? 0 : visitors.optInt("checked_out"))
            }, new String[]{"Visits", "Still inside", "Checked out"},
                    new int[]{BLUE, AMBER, GREEN},
                    new String[]{"visit", "visit_in", "visit_out"},
                    sid, roster, rosterStatus, ""));
            JSONObject parents = school.optJSONObject("parent_visits");
            int parentClasses = parents == null ? 0 : parents.optInt("classes");
            int parentStudents = parents == null ? 0 : parents.optInt("students");
            card.addView(pulseCard("Parent visit", "Today", new String[]{
                    format(parents == null ? 0 : parents.optInt("visited")),
                    format(parents == null ? 0 : parents.optInt("not_visited")),
                    format(parents == null ? 0 : parents.optInt("check_ins"))
            }, new String[]{"Visited", "Not visited", "Check-ins"},
                    new int[]{GREEN, AMBER, BLUE},
                    new String[]{"parent_in", "parent_out", "parent_checks"},
                    sid, roster, rosterStatus,
                    format(parentClasses) + " classes · " + format(parentStudents) + " students"));
        } else {
            card.addView(kpiGrid(new Kpi[]{
                    kpi(school, "students", "Students", BLUE),
                    link(registerKpi(att, "daily_present", "Present students", "On today's register", GREEN), sid, "present", roster, rosterStatus),
                    link(registerKpi(att, "daily_absent", "Absent students", "Not on today's register", RED), sid, "absent", roster, rosterStatus),
                    link(kpi(school, "staff_present", "Staff in", GREEN), sid, "staff_in", roster, rosterStatus),
                    link(kpi(school, "staff_absent", "Staff absent", RED), sid, "staff_out", roster, rosterStatus)
            }));
        }
        card.addView(rosterStatus);
        card.addView(roster);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(open.contains(id) ? View.VISIBLE : View.GONE);
        details.addView(kpiGrid(master
                ? new Kpi[]{
                kpi(school, "boys", "Boys enrolled", GREEN),
                kpi(school, "girls", "Girls enrolled", ROSE),
                kpi(school, "student_out", "Students out", AMBER),
                kpi(school, "student_inside", "Still inside", TEAL),
                kpi(school, "staff", "Staff", INDIGO)
        }
                : new Kpi[]{
                kpi(school, "boys", "Boys", GREEN),
                kpi(school, "girls", "Girls", ROSE),
                kpi(school, "staff", "Staff", INDIGO)
        }));
        addLocations(details, school.optJSONArray("locations"));
        addPrep(details, school.optJSONObject("prep_summary"));
        addArrivalKpis(details, school.optJSONArray("late_today"), school.optJSONArray("early_today"));
        card.addView(details);

        head.setOnClickListener(v -> {
            boolean show = details.getVisibility() != View.VISIBLE;
            details.setVisibility(show ? View.VISIBLE : View.GONE);
            chevron.setText(show ? "▴" : "▾");
            if (show) open.add(id);
            else open.remove(id);
        });
        return card;
    }

    private Kpi[] groupKpis(JSONObject stats) {
        return new Kpi[]{
                kpi(stats, "students", "Students", BLUE),
                kpi(stats, "boys", "Boys", GREEN),
                kpi(stats, "girls", "Girls", ROSE),
                kpi(stats, "staff", "Staff", INDIGO),
                kpi(stats, "students_present", "Students in", GREEN),
                kpi(stats, "student_out", "Students out", AMBER),
                kpi(stats, "student_inside", "Still inside", TEAL),
                kpi(stats, "staff_present", "Staff in", GREEN),
                kpi(stats, "staff_absent", "Staff absent", RED)
        };
    }

    private Kpi kpi(JSONObject stats, String key, String caption, int color) {
        int value = stats.optInt(key);
        String hint = "";
        if ("students_present".equals(key) && stats.optInt("students") > 0) {
            hint = Math.round(value * 100f / stats.optInt("students")) + "% of students";
        } else if ("staff_present".equals(key) && stats.optInt("staff") > 0) {
            hint = Math.round(value * 100f / stats.optInt("staff")) + "% of staff";
        }
        return new Kpi(format(value), caption, hint, color);
    }

    private View kpiGrid(Kpi[] items) {
        int cols = columns();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = dp(8);
                row.setLayoutParams(rp);
                wrap.addView(row);
            }
            View tile = kpiTile(items[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i % cols != 0) lp.leftMargin = dp(8);
            row.addView(tile, lp);
        }
        int remainder = items.length % cols;
        if (row != null && remainder != 0) {
            for (int i = remainder; i < cols; i++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 0, 1f);
                lp.leftMargin = dp(8);
                row.addView(spacer, lp);
            }
        }
        return wrap;
    }

    private View kpiTile(Kpi kpi) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackground(round(Color.WHITE, 14));
        tile.setElevation(dp(2));
        tile.setPadding(0, 0, 0, dp(10));

        View accent = new View(this);
        accent.setBackgroundColor(kpi.color);
        tile.addView(accent, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(12), dp(10), dp(12), 0);
        TextView value = label(kpi.value, 22, kpi.color, true);
        TextView caption = label(kpi.label, 12, MUTED, false);
        caption.setSingleLine(false);
        inner.addView(value);
        inner.addView(caption);
        if (kpi.hint.length() > 0) {
            TextView hint = label(kpi.hint, 11, kpi.color, false);
            hint.setPadding(0, dp(2), 0, 0);
            inner.addView(hint);
        }
        tile.addView(inner);
        if (kpi.mode != null && kpi.mode.length() > 0 && kpi.list != null) {
            tile.setClickable(true);
            tile.setOnClickListener(v -> openRoster(kpi.schoolId, kpi.mode, kpi.list, kpi.status, kpi.label));
        }
        return tile;
    }

    private Kpi link(Kpi kpi, int schoolId, String mode, LinearLayout list, TextView status) {
        kpi.schoolId = schoolId;
        kpi.mode = mode;
        kpi.list = list;
        kpi.status = status;
        return kpi;
    }

    private void openRoster(int schoolId, String mode, LinearLayout list, TextView statusLine, String caption) {
        try {
            JSONObject loc = new JSONObject();
            loc.put("school_id", schoolId);
            loc.put("roster", 1);
            loc.put("name", caption);
            openLocation(loc, mode, list, statusLine, caption);
        } catch (Exception ignored) {
        }
    }

    private Kpi registerKpi(JSONObject att, String key, String caption, String hint, int color) {
        JSONObject stats = att == null ? new JSONObject() : att;
        return new Kpi(format(stats.optInt(key)), caption, hint, color);
    }

    private View pulseCard(String title, String when, String[] values, String[] captions, int[] colors,
                           String[] modes, int schoolId, LinearLayout list, TextView statusLine, String footnote) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(Color.parseColor("#EFF6FF"), 16));
        card.setElevation(dp(2));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(title, 15, BLUE, true);
        head.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tag = label(when, 11, MUTED, true);
        head.addView(tag);
        card.addView(head);

        LinearLayout nums = new LinearLayout(this);
        nums.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(8);
        nums.setLayoutParams(np);
        for (int i = 0; i < values.length; i++) {
            LinearLayout pill = new LinearLayout(this);
            pill.setOrientation(LinearLayout.VERTICAL);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(4), dp(8), dp(4), dp(8));
            pill.setBackground(round(Color.parseColor("#F8FAFC"), 10));
            TextView number = label(values[i], 20, colors[i], true);
            number.setGravity(Gravity.CENTER);
            TextView cap = label(captions[i], 11, MUTED, false);
            cap.setGravity(Gravity.CENTER);
            cap.setSingleLine(false);
            cap.setMaxLines(2);
            pill.addView(number);
            pill.addView(cap);
            if (modes != null && i < modes.length && list != null) {
                String mode = modes[i];
                String caption = captions[i];
                pill.setClickable(true);
                pill.setOnClickListener(v -> openRoster(schoolId, mode, list, statusLine, title + " · " + caption));
            }
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) pp.leftMargin = dp(6);
            nums.addView(pill, pp);
        }
        card.addView(nums);
        if (footnote != null && footnote.length() > 0) {
            TextView note = label(footnote, 12, MUTED, false);
            note.setPadding(0, dp(6), 0, 0);
            card.addView(note);
        }
        return card;
    }

    private void addLocations(LinearLayout parent, JSONArray locations) {
        if (locations == null || locations.length() == 0) return;
        parent.addView(section("Student flow by location"));
        for (int i = 0; i < locations.length(); i++) {
            JSONObject loc = locations.optJSONObject(i);
            if (loc == null) continue;
            parent.addView(locationCard(loc));
        }
    }

    private View locationCard(JSONObject loc) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(Color.WHITE, 16));
        card.setElevation(dp(2));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView name = label(loc.optString("name"), 15, INK, true);
        name.setSingleLine(false);
        card.addView(name);

        LinearLayout nums = new LinearLayout(this);
        nums.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(8);
        nums.setLayoutParams(np);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setVisibility(View.GONE);
        TextView statusLine = label("Tap a number to list by class", 11, MUTED, false);
        statusLine.setPadding(0, dp(6), 0, 0);

        int tapped = loc.has("students_in") ? loc.optInt("students_in") : loc.optInt("day_in");
        nums.addView(countPill(format(tapped), "Students in", BLUE, loc, "in", list, statusLine), pillParams(false));
        nums.addView(countPill(format(loc.optInt("absent")), "Absent", RED, loc, "absent", list, statusLine), pillParams(true));
        LinearLayout nums2 = new LinearLayout(this);
        nums2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams np2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        np2.topMargin = dp(8);
        nums2.setLayoutParams(np2);
        nums2.addView(countPill(format(loc.optInt("checked_out")), "Out", AMBER, loc, "out", list, statusLine), pillParams(false));
        View spacer = new View(this);
        nums2.addView(spacer, pillParams(true));
        card.addView(nums);
        card.addView(nums2);
        card.addView(statusLine);
        card.addView(list);
        return card;
    }

    private LinearLayout.LayoutParams pillParams(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (gap) lp.leftMargin = dp(8);
        return lp;
    }

    private View countPill(String value, String caption, int color, JSONObject loc, String mode,
                           LinearLayout list, TextView statusLine) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(6), dp(8), dp(6), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)));
        bg.setCornerRadius(dp(12));
        pill.setBackground(bg);
        TextView number = label(value, 22, color, true);
        number.setGravity(Gravity.CENTER);
        TextView cap = label(caption, 11, color, true);
        cap.setGravity(Gravity.CENTER);
        cap.setSingleLine(false);
        cap.setMaxLines(2);
        pill.addView(number);
        pill.addView(cap);
        pill.setOnClickListener(v -> openLocation(loc, mode, list, statusLine, caption));
        return pill;
    }

    private void openLocation(JSONObject loc, String mode, LinearLayout list, TextView statusLine, String caption) {
        LocCache cache = list.getTag() instanceof LocCache ? (LocCache) list.getTag() : new LocCache();
        list.setTag(cache);
        if (list.getVisibility() == View.VISIBLE && mode.equals(cache.mode) && cache.className.length() == 0) {
            list.setVisibility(View.GONE);
            statusLine.setText("Tap a number to list by class");
            return;
        }
        cache.mode = mode;
        cache.className = "";
        list.setVisibility(View.VISIBLE);
        JSONArray known = cache.classes.get(mode);
        if (known != null) {
            paintClasses(list, known, loc, mode, statusLine, caption);
            return;
        }
        fetchLocation(loc, mode, "", list, statusLine, caption);
    }

    private void fetchLocation(JSONObject loc, String mode, String className, LinearLayout list,
                               TextView statusLine, String caption) {
        list.removeAllViews();
        statusLine.setText("Loading " + loc.optString("name") + "…");
        list.addView(new ProgressBar(this));
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, Object> map = new HashMap<>();
        int areaSchool = loc.optInt("school_id");
        map.put("school_id", areaSchool > 0 ? areaSchool : preferences.getInt(MainActivity.SK_ID, 0));
        map.put("post_id", preferences.getInt("soma_post", 0));
        map.put("area_id", loc.optInt("id"));
        map.put("mode", mode);
        if (className.length() > 0) map.put("class", className);
        boolean roster = loc.optInt("roster") == 1;
        String endpoint = roster ? "school_monitor_people" : "school_monitor_location";
        if (!roster) map.put("area_id", loc.optInt("id"));
        new HttpRequest(this).timeouts(20, 60).post(MainActivity.SERVER + endpoint, map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int statusCode, String statusText) {
                runOnUiThread(() -> {
                    list.removeAllViews();
                    if (object == null || object.has("error")) {
                        String why = object == null
                                ? (statusText == null || statusText.length() == 0 ? "Could not load the list" : statusText)
                                : object.optString("error", "Could not load the list");
                        list.addView(label(why, 12, RED, false));
                        return;
                    }
                    LocCache cache = list.getTag() instanceof LocCache ? (LocCache) list.getTag() : new LocCache();
                    if (!mode.equals(cache.mode)) return;
                    list.setTag(cache);
                    JSONArray people = object.optJSONArray("people");
                    if (className.length() > 0 && people != null) {
                        cache.className = className;
                        paintPeople(list, people, loc, mode, className, statusLine, caption);
                        return;
                    }
                    JSONArray classes = object.optJSONArray("classes");
                    if (classes == null) classes = new JSONArray();
                    cache.classes.put(mode, classes);
                    cache.className = "";
                    paintClasses(list, classes, loc, mode, statusLine, caption);
                });
            }
        });
    }

    private void paintClasses(LinearLayout list, JSONArray classes, JSONObject loc, String mode,
                              TextView statusLine, String caption) {
        list.removeAllViews();
        int total = 0;
        for (int i = 0; i < classes.length(); i++) {
            JSONObject row = classes.optJSONObject(i);
            if (row != null) total += row.optInt("count");
        }
        statusLine.setText(caption + " · " + format(total) + " · choose a class");
        EditText search = searchBox("Search class");
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        TextView empty = label("No class in this list", 12, MUTED, false);
        empty.setPadding(0, dp(8), 0, dp(4));
        Runnable paint = () -> {
            rows.removeAllViews();
            String query = search.getText().toString().trim().toLowerCase(Locale.US);
            int shown = 0;
            for (int i = 0; i < classes.length(); i++) {
                JSONObject row = classes.optJSONObject(i);
                if (row == null) continue;
                String name = row.optString("name");
                if (query.length() > 0 && !name.toLowerCase(Locale.US).contains(query)) continue;
                rows.addView(classRow(name, row.optInt("count"), () ->
                        fetchLocation(loc, mode, name, list, statusLine, caption)));
                shown++;
            }
            empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { paint.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        list.addView(search);
        list.addView(empty);
        list.addView(rows);
        paint.run();
    }

    private View classRow(String name, int count, Runnable open) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        TextView title = label(name, 15, INK, true);
        title.setSingleLine(false);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = label("  " + format(count) + "  ", 14, Color.WHITE, true);
        badge.setBackground(round(TEAL, 20));
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        row.addView(badge);
        row.setOnClickListener(v -> open.run());
        return row;
    }

    private void paintPeople(LinearLayout list, JSONArray people, JSONObject loc, String mode, String className,
                             TextView statusLine, String caption) {
        list.removeAllViews();
        statusLine.setText(className + " · " + format(people.length()) + " " + caption.toLowerCase(Locale.US));
        TextView back = label("‹  All classes", 14, TEAL, true);
        back.setPadding(0, dp(4), 0, dp(8));
        back.setOnClickListener(v -> {
            LocCache cache = list.getTag() instanceof LocCache ? (LocCache) list.getTag() : null;
            JSONArray known = cache == null ? null : cache.classes.get(mode);
            if (known != null) {
                cache.className = "";
                paintClasses(list, known, loc, mode, statusLine, caption);
            }
        });
        EditText search = searchBox("Search name");
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        TextView empty = label("No students in this class", 12, MUTED, false);
        empty.setPadding(0, dp(8), 0, dp(4));
        Runnable paint = () -> {
            rows.removeAllViews();
            String query = search.getText().toString().trim().toLowerCase(Locale.US);
            int shown = 0;
            for (int i = 0; i < people.length(); i++) {
                JSONObject person = people.optJSONObject(i);
                if (person == null) continue;
                String blob = (person.optString("name") + " " + person.optString("class") + " " + person.optString("note")).toLowerCase(Locale.US);
                if (query.length() > 0 && !blob.contains(query)) continue;
                rows.addView(locationPerson(person));
                shown++;
            }
            empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { paint.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        list.addView(back);
        list.addView(search);
        list.addView(empty);
        list.addView(rows);
        paint.run();
    }

    private EditText searchBox(String hint) {
        EditText search = new EditText(this);
        search.setHint(hint);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setBackground(round(Color.parseColor("#F8FAFC"), 10));
        search.setPadding(dp(10), dp(8), dp(10), dp(8));
        return search;
    }

    private View locationPerson(JSONObject person) {
        String status = person.optString("status");
        boolean absent = "absent".equals(status);
        boolean out = "out".equals(status);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView who = label(person.optString("name"), 14, INK, true);
        who.setSingleLine(false);
        text.addView(who);
        String cls = person.optString("class");
        if (cls.length() > 0) text.addView(label(cls, 12, TEAL, true));
        String when = person.optString("time_in");
        if (when.length() > 0) text.addView(label("In " + when, 11, MUTED, false));
        String note = person.optString("note");
        if (note.length() > 0) text.addView(label(note, 11, MUTED, false));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String mark = absent ? "  Absent  " : (out ? "  Out " + person.optString("time_out") + "  " : "  In  ");
        TextView badge = label(mark, 12, Color.WHITE, true);
        badge.setBackground(round(absent ? RED : (out ? AMBER : GREEN), 20));
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        row.addView(badge);
        return row;
    }

    private void addPrep(LinearLayout parent, JSONObject prep) {
        if (prep == null) return;
        addPrepSlot(parent, prep.optJSONObject("morning"), "Morning prep");
        addPrepSlot(parent, prep.optJSONObject("evening"), "Evening prep");
    }

    private void addPrepSlot(LinearLayout parent, JSONObject slot, String title) {
        if (slot == null) return;
        boolean evening = title.toLowerCase(Locale.US).contains("evening");
        if (evening && prepNotOpen(slot)) {
            parent.addView(waitingCard(slot.optString("starts", "19:00")));
            return;
        }
        parent.addView(section(title));
        parent.addView(kpiGrid(new Kpi[]{
                new Kpi(format(slot.optInt("present")), "Present", "", GREEN),
                new Kpi(format(slot.optInt("absent")), "Absent", "", RED)
        }));
        JSONArray people = slot.optJSONArray("people");
        if (people == null) return;
        int limit = Math.min(people.length(), 6);
        for (int i = 0; i < limit; i++) {
            JSONObject person = people.optJSONObject(i);
            if (person == null) continue;
            boolean absent = !"present".equals(person.optString("status"));
            String mark = absent
                    ? "Absent"
                    : (person.optString("time").length() > 0 ? person.optString("time") : "Present");
            String post = person.optString("post");
            parent.addView(personLine(person.optString("name"), post, mark, absent ? RED : GREEN));
        }
    }

    private boolean prepNotOpen(JSONObject slot) {
        if (slot.optBoolean("pending")) return true;
        Calendar clock = Calendar.getInstance();
        int now = clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE);
        return now < clockMinutes(slot.optString("starts", "19:00"));
    }

    private int clockMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        int hour = 19;
        int minute = 0;
        try {
            if (parts.length > 0) hour = Integer.parseInt(parts[0].trim());
            if (parts.length > 1) minute = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) {
            hour = 19;
            minute = 0;
        }
        return hour * 60 + minute;
    }

    private View waitingCard(String starts) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(Color.parseColor("#FFF7ED"), 14));
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(6);
        box.setLayoutParams(lp);
        box.addView(label("Evening prep has not started", 15, AMBER, true));
        box.addView(label("Opens at " + starts, 22, AMBER, true));
        TextView note = label("Absence is counted only after " + starts + ".", 12, MUTED, false);
        note.setPadding(0, dp(4), 0, 0);
        box.addView(note);
        return box;
    }

    private void addArrivalKpis(LinearLayout parent, JSONArray late, JSONArray early) {
        parent.addView(section("Arrivals today"));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout lateList = arrivalList(late, true);
        LinearLayout earlyList = arrivalList(early, false);
        lateList.setVisibility(View.GONE);
        earlyList.setVisibility(View.GONE);
        View lateTile = arrivalTile(late, "Late today", "No one late after shift start", AMBER, true, lateList, earlyList);
        View earlyTile = arrivalTile(early, "Earliest today", "No early arrivals", TEAL, false, earlyList, lateList);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = dp(8);
        row.addView(lateTile, left);
        row.addView(earlyTile, right);
        parent.addView(row);
        parent.addView(lateList);
        parent.addView(earlyList);
    }

    private View arrivalTile(JSONArray people, String title, String empty, int color, boolean late,
                             LinearLayout ownList, LinearLayout otherList) {
        int count = people == null ? 0 : people.length();
        String hint = count == 0 ? empty : (late ? "Up to " + maxMinutes(people) + " min late" : "Up to " + maxMinutes(people) + " min early");
        LinearLayout tile = (LinearLayout) kpiTile(new Kpi(format(count), title, hint, color));
        if (count == 0) return tile;
        TextView tap = label("Tap for names", 11, color, false);
        tap.setPadding(dp(12), 0, dp(12), dp(8));
        tile.addView(tap);
        tile.setOnClickListener(v -> {
            boolean show = ownList.getVisibility() != View.VISIBLE;
            ownList.setVisibility(show ? View.VISIBLE : View.GONE);
            otherList.setVisibility(View.GONE);
            tap.setText(show ? "Tap to hide" : "Tap for names");
        });
        return tile;
    }

    private LinearLayout arrivalList(JSONArray people, boolean late) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(4));
        if (people == null) return box;
        for (int i = 0; i < people.length(); i++) {
            JSONObject person = people.optJSONObject(i);
            if (person == null) continue;
            int minutes = person.optInt("minutes");
            String time = person.optString("time");
            String mark = time;
            if (minutes > 0) mark = (mark.length() > 0 ? mark + " · " : "") + minutes + " min";
            box.addView(personLine(person.optString("name"), person.optString("post"), mark, late ? AMBER : TEAL));
        }
        return box;
    }

    private int maxMinutes(JSONArray people) {
        int max = 0;
        if (people == null) return 0;
        for (int i = 0; i < people.length(); i++) {
            JSONObject person = people.optJSONObject(i);
            if (person != null) max = Math.max(max, person.optInt("minutes"));
        }
        return max;
    }

    private boolean canOpen(SharedPreferences preferences) {
        return com.qonics.utils.MenuClearanceHelper.canOpenSchoolMonitor(preferences);
    }

    private void addPeople(LinearLayout parent, JSONArray people, String title, String empty) {
        parent.addView(section(title));
        if (people == null || people.length() == 0) {
            parent.addView(label(empty, 12, MUTED, false));
            return;
        }
        for (int i = 0; i < people.length(); i++) {
            JSONObject person = people.optJSONObject(i);
            if (person == null) continue;
            parent.addView(personLine(person.optString("name"), person.optString("post"), "", INK));
        }
    }

    private View personLine(String name, String post, String mark, int markColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView who = label(name, 13, INK, false);
        who.setSingleLine(false);
        text.addView(who);
        if (post != null && post.length() > 0) text.addView(label(post, 11, MUTED, false));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (mark != null && mark.length() > 0) row.addView(label(mark, 12, markColor, true));
        return row;
    }

    private TextView section(String text) {
        TextView view = label(text, 13, INK, true);
        view.setPadding(dp(2), dp(10), dp(2), dp(6));
        return view;
    }

    private LinearLayout panel() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(Color.WHITE, 18));
        card.setElevation(dp(3));
        card.setPadding(dp(12), dp(12), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private RippleDrawable round(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(0x220A8FA7), shape, null);
    }

    private int columns() {
        float width = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        if (width >= 840) return 4;
        if (width >= 600) return 3;
        return 2;
    }

    private String format(int value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text == null ? "" : text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LocCache {
        String mode = "";
        String className = "";
        final Map<String, JSONArray> classes = new HashMap<>();
    }

    private static final class Kpi {
        final String value;
        final String label;
        final String hint;
        final int color;
        int schoolId;
        String mode = "";
        LinearLayout list;
        TextView status;

        Kpi(String value, String label, String hint, int color) {
            this.value = value;
            this.label = label;
            this.hint = hint == null ? "" : hint;
            this.color = color;
        }
    }
}
