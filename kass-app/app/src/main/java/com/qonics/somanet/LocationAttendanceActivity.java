package com.qonics.somanet;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qonics.utils.StudentPhotoHelper;

import de.hdodenhof.circleimageview.CircleImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Location attendance on the phone NFC reader. Behavior card scan is a different screen.
 */
public class LocationAttendanceActivity extends AppCompatActivity implements NFCForegroundUtil.TagListener {
    private static final String TAG = "LocAttendance";
    private static final String PREF_AREA = "loc_att_area_id";
    private static final String PREF_NAME = "loc_att_area_name";

    private SharedPreferences preferences;
    private LocationAttendanceStore store;
    private NFCForegroundUtil nfc;
    private LinearLayout body;
    private TextView subtitle;
    private LinearLayout stage;
    private View lamp;
    private CircleImageView face;
    private TextView statusLine;
    private TextView nameLine;
    private TextView detailLine;
    private ImageView cardArt;
    private TextView holdLine;
    private int areaId;
    private String areaName = "";
    private boolean scanning;
    private String lastUid = "";
    private long lastUidAt;
    private static final AtomicBoolean FLUSHING = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        store = new LocationAttendanceStore(this);
        nfc = new NFCForegroundUtil(this);
        nfc.setTagListener(this);
        areaId = preferences.getInt(PREF_AREA, 0);
        areaName = preferences.getString(PREF_NAME, "");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F4F7FB"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#1E3A8A"));
        bar.setPadding(dp(8), dp(10), dp(12), dp(10));
        TextView back = text("‹  Back", 16, Color.WHITE, true);
        back.setPadding(dp(8), dp(8), dp(12), dp(8));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Location based attendance", 16, Color.WHITE, true));
        subtitle = text("Choose a location", 12, Color.parseColor("#DBEAFE"), false);
        titles.addView(subtitle);
        bar.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        if (areaId > 0 && areaName != null && areaName.length() > 0) {
            showScan();
        } else {
            showLocations();
        }
        refreshLocations();
        uploadPending(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scanning && nfc != null) {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
            if (adapter != null && !adapter.isEnabled()) {
                Toast.makeText(this, R.string.activate_nfc, Toast.LENGTH_LONG).show();
            }
            nfc.enableScanReader();
            TapSound.prepare(this);
        }
    }

    @Override
    protected void onPause() {
        if (nfc != null) nfc.disableForeground();
        super.onPause();
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (!scanning || tag == null) return;
        String cardId = NFCForegroundUtil.bytesToHex(tag.getId());
        if (cardId.length() == 0) return;
        long now = SystemClock.elapsedRealtime();
        if (cardId.equals(lastUid) && now - lastUidAt < 700) return;
        lastUid = cardId;
        lastUidAt = now;
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(30);
        } catch (Exception ignored) {
        }
        LocationAttendanceStore.Student student = store.findStudent(cardId);
        if (student == null) {
            TapSound.speak(this, "alert");
            showResult(false, "NOT ALLOWED", "", "No student found for this card", "");
            return;
        }
        LocationAttendanceStore.Result result = store.clock(student, areaId, areaName, cardId);
        TapSound.speak(this, result.sound);
        boolean good = result.ok && !result.denied;
        String title = result.headline.length() > 0 ? result.headline : result.message;
        showResult(good, title, student.name, result.detail, student.photo);
        if (result.ok && !result.already && !result.denied) {
            uploadPending(this);
        }
    }

    private void showLocations() {
        scanning = false;
        stage = null;
        if (nfc != null) nfc.disableForeground();
        subtitle.setText(store.pendingCount() > 0
                ? store.pendingCount() + " taps waiting to sync"
                : "Choose a location");
        body.removeAllViews();
        TextView hint = text("Tap a location, then hold a student card on the phone.", 14, Color.parseColor("#334155"), false);
        hint.setPadding(0, 0, 0, dp(12));
        body.addView(hint);
        JSONArray locations = store.locations();
        if (locations.length() == 0) {
            body.addView(text("No locations saved yet. Connect once to load them.", 14, Color.parseColor("#B45309"), false));
        }
        for (int i = 0; i < locations.length(); i++) {
            JSONObject loc = locations.optJSONObject(i);
            if (loc == null) continue;
            int id = loc.optInt("id");
            String name = loc.optString("name");
            TextView row = text(name, 16, Color.parseColor("#0F172A"), true);
            row.setBackground(round(Color.WHITE));
            row.setPadding(dp(16), dp(16), dp(16), dp(16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> choose(id, name));
            body.addView(row);
        }
    }

    private void choose(int id, String name) {
        areaId = id;
        areaName = name == null ? "" : name;
        preferences.edit().putInt(PREF_AREA, areaId).putString(PREF_NAME, areaName).apply();
        showScan();
    }

    private void showScan() {
        scanning = true;
        subtitle.setText(areaName);
        body.removeAllViews();
        TextView change = text("Change location", 14, Color.parseColor("#1D4ED8"), true);
        change.setPadding(0, 0, 0, dp(12));
        change.setOnClickListener(v -> showLocations());
        body.addView(change);

        stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER_HORIZONTAL);
        stage.setBackground(round(Color.WHITE));
        stage.setPadding(dp(16), dp(20), dp(16), dp(24));
        int screen = getResources().getDisplayMetrics().heightPixels;
        stage.setMinimumHeight(Math.max(dp(520), screen - dp(150)));
        body.addView(stage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView place = text(areaName, 22, Color.parseColor("#1E3A8A"), true);
        place.setGravity(Gravity.CENTER);
        place.setPadding(0, 0, 0, dp(8));
        stage.addView(place);

        FrameLayout faceBox = new FrameLayout(this);
        lamp = new View(this);
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setColor(Color.parseColor("#86EFAC"));
        lamp.setBackground(glow);
        faceBox.addView(lamp, new FrameLayout.LayoutParams(dp(300), dp(300), Gravity.CENTER));
        face = new CircleImageView(this);
        face.setBorderWidth(dp(8));
        face.setBorderColor(Color.WHITE);
        face.setImageResource(R.drawable.placeholder_image);
        faceBox.addView(face, new FrameLayout.LayoutParams(dp(240), dp(240), Gravity.CENTER));
        LinearLayout.LayoutParams faceLp = new LinearLayout.LayoutParams(dp(300), dp(300));
        faceLp.gravity = Gravity.CENTER_HORIZONTAL;
        faceLp.topMargin = dp(8);
        faceBox.setLayoutParams(faceLp);
        faceBox.setVisibility(View.GONE);
        stage.addView(faceBox);

        statusLine = text("", 34, Color.WHITE, true);
        statusLine.setGravity(Gravity.CENTER);
        statusLine.setPadding(0, dp(12), 0, 0);
        statusLine.setVisibility(View.GONE);
        stage.addView(statusLine);

        nameLine = text("", 26, Color.WHITE, true);
        nameLine.setGravity(Gravity.CENTER);
        nameLine.setPadding(dp(8), dp(6), dp(8), 0);
        nameLine.setVisibility(View.GONE);
        stage.addView(nameLine);

        detailLine = text("", 18, Color.WHITE, false);
        detailLine.setGravity(Gravity.CENTER);
        detailLine.setPadding(dp(8), dp(4), dp(8), 0);
        detailLine.setVisibility(View.GONE);
        stage.addView(detailLine);

        cardArt = new ImageView(this);
        cardArt.setImageResource(R.drawable.card);
        cardArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cardArt.setColorFilter(Color.parseColor("#333333"));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(285), dp(160));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        ip.topMargin = dp(18);
        cardArt.setLayoutParams(ip);
        stage.addView(cardArt);

        holdLine = text(getString(R.string.sear_card).trim(), 18, Color.parseColor("#333333"), false);
        holdLine.setGravity(Gravity.CENTER);
        holdLine.setPadding(0, dp(16), 0, 0);
        stage.addView(holdLine);
        store.loadCardIndex();
        TapSound.prepare(this);
        if (nfc != null) nfc.enableScanReader();
    }

    private void showResult(boolean ok, String title, String name, String detail, String photo) {
        if (stage == null || face == null) return;
        int green = Color.parseColor("#15803D");
        int red = Color.parseColor("#B91C1C");
        stage.setBackground(round(ok ? green : red));
        if (lamp != null) {
            GradientDrawable glow = new GradientDrawable();
            glow.setShape(GradientDrawable.OVAL);
            glow.setColor(ok ? Color.parseColor("#4ADE80") : Color.parseColor("#FCA5A5"));
            lamp.setBackground(glow);
        }
        if (face.getParent() instanceof View) {
            ((View) face.getParent()).setVisibility(View.VISIBLE);
        }
        face.setBorderColor(Color.WHITE);
        face.setImageResource(R.drawable.placeholder_image);
        if (photo != null && photo.trim().length() > 3) {
            StudentPhotoHelper.loadPreferCache(face, photo);
        }
        statusLine.setText(title == null ? "" : title);
        statusLine.setTextColor(Color.WHITE);
        statusLine.setVisibility(View.VISIBLE);
        nameLine.setText(name == null ? "" : name);
        nameLine.setVisibility(name == null || name.length() == 0 ? View.GONE : View.VISIBLE);
        detailLine.setText(detail == null ? "" : detail);
        detailLine.setTextColor(Color.parseColor("#F0FDF4"));
        detailLine.setVisibility(detail == null || detail.length() == 0 ? View.GONE : View.VISIBLE);
        cardArt.setVisibility(View.GONE);
        holdLine.setText("Tap the next card");
        holdLine.setTextColor(Color.WHITE);
        holdLine.setVisibility(View.VISIBLE);
        if (stage.getChildCount() > 0 && stage.getChildAt(0) instanceof TextView) {
            ((TextView) stage.getChildAt(0)).setTextColor(Color.WHITE);
        }
    }

    private void refreshLocations() {
        if (!MainActivity.isNetworkAvailable(this)) return;
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId <= 0) return;
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId);
        map.put("post_id", preferences.getInt("soma_post", 0));
        new HttpRequest(this).timeouts(8, 20).post(MainActivity.SERVER + "device_locations", map, (object, code, status) -> {
            if (object == null || object.optInt("success") != 1) return;
            store.replaceLocations(object.optJSONArray("locations"));
            store.replaceRules(object.optJSONArray("rules"));
            store.mergeToday(object.optJSONArray("attendance_today"));
            store.loadCardIndex();
            runOnUiThread(() -> {
                if (!scanning) showLocations();
            });
        });
    }

    static void uploadPending(android.content.Context context) {
        if (context == null || !FLUSHING.compareAndSet(false, true)) return;
        if (!MainActivity.isNetworkAvailable(context)) {
            FLUSHING.set(false);
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        LocationAttendanceStore store = new LocationAttendanceStore(context);
        JSONArray events = store.pendingEvents();
        if (events.length() == 0) {
            FLUSHING.set(false);
            return;
        }
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId);
        map.put("post_id", preferences.getInt("soma_post", 0));
        map.put("events", events.toString());
        new HttpRequest(context.getApplicationContext()).timeouts(8, 30)
                .post(MainActivity.SERVER + "device_scan_batch", map, (object, code, status) -> {
                    FLUSHING.set(false);
                    if (object == null) return;
                    JSONArray results = object.optJSONArray("results");
                    if (results == null) return;
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject row = results.optJSONObject(i);
                        if (row != null && row.optInt("success") == 1) {
                            store.markSynced(row.optInt("local_id"));
                        }
                    }
                    Log.i(TAG, "flushed location taps, left " + store.pendingCount());
                });
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable round(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
