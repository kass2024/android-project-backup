package com.xander.student;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.HorizontalScrollView;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KioskActivity extends Activity {
    private static final long LOGO_TAP_WINDOW_MS = 1800L;
    /** Same UID still on the reader — one clock, never IN then OUT. */
    /** Ignore only the reader's double-hit. A second tap of the same card must still speak. */
    private static final long SAME_CARD_MS = 400L;
    /** Name stays on screen unless the next student taps sooner. */
    private static final long RESULT_SHOW_MS = 1100L;
    private static final long FAIL_SHOW_MS = 700L;

    private XanderPrefs prefs;
    private XanderStore store;
    private PhotoCache photos;
    private CardReader cardReader;
    private UsbHidCardReader usbHid;
    private DkUsbCardReader dkUsb;
    private TextView tvHint;
    private TextView tvLocationLabel;
    private TextView tvLocation;
    private TextView tvStatus;
    private TextView tvName;
    private TextView tvMeta;
    private TextView tvTime;
    private TextView tvClock;
    private TextView tvClockShadow;
    private TextView tvClockDepth;
    private TextView tvDate;
    private TextView tvReady;
    private TextView tvCardLabel;
    private TextView tvSchool;
    private TextView tvSchoolShadow;
    private TextView tvNfc;
    private TextView tvSync;
    private CircleImageView imgPhoto;
    private ImageView imgLogo;
    private View resultCard;
    private View resultPanel;
    private View pulseRing;
    private View cardSpot;
    private View scanLampLeft;
    private View scanLampRight;
    private LinearLayout visitStudentsRow;
    private View visitStudentsScroll;
    private LinearLayout visitVisitorsRow;
    private View visitVisitorsScroll;
    private TextView tvVisitVisitorsLabel;
    private TextView tvVisitStudentsLabel;
    private TextView tvVisitStaffHint;
    private boolean visitResultPinned;
    /** Student card opened the parent list. The next parent card still approves. */
    private boolean visitPreviewOnly;
    private TextView tvVisitWelcome;
    private View welcomeScroll;
    private ObjectAnimator visitWelcomeBlink;
    private ValueAnimator welcomeSlide;
    private boolean visitWelcomeShown;
    private boolean visitingUiApplied;
    private String lastVisitCard = "";
    private EditText etWedge;
    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPending;
    private IntentFilter[] nfcFilters;
    private final StringBuilder keyBuf = new StringBuilder();
    private final Handler handler = new Handler();
    private String lastCard;
    private long lastCardAt;
    /** Last student this UID matched, so a repeat tap does not fall through to FAILED. */
    private String lastPersonUid;
    private JSONObject lastPerson;
    private int lastPersonGen;
    private int scanGen;
    private final ExecutorService bgScan = Executors.newSingleThreadExecutor();
    private final ExecutorService modePoll = Executors.newSingleThreadExecutor();
    private volatile boolean modePollBusy;
    private int logoTapCount;
    private long logoTapStartedAt;
    private boolean nfcReaderOn;
    private boolean netRegistered;
    private long lastPeopleSync;
    /** Last accepted tap. Sync waits until swapping has been quiet. */
    private long lastTapAt;
    /** Upload attendance and refresh cards only after a full quiet minute. */
    private static final long IDLE_SYNC_MS = 60000;

    private final NfcAdapter.ReaderCallback nfcReader = new NfcAdapter.ReaderCallback() {
        @Override
        public void onTagDiscovered(Tag tag) {
            KioskLog.i("Kiosk", "ignored Android NFC tag to keep web-compatible reader encoding");
        }
    };

    private final BroadcastReceiver netRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSyncLabel();
            if (NetWatch.online(KioskActivity.this) && swappingQuiet()) {
                backgroundSync();
            }
        }
    };

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            String clock = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now);
            if (tvClock != null) {
                tvClock.setText(clock);
            }
            if (tvClockShadow != null) {
                tvClockShadow.setText(clock);
            }
            if (tvClockDepth != null) {
                tvClockDepth.setText(clock);
            }
            if (tvDate != null) {
                tvDate.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now));
            }
            updateSyncLabel();
            refreshVisitBanner();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable hidFlush = new Runnable() {
        @Override
        public void run() {
            String raw = keyBuf.toString();
            if (etWedge != null && etWedge.getText() != null && etWedge.getText().length() > raw.length()) {
                raw = etWedge.getText().toString();
                etWedge.setText("");
            }
            keyBuf.setLength(0);
            String uid = CardReader.clean(raw);
            if (uid.length() >= 4) {
                if (cardReader != null) {
                    cardReader.ingestHid(uid);
                } else {
                    onCard(uid);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        prefs = new XanderPrefs(this);
        store = new XanderStore(this, prefs);
        photos = new PhotoCache(this);
        if (!prefs.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_xander_kiosk);

        tvHint = (TextView) findViewById(R.id.tv_hint);
        tvLocationLabel = (TextView) findViewById(R.id.tv_location_label);
        tvLocation = (TextView) findViewById(R.id.tv_location);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvName = (TextView) findViewById(R.id.tv_name);
        tvMeta = (TextView) findViewById(R.id.tv_meta);
        tvTime = (TextView) findViewById(R.id.tv_time);
        tvClock = (TextView) findViewById(R.id.tv_clock);
        tvClockShadow = (TextView) findViewById(R.id.tv_clock_shadow);
        tvClockDepth = (TextView) findViewById(R.id.tv_clock_depth);
        tvDate = (TextView) findViewById(R.id.tv_date);
        tvReady = (TextView) findViewById(R.id.tv_ready);
        tvVisitWelcome = (TextView) findViewById(R.id.tv_visit_welcome);
        welcomeScroll = findViewById(R.id.welcome_scroll);
        tvCardLabel = (TextView) findViewById(R.id.tv_card_label);
        tvSchool = (TextView) findViewById(R.id.tv_school);
        tvSchoolShadow = (TextView) findViewById(R.id.tv_school_shadow);
        tvNfc = (TextView) findViewById(R.id.tv_nfc);
        tvSync = (TextView) findViewById(R.id.tv_sync);
        imgPhoto = (CircleImageView) findViewById(R.id.img_photo);
        imgLogo = (ImageView) findViewById(R.id.img_logo);
        resultCard = findViewById(R.id.result_card);
        resultPanel = findViewById(R.id.result_panel);
        pulseRing = findViewById(R.id.pulse_ring);
        cardSpot = findViewById(R.id.card_spot);
        scanLampLeft = findViewById(R.id.scan_lamp_left);
        scanLampRight = findViewById(R.id.scan_lamp_right);
        visitStudentsScroll = findViewById(R.id.visit_students_scroll);
        visitStudentsRow = (LinearLayout) findViewById(R.id.visit_students_row);
        visitVisitorsScroll = findViewById(R.id.visit_visitors_scroll);
        visitVisitorsRow = (LinearLayout) findViewById(R.id.visit_visitors_row);
        tvVisitVisitorsLabel = (TextView) findViewById(R.id.tv_visit_visitors_label);
        tvVisitStudentsLabel = (TextView) findViewById(R.id.tv_visit_students_label);
        tvVisitStaffHint = (TextView) findViewById(R.id.tv_visit_staff_hint);
        etWedge = (EditText) findViewById(R.id.et_nfc_wedge);
        bindWedge();
        hideSystemUi();

        setSchoolName(prefs.schoolName());
        photos.bindLogo(imgLogo, prefs.schoolLogo());
        applyModeUi();

        View btSettings = findViewById(R.id.bt_settings);
        if (btSettings != null) {
            btSettings.setVisibility(View.GONE);
        }
        imgLogo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleLogoTap();
            }
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcPending = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        nfcFilters = new IntentFilter[]{
                new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        };
        updateNfcLabel();
        startPulse();
        DeviceLights.on(this);
        KioskSound.prepare(this);
        startCardReader();
        handler.post(clockTick);
        backgroundCardSync();
        backgroundSync();
        handler.post(visitorCardPoll);
        handler.post(liveModePoll);
        photos.applyRoster(store.listStudents());
        store.warmIndex();
    }

    private void bindWedge() {
        if (etWedge == null) {
            return;
        }
        etWedge.setFocusable(true);
        etWedge.setFocusableInTouchMode(true);
        etWedge.requestFocus();
        etWedge.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s == null) {
                    return;
                }
                String raw = s.toString();
                if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
                    String uid = CardReader.clean(raw);
                    s.clear();
                    ingest(uid);
                } else if (raw.length() >= 4) {
                    handler.removeCallbacks(hidFlush);
                    handler.postDelayed(hidFlush, 30);
                }
            }
        });
        etWedge.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String uid = CardReader.clean(v.getText() == null ? "" : v.getText().toString());
                    v.setText("");
                    ingest(uid);
                    return true;
                }
                return false;
            }
        });
    }

    private void setSchoolName(String name) {
        if (tvSchool != null) {
            tvSchool.setText(name);
        }
        if (tvSchoolShadow != null) {
            tvSchoolShadow.setText(name);
        }
    }

    private void ingest(String uid) {
        if (uid.length() < 4) {
            return;
        }
        if (cardReader != null) {
            cardReader.ingestHid(uid);
        } else {
            onCard(uid);
        }
    }

    private void onCard(final String rawUid) {
        final String localUid = CardUid.toStorage(rawUid);
        final String scanUid = CardUid.forScan(rawUid);
        final String uid = localUid.length() >= 4 ? localUid : scanUid;
        if (uid.length() < 4) {
            return;
        }
        // A finished visit stays on screen until the next card. That tap is a new scan.
        if (visitResultPinned) {
            visitResultPinned = false;
            visitPreviewOnly = false;
            lastVisitCard = "";
        }
        KioskLog.i("Kiosk", "scan raw=" + rawUid
                + " local=" + localUid
                + " scan=" + scanUid
                + " revScan=" + CardUid.reverseBytes(scanUid)
                + " decimalWedge=" + (rawUid != null && rawUid.trim().matches("\\d+") && rawUid.trim().length() != 8));
        android.util.Log.e("XanderVisitor", "TAP raw=" + rawUid
                + " storage=" + localUid
                + " reader=" + scanUid
                + " variants=" + java.util.Arrays.toString(CardUid.lookupVariants(rawUid)));
        long now = System.currentTimeMillis();
        if (uid.equals(lastCard) && now - lastCardAt < SAME_CARD_MS) {
            return;
        }
        lastCard = uid;
        lastCardAt = now;
        scanGen++;
        DeviceLights.idle(this);
        prepareResultSurface();

        // Student index first. Do not scan the visitor table or count the roster on this tap.
        String[] variants = CardUid.lookupVariants(rawUid);
        JSONObject person = null;
        int rosterGen = store.indexGeneration();
        if (uid.equals(lastPersonUid) && lastPerson != null && rosterGen == lastPersonGen) {
            person = lastPerson;
        }
        for (int i = 0; i < variants.length && person == null; i++) {
            person = store.findByCard(variants[i]);
        }
        if (person != null) {
            lastPersonUid = uid;
            lastPerson = person;
            lastPersonGen = store.indexGeneration();
        }
        if (person != null) {
            if (!XanderStore.isDayScholar(person)) {
                denyBoarding(person);
                return;
            }
            if (prefs.visitingOnly()) {
                openAllowedVisitors(person);
                return;
            }
            verifyStudentLocal(localUid, person);
            return;
        }
        JSONArray visitors = null;
        for (int i = 0; i < variants.length; i++) {
            JSONArray hit = store.findVisitorsByCard(variants[i]);
            if (hit != null && hit.length() > 0) {
                visitors = hit;
                break;
            }
        }
        KioskLog.i("Kiosk", "local lookup student=0 visitors="
                + (visitors == null ? 0 : visitors.length())
                + " variants=" + variants.length);
        android.util.Log.e("XanderVisitor", "LOOKUP student=0 visitors="
                + (visitors == null ? 0 : visitors.length()));

        if (visitors != null && visitors.length() > 0) {
            routeVisitorCard(localUid, visitors);
            return;
        }

        fail("", true);
        if (NetWatch.online(this)) {
            pullAssignedVisitor(rawUid, localUid, scanGen);
        }
    }

    /** A card assigned on the server while we are online. Does not clock a student. */
    private void pullAssignedVisitor(final String rawUid, final String localUid, final int gen) {
        bgScan.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject cards = XanderApi.syncCards(prefs.serverUrl(), prefs.schoolId());
                    if (!XanderApi.ok(cards)) {
                        return;
                    }
                    store.applyVisitorCardIndex(cards.optJSONArray("visitors"));
                    String[] variants = CardUid.lookupVariants(rawUid);
                    JSONArray visitors = null;
                    for (int i = 0; i < variants.length; i++) {
                        JSONArray hit = store.findVisitorsByCard(variants[i]);
                        if (hit != null && hit.length() > 0) {
                            visitors = hit;
                            break;
                        }
                    }
                    if (visitors == null) {
                        return;
                    }
                    final JSONArray found = visitors;
                    final String uid = localUid;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != scanGen) {
                                return;
                            }
                            verifyParentVisitLocal(uid, found);
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("XanderVisitor", "visitor pull " + e.getMessage());
                }
            }
        });
    }

    /**
     * Soft online probe for cards missing from local roster. Does not block UI.
     * Pulls the latest card index, then student + visitor scan.
     */
    private void probeUnknownCardBackground(final String rawUid, final String localUid, final String scanUid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String storage = localUid != null && localUid.length() >= 4 ? localUid : scanUid;
                    String reader = scanUid != null && scanUid.length() >= 4 ? scanUid : localUid;
                    android.util.Log.e("XanderVisitor", "BG_PROBE storage=" + storage + " reader=" + reader);
                    try {
                        JSONObject cards = XanderApi.syncCards(prefs.serverUrl(), prefs.schoolId());
                        if (XanderApi.ok(cards)) {
                            store.applyPeopleCards(cards);
                            String[] variants = CardUid.lookupVariants(rawUid);
                            for (int i = 0; i < variants.length; i++) {
                                JSONObject person = store.findByCard(variants[i]);
                                if (person != null) {
                                    store.cacheStudentFromScan(storage, personFromLocal(person, storage));
                                    android.util.Log.e("XanderVisitor", "BG_CACHED student from card poll " + storage);
                                    return;
                                }
                                JSONArray hit = store.findVisitorsByCard(variants[i]);
                                if (hit != null && hit.length() > 0) {
                                    android.util.Log.e("XanderVisitor", "BG_CACHED visitor from card poll " + storage);
                                    return;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    int areaId = prefs.areaId();
                    if (areaId > 0) {
                        JSONObject studentResult = XanderApi.scanCard(
                                prefs.serverUrl(), prefs.schoolId(), storage, areaId);
                        if (!XanderApi.ok(studentResult) && reader != null
                                && reader.length() >= 4 && !reader.equalsIgnoreCase(storage)) {
                            JSONObject alt = XanderApi.scanCard(
                                    prefs.serverUrl(), prefs.schoolId(), reader, areaId);
                            if (XanderApi.ok(alt)) {
                                studentResult = alt;
                            }
                        }
                        if (XanderApi.ok(studentResult)) {
                            store.cacheStudentFromScan(storage, studentResult);
                            android.util.Log.e("XanderVisitor", "BG_CACHED student under " + storage);
                            return;
                        }
                    }
                    JSONObject visitorResult = scanVisitorLikeWeb(storage, reader);
                    if (isVisitorResult(visitorResult)) {
                        store.cacheVisitorsFromScan(storage, visitorResult);
                        if (reader != null && reader.length() >= 4
                                && !reader.equalsIgnoreCase(storage)) {
                            store.cacheVisitorsFromScan(reader, visitorResult);
                        }
                        android.util.Log.e("XanderVisitor", "BG_CACHED visitor under "
                                + storage + "/" + reader + " — next tap will be instant");
                    }
                } catch (Exception e) {
                    android.util.Log.e("XanderVisitor", "BG_PROBE err " + e.getMessage());
                }
            }
        }, "card-bg-probe").start();
    }

    private JSONObject personFromLocal(JSONObject person, String card) {
        JSONObject wrap = new JSONObject();
        try {
            wrap.put("success", 1);
            wrap.put("person", person);
            if (person != null && (person.optString("card", "").length() < 4) && card != null) {
                person.put("card", card);
            }
        } catch (Exception ignored) {
        }
        return wrap;
    }

    /** Fire-and-forget server confirm for a visitor already shown locally. */
    private void syncVisitorOnlineBackground(final String localUid, final String scanUid) {
        bgScan.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String webUid = localUid != null && localUid.length() >= 4 ? localUid : scanUid;
                    JSONObject remote = XanderApi.scanVisitorCard(prefs.serverUrl(), prefs.schoolId(), webUid);
                    if (remote == null || !(XanderApi.ok(remote) || remote.optBoolean("allowed", false))) {
                        return;
                    }
                    store.cacheVisitorsFromScan(webUid, remote);
                    final JSONObject copy = remote;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (visitResultPinned) {
                                refreshVisitPeople(copy);
                            }
                        }
                    });
                } catch (Exception e) {
                    KioskLog.e("Kiosk", "bg visitor sync: " + e.getMessage());
                }
            }
        });
    }

    private void verifyStudentLocal(String uid, JSONObject person) {
        if (!XanderStore.isDayScholar(person)) {
            denyBoarding(person);
            return;
        }
        int area = prefs.areaId();
        if (area <= 0) {
            DeviceLights.fail(this);
            setScanLamp(-1);
            fail("Set a location in Settings", false);
            return;
        }
        JSONObject local = store.clockLocal(
                person.optInt("id", 0),
                area,
                uid,
                person.optString("name", ""),
                person.optString("extra", person.optString("class", "")),
                person.optString("photo", ""));
        try {
            JSONObject shown = local.optJSONObject("person");
            if (shown != null) {
                shown.put("regno", person.optString("regno", ""));
                shown.put("class", person.optString("class", person.optString("extra", "")));
                shown.put("studying_mode", XanderStore.studyingModeOf(person));
            }
        } catch (Exception ignored) {
        }
        showResult(local);
        store.pushPendingNow();
        queueFlush();
    }

    private void denyBoarding(JSONObject person) {
        DeviceLights.fail(this);
        setScanLamp(-1);
        JSONObject json = new JSONObject();
        try {
            json.put("success", 0);
            json.put("denied", "boarding");
            json.put("message", "Boarding students cannot swipe here");
            if (person != null) {
                json.put("person", person);
            }
        } catch (Exception ignored) {
        }
        showResult(json);
    }

    /** Dayscholar card in visiting-only: same allowed-visitor list a boarding visit shows. */
    private void showAllowedVisitorsForStudent(JSONObject person) {
        int studentId = person == null ? 0 : person.optInt("id", 0);
        JSONArray visitors = registeredParents(store.visitorsForStudent(studentId));
        JSONObject local = new JSONObject();
        JSONArray students = new JSONArray();
        if (person != null) {
            students.put(person);
        }
        boolean hasParents = visitors.length() > 0;
        try {
            local.put("success", 1);
            local.put("allowed", true);
            local.put("action", "preview");
            local.put("visitors", visitors);
            local.put("students", students);
            if (person != null) {
                local.put("student", person);
            }
            local.put("message", hasParents ? "Registered parents" : "No registered parents");
        } catch (Exception ignored) {
        }
        showParentVisitResult(local);
    }

    /** Parent card, including a dayscholar's parents. The visit is saved for the report. */
    private void routeVisitorCard(String uid, JSONArray visitors) {
        openParentVisit(uid, visitors);
    }


    private JSONArray registeredParents(JSONArray visitors) {
        JSONArray out = new JSONArray();
        for (int i = 0; visitors != null && i < visitors.length(); i++) {
            JSONObject visitor = visitors.optJSONObject(i);
            if (visitor != null && isRegisteredParent(visitor.optString("relationship", ""))) {
                out.put(visitor);
            }
        }
        return out;
    }

    /** Visitors whose card UID is the tapped card. Shared-card siblings stay. Other people do not. */
    private JSONArray onlyRegisteredOnCard(JSONArray visitors, String scannedCard) {
        JSONArray out = new JSONArray();
        if (visitors == null || scannedCard == null) {
            return out;
        }
        String scanned = scannedCard.replaceAll("[^A-Fa-f0-9]", "").toUpperCase(Locale.US);
        if (scanned.length() < 4) {
            return out;
        }
        String rev = CardUid.reverseBytes(scanned);
        for (int i = 0; i < visitors.length(); i++) {
            JSONObject visitor = visitors.optJSONObject(i);
            if (visitor == null) {
                continue;
            }
            String raw = visitor.optString("card", visitor.optString("card_stored", ""));
            String owned = raw.replaceAll("[^A-Fa-f0-9]", "").toUpperCase(Locale.US);
            if (owned.length() < 4) {
                continue;
            }
            String ownedRev = CardUid.reverseBytes(owned);
            if (owned.equals(scanned) || owned.equals(rev) || ownedRev.equals(scanned)) {
                out.put(visitor);
            }
        }
        return out;
    }

    /**
     * Normal-mode visiting card. People with a different card are dropped.
     * Shared-card siblings stay. A payload with no card numbers falls back to the local roster for this UID.
     */
    private JSONArray visitorsOnThisCard(JSONArray visitors, String scannedCard) {
        boolean sawCard = false;
        if (visitors != null) {
            for (int i = 0; i < visitors.length(); i++) {
                JSONObject visitor = visitors.optJSONObject(i);
                if (visitor == null) {
                    continue;
                }
                String raw = visitor.optString("card", visitor.optString("card_stored", ""));
                if (raw.replaceAll("[^A-Fa-f0-9]", "").length() >= 4) {
                    sawCard = true;
                    break;
                }
            }
        }
        JSONArray matched = onlyRegisteredOnCard(visitors, scannedCard);
        if (sawCard) {
            return matched;
        }
        if (scannedCard != null && scannedCard.length() >= 4) {
            JSONArray local = store.findVisitorsByCard(scannedCard);
            if (local != null && local.length() > 0) {
                return local;
            }
        }
        return visitors == null ? new JSONArray() : visitors;
    }

    private static boolean isRegisteredParent(String relationship) {
        String rel = relationship == null ? "" : relationship.trim().toLowerCase(Locale.US);
        return rel.contains("father") || rel.contains("mother") || rel.contains("parent")
                || rel.contains("guardian") || rel.contains("dad") || rel.contains("mum")
                || rel.contains("mom");
    }


    /** Visitors already on the device are shown and clocked at once. The upload follows. */
    private void openParentVisit(final String uid, final JSONArray localVisitors) {
        if (localVisitors != null && localVisitors.length() > 0) {
            verifyParentVisitLocal(uid, localVisitors);
            return;
        }
        if (!NetWatch.online(this)) {
            verifyParentVisitLocal(uid, localVisitors);
            return;
        }
        final int gen = scanGen;
        bgScan.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject remote = null;
                try {
                    remote = XanderApi.scanVisitorCard(prefs.serverUrl(), prefs.schoolId(), uid);
                } catch (Exception e) {
                    KioskLog.e("Kiosk", "live visitor: " + e.getMessage());
                }
                final JSONObject live = remote;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (staleScan(gen)) {
                            return;
                        }
                        if (live != null && (XanderApi.ok(live) || live.optBoolean("allowed", false))) {
                            store.rememberLiveVisitorClock(uid, live);
                            if (live.has("visit_mode")) {
                                prefs.saveVisitMode(live.optString("visit_mode", "normal"));
                            }
                            showParentVisitResult(live);
                            return;
                        }
                        verifyParentVisitLocal(uid, localVisitors);
                    }
                });
            }
        });
    }

    /** Dayscholar card: show the registered parents and save a visit for the report. */
    private void openAllowedVisitors(final JSONObject person) {
        if (person == null) {
            return;
        }
        int studentId = person.optInt("id", 0);
        JSONArray parents = registeredParents(store.visitorsForStudent(studentId));
        if (parents.length() == 0) {
            showAllowedVisitorsForStudent(person);
            return;
        }
        java.util.LinkedHashMap<String, JSONArray> byCard = new java.util.LinkedHashMap<String, JSONArray>();
        for (int i = 0; i < parents.length(); i++) {
            JSONObject parent = parents.optJSONObject(i);
            if (parent == null) {
                continue;
            }
            String card = parent.optString("card", parent.optString("card_stored", ""));
            card = card.replaceAll("[^A-Fa-f0-9]", "").toUpperCase(Locale.US);
            if (card.length() < 4) {
                continue;
            }
            JSONArray group = byCard.get(card);
            if (group == null) {
                group = new JSONArray();
                byCard.put(card, group);
            }
            group.put(parent);
        }
        if (byCard.isEmpty()) {
            showAllowedVisitorsForStudent(person);
            return;
        }
        JSONObject last = null;
        for (java.util.Map.Entry<String, JSONArray> entry : byCard.entrySet()) {
            last = store.clockVisitorLocal(entry.getKey(), entry.getValue());
        }
        if (last == null) {
            showAllowedVisitorsForStudent(person);
            return;
        }
        try {
            last.put("visitors", parents);
            JSONArray students = new JSONArray();
            students.put(person);
            last.put("students", students);
            last.put("student", person);
        } catch (Exception ignored) {
        }
        showParentVisitResult(last);
        store.pushPendingNow();
        queueFlush();
    }

    private void verifyParentVisitLocal(String uid, JSONArray visitors) {
        DeviceLights.pass(this);
        setScanLamp(1);
        JSONObject local = store.clockVisitorLocal(uid, visitors);
        try {
            local.put("visitors", visitors);
            local.put("students", store.studentsForVisitors(visitors));
        } catch (Exception ignored) {
        }
        showParentVisitResult(local);
        store.pushPendingNow();
        queueFlush();
    }

    /**
     * Online auto-detect for cards not in local DB.
     * A newer tap (scanGen) drops this result so CHECKING never stacks over the next student.
     */
    private void verifyCardOnline(final String localUid, final String scanUid, final JSONObject localPerson, final JSONArray localVisitors) {
        final int gen = scanGen;
        if (!NetWatch.online(this)) {
            if (localVisitors != null && localVisitors.length() > 0) {
                verifyParentVisitLocal(localUid, localVisitors);
            } else if (localPerson != null) {
                verifyStudentLocal(localUid, localPerson);
            } else {
                DeviceLights.fail(this);
                setScanLamp(-1);
                fail("Card not registered\nUID " + localUid, true);
            }
            return;
        }
        tvStatus.setText("CHECKING");
        tvName.setText("Verifying card");
        tvMeta.setText("Auto: student attendance or parent visiting");
        tvTime.setText("");
        imgPhoto.setImageResource(R.drawable.bg_photo_placeholder);
        if (resultPanel != null) {
            resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
        }
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(1f);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (staleScan(gen)) {
                    return;
                }
                try {
                    final int areaId = prefs.areaId();
                    final String webStorageUid = localUid != null && localUid.length() >= 4
                            ? localUid : scanUid;
                    final String readerUid = scanUid != null && scanUid.length() >= 4
                            ? scanUid : localUid;

                    KioskLog.i("Kiosk", "online web-parity storage=" + webStorageUid
                            + " reader=" + readerUid
                            + " school=" + prefs.schoolId()
                            + " area=" + areaId
                            + " rosterS=" + store.studentCount()
                            + " rosterV=" + store.visitorCount());

                    try {
                        JSONObject cards = XanderApi.syncCardsQuick(prefs.serverUrl(), prefs.schoolId());
                        if (XanderApi.ok(cards)) {
                            store.applyPeopleCards(cards);
                            JSONObject localHit = store.findByCard(webStorageUid);
                            if (localHit == null && readerUid != null) {
                                localHit = store.findByCard(readerUid);
                            }
                            if (localHit == null) {
                                String[] variants = CardUid.lookupVariants(
                                        webStorageUid.length() >= 4 ? webStorageUid : readerUid);
                                for (int i = 0; i < variants.length && localHit == null; i++) {
                                    localHit = store.findByCard(variants[i]);
                                }
                            }
                            if (localHit != null) {
                                final JSONObject person = localHit;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (staleScan(gen)) {
                                            return;
                                        }
                                        verifyStudentLocal(localUid, person);
                                    }
                                });
                                if (areaId > 0) {
                                    try {
                                        XanderApi.scanCard(prefs.serverUrl(), prefs.schoolId(), webStorageUid, areaId);
                                    } catch (Exception ignored) {
                                    }
                                }
                                return;
                            }
                            JSONArray localVis = store.findVisitorsByCard(webStorageUid);
                            if ((localVis == null || localVis.length() == 0) && readerUid != null) {
                                localVis = store.findVisitorsByCard(readerUid);
                            }
                            if (localVis != null && localVis.length() > 0) {
                                final JSONArray vis = localVis;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (staleScan(gen)) {
                                            return;
                                        }
                                        verifyParentVisitLocal(localUid, vis);
                                    }
                                });
                                return;
                            }
                        }
                    } catch (Exception e) {
                        KioskLog.e("Kiosk", "card poll during verify: " + e.getMessage());
                    }

                    if (staleScan(gen)) {
                        return;
                    }

                    final JSONObject[] studentHolder = new JSONObject[1];
                    final JSONObject[] visitorHolder = new JSONObject[1];
                    Thread tStudent = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (areaId <= 0 || staleScan(gen)) {
                                return;
                            }
                            try {
                                JSONObject result = XanderApi.scanCard(
                                        prefs.serverUrl(), prefs.schoolId(), webStorageUid, areaId);
                                if (!XanderApi.ok(result) && readerUid != null
                                        && readerUid.length() >= 4
                                        && !readerUid.equalsIgnoreCase(webStorageUid)) {
                                    JSONObject alt = XanderApi.scanCard(
                                            prefs.serverUrl(), prefs.schoolId(), readerUid, areaId);
                                    if (XanderApi.ok(alt)) {
                                        result = alt;
                                    }
                                }
                                studentHolder[0] = result;
                            } catch (Exception e) {
                                KioskLog.e("Kiosk", "device_scan err " + e.getMessage());
                            }
                        }
                    }, "online-student");
                    Thread tVisitor = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (staleScan(gen)) {
                                return;
                            }
                            try {
                                visitorHolder[0] = scanVisitorLikeWeb(webStorageUid, readerUid);
                            } catch (Exception e) {
                                KioskLog.e("Kiosk", "visitor scan err " + e.getMessage());
                            }
                        }
                    }, "online-visitor");
                    tStudent.start();
                    tVisitor.start();
                    try {
                        tStudent.join(700);
                    } catch (InterruptedException ignored) {
                    }
                    try {
                        tVisitor.join(700);
                    } catch (InterruptedException ignored) {
                    }
                    if (staleScan(gen)) {
                        return;
                    }

                    JSONObject visitorResult = visitorHolder[0];
                    if (isVisitorResult(visitorResult) || isActiveVisitorDenial(visitorResult)) {
                        store.cacheVisitorsFromScan(webStorageUid, visitorResult);
                        final JSONObject vFinal = visitorResult;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (staleScan(gen)) {
                                    return;
                                }
                                showParentVisitResult(vFinal);
                            }
                        });
                        return;
                    }

                    JSONObject result = studentHolder[0];
                    logApiResult("device_scan " + webStorageUid, result);
                    if (XanderApi.ok(result)) {
                        store.cacheStudentFromScan(webStorageUid, result);
                    }

                    final JSONObject finalResult = result;
                    final String shownUid = webStorageUid;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (staleScan(gen)) {
                                return;
                            }
                            applyOnlineScanResult(finalResult, localUid, localPerson, localVisitors, shownUid);
                        }
                    });
                } catch (final Exception e) {
                    KioskLog.e("Kiosk", "online verify fail: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (staleScan(gen)) {
                                return;
                            }
                            if (localVisitors != null && localVisitors.length() > 0) {
                                verifyParentVisitLocal(localUid, localVisitors);
                            } else if (localPerson != null) {
                                verifyStudentLocal(localUid, localPerson);
                            } else {
                                DeviceLights.fail(KioskActivity.this);
                                setScanLamp(-1);
                                fail(KioskLog.friendlyNet(e) + "\nUID " + localUid, false);
                            }
                        }
                    });
                }
            }
        }, "card-verify").start();
    }

    private boolean staleScan(int gen) {
        return gen != scanGen;
    }

    /** Drop the current overlay instantly so the next student replaces it. */
    private void prepareResultSurface() {
        handler.removeCallbacks(hideResult);
        if (resultCard != null) {
            resultCard.animate().cancel();
            resultCard.setAlpha(1f);
        }
    }

    /** Same order as web: storage UID (001EA28E) then reader (8EA21E00). */
    private JSONObject scanVisitorLikeWeb(String storageUid, String readerUid) {
        JSONObject last = null;
        String[] order = new String[]{storageUid, readerUid};
        for (int i = 0; i < order.length; i++) {
            String uid = order[i] == null ? "" : order[i].trim();
            if (uid.length() < 4) {
                continue;
            }
            if (i > 0 && uid.equalsIgnoreCase(order[0])) {
                continue;
            }
            try {
                KioskLog.i("Kiosk", "visitor_card_scan (web) uid=" + uid);
                JSONObject result = XanderApi.scanVisitorCard(
                        prefs.serverUrl(), prefs.schoolId(), uid);
                logApiResult("visitor_card_scan " + uid, result);
                last = result;
                if (isVisitorResult(result) || isActiveVisitorDenial(result)) {
                    return result;
                }
                if (isNoVisitorRegistered(result)) {
                    continue;
                }
            } catch (Exception e) {
                KioskLog.e("Kiosk", "visitor_card_scan err uid=" + uid + " " + e.getMessage());
            }
        }
        return last;
    }

    private boolean isNoVisitorRegistered(JSONObject json) {
        if (json == null) {
            return false;
        }
        String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
        return msg.contains("no visitor registered");
    }

    /** Denial for a known visitor (inactive etc.) — not a generic miss. */
    private boolean isActiveVisitorDenial(JSONObject json) {
        if (json == null || XanderApi.ok(json) || json.optBoolean("allowed", false)) {
            return false;
        }
        if (isNoVisitorRegistered(json)) {
            return false;
        }
        return "visitor".equalsIgnoreCase(json.optString("kind", ""))
                || json.optJSONObject("visitor") != null
                || json.optJSONArray("visitors") != null;
    }

    private void applyOnlineScanResult(JSONObject result, String localUid,
                                       JSONObject localPerson, JSONArray localVisitors) {
        applyOnlineScanResult(result, localUid, localPerson, localVisitors, localUid);
    }

    private void applyOnlineScanResult(JSONObject result, String localUid,
                                       JSONObject localPerson, JSONArray localVisitors, String shownUid) {
        if (isVisitorResult(result) || isActiveVisitorDenial(result)) {
            showParentVisitResult(result);
            return;
        }
        if (isBoardingDenied(result) || (result != null && result.optJSONObject("person") != null
                && !XanderStore.isDayScholar(result.optJSONObject("person")))) {
            store.cacheStudentFromScan(localUid, result);
            JSONObject person = result.optJSONObject("person");
            if (person == null) {
                person = localPerson;
            }
            if (person != null) {
                denyBoarding(person);
            } else {
                showResult(result);
            }
            return;
        }
        if (XanderApi.ok(result)) {
            store.cacheStudentFromScan(localUid, result);
            showResult(result);
            return;
        }
        if (localVisitors != null && localVisitors.length() > 0) {
            verifyParentVisitLocal(localUid, localVisitors);
            return;
        }
        if (localPerson != null) {
            verifyStudentLocal(localUid, localPerson);
            return;
        }
        String msg = XanderApi.message(result, "Card not registered");
        String uid = shownUid != null && shownUid.length() >= 4 ? shownUid : localUid;
        fail(msg + "\nUID " + uid, true);
    }

    private boolean isUsefulScanResult(JSONObject json) {
        if (json == null) {
            return false;
        }
        if (XanderApi.ok(json) || isVisitorResult(json) || isVisitorDenial(json) || isBoardingDenied(json)) {
            return true;
        }
        String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
        // Generic miss — allow UID-order retry.
        return !(msg.contains("card not found")
                || msg.contains("no visitor registered")
                || msg.contains("not registered")
                || msg.contains("school and card"));
    }

    private void logApiResult(String label, JSONObject json) {
        if (json == null) {
            KioskLog.e("Kiosk", label + " null");
            return;
        }
        KioskLog.i("Kiosk", label
                + " ok=" + XanderApi.ok(json)
                + " allowed=" + json.optBoolean("allowed", false)
                + " kind=" + json.optString("kind", "")
                + " action=" + json.optString("action", "")
                + " msg=" + XanderApi.message(json, ""));
    }

    private boolean isBoardingDenied(JSONObject json) {
        if (json == null) {
            return false;
        }
        if ("boarding".equalsIgnoreCase(json.optString("denied", ""))) {
            return true;
        }
        String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
        return msg.contains("boarding") && (msg.contains("not allow") || msg.contains("cannot")
                || msg.contains("day scholar") || msg.contains("day student"));
    }

    private void showResult(JSONObject json) {
        boolean ok = XanderApi.ok(json);
        JSONObject person = json.optJSONObject("person");
        String status = json.optString("status", "");
        String name = person == null ? "" : person.optString("name", "");
        String extra = studentMetaLine(person);
        String msg = XanderApi.message(json, "");
        tvName.setText(name.length() == 0 ? XanderApi.message(json, "Unknown") : name);
        tvMeta.setText(extra.length() == 0 ? msg : extra);
        if (isBoardingDenied(json) || XanderStore.studyingModeOf(person) == 0) {
            showAttention(name.length() == 0 ? "Boarding student" : name);
            DeviceLights.fail(this);
            setScanLamp(-1);
            KioskSound.alert(this);
        } else if (ok && json.optInt("wait", 0) == 1) {
            showPlainStatus();
            tvStatus.setText("WAIT");
            tvTime.setText(json.optString("time", ""));
            tvMeta.setText(msg.length() == 0 ? "Not time to clock" : msg);
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.alreadyIn(this);
        } else if (ok && "LUNCH".equalsIgnoreCase(status)) {
            showPlainStatus();
            tvStatus.setText(json.optInt("already", 0) == 1 ? "Already" : "LUNCH");
            tvTime.setText("LUNCH  " + json.optString("time", ""));
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_out);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            if (json.optInt("already", 0) == 1) {
                KioskSound.alreadyIn(this);
            } else {
                KioskSound.lunch(this);
                GateRelay.pulse(this);
            }
        } else if (ok && "BACK".equalsIgnoreCase(status)) {
            showPlainStatus();
            tvStatus.setText(json.optInt("already", 0) == 1 ? "Already" : "BACK");
            tvTime.setText("BACK  " + json.optString("time", ""));
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            if (json.optInt("already", 0) == 1) {
                KioskSound.alreadyIn(this);
            } else {
                KioskSound.backFromLunch(this);
                GateRelay.pulse(this);
            }
        } else if (ok && json.optInt("already", 0) == 1) {
            showPlainStatus();
            tvStatus.setText("OUT".equalsIgnoreCase(status) ? "Already out" : "Already In");
            tvTime.setText(("OUT".equalsIgnoreCase(status) ? "OUT  " : "IN  ") + json.optString("time", ""));
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.alreadyIn(this);
        } else if (ok && "IN".equalsIgnoreCase(status)) {
            showPlainStatus();
            tvStatus.setText("WELCOME");
            tvTime.setText("IN  " + json.optString("time", ""));
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.welcome(this);
            GateRelay.pulse(this);
        } else if (ok && "OUT".equalsIgnoreCase(status)) {
            showPlainStatus();
            tvStatus.setText("GOODBYE");
            tvTime.setText("OUT  " + json.optString("time", ""));
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_out);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.goodbye(this);
            GateRelay.pulse(this);
        } else {
            tvStatus.setText("HOLD");
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
            }
            tvMeta.setText(msg.length() == 0 ? "Not accepted" : msg);
            DeviceLights.fail(this);
            setScanLamp(-1);
            KioskSound.alert(this);
        }
        int sid = person == null ? 0 : person.optInt("id", 0);
        String photo = person == null ? "" : person.optString("photo", "");
        photos.bind(imgPhoto, sid, photo);
        if (imgPhoto != null) {
            imgPhoto.setVisibility(View.VISIBLE);
        }
        hideVisitExtras();
        visitResultPinned = false;
        if (resultCard != null) {
            resultCard.animate().cancel();
            resultCard.setVisibility(View.VISIBLE);
            resultCard.setAlpha(1f);
        }
        handler.removeCallbacks(hideResult);
        handler.postDelayed(hideResult, RESULT_SHOW_MS);
        updateSyncLabel();
    }

    /** Class only — never "P5 A · P5 A". */
    private String studentMetaLine(JSONObject person) {
        if (person == null) {
            return "";
        }
        String cls = person.optString("class", "").trim();
        if (cls.length() == 0) {
            cls = person.optString("extra", "").trim();
        }
        String regno = person.optString("regno", "").trim();
        if (cls.length() > 0) {
            return cls;
        }
        return regno;
    }

    private void showParentVisitResult(JSONObject json) {
        boolean ok = XanderApi.ok(json) || json.optBoolean("allowed", false);
        String action = json.optString("action", "").toLowerCase(Locale.US);
        JSONArray visitors = json.optJSONArray("visitors");
        JSONObject primaryVisitor = json.optJSONObject("visitor");
        if ((visitors == null || visitors.length() == 0) && primaryVisitor != null) {
            visitors = new JSONArray();
            visitors.put(primaryVisitor);
        }
        JSONArray students;
        if (!"preview".equals(action)) {
            String scanned = lastCard != null && lastCard.length() >= 4
                    ? lastCard
                    : json.optString("card", "");
            visitors = visitorsOnThisCard(visitors, scanned);
            students = store.studentsForVisitors(visitors);
        } else {
            students = json.optJSONArray("students");
            if (students == null || students.length() == 0) {
                students = store.studentsForVisitors(visitors);
            }
        }
        JSONArray displayVisitors = store.uniqueVisitorsForDisplay(visitors);
        if (displayVisitors != null && displayVisitors.length() > 0) {
            visitors = displayVisitors;
            try {
                json.put("visitors", displayVisitors);
            } catch (Exception ignored) {
            }
        }
        if (ok) {
            String card = lastCard != null && lastCard.length() >= 4
                    ? lastCard
                    : json.optString("card", "");
            if (card != null && card.length() >= 4) {
                store.cacheVisitorsFromScan(card, json);
            }
        }
        JSONObject primaryStudent = json.optJSONObject("student");
        if (primaryStudent == null && students != null && students.length() > 0) {
            primaryStudent = students.optJSONObject(0);
        }

        String cardHolder = "";
        if (visitors != null && visitors.length() > 0) {
            JSONObject first = visitors.optJSONObject(0);
            if (first != null) {
                cardHolder = first.optString("names", first.optString("name", "")).trim();
            }
        }
        if (cardHolder.length() == 0 && primaryVisitor != null) {
            cardHolder = primaryVisitor.optString("names", primaryVisitor.optString("name", "")).trim();
        }
        String msg = XanderApi.message(json, ok ? "Visitor verified" : "Visitor not allowed");
        String clock = json.optString("time_label", "");
        if (clock.length() > 5 && clock.contains(":")) {
            int sp = clock.lastIndexOf(' ');
            if (sp > 0 && clock.length() - sp >= 5) {
                clock = clock.substring(sp + 1, Math.min(clock.length(), sp + 6));
            }
        }

        tvName.setText(cardHolder.length() == 0 ? "Visitor card" : cardHolder);
        tvName.setVisibility(ok ? View.GONE : View.VISIBLE);
        tvMeta.setText(ok ? "" : msg);
        tvMeta.setVisibility(ok ? View.GONE : View.VISIBLE);
        imgPhoto.setImageResource(R.drawable.bg_photo_placeholder);
        if (imgPhoto != null) {
            imgPhoto.setVisibility(View.GONE);
        }
        bindVisitVisitors(visitors, lastCard, cardHolder);
        bindVisitStudents(students, primaryStudent);

        boolean preview = ok && "preview".equals(action);
        if (preview) {
            boolean hasParents = visitors != null && visitors.length() > 0;
            tvStatus.setText(hasParents ? "WELCOME" : "NO VISITORS");
            tvTime.setText("");
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(hasParents ? R.drawable.bg_result_in : R.drawable.bg_result_hold);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            if (hasParents) {
                KioskSound.welcome(this);
            } else {
                KioskSound.alert(this);
            }
            visitPreviewOnly = true;
        } else if (ok && "out".equals(action)) {
            tvStatus.setText("VISIT OUT");
            tvTime.setText(clock.length() == 0 ? "OUT" : "OUT  " + clock);
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_out);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.goodbye(this);
        } else if (ok) {
            tvStatus.setText("mixed".equals(action) ? "UPDATED" : "VISIT IN");
            tvTime.setText(clock.length() == 0 ? "IN" : "IN  " + clock);
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.welcome(this);
        } else {
            tvStatus.setText("DENIED");
            tvTime.setText("");
            tvMeta.setText(msg);
            tvMeta.setVisibility(View.VISIBLE);
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
            }
            DeviceLights.fail(this);
            setScanLamp(-1);
            KioskSound.alert(this);
        }

        resultCard.setVisibility(View.VISIBLE);
        resultCard.animate().cancel();
        resultCard.setAlpha(1f);
        handler.removeCallbacks(hideResult);
        visitResultPinned = true;
        if (preview) {
            lastVisitCard = "";
            if (tvVisitStaffHint != null) {
                tvVisitStaffHint.setText(visitors != null && visitors.length() > 0
                        ? "Registered parents"
                        : "No registered parents");
                tvVisitStaffHint.setVisibility(View.VISIBLE);
            }
        } else {
            visitPreviewOnly = false;
            lastVisitCard = lastCard;
            if (tvVisitStaffHint != null) {
                tvVisitStaffHint.setVisibility(View.GONE);
            }
        }
        setVisitWelcome(false);
        updateSyncLabel();
    }

    private final Runnable hideResult = new Runnable() {
        @Override
        public void run() {
            if (resultCard != null) {
                resultCard.animate().cancel();
                resultCard.setVisibility(View.GONE);
                resultCard.setAlpha(1f);
            }
            hideVisitExtras();
            if (imgPhoto != null) {
                imgPhoto.setVisibility(View.VISIBLE);
            }
            visitResultPinned = false;
            visitPreviewOnly = false;
            lastVisitCard = "";
            refreshVisitBanner();
            DeviceLights.idle(KioskActivity.this);
            setScanLamp(0);
            tvHint.setText(defaultHintText());
            if (tvReady != null) {
                tvReady.setText("READY");
            }
            focusWedge();
        }
    };

    private void showPlainStatus() {
        if (tvStatus != null) {
            tvStatus.setTextColor(0xFFFFFFFF);
            tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 64);
        }
        if (tvName != null) {
            tvName.setTextColor(0xFFFFFFFF);
            tvName.setVisibility(android.view.View.VISIBLE);
        }
        if (tvMeta != null) {
            tvMeta.setTextColor(0xFFE0E7FF);
            tvMeta.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void showAttention(String name) {
        if (tvStatus != null) {
            tvStatus.setText("ATTENTION");
            tvStatus.setTextColor(0xFFFF2A2A);
            tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 78);
        }
        if (tvName != null) {
            tvName.setText(name);
            tvName.setTextColor(0xFFFF2A2A);
            tvName.setVisibility(android.view.View.VISIBLE);
        }
        if (tvMeta != null) {
            tvMeta.setText("Boarding students cannot swipe");
            tvMeta.setTextColor(0xFFFF2A2A);
            tvMeta.setVisibility(android.view.View.VISIBLE);
        }
        if (tvTime != null) {
            tvTime.setText("");
        }
        if (resultPanel != null) {
            resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
        }
    }

    private void fail(String msg, boolean unknownCard) {
        if (unknownCard) {
            if (tvStatus != null) {
                tvStatus.setText("FAILED");
                tvStatus.setTextColor(0xFFFF2A2A);
                tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 92);
            }
            if (tvName != null) {
                tvName.setText("");
                tvName.setVisibility(android.view.View.GONE);
            }
            if (tvMeta != null) {
                tvMeta.setText("");
                tvMeta.setVisibility(android.view.View.GONE);
            }
        } else {
            showPlainStatus();
            tvStatus.setText("HOLD");
            tvName.setText(msg);
            tvMeta.setText("");
        }
        tvTime.setText("");
        imgPhoto.setImageResource(R.drawable.bg_photo_placeholder);
        if (imgPhoto != null) {
            imgPhoto.setVisibility(unknownCard ? android.view.View.GONE : android.view.View.VISIBLE);
        }
        hideVisitExtras();
        visitResultPinned = false;
        if (resultPanel != null) {
            resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
        }
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(1f);
        DeviceLights.fail(this);
        setScanLamp(-1);
        KioskSound.alert(this);
        handler.removeCallbacks(hideResult);
        handler.postDelayed(hideResult, FAIL_SHOW_MS);
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
            if (tvReady != null) {
                tvReady.setText("REGISTERED");
            }
        } else if (state < 0) {
            lamp = R.drawable.bg_lamp_bad;
            spot = R.drawable.bg_card_spot_bad;
            pulse = R.drawable.bg_pulse_bad;
            label = "NO";
            if (tvReady != null) {
                tvReady.setText("UNKNOWN");
            }
        } else if (tvReady != null) {
            tvReady.setText("READY");
        }
        if (scanLampLeft != null) {
            scanLampLeft.setBackgroundResource(lamp);
        }
        if (scanLampRight != null) {
            scanLampRight.setBackgroundResource(lamp);
        }
        if (cardSpot != null) {
            cardSpot.setBackgroundResource(spot);
        }
        if (pulseRing != null) {
            pulseRing.setBackgroundResource(pulse);
        }
        if (tvCardLabel != null) {
            tvCardLabel.setText(label);
        }
    }

    private void openSettingsLocked() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Password");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setMessage("Enter password to continue")
                .setView(input)
                .setPositiveButton("Open", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String pass = input.getText() == null ? "" : input.getText().toString();
                        if ("HFSecurity".equals(pass)) {
                            startActivity(new Intent(KioskActivity.this, SettingsActivity.class));
                        } else {
                            Toast.makeText(KioskActivity.this, "Wrong password", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleLogoTap() {
        long now = System.currentTimeMillis();
        if (logoTapStartedAt == 0L || now - logoTapStartedAt > LOGO_TAP_WINDOW_MS) {
            logoTapStartedAt = now;
            logoTapCount = 0;
        }
        logoTapCount++;
        if (logoTapCount >= 3) {
            logoTapCount = 0;
            logoTapStartedAt = 0L;
            openSettingsLocked();
        }
    }

    private void applyModeUi() {
        boolean visiting = prefs.visitingOnly();
        visitingUiApplied = visiting;
        if (tvLocationLabel != null) {
            tvLocationLabel.setText(visiting ? "Device mode:" : "Attendance location:");
        }
        if (tvLocation != null) {
            if (visiting) {
                tvLocation.setText("Parent visiting");
            } else {
                String loc = prefs.areaName();
                tvLocation.setText(loc.length() == 0 ? "Set location in Settings" : loc);
            }
        }
        if (tvHint != null) {
            tvHint.setText(defaultHintText());
        }
        if (tvNfc != null) {
            tvNfc.setText(visiting
                    ? "Dayscholar card shows registered parents. No visit is saved."
                    : "Use the bottom reader for student and visitor cards");
        }
        refreshVisitBanner();
    }

    private void refreshVisitBanner() {
        boolean visiting = prefs.visitingOnly();
        if (!visiting && visitPreviewOnly) {
            visitPreviewOnly = false;
            visitResultPinned = false;
            lastVisitCard = "";
            if (resultCard != null) {
                resultCard.setVisibility(View.GONE);
            }
            hideVisitExtras();
        }
        if (visiting != visitingUiApplied) {
            applyModeUi();
            return;
        }
        boolean idle = resultCard == null || resultCard.getVisibility() != View.VISIBLE;
        setVisitWelcome(visiting && idle);
    }

    private void setVisitWelcome(boolean show) {
        View banner = welcomeScroll != null ? welcomeScroll : tvVisitWelcome;
        if (banner == null) {
            return;
        }
        if (!show) {
            if (visitWelcomeBlink != null) {
                visitWelcomeBlink.cancel();
            }
            if (welcomeSlide != null) {
                welcomeSlide.cancel();
            }
            banner.setAlpha(1f);
            if (welcomeScroll instanceof HorizontalScrollView) {
                ((HorizontalScrollView) welcomeScroll).scrollTo(0, 0);
            }
            banner.setVisibility(View.GONE);
            visitWelcomeShown = false;
            return;
        }
        if (visitWelcomeShown && banner.getVisibility() == View.VISIBLE) {
            return;
        }
        banner.setVisibility(View.VISIBLE);
        if (visitWelcomeBlink == null) {
            visitWelcomeBlink = ObjectAnimator.ofFloat(banner, "alpha", 1f, 0.35f, 1f);
            visitWelcomeBlink.setDuration(1100);
            visitWelcomeBlink.setRepeatCount(ValueAnimator.INFINITE);
            visitWelcomeBlink.setRepeatMode(ValueAnimator.RESTART);
        }
        if (!visitWelcomeBlink.isStarted()) {
            visitWelcomeBlink.start();
        }
        banner.post(new Runnable() {
            @Override
            public void run() {
                startWelcomeSlide();
            }
        });
        visitWelcomeShown = true;
    }

    private void startWelcomeSlide() {
        if (!(welcomeScroll instanceof HorizontalScrollView) || tvVisitWelcome == null) {
            return;
        }
        final HorizontalScrollView scroller = (HorizontalScrollView) welcomeScroll;
        int full = tvVisitWelcome.getWidth();
        int span = full / 2;
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

    private String defaultHintText() {
        return prefs != null && prefs.visitingOnly()
                ? "Tap a dayscholar or visitor card"
                : "Tap student or visitor card";
    }

    private String summarizeVisitors(JSONArray visitors, JSONObject primaryVisitor) {
        return listAllVisitors(visitors, primaryVisitor);
    }

    /** Full list of allowed visitors for the visit result card (one line each). */
    private String listAllVisitors(JSONArray visitors, JSONObject primaryVisitor) {
        StringBuilder sb = new StringBuilder();
        if (visitors != null && visitors.length() > 0) {
            for (int i = 0; i < visitors.length(); i++) {
                JSONObject v = visitors.optJSONObject(i);
                if (v == null) {
                    continue;
                }
                String name = v.optString("names", v.optString("name", "")).trim();
                if (name.length() == 0) {
                    continue;
                }
                String rel = v.optString("relationship", "").trim();
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(name);
                if (rel.length() > 0) {
                    sb.append("  ·  ").append(rel);
                }
            }
        }
        if (sb.length() > 0) {
            return sb.toString();
        }
        return primaryVisitor == null ? "" : primaryVisitor.optString("names", "");
    }

    private String summarizeStudents(JSONArray students, JSONObject primaryStudent) {
        return listAllStudents(students, primaryStudent);
    }

    /** Full list of students being visited (one line each). */
    private String listAllStudents(JSONArray students, JSONObject primaryStudent) {
        StringBuilder sb = new StringBuilder();
        if (students != null && students.length() > 0) {
            for (int i = 0; i < students.length(); i++) {
                JSONObject s = students.optJSONObject(i);
                if (s == null) {
                    continue;
                }
                String name = s.optString("name", "").trim();
                if (name.length() == 0) {
                    continue;
                }
                String cls = s.optString("class", "").trim();
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(name);
                if (cls.length() > 0) {
                    sb.append("  ·  ").append(cls);
                }
            }
        }
        if (sb.length() > 0) {
            return sb.toString();
        }
        if (primaryStudent == null) {
            return "";
        }
        String name = primaryStudent.optString("name", "");
        String cls = primaryStudent.optString("class", "");
        return cls.length() == 0 ? name : name + "  ·  " + cls;
    }

    private void hideVisitExtras() {
        if (visitStudentsRow != null) {
            visitStudentsRow.removeAllViews();
        }
        if (visitStudentsScroll != null) {
            visitStudentsScroll.setVisibility(View.GONE);
        }
        if (visitVisitorsRow != null) {
            visitVisitorsRow.removeAllViews();
        }
        if (visitVisitorsScroll != null) {
            visitVisitorsScroll.setVisibility(View.GONE);
        }
        if (tvVisitVisitorsLabel != null) {
            tvVisitVisitorsLabel.setVisibility(View.GONE);
        }
        if (tvVisitStudentsLabel != null) {
            tvVisitStudentsLabel.setVisibility(View.GONE);
        }
        if (tvVisitStaffHint != null) {
            tvVisitStaffHint.setVisibility(View.GONE);
        }
        if (tvMeta != null) {
            tvMeta.setVisibility(View.VISIBLE);
        }
        if (tvName != null) {
            tvName.setVisibility(View.VISIBLE);
        }
    }

    private void refreshVisitPeople(JSONObject json) {
        if (json == null) {
            return;
        }
        JSONArray visitors = json.optJSONArray("visitors");
        JSONObject primaryVisitor = json.optJSONObject("visitor");
        if ((visitors == null || visitors.length() == 0) && primaryVisitor != null) {
            visitors = new JSONArray();
            visitors.put(primaryVisitor);
        }
        String action = json.optString("action", "").toLowerCase(Locale.US);
        if (!"preview".equals(action)) {
            String scanned = lastCard != null && lastCard.length() >= 4
                    ? lastCard
                    : json.optString("card", "");
            visitors = visitorsOnThisCard(visitors, scanned);
        }
        JSONArray display = store.uniqueVisitorsForDisplay(visitors);
        JSONArray students = "preview".equals(action)
                ? json.optJSONArray("students")
                : store.studentsForVisitors(visitors);
        if (students == null || students.length() == 0) {
            students = store.studentsForVisitors(visitors);
        }
        String cardHolder = "";
        if (visitors != null && visitors.length() > 0) {
            JSONObject first = visitors.optJSONObject(0);
            if (first != null) {
                cardHolder = first.optString("names", first.optString("name", "")).trim();
            }
        }
        if (cardHolder.length() == 0 && primaryVisitor != null) {
            cardHolder = primaryVisitor.optString("names", primaryVisitor.optString("name", "")).trim();
        }
        bindVisitVisitors(display, lastCard, cardHolder);
        bindVisitStudents(students, json.optJSONObject("student"));
    }

    private void bindVisitVisitors(JSONArray visitors, String scannedCard, String cardHolderName) {
        if (visitVisitorsRow == null) {
            return;
        }
        visitVisitorsRow.removeAllViews();
        JSONArray unique = store.uniqueVisitorsForDisplay(visitors);
        JSONArray ordered = new JSONArray();
        JSONArray rest = new JSONArray();
        if (unique != null) {
            for (int i = 0; i < unique.length(); i++) {
                JSONObject visitor = unique.optJSONObject(i);
                if (visitor == null) {
                    continue;
                }
                if (isScannedVisitor(visitor, scannedCard, cardHolderName)) {
                    ordered.put(visitor);
                } else {
                    rest.put(visitor);
                }
            }
        }
        for (int i = 0; i < rest.length(); i++) {
            ordered.put(rest.optJSONObject(i));
        }
        int added = 0;
        for (int i = 0; i < ordered.length(); i++) {
            JSONObject visitor = ordered.optJSONObject(i);
            if (visitor == null) {
                continue;
            }
            String name = visitor.optString("names", visitor.optString("name", "")).trim();
            if (name.length() == 0) {
                continue;
            }
            String rel = visitor.optString("relationship", "").trim();
            boolean scanned = isScannedVisitor(visitor, scannedCard, cardHolderName);
            visitVisitorsRow.addView(makeVisitVisitorTile(name, rel, scanned, ordered.length()));
            added++;
        }
        if (tvVisitVisitorsLabel != null) {
            tvVisitVisitorsLabel.setText(added <= 0 ? "ALLOWED VISITORS" : ("ALLOWED VISITORS  ·  " + added));
            tvVisitVisitorsLabel.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
        }
        if (visitVisitorsScroll != null) {
            visitVisitorsScroll.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isScannedVisitor(JSONObject visitor, String scannedCard, String cardHolderName) {
        if (visitor == null) {
            return false;
        }
        String card = visitor.optString("card", visitor.optString("card_stored", "")).trim();
        if (scannedCard != null && scannedCard.length() >= 4 && card.length() >= 4) {
            String a = scannedCard.replaceAll("[^A-Fa-f0-9]", "").toUpperCase(Locale.US);
            String b = card.replaceAll("[^A-Fa-f0-9]", "").toUpperCase(Locale.US);
            if (a.equals(b) || CardUid.reverseBytes(a).equals(b)) {
                return true;
            }
        }
        if (cardHolderName != null && cardHolderName.length() > 0) {
            String name = visitor.optString("names", visitor.optString("name", "")).trim();
            return name.equalsIgnoreCase(cardHolderName) && card.length() >= 4;
        }
        return false;
    }

    private LinearLayout makeVisitVisitorTile(String name, String rel, boolean scanned, int total) {
        float density = getResources().getDisplayMetrics().density;
        boolean few = total <= 3;
        boolean one = total <= 1;
        int pad = (int) ((one ? 18 : (few ? 14 : 12)) * density);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tile.setBackgroundResource(scanned ? R.drawable.bg_visit_visitor_tile_active : R.drawable.bg_visit_visitor_tile);
        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tileLp.setMargins(0, pad / 3, 0, pad / 3);
        tile.setLayoutParams(tileLp);
        tile.setPadding(pad, pad, pad, pad);
        tile.setAlpha(0f);
        tile.animate().alpha(1f).setDuration(220)
                .setStartDelay(40L * Math.min(6, visitVisitorsRow.getChildCount()))
                .setInterpolator(new DecelerateInterpolator()).start();

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFFFFFFFF);
        int nameSp = one ? 28 : (few ? 22 : 18);
        if (name.length() > 24) {
            nameSp -= 4;
        }
        if (name.length() > 36) {
            nameSp -= 2;
        }
        if (nameSp < 16) {
            nameSp = 16;
        }
        nameView.setTextSize(nameSp);
        nameView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        nameView.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        nameView.setMaxLines(3);
        nameView.setHorizontallyScrolling(false);
        keepNameWhole(nameView);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameView.setLayoutParams(nameLp);
        tile.addView(nameView);

        String chipText = rel.length() > 0 ? rel : "Visitor";
        if (scanned) {
            chipText = chipText + "  ·  this card";
        }
        TextView chip = new TextView(this);
        chip.setText(chipText);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(one ? 15 : (few ? 14 : 13));
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding((int) (14 * density), (int) (7 * density), (int) (14 * density), (int) (7 * density));
        chip.setBackgroundResource(relationshipChip(rel, scanned));
        chip.setMaxLines(1);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = (int) (8 * density);
        chip.setLayoutParams(chipLp);
        tile.addView(chip);
        return tile;
    }

    /** Keep a surname on one line. Wrap only at spaces. */
    private static void keepNameWhole(TextView view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
            view.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        }
    }

    private int relationshipChip(String rel, boolean scanned) {
        if (scanned) {
            return R.drawable.bg_rel_scanned;
        }
        String n = rel == null ? "" : rel.trim().toLowerCase(Locale.US);
        if (n.contains("father") || n.contains("dad") || n.equals("papa")) {
            return R.drawable.bg_rel_father;
        }
        if (n.contains("mother") || n.contains("mum") || n.contains("mom") || n.equals("mama")) {
            return R.drawable.bg_rel_mother;
        }
        if (n.contains("guardian") || n.contains("uncle") || n.contains("aunt")) {
            return R.drawable.bg_rel_guardian;
        }
        return R.drawable.bg_rel_other;
    }

    private void bindVisitStudents(JSONArray students, JSONObject primaryStudent) {
        if (visitStudentsRow == null) {
            return;
        }
        visitStudentsRow.removeAllViews();
        JSONArray list = students;
        if (list == null || list.length() == 0) {
            list = new JSONArray();
            if (primaryStudent != null) {
                list.put(primaryStudent);
            }
        }
        int added = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject student = list.optJSONObject(i);
            if (student == null) {
                continue;
            }
            int studentId = student.optInt("id", 0);
            String name = student.optString("name", "").trim();
            String cls = student.optString("class", "").trim();
            String photo = student.optString("photo", "");
            if (studentId > 0) {
                JSONObject localStudent = store.findStudentById(studentId);
                if (localStudent != null) {
                    if (name.length() == 0) {
                        name = localStudent.optString("name", "");
                    }
                    if (cls.length() == 0) {
                        cls = localStudent.optString("class", localStudent.optString("extra", ""));
                    }
                }
            }
            if (!PhotoCache.isRealPhoto(photo)) {
                photo = "";
            }
            if (name.length() == 0 && studentId <= 0) {
                continue;
            }
            visitStudentsRow.addView(makeVisitStudentTile(studentId, name, cls, photo, Math.max(1, list.length())));
            added++;
        }
        if (visitStudentsScroll != null) {
            visitStudentsScroll.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
        }
        if (tvVisitStudentsLabel != null) {
            tvVisitStudentsLabel.setText(added <= 0 ? "STUDENTS TO VISIT" : ("STUDENTS TO VISIT  ·  " + added));
            tvVisitStudentsLabel.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private LinearLayout makeVisitStudentTile(int studentId, String name, String cls, String photo, int total) {
        float density = getResources().getDisplayMetrics().density;
        int photoSize = (int) ((total <= 1 ? 200 : (total == 2 ? 160 : 130)) * density);
        int pad = (int) (12 * density);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int side = (int) (36 * density);
        int tileW = total <= 1
                ? Math.max((int) (220 * density), screenW - side * 2)
                : (int) ((total == 2 ? 220 : 180) * density);
        if (total <= 1) {
            photoSize = Math.min(photoSize, tileW - (int) (48 * density));
        }
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        tile.setBackgroundResource(R.drawable.bg_visit_student_tile);
        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(tileW,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tileLp.setMargins(pad, 0, pad, 0);
        tile.setLayoutParams(tileLp);
        tile.setAlpha(0f);
        tile.animate().alpha(1f).setDuration(240)
                .setStartDelay(50L * Math.min(6, visitStudentsRow.getChildCount()))
                .setInterpolator(new DecelerateInterpolator()).start();

        CircleImageView photoView = new CircleImageView(this);
        LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(photoSize, photoSize);
        photoView.setLayoutParams(photoLp);
        photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photoView.setBackgroundResource(R.drawable.bg_photo_placeholder);
        photos.bind(photoView, studentId, photo);
        tile.addView(photoView);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(total <= 1 ? 26 : 22);
        nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);
        nameView.setGravity(android.view.Gravity.CENTER);
        nameView.setMaxLines(3);
        keepNameWhole(nameView);
        nameView.setPadding((int) (8 * density), pad / 2, (int) (8 * density), 0);
        LinearLayout.LayoutParams studentNameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameView.setLayoutParams(studentNameLp);
        tile.addView(nameView);

        if (cls != null && cls.length() > 0) {
            TextView classView = new TextView(this);
            classView.setText(cls);
            classView.setTextColor(0xFFBBF7D0);
            classView.setTextSize(total <= 1 ? 18 : 15);
            classView.setGravity(android.view.Gravity.CENTER);
            classView.setMaxLines(2);
            tile.addView(classView);
        }
        return tile;
    }

    private boolean isVisitorResult(JSONObject json) {
        if (json == null) {
            return false;
        }
        if ("visitor".equalsIgnoreCase(json.optString("kind", ""))) {
            return json.optBoolean("allowed", false) || XanderApi.ok(json);
        }
        return json.optBoolean("allowed", false)
                || ((json.optJSONArray("visitors") != null || json.optJSONObject("visitor") != null)
                && (XanderApi.ok(json) || json.optBoolean("allowed", false)
                || json.has("action")));
    }

    private boolean isVisitorDenial(JSONObject json) {
        if (json == null) {
            return false;
        }
        if (XanderApi.ok(json) || json.optBoolean("allowed", false)) {
            return false;
        }
        // Explicit visitor API payloads (including "no visitor registered").
        if ("visitor".equalsIgnoreCase(json.optString("kind", ""))) {
            return true;
        }
        String error = XanderApi.message(json, "").toLowerCase(Locale.US);
        if (error.contains("no visitor registered")) {
            return true;
        }
        return json.optJSONArray("visitors") != null
                || json.optJSONObject("visitor") != null
                || error.contains("visitor")
                || error.contains("visit");
    }

    private boolean swappingQuiet() {
        return lastTapAt == 0 || System.currentTimeMillis() - lastTapAt >= IDLE_SYNC_MS;
    }

    /** Save the tap now. Upload and card download wait until swapping stops. */
    private void queueFlush() {
        lastTapAt = System.currentTimeMillis();
        handler.removeCallbacks(idleSync);
        handler.postDelayed(idleSync, IDLE_SYNC_MS);
    }

    private final Runnable idleSync = new Runnable() {
        @Override
        public void run() {
            if (!swappingQuiet()) {
                handler.postDelayed(this, IDLE_SYNC_MS);
                return;
            }
            SyncService.request(KioskActivity.this);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateSyncLabel();
                }
            }, 800);
        }
    };

    private void startPulse() {
        if (pulseRing == null) {
            return;
        }
        pulseRing.animate().cancel();
        pulseRing.setScaleX(1f);
        pulseRing.setScaleY(1f);
        pulseRing.setAlpha(0.55f);
        pulseRing.animate()
                .scaleX(1.18f).scaleY(1.18f).alpha(0.12f)
                .setDuration(1400)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (pulseRing != null) {
                            startPulse();
                        }
                    }
                })
                .start();
    }

    private void startCardReader() {
        CardReader.Listener listen = new CardReader.Listener() {
            @Override
            public void onCard(String uid) {
                KioskActivity.this.onCard(uid);
            }

            @Override
            public void onDebug(String line) {
                android.util.Log.i("Card", line);
            }
        };
        if (cardReader == null) {
            cardReader = new CardReader();
        }
        cardReader.start(this, listen);
        if (dkUsb == null) {
            dkUsb = new DkUsbCardReader();
        }
        android.util.Log.i("Card", "starting original DK reader");
        dkUsb.start(this, listen);
    }

    /** Ask the server for Normal / Visiting only every few seconds while online. */
    private final Runnable liveModePoll = new Runnable() {
        @Override
        public void run() {
            if (NetWatch.online(KioskActivity.this) && !modePollBusy && prefs != null && prefs.schoolId() > 0) {
                modePollBusy = true;
                modePoll.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final JSONObject live = XanderApi.visitMode(prefs.serverUrl(), prefs.schoolId());
                            if (live != null && XanderApi.ok(live) && live.has("visit_mode")) {
                                final String mode = "visiting".equalsIgnoreCase(live.optString("visit_mode", "normal"))
                                        ? "visiting" : "normal";
                                final String current = prefs.visitingOnly() ? "visiting" : "normal";
                                if (!mode.equals(current)) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            prefs.saveVisitMode(mode);
                                            refreshVisitBanner();
                                        }
                                    });
                                }
                            }
                        } catch (Exception ignored) {
                        } finally {
                            modePollBusy = false;
                        }
                    }
                });
            }
            handler.postDelayed(this, 800);
        }
    };

    /** Keep newly assigned visitor cards local while the internet is up. */
    private final Runnable visitorCardPoll = new Runnable() {
        @Override
        public void run() {
            if (NetWatch.online(KioskActivity.this)) {
                SyncService.requestVisitors(KioskActivity.this);
            }
            handler.postDelayed(this, 4000);
        }
    };

    private void backgroundCardSync() {
        try {
            SyncService.requestCards(this);
        } catch (Exception ignored) {
        }
    }

    private void backgroundSync() {
        Intent i = new Intent(this, SyncService.class);
        long now = System.currentTimeMillis();
        if (now - lastPeopleSync > 120000) {
            i.putExtra("force_people", true);
            lastPeopleSync = now;
        }
        try {
            startService(i);
        } catch (Exception ignored) {
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                photos.bindLogo(imgLogo, prefs.schoolLogo());
                updateSyncLabel();
            }
        }, 600);
    }

    private void updateSyncLabel() {
        if (tvSync == null) {
            return;
        }
        int pending = store.pendingCount();
        boolean online = NetWatch.online(this);
        if (pending > 0) {
            tvSync.setText(online
                    ? pending + " waiting · sending…"
                    : pending + " saved offline");
            tvSync.setTextColor(0xFFBFDBFE);
        } else {
            tvSync.setText(online ? "Online · synced" : "Offline · cards still work");
            tvSync.setTextColor(0xFFFFFFFF);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || event.getUnicodeChar() == '\n' || event.getUnicodeChar() == '\r') {
            handler.removeCallbacks(hidFlush);
            String uid = CardReader.clean(keyBuf.toString());
            keyBuf.setLength(0);
            if (etWedge != null) {
                etWedge.setText("");
            }
            ingest(uid);
            return uid.length() >= 4 || super.dispatchKeyEvent(event);
        }
        int ch = event.getUnicodeChar();
        if ((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')) {
            keyBuf.append((char) ch);
            if (keyBuf.length() > 24) {
                keyBuf.setLength(0);
            }
            handler.removeCallbacks(hidFlush);
            // Keyboard wedge types ~10 decimal digits; 30ms was splitting the UID mid-scan.
            handler.postDelayed(hidFlush, 120);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNfc(intent);
    }

    private void handleNfc(Intent intent) {
        if (intent == null) {
            return;
        }
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) {
            return;
        }
        KioskLog.i("Kiosk", "ignored Android NFC intent to keep web-compatible reader encoding");
    }

    private String bytesToHex(byte[] id) {
        if (id == null || id.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < id.length; i++) {
            sb.append(String.format("%02X", id[i] & 0xff));
        }
        return sb.toString();
    }

    private void enableNfc() {
        nfcReaderOn = false;
        updateNfcLabel();
        focusWedge();
    }

    private void disableNfc() {
        if (nfcAdapter == null) {
            return;
        }
        try {
            if (nfcReaderOn) {
                nfcAdapter.disableReaderMode(this);
            }
        } catch (Exception ignored) {
        }
        nfcReaderOn = false;
        try {
            nfcAdapter.disableForegroundDispatch(this);
        } catch (Exception ignored) {
        }
    }

    private void updateNfcLabel() {
        if (tvNfc == null) {
            return;
        }
        tvNfc.setText("Use the bottom card reader only");
        tvNfc.setTextColor(0xFFA5F3FC);
    }

    private void focusWedge() {
        if (etWedge != null) {
            etWedge.requestFocus();
        }
    }

    private void hideSystemUi() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } catch (Exception ignored) {
        }
        DeviceLights.hideSystemChrome(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        applyModeUi();
        setSchoolName(prefs.schoolName());
        photos.bindLogo(imgLogo, prefs.schoolLogo());
        DeviceLights.on(this);
        startCardReader();
        KioskSound.prepare(this);
        enableNfc();
        if (!netRegistered) {
            registerReceiver(netRx, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            netRegistered = true;
        }
        updateSyncLabel();
        backgroundSync();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                enableNfc();
                focusWedge();
            }
        }, 800);
        focusWedge();
        startPulse();
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableNfc();
        if (netRegistered) {
            try {
                unregisterReceiver(netRx);
            } catch (Exception ignored) {
            }
            netRegistered = false;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        disableNfc();
        if (cardReader != null) {
            cardReader.stop();
        }
        if (dkUsb != null) {
            dkUsb.stop();
        }
        if (usbHid != null) {
            usbHid.stop();
        }
        try {
            bgScan.shutdownNow();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
