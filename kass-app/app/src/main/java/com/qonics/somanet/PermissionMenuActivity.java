package com.qonics.somanet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Permission home: given today, justified, and waiting to justify.
 */
public class PermissionMenuActivity extends AppCompatActivity {
    private static final int GIVEN = 0;
    private static final int JUSTIFIED = 1;
    private static final int WAITING = 2;

    private static final int TEAL = 0xFF0A8FA7;
    private static final int GREEN = 0xFF15803D;
    private static final int BLUE = 0xFF1D4ED8;
    private static final int AMBER = 0xFFB45309;
    private static final int INK = 0xFF0F172A;
    private static final int MUTED = 0xFF64748B;
    private static final int PAGE = 0xFFF1F5F9;
    private static final int LINE = 0xFFE2E8F0;

    private static final String[] MONTHS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private SharedPreferences preferences;
    private LinearLayout list;
    private TextView summary;
    private TextView section;
    private TextView numGiven;
    private TextView numJust;
    private TextView numWait;
    private TextView capGiven;
    private TextView capJust;
    private TextView capWait;
    private LinearLayout tileGiven;
    private LinearLayout tileJust;
    private LinearLayout tileWait;
    private ProgressBar busy;
    private int mode = WAITING;
    private boolean userPicked;
    private int givenCount;
    private int justifiedCount;
    private int waitingCount;
    private int outNow;
    private JSONArray givenRows = new JSONArray();
    private JSONArray justifiedRows = new JSONArray();
    private JSONArray waitRows = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAGE);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(TEAL);
        bar.setPadding(dp(8), dp(12), dp(14), dp(12));
        TextView back = text("‹   Permission", 18, Color.WHITE, true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(16), dp(14), dp(28));

        TextView school = text(preferences.getString(MainActivity.SK_NAME, ""), 18, INK, true);
        body.addView(school);
        summary = text("Loading permissions", 13, MUTED, false);
        summary.setPadding(0, dp(4), 0, 0);
        body.addView(summary);

        TextView neu = text("+   New permission", 15, Color.WHITE, true);
        neu.setGravity(Gravity.CENTER);
        neu.setBackground(round(TEAL, 14));
        neu.setPadding(dp(12), dp(13), dp(12), dp(13));
        LinearLayout.LayoutParams neuLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        neuLp.topMargin = dp(14);
        neu.setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchStudentActivity.class);
            intent.putExtra("who", 1);
            startActivity(intent);
        });
        body.addView(neu, neuLp);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tilesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tilesLp.topMargin = dp(14);
        tiles.setLayoutParams(tilesLp);

        tileGiven = tile(BLUE);
        numGiven = (TextView) tileGiven.getChildAt(0);
        capGiven = (TextView) tileGiven.getChildAt(1);
        capGiven.setText("Given");
        tileGiven.setOnClickListener(v -> pick(GIVEN));

        tileJust = tile(GREEN);
        numJust = (TextView) tileJust.getChildAt(0);
        capJust = (TextView) tileJust.getChildAt(1);
        capJust.setText("Justified");
        tileJust.setOnClickListener(v -> pick(JUSTIFIED));

        tileWait = tile(AMBER);
        numWait = (TextView) tileWait.getChildAt(0);
        capWait = (TextView) tileWait.getChildAt(1);
        capWait.setText("Waiting");
        tileWait.setOnClickListener(v -> pick(WAITING));

        tiles.addView(tileGiven, tileLp(0));
        tiles.addView(tileJust, tileLp(6));
        tiles.addView(tileWait, tileLp(6));
        body.addView(tiles);

        section = text("Waiting to justify", 15, INK, true);
        section.setPadding(dp(2), dp(18), 0, dp(2));
        body.addView(section);

        busy = new ProgressBar(this);
        body.addView(busy);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        paintTiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        busy.setVisibility(View.VISIBLE);
        new HttpRequest(this).get(MainActivity.SERVER + "permission_dashboard/" + schoolId,
                JSONObject.class, new Callback() {
                    @Override
                    public void HttpCallback(JSONObject object, int statusCode, String status) {
                        busy.setVisibility(View.GONE);
                        if (object == null) {
                            Toast.makeText(PermissionMenuActivity.this, status, Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (object.has("error")) {
                            Toast.makeText(PermissionMenuActivity.this,
                                    object.optString("error"), Toast.LENGTH_LONG).show();
                            return;
                        }
                        givenCount = object.optInt("given_today");
                        justifiedCount = object.optInt("justified_today");
                        waitingCount = object.optInt("waiting");
                        outNow = object.optInt("out_now");
                        givenRows = object.optJSONArray("given");
                        justifiedRows = object.optJSONArray("done");
                        waitRows = object.optJSONArray("waiting_list");
                        if (givenRows == null) givenRows = new JSONArray();
                        if (justifiedRows == null) justifiedRows = new JSONArray();
                        if (waitRows == null) waitRows = new JSONArray();
                        numGiven.setText(String.valueOf(givenCount));
                        numJust.setText(String.valueOf(justifiedCount));
                        numWait.setText(String.valueOf(waitingCount));
                        String line = givenCount + " given today  ·  "
                                + justifiedCount + " justified  ·  "
                                + waitingCount + " waiting to justify";
                        if (outNow > 0) {
                            line += "  ·  " + outNow + " out now";
                        }
                        summary.setText(line);
                        if (!userPicked) {
                            if (waitingCount > 0) mode = WAITING;
                            else if (givenCount > 0) mode = GIVEN;
                            else mode = JUSTIFIED;
                        }
                        paintTiles();
                        paint();
                    }
                });
    }

    private void pick(int next) {
        userPicked = true;
        mode = next;
        paintTiles();
        paint();
    }

    private void paintTiles() {
        styleTile(tileGiven, numGiven, capGiven, BLUE, mode == GIVEN);
        styleTile(tileJust, numJust, capJust, GREEN, mode == JUSTIFIED);
        styleTile(tileWait, numWait, capWait, AMBER, mode == WAITING);
    }

    private void styleTile(LinearLayout tile, TextView number, TextView caption, int color, boolean on) {
        tile.setBackground(round(on ? color : Color.WHITE, 14));
        number.setTextColor(on ? Color.WHITE : color);
        caption.setTextColor(on ? Color.WHITE : MUTED);
    }

    private void paint() {
        if (mode == GIVEN) section.setText("Given permission");
        else if (mode == JUSTIFIED) section.setText("Justified");
        else section.setText("Waiting to justify");

        list.removeAllViews();
        JSONArray rows = mode == GIVEN ? givenRows : (mode == JUSTIFIED ? justifiedRows : waitRows);
        if (rows.length() == 0) {
            TextView empty = text(emptyText(), 14, MUTED, false);
            empty.setPadding(dp(4), dp(18), dp(4), 0);
            list.addView(empty);
            return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            list.addView(card(row));
        }
    }

    private String emptyText() {
        if (mode == GIVEN) return "No permission has been given today.";
        if (mode == JUSTIFIED) return "No permission has been justified today.";
        return "No student is waiting to justify.";
    }

    private View card(JSONObject row) {
        boolean justified = "1".equals(row.optString("status"));
        boolean out = row.optInt("out_now") == 1 || isOutNow(row);
        int accent = justified ? GREEN : (out ? AMBER : BLUE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardShape());
        card.setElevation(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);

        View stripe = new View(this);
        stripe.setBackgroundColor(accent);
        card.addView(stripe, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)));

        LinearLayout rowLine = new LinearLayout(this);
        rowLine.setOrientation(LinearLayout.HORIZONTAL);
        rowLine.setPadding(dp(12), dp(12), dp(12), dp(12));
        rowLine.setGravity(Gravity.CENTER_VERTICAL);

        CircleImageView photo = new CircleImageView(this);
        photo.setBorderWidth(dp(1));
        photo.setBorderColor(LINE);
        StudentPhotoHelper.load(photo, row.optString("photo"));
        LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        photoLp.rightMargin = dp(12);
        rowLine.addView(photo, photoLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(row.optString("name"), 15, INK, true);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chip = text(chipLabel(justified, out), 11, accent, true);
        chip.setBackground(chipShape(accent));
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        nameRow.addView(chip);
        textCol.addView(nameRow);

        String klass = row.optString("class_label").trim();
        String regno = row.optString("regno").trim();
        String who = klass;
        if (!regno.isEmpty()) who = who.isEmpty() ? regno : who + "   ·   " + regno;
        if (!who.isEmpty()) {
            TextView meta = text(who, 12, MUTED, false);
            meta.setPadding(0, dp(2), 0, 0);
            textCol.addView(meta);
        }

        String where = row.optString("destination").trim();
        String reason = row.optString("reason").trim();
        String trip = where;
        if (!reason.isEmpty()) trip = trip.isEmpty() ? reason : trip + "   ·   " + reason;
        if (!trip.isEmpty()) {
            TextView dest = text(trip, 13, INK, false);
            dest.setPadding(0, dp(4), 0, 0);
            textCol.addView(dest);
        }

        String span = clock(row.optString("leave_time")) + "   →   " + clock(row.optString("return_time"));
        TextView when = text(span, 12, MUTED, false);
        when.setPadding(0, dp(4), 0, 0);
        textCol.addView(when);

        rowLine.addView(textCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(rowLine);
        card.setOnClickListener(v -> openStudent(row));
        return card;
    }

    private String chipLabel(boolean justified, boolean out) {
        if (justified) return "Justified";
        if (out) return "Out now";
        return "Waiting";
    }

    private boolean isOutNow(JSONObject row) {
        if ("1".equals(row.optString("status"))) return false;
        String leave = row.optString("leave_time");
        String back = row.optString("return_time");
        if (leave.length() < 16 || back.length() < 16) return false;
        String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new java.util.Date());
        return leave.compareTo(now) <= 0 && back.compareTo(now) >= 0;
    }

    private String clock(String raw) {
        if (raw == null || raw.length() < 16) return raw == null ? "" : raw;
        try {
            int month = Integer.parseInt(raw.substring(5, 7));
            if (month < 1 || month > 12) return raw;
            return raw.substring(8, 10) + " " + MONTHS[month - 1] + ", " + raw.substring(11, 16);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private void openStudent(JSONObject row) {
        Intent intent = new Intent(this, PermissionActivity.class);
        intent.putExtra("student_id", row.optInt("student_id"));
        intent.putExtra("name", row.optString("name"));
        intent.putExtra("class", row.optString("class_label"));
        intent.putExtra("regno", row.optString("regno"));
        intent.putExtra("photo", row.optString("photo"));
        intent.putExtra("term", "");
        startActivity(intent);
    }

    private LinearLayout tile(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(round(Color.WHITE, 14));
        box.setPadding(dp(6), dp(12), dp(6), dp(12));
        TextView value = text("–", 22, color, true);
        value.setGravity(Gravity.CENTER);
        TextView caption = text("", 12, MUTED, true);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(2), 0, 0);
        box.addView(value);
        box.addView(caption);
        return box;
    }

    private LinearLayout.LayoutParams tileLp(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(left);
        return lp;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private GradientDrawable cardShape() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.WHITE);
        shape.setCornerRadius(dp(14));
        shape.setStroke(dp(1), LINE);
        return shape;
    }

    private GradientDrawable chipShape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)));
        shape.setCornerRadius(dp(20));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
