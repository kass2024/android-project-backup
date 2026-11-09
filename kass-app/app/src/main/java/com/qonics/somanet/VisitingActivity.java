package com.qonics.somanet;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.LinearInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Parent visiting, same rules as the USB student kiosk in visiting mode.
 * Cards are read with the phone NFC reader used by Behavior. No USB wedge.
 */
public class VisitingActivity extends AppCompatActivity implements NFCForegroundUtil.TagListener {
    private static final String TAG = "Visiting";
    private static final String PREF_VISIT_MODE = "visit_mode";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final String DB = "somanet_attendance_app_db";

    private SharedPreferences preferences;
    private SQLiteDatabase db;
    private NFCForegroundUtil nfc;
    private NfcAdapter nfcAdapter;

    private View resultCard;
    private LinearLayout resultPane;
    private View welcomeScroll;
    private TextView tvWelcome;
    private TextView tvSchool;
    private TextView tvSchoolShadow;
    private TextView tvLocationLabel;
    private TextView tvLocation;
    private TextView tvClock;
    private TextView tvClockShadow;
    private TextView tvClockDepth;
    private TextView tvDate;
    private TextView tvHint;
    private TextView tvNfc;
    private TextView tvSync;
    private TextView tvCardLabel;
    private View scanLampLeft;
    private View scanLampRight;
    private View cardSpot;
    private View pulseRing;
    private boolean visitingOnly;
    private boolean welcomeShown;
    private boolean modePollBusy;
    private ObjectAnimator welcomeBlink;
    private ValueAnimator welcomeSlide;
    private TextView tvReady;
    private TextView tvStatus;
    private TextView tvTime;
    private TextView tvMeta;
    private TextView tvStudentsLabel;
    private TextView tvVisitorsLabel;
    private TextView tvStaffHint;
    private View studentsScroll;
    private LinearLayout studentsRow;
    private LinearLayout visitorsRow;

    private final HashMap<String, StudentHit> studentsByCard = new HashMap<>();
    private final HashMap<Integer, StudentHit> studentsById = new HashMap<>();
    private final ArrayList<JSONObject> visitors = new ArrayList<>();
    private final HashMap<String, ArrayList<JSONObject>> visitorsByCard = new HashMap<>();

    private int scanGen = 0;
    private boolean busy;
    private boolean rosterReady;
    private String pendingCard = "";
    private String lastCard = "";
    private long lastCardAt;
    private boolean resultPinned;
    private boolean previewOnly;
    private String pinnedVisitorCard = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visiting);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        db = openOrCreateDatabase(DB, Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        ensureCache();
        bind();
        loadStudents();
        loadVisitorCache();
        rosterReady = !online() || !visitors.isEmpty();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            nfc = new NFCForegroundUtil(this);
            nfc.setTagListener(this);
        }
        refreshVisitors();
        visitingOnly = "visiting".equals(preferences.getString(PREF_VISIT_MODE, "normal"));
        applyModeUi();
    }

    private void bind() {
        resultCard = findViewById(R.id.result_card);
        resultPane = findViewById(R.id.result_panel);
        welcomeScroll = findViewById(R.id.welcome_scroll);
        tvWelcome = findViewById(R.id.tv_visit_welcome);
        tvSchool = findViewById(R.id.tv_school);
        tvSchoolShadow = findViewById(R.id.tv_school_shadow);
        tvLocationLabel = findViewById(R.id.tv_location_label);
        tvLocation = findViewById(R.id.tv_location);
        tvClock = findViewById(R.id.tv_clock);
        tvClockShadow = findViewById(R.id.tv_clock_shadow);
        tvClockDepth = findViewById(R.id.tv_clock_depth);
        tvDate = findViewById(R.id.tv_date);
        tvHint = findViewById(R.id.tv_hint);
        tvNfc = findViewById(R.id.tv_nfc);
        tvSync = findViewById(R.id.tv_sync);
        tvCardLabel = findViewById(R.id.tv_card_label);
        scanLampLeft = findViewById(R.id.scan_lamp_left);
        scanLampRight = findViewById(R.id.scan_lamp_right);
        cardSpot = findViewById(R.id.card_spot);
        pulseRing = findViewById(R.id.pulse_ring);
        tvReady = findViewById(R.id.tv_ready);
        tvStatus = findViewById(R.id.tv_status);
        tvTime = findViewById(R.id.tv_time);
        tvMeta = findViewById(R.id.tv_meta);
        tvStudentsLabel = findViewById(R.id.tv_visit_students_label);
        tvVisitorsLabel = findViewById(R.id.tv_visit_visitors_label);
        tvStaffHint = findViewById(R.id.tv_visit_staff_hint);
        studentsScroll = findViewById(R.id.visit_students_scroll);
        studentsRow = findViewById(R.id.visit_students_row);
        visitorsRow = findViewById(R.id.visit_visitors_row);
        String school = preferences.getString(MainActivity.SK_NAME, "");
        if (school == null || school.trim().length() == 0) {
            school = "Visiting";
        }
        tvSchool.setText(school);
        tvSchoolShadow.setText(school);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.visiting_no_nfc, Toast.LENGTH_LONG).show();
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            Toast.makeText(this, R.string.activate_nfc, Toast.LENGTH_LONG).show();
        }
        // Scan reader releases the card so the next tap (staff receive) is seen.
        nfc.enableScanReader();
        handler.post(clockTick);
        handler.post(modePoll);
        applyModeUi();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(modePoll);
        if (nfc != null) {
            nfc.disableForeground();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (welcomeBlink != null) welcomeBlink.cancel();
        if (welcomeSlide != null) welcomeSlide.cancel();
        super.onDestroy();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            onTagDiscovered(tag);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (tag == null || busy) {
            return;
        }
        String reader = NFCForegroundUtil.bytesToHex(tag.getId());
        if (reader.length() < 4) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (reader.equals(lastCard) && now - lastCardAt < (resultPinned ? 2500 : 400)) {
            return;
        }
        lastCard = reader;
        lastCardAt = now;
        vibrate();
        if (!rosterReady) {
            pendingCard = reader;
            showChecking();
            return;
        }
        if (resultPinned && !previewOnly && !sameCard(reader, pinnedVisitorCard)) {
            tryReceive(reader);
            return;
        }
        handleCard(reader);
    }

    private void handleCard(String reader) {
        JSONArray onCard = visitorsForCard(reader);
        if (onCard.length() > 0) {
            if (visitingOnly && dayscholarParentsOnly(onCard)) {
                showDayscholarParents(onCard);
            } else {
                clockVisitor(reader);
            }
            return;
        }
        StudentHit student = findStudent(reader);
        if (student != null) {
            if (student.mode == 0) {
                showHold(getString(R.string.visiting_boarding), getString(R.string.visiting_boarding_detail));
            } else if (visitingOnly) {
                showStudentParents(student);
            } else {
                showHold(getString(R.string.visiting_normal_day), getString(R.string.visiting_normal_day_detail));
            }
            return;
        }
        clockVisitor(reader);
    }

    /** Dayscholar card: list registered parents. Does not save a visit. */
    private void showStudentParents(final StudentHit student) {
        if (!online() || schoolId() <= 0) {
            showPreview(parentsForStudent(student.id), studentJson(student),
                    parentsForStudent(student.id).length() > 0
                            ? getString(R.string.visiting_registered)
                            : getString(R.string.visiting_no_parents), "");
            return;
        }
        final int gen = ++scanGen;
        busy = true;
        showChecking();
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId());
        map.put("student_id", student.id);
        new HttpRequest(this).timeouts(4, 8).post(MainActivity.SERVER + "visitor_allowed", map, (object, code, status) -> {
            if (gen != scanGen) {
                return;
            }
            busy = false;
            JSONArray parents = filterParents(object == null ? null : object.optJSONArray("visitors"));
            if (parents.length() == 0) {
                parents = parentsForStudent(student.id);
            }
            showPreview(parents, studentJson(student),
                    parents.length() > 0
                            ? getString(R.string.visiting_registered)
                            : getString(R.string.visiting_no_parents), "");
        });
    }

    private void showDayscholarParents(JSONArray seed) {
        JSONArray parents = new JSONArray();
        HashSet<Integer> seenStudents = new HashSet<>();
        HashSet<Integer> seenParents = new HashSet<>();
        StudentHit person = null;
        for (int i = 0; i < seed.length(); i++) {
            JSONObject visitor = seed.optJSONObject(i);
            if (visitor == null) {
                continue;
            }
            int studentId = visitor.optInt("student_id", 0);
            if (studentId <= 0 || !seenStudents.add(studentId)) {
                continue;
            }
            if (person == null) {
                person = studentsById.get(studentId);
            }
            JSONArray linked = parentsForStudent(studentId);
            for (int p = 0; p < linked.length(); p++) {
                JSONObject parent = linked.optJSONObject(p);
                if (parent == null) {
                    continue;
                }
                int vid = parent.optInt("id", 0);
                if (vid > 0 && !seenParents.add(vid)) {
                    continue;
                }
                parents.put(parent);
            }
        }
        if (parents.length() == 0) {
            parents = filterParents(seed);
        }
        showPreview(parents, person == null ? null : studentJson(person),
                parents.length() > 0
                        ? getString(R.string.visiting_registered)
                        : getString(R.string.visiting_no_parents), lastCard);
    }

    private void clockVisitor(final String reader) {
        if (!online() || schoolId() <= 0) {
            showHold(getString(R.string.visiting_offline), getString(R.string.visiting_offline_detail));
            return;
        }
        final int gen = ++scanGen;
        busy = true;
        showChecking();
        final String storage = NFCForegroundUtil.storageForm(reader);
        postVisitorScan(gen, storage.length() >= 4 ? storage : reader, reader, true);
    }

    private void postVisitorScan(final int gen, final String uid, final String reader, final boolean tryOther) {
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId());
        map.put("card", uid);
        map.put("source", "android");
        new HttpRequest(this).timeouts(6, 12).post(MainActivity.SERVER + "visitor_card_scan", map, (object, code, status) -> {
            if (gen != scanGen) {
                return;
            }
            if (isVisitorResult(object) || isActiveDenial(object)) {
                busy = false;
                showClock(object, uid);
                return;
            }
            String other = reader != null && reader.length() >= 4 && !reader.equalsIgnoreCase(uid) ? reader : "";
            if (tryOther && other.length() >= 4 && (object == null || isNoVisitor(object))) {
                postVisitorScan(gen, other, reader, false);
                return;
            }
            lookupStudentOnline(gen, reader);
        });
    }

    private void lookupStudentOnline(final int gen, final String reader) {
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId());
        map.put("card", reader);
        new HttpRequest(this).timeouts(4, 8).post(MainActivity.SERVER + "discipline_card_scan", map, (object, code, status) -> {
            if (gen != scanGen) {
                return;
            }
            busy = false;
            JSONObject st = object == null ? null : object.optJSONObject("student");
            if (st != null && st.optInt("id", 0) > 0) {
                StudentHit hit = new StudentHit();
                hit.id = st.optInt("id");
                hit.name = st.optString("name", "");
                hit.regno = st.optString("regno", "");
                hit.classe = st.optString("class", "");
                hit.photo = st.optString("photo", "");
                hit.card = object.optString("card", NFCForegroundUtil.storageForm(reader));
                hit.mode = st.has("studying_mode") ? modeOf(String.valueOf(st.opt("studying_mode"))) : -1;
                StudentHit known = studentsById.get(hit.id);
                if (known != null) {
                    if (hit.classe.length() == 0) hit.classe = known.classe;
                    if (hit.photo.length() < 4) hit.photo = known.photo;
                    if (hit.mode < 0) hit.mode = known.mode;
                }
                studentsById.put(hit.id, hit);
                if (hit.card.length() >= 4) {
                    for (String variant : NFCForegroundUtil.lookupVariants(hit.card)) {
                        studentsByCard.put(variant, hit);
                    }
                }
                for (String variant : NFCForegroundUtil.lookupVariants(reader)) {
                    studentsByCard.put(variant, hit);
                }
                if (hit.mode == 0) {
                    showHold(getString(R.string.visiting_boarding), getString(R.string.visiting_boarding_detail));
                    return;
                }
                if (!visitingOnly) {
                    showHold(getString(R.string.visiting_normal_day), getString(R.string.visiting_normal_day_detail));
                    return;
                }
                showStudentParents(hit);
                return;
            }
            String msg = messageOf(object, getString(R.string.visiting_unknown));
            showHold(getString(R.string.visiting_denied), msg);
        });
    }

    private void tryReceive(final String staffCard) {
        if (!online() || schoolId() <= 0 || pinnedVisitorCard.length() < 4) {
            handleCard(staffCard);
            return;
        }
        final int gen = ++scanGen;
        busy = true;
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId());
        map.put("card", pinnedVisitorCard);
        map.put("staff_card", staffCard);
        map.put("source", "android");
        new HttpRequest(this).timeouts(6, 12).post(MainActivity.SERVER + "visitor_receive_scan", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (gen != scanGen) {
                    return;
                }
                busy = false;
                boolean staff = object != null && "staff".equalsIgnoreCase(object.optString("kind", ""));
                if (staff && (ok(object) || object.optBoolean("allowed", false))) {
                    String msg = messageOf(object, getString(R.string.visiting_received));
                    tvStaffHint.setText(msg);
                    tvStaffHint.setVisibility(View.VISIBLE);
                    tvReady.setText(getString(R.string.visiting_received));
                    return;
                }
                handleCard(staffCard);
            }
        });
    }

    private void showPreview(JSONArray parents, JSONObject student, String hint, String scanned) {
        JSONObject local = new JSONObject();
        try {
            local.put("success", 1);
            local.put("allowed", true);
            local.put("action", "preview");
            local.put("visitors", parents == null ? new JSONArray() : parents);
            if (student != null) {
                JSONArray students = new JSONArray();
                students.put(student);
                local.put("students", students);
                local.put("student", student);
            }
            local.put("message", hint);
        } catch (Exception ignored) {
        }
        showClock(local, scanned == null ? "" : scanned);
    }

    private void showClock(JSONObject json, String scanned) {
        boolean ok = ok(json) || (json != null && json.optBoolean("allowed", false));
        String action = json == null ? "" : json.optString("action", "").toLowerCase(Locale.US);
        JSONArray people = json == null ? null : json.optJSONArray("visitors");
        JSONObject primaryVisitor = json == null ? null : json.optJSONObject("visitor");
        if ((people == null || people.length() == 0) && primaryVisitor != null) {
            people = new JSONArray();
            people.put(primaryVisitor);
        }
        if (people == null) {
            people = new JSONArray();
        }
        JSONArray students = json == null ? null : json.optJSONArray("students");
        if (students == null || students.length() == 0) {
            students = studentsFor(people);
            if (json != null && json.optJSONObject("student") != null && students.length() == 0) {
                students.put(json.optJSONObject("student"));
            }
        }
        String cardHolder = "";
        if (people.length() > 0) {
            JSONObject first = people.optJSONObject(0);
            if (first != null) {
                cardHolder = first.optString("names", first.optString("name", "")).trim();
            }
        }
        String msg = messageOf(json, ok ? getString(R.string.visiting_verified) : getString(R.string.visiting_not_allowed));
        String clock = json == null ? "" : json.optString("time_label", "");
        if (clock.length() > 5 && clock.contains(":")) {
            int sp = clock.lastIndexOf(' ');
            if (sp > 0) {
                clock = clock.substring(sp + 1, Math.min(clock.length(), sp + 6));
            }
        }
        boolean preview = ok && "preview".equals(action);
        previewOnly = preview;
        resultPinned = true;
        if (!preview && scanned != null && scanned.length() >= 4) {
            pinnedVisitorCard = scanned;
        }
        showResultCard();
        tvMeta.setVisibility(View.GONE);
        if (preview) {
            boolean hasParents = people.length() > 0;
            paintResult(hasParents ? R.drawable.bg_result_in : R.drawable.bg_result_hold,
                    hasParents ? "WELCOME" : "NO VISITORS", "");
            tvReady.setText(hasParents ? "REGISTERED" : "UNKNOWN");
            setScanLamp(hasParents ? 1 : -1);
        } else if (ok && "out".equals(action)) {
            paintResult(R.drawable.bg_result_out, "VISIT OUT", clock.length() == 0 ? "OUT" : "OUT  " + clock);
            tvReady.setText("REGISTERED");
            setScanLamp(1);
        } else if (ok) {
            paintResult(R.drawable.bg_result_in, "mixed".equals(action) ? "UPDATED" : "VISIT IN",
                    clock.length() == 0 ? "IN" : "IN  " + clock);
            tvReady.setText("REGISTERED");
            setScanLamp(1);
            if (json != null && json.optBoolean("too_soon", false)) {
                tvMeta.setText(msg);
                tvMeta.setVisibility(View.VISIBLE);
            }
        } else {
            paintResult(R.drawable.bg_result_hold, "DENIED", "");
            tvMeta.setText(msg);
            tvMeta.setVisibility(View.VISIBLE);
            tvReady.setText("UNKNOWN");
            setScanLamp(-1);
            previewOnly = true;
        }
        bindStudents(students);
        bindVisitors(people, scanned, cardHolder);
        if (preview) {
            tvStaffHint.setText(people.length() > 0
                    ? getString(R.string.visiting_registered)
                    : getString(R.string.visiting_no_parents));
            tvStaffHint.setVisibility(View.VISIBLE);
        } else if (ok) {
            tvStaffHint.setText(R.string.visiting_tap_staff);
            tvStaffHint.setVisibility(View.VISIBLE);
        } else {
            tvStaffHint.setVisibility(View.GONE);
        }
        rememberVisitors(scanned, people);
    }

    private void showHold(String title, String detail) {
        previewOnly = true;
        resultPinned = true;
        pinnedVisitorCard = "";
        showResultCard();
        paintResult(R.drawable.bg_result_hold, title, "");
        tvMeta.setText(detail);
        tvMeta.setVisibility(View.VISIBLE);
        tvReady.setText("UNKNOWN");
        setScanLamp(-1);
        studentsRow.removeAllViews();
        visitorsRow.removeAllViews();
        studentsScroll.setVisibility(View.GONE);
        tvStudentsLabel.setVisibility(View.GONE);
        tvVisitorsLabel.setVisibility(View.GONE);
        tvStaffHint.setVisibility(View.GONE);
    }

    private void showChecking() {
        showResultCard();
        paintResult(R.drawable.bg_result_hold, getString(R.string.visiting_checking), "");
        tvMeta.setVisibility(View.GONE);
        tvStaffHint.setVisibility(View.GONE);
        tvReady.setText(getString(R.string.visiting_checking));
        setScanLamp(0);
    }

    private void paintResult(int background, String status, String time) {
        resultPane.setBackgroundResource(background);
        tvStatus.setText(status);
        tvTime.setText(time == null ? "" : time);
    }

    private void showResultCard() {
        resultCard.setVisibility(View.VISIBLE);
        resultPane.setVisibility(View.VISIBLE);
        setVisitWelcome(false);
    }

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            String clock = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now);
            String date = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now);
            tvClock.setText(clock);
            tvClockShadow.setText(clock);
            tvClockDepth.setText(clock);
            tvDate.setText(date);
            if (tvSync != null) {
                tvSync.setText(online() ? "Online" : "Offline");
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable modePoll = new Runnable() {
        @Override
        public void run() {
            if (online() && !modePollBusy && schoolId() > 0) {
                modePollBusy = true;
                Map<String, Object> map = new HashMap<>();
                map.put("school_id", schoolId());
                new HttpRequest(VisitingActivity.this).timeouts(2, 3)
                        .post(MainActivity.SERVER + "visit_kiosk_mode", map, (object, code, status) -> {
                            modePollBusy = false;
                            if (object != null && ok(object) && object.has("visit_mode")) {
                                boolean next = "visiting".equalsIgnoreCase(object.optString("visit_mode", "normal"));
                                if (next != visitingOnly) {
                                    visitingOnly = next;
                                    preferences.edit().putString(PREF_VISIT_MODE, next ? "visiting" : "normal").apply();
                                    if (!next && previewOnly) {
                                        previewOnly = false;
                                        resultPinned = false;
                                        resultCard.setVisibility(View.GONE);
                                    }
                                    applyModeUi();
                                }
                            }
                        });
            }
            handler.postDelayed(this, 1500);
        }
    };

    private void applyModeUi() {
        if (tvLocationLabel != null) {
            tvLocationLabel.setText(visitingOnly ? "Device mode:" : "Current mode:");
        }
        if (tvLocation != null) {
            tvLocation.setText(visitingOnly ? "Parent visiting" : "Normal");
        }
        if (tvHint != null) {
            tvHint.setText(visitingOnly ? R.string.visiting_hint_only : R.string.visiting_hint_normal);
        }
        if (tvNfc != null) {
            tvNfc.setText(visitingOnly ? R.string.visiting_nfc_only : R.string.visiting_nfc_normal);
        }
        boolean idle = resultCard == null || resultCard.getVisibility() != View.VISIBLE;
        setVisitWelcome(visitingOnly && idle);
    }

    private void setVisitWelcome(boolean show) {
        if (welcomeScroll == null) {
            return;
        }
        if (!show) {
            if (welcomeBlink != null) welcomeBlink.cancel();
            if (welcomeSlide != null) welcomeSlide.cancel();
            welcomeScroll.setAlpha(1f);
            if (welcomeScroll instanceof HorizontalScrollView) {
                ((HorizontalScrollView) welcomeScroll).scrollTo(0, 0);
            }
            welcomeScroll.setVisibility(View.GONE);
            welcomeShown = false;
            return;
        }
        if (welcomeShown && welcomeScroll.getVisibility() == View.VISIBLE) {
            return;
        }
        welcomeScroll.setVisibility(View.VISIBLE);
        if (welcomeBlink == null && tvWelcome != null) {
            welcomeBlink = ObjectAnimator.ofFloat(welcomeScroll, "alpha", 1f, 0.35f, 1f);
            welcomeBlink.setDuration(1100);
            welcomeBlink.setRepeatCount(ValueAnimator.INFINITE);
            welcomeBlink.setRepeatMode(ValueAnimator.RESTART);
        }
        if (welcomeBlink != null && !welcomeBlink.isStarted()) {
            welcomeBlink.start();
        }
        welcomeScroll.post(new Runnable() {
            @Override
            public void run() {
                startWelcomeSlide();
            }
        });
        welcomeShown = true;
    }

    private void startWelcomeSlide() {
        if (!(welcomeScroll instanceof HorizontalScrollView) || tvWelcome == null) {
            return;
        }
        final HorizontalScrollView scroller = (HorizontalScrollView) welcomeScroll;
        int span = tvWelcome.getWidth() / 2;
        if (span < 40) {
            return;
        }
        if (welcomeSlide != null) {
            welcomeSlide.cancel();
        }
        welcomeSlide = ValueAnimator.ofInt(0, span);
        welcomeSlide.setDuration(Math.max(7000, span * 16L));
        welcomeSlide.setRepeatCount(ValueAnimator.INFINITE);
        welcomeSlide.setInterpolator(new LinearInterpolator());
        welcomeSlide.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scroller.scrollTo((Integer) animation.getAnimatedValue(), 0);
            }
        });
        welcomeSlide.start();
    }

    private void setScanLamp(int state) {
        int lamp = R.drawable.bg_lamp_idle;
        int spot = R.drawable.bg_card_spot;
        int pulse = R.drawable.bg_pulse_ring;
        String label = "TAP";
        if (state > 0) {
            lamp = R.drawable.bg_lamp_ok;
            spot = R.drawable.bg_card_spot_ok;
            pulse = R.drawable.bg_pulse_ok;
            label = "OK";
        } else if (state < 0) {
            lamp = R.drawable.bg_lamp_bad;
            spot = R.drawable.bg_card_spot_bad;
            pulse = R.drawable.bg_pulse_bad;
            label = "NO";
        }
        if (scanLampLeft != null) scanLampLeft.setBackgroundResource(lamp);
        if (scanLampRight != null) scanLampRight.setBackgroundResource(lamp);
        if (cardSpot != null) cardSpot.setBackgroundResource(spot);
        if (pulseRing != null) pulseRing.setBackgroundResource(pulse);
        if (tvCardLabel != null) tvCardLabel.setText(label);
    }

    private void bindStudents(JSONArray students) {
        studentsRow.removeAllViews();
        int total = students == null ? 0 : students.length();
        int added = 0;
        for (int i = 0; students != null && i < students.length(); i++) {
            JSONObject student = students.optJSONObject(i);
            if (student == null) {
                continue;
            }
            int id = student.optInt("id", student.optInt("student_id", 0));
            String name = student.optString("name", student.optString("student_name", "")).trim();
            String cls = student.optString("class", student.optString("classe", "")).trim();
            String photo = student.optString("photo", "");
            StudentHit local = id > 0 ? studentsById.get(id) : null;
            if (local != null) {
                if (name.length() == 0) name = local.name;
                if (cls.length() == 0) cls = local.classe;
                if (photo.length() < 4) photo = local.photo;
            }
            if (name.length() == 0) {
                continue;
            }
            studentsRow.addView(studentTile(name, cls, photo, Math.max(1, total)));
            added++;
        }
        studentsScroll.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
        tvStudentsLabel.setText(added <= 0 ? getString(R.string.visiting_students)
                : getString(R.string.visiting_students) + "  ·  " + added);
        tvStudentsLabel.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
    }

    private void bindVisitors(JSONArray people, String scanned, String cardHolder) {
        visitorsRow.removeAllViews();
        int added = 0;
        HashSet<Integer> seen = new HashSet<>();
        int total = people == null ? 0 : people.length();
        for (int i = 0; people != null && i < people.length(); i++) {
            JSONObject visitor = people.optJSONObject(i);
            if (visitor == null) {
                continue;
            }
            int id = visitor.optInt("id", 0);
            if (id > 0 && !seen.add(id)) {
                continue;
            }
            String name = visitor.optString("names", visitor.optString("name", "")).trim();
            if (name.length() == 0) {
                continue;
            }
            String rel = visitor.optString("relationship", "").trim();
            boolean scannedThis = isScanned(visitor, scanned, cardHolder);
            visitorsRow.addView(visitorTile(name, rel, scannedThis, Math.max(1, total)));
            added++;
        }
        tvVisitorsLabel.setText(added <= 0 ? getString(R.string.visiting_allowed)
                : getString(R.string.visiting_allowed) + "  ·  " + added);
        tvVisitorsLabel.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
    }

    private LinearLayout studentTile(String name, String cls, String photo, int total) {
        float density = getResources().getDisplayMetrics().density;
        int tileW = (int) ((total <= 1 ? 220 : 170) * density);
        int photoSize = (int) ((total <= 1 ? 120 : 88) * density);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setBackgroundResource(R.drawable.bg_visit_student_tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (8 * density), 0, (int) (8 * density), 0);
        tile.setLayoutParams(lp);
        tile.setPadding((int) (10 * density), (int) (12 * density), (int) (10 * density), (int) (12 * density));
        CircleImageView image = new CircleImageView(this);
        image.setLayoutParams(new LinearLayout.LayoutParams(photoSize, photoSize));
        StudentPhotoHelper.load(image, photo);
        tile.addView(image);
        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(total <= 1 ? 18 : 15);
        nameView.setGravity(Gravity.CENTER);
        nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);
        nameView.setPadding(0, (int) (8 * density), 0, 0);
        tile.addView(nameView);
        if (cls != null && cls.length() > 0) {
            TextView classView = new TextView(this);
            classView.setText(cls.trim());
            classView.setTextColor(0xFFBBF7D0);
            classView.setTextSize(13);
            classView.setGravity(Gravity.CENTER);
            tile.addView(classView);
        }
        return tile;
    }

    private LinearLayout visitorTile(String name, String rel, boolean scanned, int total) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackgroundResource(scanned ? R.drawable.bg_visit_visitor_tile_active : R.drawable.bg_visit_visitor_tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (6 * density);
        tile.setLayoutParams(lp);
        int pad = (int) (12 * density);
        tile.setPadding(pad, pad, pad, pad);
        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(total <= 1 ? 22 : 18);
        nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);
        tile.addView(nameView);
        TextView chip = new TextView(this);
        String label = (rel.length() > 0 ? rel : "Visitor") + (scanned ? "  ·  this card" : "");
        chip.setText(label);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(13);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = (int) (6 * density);
        chip.setLayoutParams(chipLp);
        tile.addView(chip);
        return tile;
    }

    private void refreshVisitors() {
        int schoolId = schoolId();
        if (!online() || schoolId <= 0) {
            rosterReady = true;
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId);
        new HttpRequest(this).timeouts(8, 25).post(MainActivity.SERVER + "visitor_list", map, (object, code, status) -> {
            rosterReady = true;
            if (object != null && ok(object)) {
                JSONArray list = object.optJSONArray("visitors");
                if (list != null) {
                    replaceVisitorCache(list);
                    loadVisitorCache();
                }
            }
            if (pendingCard.length() >= 4 && !busy) {
                String card = pendingCard;
                pendingCard = "";
                handleCard(card);
            }
        });
    }

    private void ensureCache() {
        db.execSQL("CREATE TABLE IF NOT EXISTS tb_visit_cache ("
                + "visitor_id INTEGER PRIMARY KEY, student_id INTEGER, names TEXT, phone TEXT, "
                + "relationship TEXT, card TEXT, photo TEXT, student_name TEXT)");
    }

    private void replaceVisitorCache(JSONArray list) {
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM tb_visit_cache");
            for (int i = 0; i < list.length(); i++) {
                JSONObject row = list.optJSONObject(i);
                if (row == null || row.optInt("id", 0) <= 0) {
                    continue;
                }
                if (row.has("status") && row.optInt("status", 1) != 1) {
                    continue;
                }
                db.execSQL("INSERT OR REPLACE INTO tb_visit_cache "
                                + "(visitor_id, student_id, names, phone, relationship, card, photo, student_name) "
                                + "VALUES (?,?,?,?,?,?,?,?)",
                        new Object[]{
                                row.optInt("id"),
                                row.optInt("student_id", 0),
                                row.optString("names", row.optString("name", "")),
                                row.optString("phone", ""),
                                row.optString("relationship", ""),
                                row.optString("card", "").trim().toUpperCase(Locale.US),
                                row.optString("photo", ""),
                                row.optString("student_name", "")
                        });
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "cache visitors", e);
        } finally {
            db.endTransaction();
        }
    }

    private void rememberVisitors(String card, JSONArray people) {
        if (people == null || people.length() == 0) {
            return;
        }
        try {
            replaceVisitorCache(mergeKnown(people, card));
            loadVisitorCache();
        } catch (Exception e) {
            Log.e(TAG, "remember visitors", e);
        }
    }

    private JSONArray mergeKnown(JSONArray fresh, String card) {
        JSONArray all = new JSONArray();
        HashSet<Integer> seen = new HashSet<>();
        for (JSONObject row : visitors) {
            int id = row.optInt("id", 0);
            if (id > 0 && seen.add(id)) {
                all.put(row);
            }
        }
        for (int i = 0; i < fresh.length(); i++) {
            JSONObject row = fresh.optJSONObject(i);
            if (row == null) {
                continue;
            }
            if (card != null && card.length() >= 4 && row.optString("card", "").length() < 4) {
                try {
                    row.put("card", card);
                } catch (Exception ignored) {
                }
            }
            int id = row.optInt("id", 0);
            if (id > 0 && !seen.add(id)) {
                continue;
            }
            all.put(row);
        }
        return all;
    }

    private void loadVisitorCache() {
        visitors.clear();
        visitorsByCard.clear();
        Cursor c = db.rawQuery("SELECT visitor_id, student_id, names, phone, relationship, card, photo, student_name FROM tb_visit_cache", null);
        while (c.moveToNext()) {
            JSONObject row = new JSONObject();
            try {
                row.put("id", c.getInt(0));
                row.put("student_id", c.getInt(1));
                row.put("names", c.getString(2) == null ? "" : c.getString(2));
                row.put("phone", c.getString(3) == null ? "" : c.getString(3));
                row.put("relationship", c.getString(4) == null ? "" : c.getString(4));
                row.put("card", c.getString(5) == null ? "" : c.getString(5));
                row.put("photo", c.getString(6) == null ? "" : c.getString(6));
                row.put("student_name", c.getString(7) == null ? "" : c.getString(7));
            } catch (Exception ignored) {
                continue;
            }
            visitors.add(row);
            String card = row.optString("card", "");
            if (card.length() >= 4) {
                for (String variant : NFCForegroundUtil.lookupVariants(card)) {
                    ArrayList<JSONObject> bucket = visitorsByCard.get(variant);
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        visitorsByCard.put(variant, bucket);
                    }
                    bucket.add(row);
                }
            }
        }
        c.close();
    }

    private void loadStudents() {
        studentsByCard.clear();
        studentsById.clear();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT id, name, regno, classe, IFNULL(photo,''), IFNULL(card,''), IFNULL(CAST(studying_mode AS TEXT),'-1') "
                            + "FROM tb_student WHERE IFNULL(status,1)<>0",
                    null);
            while (c.moveToNext()) {
                StudentHit hit = new StudentHit();
                hit.id = c.getInt(0);
                hit.name = c.getString(1) == null ? "" : c.getString(1);
                hit.regno = c.getString(2) == null ? "" : c.getString(2);
                hit.classe = c.getString(3) == null ? "" : c.getString(3);
                hit.photo = c.getString(4) == null ? "" : c.getString(4);
                hit.card = c.getString(5) == null ? "" : c.getString(5).trim();
                hit.mode = modeOf(c.getString(6));
                studentsById.put(hit.id, hit);
                if (hit.card.length() >= 4) {
                    for (String variant : NFCForegroundUtil.lookupVariants(hit.card)) {
                        studentsByCard.put(variant, hit);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "load students", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private JSONArray visitorsForCard(String raw) {
        JSONArray out = new JSONArray();
        HashSet<Integer> seen = new HashSet<>();
        if (raw == null) {
            return out;
        }
        for (String variant : NFCForegroundUtil.lookupVariants(raw)) {
            ArrayList<JSONObject> bucket = visitorsByCard.get(variant);
            if (bucket == null) {
                continue;
            }
            for (JSONObject row : bucket) {
                int id = row.optInt("id", 0);
                if (id > 0 && !seen.add(id)) {
                    continue;
                }
                out.put(row);
            }
        }
        return out;
    }

    private JSONArray parentsForStudent(int studentId) {
        JSONArray out = new JSONArray();
        for (JSONObject row : visitors) {
            if (row.optInt("student_id", 0) == studentId && isParent(row.optString("relationship", ""))) {
                out.put(row);
            }
        }
        return out;
    }

    private JSONArray filterParents(JSONArray raw) {
        JSONArray out = new JSONArray();
        if (raw == null) {
            return out;
        }
        for (int i = 0; i < raw.length(); i++) {
            JSONObject row = raw.optJSONObject(i);
            if (row != null && isParent(row.optString("relationship", ""))) {
                out.put(row);
            }
        }
        return out;
    }

    private boolean dayscholarParentsOnly(JSONArray people) {
        boolean saw = false;
        for (int i = 0; i < people.length(); i++) {
            JSONObject visitor = people.optJSONObject(i);
            if (visitor == null) {
                continue;
            }
            int studentId = visitor.optInt("student_id", 0);
            if (studentId <= 0) {
                return false;
            }
            StudentHit student = studentsById.get(studentId);
            if (student == null || student.mode != 1) {
                return false;
            }
            saw = true;
        }
        return saw;
    }

    private JSONArray studentsFor(JSONArray people) {
        JSONArray out = new JSONArray();
        HashSet<Integer> seen = new HashSet<>();
        for (int i = 0; people != null && i < people.length(); i++) {
            JSONObject visitor = people.optJSONObject(i);
            if (visitor == null) {
                continue;
            }
            int studentId = visitor.optInt("student_id", 0);
            if (studentId <= 0 || !seen.add(studentId)) {
                continue;
            }
            StudentHit local = studentsById.get(studentId);
            if (local != null) {
                out.put(studentJson(local));
            } else {
                JSONObject row = new JSONObject();
                try {
                    row.put("id", studentId);
                    row.put("name", visitor.optString("student_name", ""));
                    row.put("class", visitor.optString("student_class", visitor.optString("class", "")));
                } catch (Exception ignored) {
                }
                out.put(row);
            }
        }
        return out;
    }

    private JSONObject studentJson(StudentHit student) {
        JSONObject row = new JSONObject();
        try {
            row.put("id", student.id);
            row.put("name", student.name);
            row.put("regno", student.regno);
            row.put("class", student.classe);
            row.put("photo", student.photo);
            row.put("studying_mode", student.mode);
        } catch (Exception ignored) {
        }
        return row;
    }

    private StudentHit findStudent(String raw) {
        for (String variant : NFCForegroundUtil.lookupVariants(raw)) {
            StudentHit hit = studentsByCard.get(variant);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static boolean isParent(String relationship) {
        String rel = relationship == null ? "" : relationship.trim().toLowerCase(Locale.US);
        return rel.contains("father") || rel.contains("mother") || rel.contains("parent")
                || rel.contains("guardian") || rel.contains("dad") || rel.contains("mum")
                || rel.contains("mom");
    }

    private boolean isScanned(JSONObject visitor, String scanned, String cardHolder) {
        String card = visitor.optString("card", "").trim();
        if (scanned != null && scanned.length() >= 4 && card.length() >= 4) {
            String a = scanned.replaceAll("[^A-Fa-f0-9]", "").toUpperCase(Locale.US);
            String b = card.replaceAll("[^A-Fa-f0-9]", "").toUpperCase(Locale.US);
            if (a.equals(b) || NFCForegroundUtil.reverseBytePairs(a).equals(b)) {
                return true;
            }
        }
        if (cardHolder != null && cardHolder.length() > 0) {
            String name = visitor.optString("names", visitor.optString("name", "")).trim();
            return name.equalsIgnoreCase(cardHolder) && card.length() >= 4;
        }
        return false;
    }

    private boolean sameCard(String a, String b) {
        if (a == null || b == null || a.length() < 4 || b.length() < 4) {
            return false;
        }
        HashSet<String> left = new HashSet<>();
        for (String variant : NFCForegroundUtil.lookupVariants(a)) {
            left.add(variant);
        }
        for (String variant : NFCForegroundUtil.lookupVariants(b)) {
            if (left.contains(variant)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ok(JSONObject json) {
        if (json == null) {
            return false;
        }
        if (json.optInt("success", -1) == 1) {
            return true;
        }
        Object s = json.opt("success");
        return s instanceof Boolean && (Boolean) s;
    }

    private static String messageOf(JSONObject json, String fallback) {
        if (json == null) {
            return fallback;
        }
        String message = json.optString("message", "");
        if (message.length() == 0) {
            message = json.optString("error", "");
        }
        return message.length() == 0 ? fallback : message;
    }

    private static boolean isNoVisitor(JSONObject json) {
        return messageOf(json, "").toLowerCase(Locale.US).contains("no visitor registered");
    }

    private static boolean isVisitorResult(JSONObject json) {
        if (json == null) {
            return false;
        }
        if ("visitor".equalsIgnoreCase(json.optString("kind", ""))) {
            return json.optBoolean("allowed", false) || ok(json);
        }
        return json.optBoolean("allowed", false)
                || ((json.optJSONArray("visitors") != null || json.optJSONObject("visitor") != null)
                && (ok(json) || json.optBoolean("allowed", false) || json.has("action")));
    }

    private static boolean isActiveDenial(JSONObject json) {
        if (json == null || ok(json) || json.optBoolean("allowed", false) || isNoVisitor(json)) {
            return false;
        }
        return "visitor".equalsIgnoreCase(json.optString("kind", ""))
                || json.optJSONObject("visitor") != null
                || json.optJSONArray("visitors") != null;
    }

    private static int modeOf(String raw) {
        if (raw == null) {
            return -1;
        }
        String value = raw.trim().toLowerCase(Locale.US);
        if (value.length() == 0 || "null".equals(value)) {
            return -1;
        }
        if ("0".equals(value) || value.contains("board")) {
            return 0;
        }
        if ("1".equals(value) || value.contains("day")) {
            return 1;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private int schoolId() {
        return preferences.getInt(MainActivity.SK_ID, 0);
    }

    private boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void vibrate() {
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(30);
            }
        } catch (Exception ignored) {
        }
    }

    private static final class StudentHit {
        int id;
        String name = "";
        String regno = "";
        String classe = "";
        String photo = "";
        String card = "";
        int mode = -1;
    }
}
