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
import android.hardware.usb.UsbManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KioskActivity extends Activity {
    private static final long LOGO_TAP_WINDOW_MS = 1800L;
    /** How long the result stays up before the next tap. Same for every location. */
    private static final int RESULT_HOLD_MS = 1000;
    /** Unpaid feeding stays up long enough to read the other payments. */
    private static final int FEEDING_CHECK_MS = 5000;

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
    private View feePanel;
    private TextView tvSchoolPaid;
    private TextView tvSchoolDue;
    private TextView tvExtraPaid;
    private TextView tvExtraDue;
    private TextView tvPromiseLabel;
    private TextView tvPromiseDate;
    private int feeCheckSeq;
    private View pulseRing;
    private View cardSpot;
    private View scanLampLeft;
    private View scanLampRight;
    private EditText etWedge;
    private UsbWedge usbWedge;
    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPending;
    private IntentFilter[] nfcFilters;
    private final StringBuilder keyBuf = new StringBuilder();
    private final Handler handler = new Handler();
    private String lastCard;
    private long lastCardAt;
    private int logoTapCount;
    private long logoTapStartedAt;
    private boolean nfcReaderOn;
    private boolean netRegistered;
    private boolean locationSyncing;
    private boolean usbAttachRegistered;
    private boolean modeRegistered;
    private boolean passwordEntryOpen;
    private long lastPeopleSync;
    private View welcomeScroll;
    private TextView tvVisitWelcome;
    private LinearLayout visitStudentsRow;
    private LinearLayout visitVisitorsRow;
    private View visitStudentsScroll;
    private View visitVisitorsScroll;
    private TextView tvVisitStudentsLabel;
    private TextView tvVisitVisitorsLabel;
    private TextView tvVisitStaffHint;
    private boolean visitPreviewOnly;
    private boolean visitResultPinned;
    private int lastRosterCount;
    private boolean visitingUiApplied;
    private boolean visitWelcomeShown;
    private boolean modePollBusy;
    private ObjectAnimator visitWelcomeBlink;
    private ValueAnimator welcomeSlide;
    private final ExecutorService modePoll = Executors.newSingleThreadExecutor();

    private final BroadcastReceiver usbAttachRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                startCardReader();
            }
        }
    };

    private final NfcAdapter.ReaderCallback nfcReader = new NfcAdapter.ReaderCallback() {
        @Override
        public void onTagDiscovered(Tag tag) {
            KioskLog.i("Kiosk", "ignored Android NFC tag to keep web-compatible reader encoding");
        }
    };

    private final BroadcastReceiver modeRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || prefs == null) {
                return;
            }
            String mode = intent.getStringExtra("visit_mode");
            if (mode == null) {
                return;
            }
            prefs.saveVisitMode(mode);
            refreshVisitBanner();
        }
    };

    private final BroadcastReceiver locationRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshScheduledLocation();
        }
    };

    private final BroadcastReceiver netRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSyncLabel();
            if (NetWatch.online(KioskActivity.this)) {
                backgroundSync();
                handler.removeCallbacks(liveModePoll);
                handler.post(liveModePoll);
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
            refreshScheduledLocation();
            if (now.getSeconds() % 15 == 0) {
                backgroundLocationSync();
            }
            if (!DeviceLights.holding()) {
                DeviceLights.keepWhiteOff(KioskActivity.this);
            }
            if (now.getSeconds() % 8 == 0) {
                KioskSound.prepare(KioskActivity.this);
            }
            if (now.getSeconds() % 20 == 0 && NetWatch.online(KioskActivity.this)) {
                backgroundCardSync();
            }
            if (NetWatch.online(KioskActivity.this)
                    && (store.pendingCount() > 0 && now.getSeconds() % 5 == 0
                    || now.getSeconds() % 20 == 0)) {
                backgroundSync();
            }
            updateSyncLabel();
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
            String uid = raw.replaceAll("[^A-Za-z0-9]", "");
            if (uid.length() >= 4) {
                onCard(uid);
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
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        prefs = new XanderPrefs(this);
        store = new XanderStore(this, prefs);
        photos = PhotoCache.get(this);
        photos.setBaseUrl(prefs.serverUrl());
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
        welcomeScroll = findViewById(R.id.welcome_scroll);
        tvVisitWelcome = (TextView) findViewById(R.id.tv_visit_welcome);
        visitStudentsRow = (LinearLayout) findViewById(R.id.visit_students_row);
        visitVisitorsRow = (LinearLayout) findViewById(R.id.visit_visitors_row);
        visitStudentsScroll = findViewById(R.id.visit_students_scroll);
        visitVisitorsScroll = findViewById(R.id.visit_visitors_scroll);
        tvVisitStudentsLabel = (TextView) findViewById(R.id.tv_visit_students_label);
        tvVisitVisitorsLabel = (TextView) findViewById(R.id.tv_visit_visitors_label);
        tvVisitStaffHint = (TextView) findViewById(R.id.tv_visit_staff_hint);
        tvCardLabel = (TextView) findViewById(R.id.tv_card_label);
        tvSchool = (TextView) findViewById(R.id.tv_school);
        tvSchoolShadow = (TextView) findViewById(R.id.tv_school_shadow);
        tvNfc = (TextView) findViewById(R.id.tv_nfc);
        tvSync = (TextView) findViewById(R.id.tv_sync);
        imgPhoto = (CircleImageView) findViewById(R.id.img_photo);
        imgLogo = (ImageView) findViewById(R.id.img_logo);
        resultCard = findViewById(R.id.result_card);
        resultPanel = findViewById(R.id.result_panel);
        feePanel = findViewById(R.id.fee_panel);
        tvSchoolPaid = (TextView) findViewById(R.id.tv_school_paid);
        tvSchoolDue = (TextView) findViewById(R.id.tv_school_due);
        tvExtraPaid = (TextView) findViewById(R.id.tv_extra_paid);
        tvExtraDue = (TextView) findViewById(R.id.tv_extra_due);
        tvPromiseLabel = (TextView) findViewById(R.id.tv_promise_label);
        tvPromiseDate = (TextView) findViewById(R.id.tv_promise_date);
        pulseRing = findViewById(R.id.pulse_ring);
        cardSpot = findViewById(R.id.card_spot);
        scanLampLeft = findViewById(R.id.scan_lamp_left);
        scanLampRight = findViewById(R.id.scan_lamp_right);
        etWedge = (EditText) findViewById(R.id.et_nfc_wedge);
        usbWedge = new UsbWedge(new UsbWedge.Listener() {
            @Override
            public void onUid(String uid) {
                ingest(uid);
            }
        });
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
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                XanderCompat.pendingMutable());
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
        applyModeUi();
        handler.post(liveModePoll);
        handler.post(visitorCardPoll);
        handler.post(clockTick);
        backgroundSync();
        backgroundCardSync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                store.warmIndex();
                photos.warmRam();
                photos.prefetch(store.listStudents());
            }
        }, "kiosk-warm").start();
    }

    private void bindWedge() {
        if (etWedge == null) {
            return;
        }
        etWedge.setInputType(InputType.TYPE_NULL);
        etWedge.setCursorVisible(false);
        etWedge.setFocusable(true);
        etWedge.setFocusableInTouchMode(true);
        try {
            etWedge.setShowSoftInputOnFocus(false);
        } catch (Exception ignored) {
        }
        etWedge.requestFocus();
        hideIme();
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
        if (uid == null) {
            return;
        }
        String raw = uid.replaceAll("[^A-Za-z0-9]", "");
        if (raw.length() < 4) {
            return;
        }
        onCard(raw);
    }

    private void onCard(final String rawUid) {
        final String localUid = CardUid.webNormalize(rawUid);
        final String scanUid = CardUid.forScan(rawUid);
        final String hexUid = CardUid.toStorageHex(rawUid);
        String uid = localUid.length() >= 4 ? localUid : (scanUid.length() >= 4 ? scanUid : hexUid);
        if (uid.length() < 4) {
            return;
        }
        // A finished visit stays on screen until the next card. That tap is a new scan.
        if (visitResultPinned) {
            visitResultPinned = false;
            visitPreviewOnly = false;
        }
        long now = System.currentTimeMillis();
        if (uid.equals(lastCard) && now - lastCardAt < 180) {
            return;
        }
        lastCard = uid;
        lastCardAt = now;

        JSONArray visitors = store.findVisitorsByCard(rawUid);
        if (visitors != null && visitors.length() > 0) {
            JSONObject first = visitors.optJSONObject(0);
            if (first != null) {
                String stored = first.optString("card_stored", first.optString("card", ""));
                if (stored.length() >= 4) {
                    uid = stored;
                }
            }
            lastCard = uid;
            routeVisitorCard(uid, visitors);
            return;
        }

        JSONObject person = store.findByCard(rawUid);
        if (person != null) {
            String stored = person.optString("card_stored", person.optString("card", ""));
            if (stored.length() >= 4) {
                uid = stored;
            }
        }
        final String clockUid = uid;
        lastCard = clockUid;

        if (person != null) {
            if (prefs.visitingOnly() && !XanderStore.isBoarding(person)) {
                openAllowedVisitors(person);
                return;
            }
            if (denyIfResidenceBlocked(person)) {
                return;
            }
            if (verifyStudentLocal(clockUid, person) && NetWatch.online(this)) {
                syncStudentOnlineBackground(clockUid, scanUid.length() >= 4 ? scanUid : clockUid);
            }
            return;
        }

        JSONObject staff = store.findStaffByAnyCard(rawUid);
        if (staff != null) {
            if (isMorningPrepLocation() || isEveningPrepLocation()) {
                String slot = isMorningPrepLocation() ? "morning" : "evening";
                int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
                JSONObject duty = store.findPrepDuty(rawUid, dow, slot);
                if (duty != null) {
                    verifyPrepLocal(clockUid, duty);
                    return;
                }
            }
            verifyStaffLocal(clockUid, staff);
            return;
        }

        if (store.hasRoster()) {
            if (NetWatch.online(this)) {
                verifyCardOnline(localUid, scanUid.length() >= 4 ? scanUid : uid, null, null);
                return;
            }
            DeviceLights.fail(this);
            setScanLamp(-1);
            fail("Card not registered\nUID " + uid, true);
            return;
        }

        if (!NetWatch.online(this)) {
            DeviceLights.fail(this);
            setScanLamp(-1);
            fail("Card not registered\nUID " + uid, true);
            return;
        }
        verifyCardOnline(localUid, scanUid.length() >= 4 ? scanUid : uid, null, null);
    }

    /**
     * Soft online probe for cards missing from local roster. Does not block UI.
     * If server accepts a visitor, cache it under the scanned UID for the next tap (&lt;1s).
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
                                    store.cacheStudentFromScan(storage, person);
                                    android.util.Log.e("XanderVisitor", "BG_CACHED student from card poll " + storage);
                                    return;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    JSONObject visitorResult = scanVisitorLikeWeb(storage, reader);
                    if (isVisitorResult(visitorResult)) {
                        // Cache under both byte-orders so DK tap matches immediately.
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
        }, "visitor-bg-probe").start();
    }

    /** Fire-and-forget server confirm for a student already shown locally. */
    private void syncStudentOnlineBackground(final String localUid, final String scanUid) {
        queueFlush();
    }

    /** Fire-and-forget server confirm for a visitor already shown locally. */
    private void syncVisitorOnlineBackground(final String localUid, final String scanUid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Same as web Verify Parent Visit: POST storage-order UID.
                    String webUid = localUid != null && localUid.length() >= 4 ? localUid : scanUid;
                    XanderApi.scanVisitorCard(prefs.serverUrl(), prefs.schoolId(), webUid);
                } catch (Exception e) {
                    KioskLog.e("Kiosk", "bg visitor sync: " + e.getMessage());
                }
            }
        }, "bg-visitor-sync").start();
    }

    /** @return true when a new IN/OUT was saved and should sync. */
    private boolean verifyStudentLocal(String uid, JSONObject person) {
        if (denyIfResidenceBlocked(person)) {
            return false;
        }
        DeviceLights.pass(this);
        setScanLamp(1);
        int area = prefs.areaId();
        if (area <= 0) {
            DeviceLights.fail(this);
            setScanLamp(-1);
            fail("Set a location in Settings", false);
            return false;
        }
        boolean entryOnly = isCafeteriaLocation() || isDormitoryLocation();
        JSONObject local = store.clockLocal(
                person.optInt("id", 0),
                area,
                uid,
                person.optString("name", ""),
                person.optString("extra", person.optString("class", "")),
                person.optString("photo", ""),
                entryOnly);
        try {
            JSONObject shown = local.optJSONObject("person");
            if (shown != null) {
                shown.put("regno", person.optString("regno", ""));
                shown.put("class", person.optString("class", person.optString("extra", "")));
                shown.put("studying_mode", XanderStore.studyingModeOf(person));
                if (person.has("feeding_paid")) {
                    shown.put("feeding_paid", XanderStore.feedingPaidOf(person));
                }
                if (person.has("feeding_amount")) {
                    shown.put("feeding_amount", XanderStore.feedingAmountOf(person));
                }
                if (person.has("transport_amount")) {
                    shown.put("transport_amount", XanderStore.transportAmountOf(person));
                }
                if (person.has("transport_required")) {
                    shown.put("transport_required", XanderStore.transportRequiredOf(person));
                }
                String hostel = XanderStore.hostelOf(person);
                if (hostel.length() > 0) {
                    shown.put("hostel", hostel);
                }
                JSONObject fees = person.optJSONObject("fees");
                if (fees != null && fees.optInt("success", 0) == 1) {
                    shown.put("fees", fees);
                }
            }
        } catch (Exception ignored) {
        }
        showResult(local);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                queueFlush();
            }
        }, 80);
        if (local.optInt("wait", 0) == 1 || isAlreadyIn(local)) {
            return false;
        }
        return true;
    }

    /** Staff card at any attendance location. Prep rota still marks invigilators present. */
    private void verifyStaffLocal(String uid, JSONObject staff) {
        int area = prefs.areaId();
        if (area <= 0) {
            DeviceLights.fail(this);
            setScanLamp(-1);
            fail("Set a location in Settings", false);
            return;
        }
        boolean entryOnly = isCafeteriaLocation() || isDormitoryLocation() || isEveningPrepLocation();
        boolean once = isCafeteriaLocation() && isLeadershipPost(staff.optString("post", ""));
        JSONObject local = store.clockStaffLocal(
                staff.optInt("id", 0),
                area,
                uid,
                staff.optString("name", ""),
                staff.optString("post", ""),
                staff.optString("photo", ""),
                entryOnly,
                once);
        if ("once".equalsIgnoreCase(local.optString("denied", ""))) {
            DeviceLights.fail(this);
            setScanLamp(-1);
        } else {
            DeviceLights.pass(this);
            setScanLamp(1);
        }
        showResult(local);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                queueFlush();
            }
        }, 80);
    }

    /** Invigilator on today's morning or evening prep rota. One tap marks them present. */
    private void verifyPrepLocal(String uid, JSONObject duty) {
        int area = prefs.areaId();
        if (area <= 0) {
            DeviceLights.fail(this);
            setScanLamp(-1);
            fail("Set a location in Settings", false);
            return;
        }
        DeviceLights.pass(this);
        setScanLamp(1);
        JSONObject local = store.clockPrepLocal(
                duty.optInt("id", 0),
                area,
                uid,
                duty.optString("name", ""),
                duty.optString("post", ""),
                duty.optString("photo", ""));
        showResult(local);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                queueFlush();
            }
        }, 80);
    }

    private boolean denyIfResidenceBlocked(JSONObject person) {
        if (shouldBlockBoarding(person)) {
            denyBoarding(person);
            return true;
        }
        if (shouldBlockDayScholar(person)) {
            denyDayScholar(person);
            return true;
        }
        if (shouldBlockUnpaidFeeding(person)) {
            denyFeeding(person);
            return true;
        }
        if (shouldBlockUnpaidTransport(person)) {
            denyTransport(person);
            return true;
        }
        return false;
    }

    private void denyBoarding(JSONObject person) {
        DeviceLights.fail(this);
        setScanLamp(-1);
        JSONObject json = new JSONObject();
        try {
            json.put("success", 0);
            json.put("denied", "boarding");
            json.put("message", "Boarding student — school gate is for day scholars only");
            if (person != null) {
                json.put("person", person);
            }
        } catch (Exception ignored) {
        }
        showResult(json);
    }

    private void denyDayScholar(JSONObject person) {
        DeviceLights.fail(this);
        setScanLamp(-1);
        JSONObject json = new JSONObject();
        try {
            json.put("success", 0);
            json.put("denied", "day");
            json.put("message", "Day scholar — dormitories are for boarding students only");
            if (person != null) {
                json.put("person", person);
            }
        } catch (Exception ignored) {
        }
        showResult(json);
    }

    private void denyFeeding(JSONObject person) {
        DeviceLights.fail(this);
        setScanLamp(-1);
        JSONObject json = new JSONObject();
        try {
            json.put("success", 0);
            json.put("denied", "feeding");
            json.put("feeding_paid", 0);
            json.put("message", "Feeding not paid");
            if (person != null) {
                json.put("person", person);
            }
        } catch (Exception ignored) {
        }
        showResult(json);
    }

    private void denyTransport(JSONObject person) {
        DeviceLights.fail(this);
        setScanLamp(-1);
        JSONObject json = new JSONObject();
        try {
            json.put("success", 0);
            json.put("denied", "transport");
            json.put("message", "Transport not paid");
            if (person != null) {
                json.put("person", person);
            }
        } catch (Exception ignored) {
        }
        showResult(json);
    }

    private JSONObject cachedFees(JSONObject person, JSONObject json) {
        JSONObject fees = json == null ? null : json.optJSONObject("fees");
        if (fees != null && fees.optInt("success", 0) == 1) {
            return fees;
        }
        fees = person == null ? null : person.optJSONObject("fees");
        if (fees != null && fees.optInt("success", 0) == 1) {
            return fees;
        }
        return null;
    }

    private void loadFeeBrief(final JSONObject person, JSONObject already) {
        final int seq = ++feeCheckSeq;
        if (already != null && already.optInt("success", 0) == 1) {
            paintFeeBrief(already);
            return;
        }
        final int studentId = person == null ? 0 : person.optInt("id", 0);
        if (studentId <= 0 || !NetWatch.online(this)) {
            if (cachedFees(person, null) == null) {
                paintFeeBrief(null);
            }
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject brief = null;
                try {
                    brief = XanderApi.feeBrief(prefs.serverUrl(), prefs.schoolId(), studentId);
                } catch (Exception e) {
                    KioskLog.e("Kiosk", "fee brief: " + e.getMessage());
                }
                final JSONObject got = brief;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != feeCheckSeq) {
                            return;
                        }
                        if (got != null && got.optInt("success", 0) == 1) {
                            store.saveStudentFees(studentId, got);
                            paintFeeBrief(got);
                        }
                    }
                });
            }
        }, "fee-brief").start();
    }

    private void paintFeeBrief(JSONObject fees) {
        if (feePanel != null) {
            feePanel.setVisibility(View.VISIBLE);
        }
        if (fees == null || fees.optInt("success", 0) != 1) {
            if (tvSchoolPaid != null) {
                tvSchoolPaid.setText("—");
            }
            if (tvExtraPaid != null) {
                tvExtraPaid.setText("—");
            }
            if (tvSchoolDue != null) {
                tvSchoolDue.setText("Could not load");
            }
            if (tvExtraDue != null) {
                tvExtraDue.setText("Could not load");
            }
            if (tvPromiseDate != null) {
                tvPromiseDate.setText("Not available");
            }
            return;
        }
        String schoolPaid = fees.optString("school_paid_label", "0");
        String schoolDue = fees.optString("school_expected_label", "0");
        String extraPaid = fees.optString("extra_paid_label", "0");
        String extraDue = fees.optString("extra_expected_label", "0");
        if (tvSchoolPaid != null) {
            tvSchoolPaid.setText(schoolPaid);
        }
        if (tvSchoolDue != null) {
            tvSchoolDue.setText("paid of " + schoolDue);
        }
        if (tvExtraPaid != null) {
            tvExtraPaid.setText(extraPaid);
        }
        if (tvExtraDue != null) {
            tvExtraDue.setText("paid of " + extraDue);
        }
        String when = fees.optString("promised_label", "");
        boolean overdue = fees.optInt("promised_overdue", 0) == 1;
        if (tvPromiseLabel != null) {
            tvPromiseLabel.setText(when.length() == 0 ? "NEXT PAYMENT PROMISED" : (overdue ? "PROMISED DATE PASSED" : "NEXT PAYMENT PROMISED"));
        }
        if (tvPromiseDate != null) {
            tvPromiseDate.setText(when.length() == 0 ? "No date promised" : when);
        }
    }

    /** School fees and other fees for this term. Uses the last sync when offline. */
    private void showOtherPayments(JSONObject person, JSONObject json) {
        JSONObject fees = cachedFees(person, json);
        if (fees != null) {
            paintFeeBrief(fees);
            if (NetWatch.online(this)) {
                loadFeeBrief(person, null);
            }
            return;
        }
        if (feePanel != null) {
            feePanel.setVisibility(View.VISIBLE);
        }
        if (tvSchoolPaid != null) {
            tvSchoolPaid.setText("…");
        }
        if (tvExtraPaid != null) {
            tvExtraPaid.setText("…");
        }
        if (tvSchoolDue != null) {
            tvSchoolDue.setText("checking");
        }
        if (tvExtraDue != null) {
            tvExtraDue.setText("checking");
        }
        if (tvPromiseDate != null) {
            tvPromiseDate.setText("Checking…");
        }
        loadFeeBrief(person, null);
    }

    private void hideFeePanel() {
        feeCheckSeq++;
        if (feePanel != null) {
            feePanel.setVisibility(View.GONE);
        }
    }

    private String normalizedAreaName() {
        String name = prefs.areaName();
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * School Gate only — not Library, classroom, or any other area.
     */
    private boolean isSchoolGateLocation() {
        String n = normalizedAreaName();
        if (n.length() == 0) {
            return false;
        }
        return n.equals("gate") || n.equals("school gate") || n.contains("school gate")
                || n.endsWith(" gate");
    }

    /**
     * Dormitory / hostel locations — boarding students only.
     */
    private boolean isDormitoryLocation() {
        String n = normalizedAreaName();
        if (n.length() == 0) {
            return false;
        }
        return n.contains("dormitor") || n.equals("dorm") || n.startsWith("dorm ")
                || n.endsWith(" dorm") || n.contains("hostel");
    }

    /** Cafeteria / canteen. Spelling "cafetria" is accepted. */
    private boolean isCafeteriaLocation() {
        String n = normalizedAreaName();
        if (n.length() == 0) {
            return false;
        }
        return n.contains("cafeteria") || n.contains("cafetria") || n.contains("canteen")
                || n.contains("dining") || n.contains("refectory");
    }

    /** Head teacher, director, principal, dean, and the other school-leader posts. */
    private boolean isLeadershipPost(String title) {
        String t = title == null ? "" : title.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
        if (t.length() == 0) {
            return false;
        }
        if (t.equals("head master") || t.equals("headmaster")
                || t.equals("head mistress") || t.equals("headmistress")
                || t.equals("head teacher") || t.equals("deputy head teacher") || t.equals("deputy headteacher")
                || t.equals("executive principal") || t.equals("principal")
                || t.equals("director") || t.equals("deputy director")
                || t.equals("dos") || t.equals("director of studies")
                || t.equals("dean") || t.equals("dean of studies")
                || t.equals("dht discipline") || t.equals("dht academics") || t.equals("dht academic")) {
            return true;
        }
        return t.startsWith("head master") || t.startsWith("headmaster")
                || t.startsWith("head mistress") || t.startsWith("headmistress")
                || t.startsWith("head teacher") || t.startsWith("deputy head")
                || t.startsWith("executive principal") || t.startsWith("deputy director")
                || t.startsWith("director of studies") || t.startsWith("dean of studies")
                || t.startsWith("dos ");
    }

    /** Attendance location named Transport. */
    private boolean isTransportLocation() {
        String n = normalizedAreaName();
        if (n.length() == 0) {
            return false;
        }
        return n.contains("transport");
    }

    /** Attendance location named Morning prep. */
    private boolean isMorningPrepLocation() {
        String n = normalizedAreaName();
        return n.contains("morning") && (n.contains("prep") || n.contains("preparation"));
    }

    /** Attendance location named Evening prep. */
    private boolean isEveningPrepLocation() {
        String n = normalizedAreaName();
        return n.contains("evening") && (n.contains("prep") || n.contains("preparation"));
    }

    /** Current-term Feeding paid. 60,000 RWF or more may eat. Nothing else counts. */
    private static final int CAFETERIA_EAT_MIN = 60000;

    /**
     * Cafeteria only. Boarding students are always allowed.
     * Day scholars are allowed only when this term's Feeding is 60,000 or more.
     */
    private boolean shouldBlockUnpaidFeeding(JSONObject person) {
        if (person == null || !isCafeteriaLocation()) {
            return false;
        }
        return !cafeteriaMayEat(person);
    }

    private boolean cafeteriaMayEat(JSONObject person) {
        if (person == null) {
            return false;
        }
        if (XanderStore.isBoarding(person)) {
            return true;
        }
        return XanderStore.feedingAmountOf(person) >= CAFETERIA_EAT_MIN;
    }

    /**
     * Transport only, and only day scholars.
     * Allowed when this term's Transport fee is paid in full.
     */
    private boolean shouldBlockUnpaidTransport(JSONObject person) {
        if (person == null || !isTransportLocation() || !XanderStore.isKnownDayScholar(person)) {
            return false;
        }
        return !XanderStore.transportMayRide(person);
    }

    private String transportAllowReason(JSONObject person) {
        int required = XanderStore.transportRequiredOf(person);
        if (required > 0) {
            return "TRANSPORT " + String.format(Locale.US, "%,d", required) + " OR MORE";
        }
        return "TRANSPORT PAID";
    }

    private String cafeteriaAllowReason(JSONObject person) {
        if (XanderStore.isBoarding(person)) {
            return "BOARDING STUDENT";
        }
        return "FEEDING 60,000 OR MORE";
    }

    private String dormitoryLabel(JSONObject person) {
        String hostel = XanderStore.hostelOf(person);
        if (hostel.length() > 0) {
            return hostel;
        }
        String area = prefs.areaName();
        return area == null ? "" : area.trim();
    }

    private boolean cafeteriaFeedingPaid(JSONObject person) {
        return isCafeteriaLocation()
                && XanderStore.isKnownDayScholar(person)
                && XanderStore.feedingPaidOf(person) == 1;
    }

    /**
     * School gate: day scholars only. Boarding blocked here, allowed everywhere else
     * except this gate rule.
     */
    private boolean shouldBlockBoarding(JSONObject person) {
        if (person == null || !XanderStore.isBoarding(person)) {
            return false;
        }
        return isSchoolGateLocation();
    }

    /**
     * Dormitories: boarding only. Day scholars blocked here, allowed everywhere else.
     */
    private boolean shouldBlockDayScholar(JSONObject person) {
        if (person == null || !XanderStore.isKnownDayScholar(person)) {
            return false;
        }
        return isDormitoryLocation();
    }

    private void verifyParentVisitLocal(String uid, JSONArray visitors) {
        DeviceLights.pass(this);
        setScanLamp(1);
        JSONObject local = store.clockVisitorLocal(uid, visitors);
        showParentVisitResult(local);
        queueFlush();
    }

    /**
     * Online auto-detect for cards not in local DB.
     * Mirror web verify: try visitor_card_scan with storage UID first (001EA28E),
     * then device_scan for students. Cap wait so UI never sticks on CHECKING.
     */
    private void verifyCardOnline(final String localUid, final String scanUid, final JSONObject localPerson, final JSONArray localVisitors) {
        JSONArray known = localVisitors;
        if (known == null || known.length() == 0) {
            known = store.findVisitorsByCard(localUid);
        }
        if ((known == null || known.length() == 0) && scanUid != null) {
            known = store.findVisitorsByCard(scanUid);
        }
        if (known != null && known.length() > 0) {
            routeVisitorCard(localUid, known);
            return;
        }
        if (!NetWatch.online(this)) {
            if (localVisitors != null && localVisitors.length() > 0) {
                routeVisitorCard(localUid, localVisitors);
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
        hideVisitExtras();
        imgPhoto.setImageResource(R.drawable.bg_photo_placeholder);
        if (resultPanel != null) {
            resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
        }
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(1f);
        new Thread(new Runnable() {
            @Override
            public void run() {
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
                            + " rosterV=" + store.visitorCount());

                    // 1) Same as web parent_visiting/verify → visitor endpoint with storage UID.
                    JSONObject visitorResult = scanVisitorLikeWeb(webStorageUid, readerUid);
                    if (isVisitorResult(visitorResult) || isActiveVisitorDenial(visitorResult)) {
                        final JSONObject vFinal = visitorResult;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showParentVisitResult(vFinal);
                            }
                        });
                        return;
                    }

                    // 2) Student attendance (unchanged rules).
                    KioskLog.i("Kiosk", "online device_scan (student) uid=" + webStorageUid);
                    JSONObject result = XanderApi.scanCard(
                            prefs.serverUrl(), prefs.schoolId(), webStorageUid, areaId);
                    logApiResult("device_scan " + webStorageUid, result);

                    if (!XanderApi.ok(result)
                            && !isVisitorResult(result)
                            && !isActiveVisitorDenial(result)
                            && readerUid != null
                            && readerUid.length() >= 4
                            && !readerUid.equalsIgnoreCase(webStorageUid)) {
                        JSONObject alt = XanderApi.scanCard(
                                prefs.serverUrl(), prefs.schoolId(), readerUid, areaId);
                        logApiResult("device_scan " + readerUid, alt);
                        if (XanderApi.ok(alt) || isVisitorResult(alt) || isActiveVisitorDenial(alt)) {
                            result = alt;
                        }
                    }

                    if (isVisitorResult(result) || isActiveVisitorDenial(result)) {
                        final JSONObject vFinal = result;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showParentVisitResult(vFinal);
                            }
                        });
                        return;
                    }

                    final JSONObject finalResult = result;
                    final String shownUid = webStorageUid;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            applyOnlineScanResult(finalResult, localUid, localPerson, localVisitors, shownUid);
                        }
                    });
                } catch (final Exception e) {
                    KioskLog.e("Kiosk", "online verify fail: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (localVisitors != null && localVisitors.length() > 0) {
                                routeVisitorCard(localUid, localVisitors);
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
        String scannedKind = result == null ? "" : result.optString("kind", "");
        if ("staff".equalsIgnoreCase(scannedKind) || "prep".equalsIgnoreCase(scannedKind)) {
            showResult(result);
            return;
        }
        JSONObject scannedPerson = result == null ? null : result.optJSONObject("person");
        if (scannedPerson != null && prefs.visitingOnly() && !XanderStore.isBoarding(scannedPerson)) {
            openAllowedVisitors(scannedPerson);
            return;
        }
        if (scannedPerson != null && denyIfResidenceBlocked(scannedPerson)) {
            store.cacheStudentFromScan(localUid, result);
            return;
        }
        if (scannedPerson != null && XanderStore.isKnownDayScholar(scannedPerson)) {
            if (XanderApi.ok(result)) {
                store.cacheStudentFromScan(localUid, result);
                showResult(result);
                return;
            }
        } else if ((isBoardingDenied(result) && isSchoolGateLocation())
                || shouldBlockBoarding(scannedPerson)
                || (isDayScholarDenied(result) && isDormitoryLocation())
                || shouldBlockDayScholar(scannedPerson)
                || (isFeedingDenied(result) && isCafeteriaLocation())
                || shouldBlockUnpaidFeeding(scannedPerson)
                || (isTransportDenied(result) && isTransportLocation())
                || shouldBlockUnpaidTransport(scannedPerson)) {
            store.cacheStudentFromScan(localUid, result);
            if (scannedPerson != null) {
                if (shouldBlockUnpaidTransport(scannedPerson) || isTransportDenied(result)) {
                    denyTransport(scannedPerson);
                } else if (shouldBlockUnpaidFeeding(scannedPerson) || isFeedingDenied(result)) {
                    denyFeeding(scannedPerson);
                } else if (shouldBlockDayScholar(scannedPerson) || isDayScholarDenied(result)) {
                    denyDayScholar(scannedPerson);
                } else {
                    denyBoarding(scannedPerson);
                }
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
            routeVisitorCard(localUid, localVisitors);
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
        if (XanderApi.ok(json) || isVisitorResult(json) || isVisitorDenial(json)
                || isBoardingDenied(json) || isDayScholarDenied(json) || isFeedingDenied(json)
                || isTransportDenied(json) || isAlreadyIn(json)) {
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

    private boolean isAlreadyIn(JSONObject json) {
        if (json == null) {
            return false;
        }
        if (json.optBoolean("already", false) || json.optInt("already", 0) == 1) {
            return true;
        }
        String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
        return msg.contains("already in") || msg.contains("already checked in");
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
                || msg.contains("day scholar"));
    }

    private boolean isDayScholarDenied(JSONObject json) {
        if (json == null) {
            return false;
        }
        if ("day".equalsIgnoreCase(json.optString("denied", ""))) {
            return true;
        }
        String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
        return (msg.contains("day scholar") || msg.contains("dayscholar"))
                && (msg.contains("dorm") || msg.contains("hostel") || msg.contains("boarding")
                || msg.contains("not allow") || msg.contains("cannot"));
    }

    private boolean isFeedingDenied(JSONObject json) {
        if (json == null) {
            return false;
        }
        if ("feeding".equalsIgnoreCase(json.optString("denied", ""))) {
            return true;
        }
        String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
        return msg.contains("feeding") && (msg.contains("not paid") || msg.contains("unpaid"));
    }

    private boolean isTransportDenied(JSONObject json) {
        if (json == null) {
            return false;
        }
        if ("transport".equalsIgnoreCase(json.optString("denied", ""))) {
            return true;
        }
        String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
        return msg.contains("transport") && (msg.contains("not paid") || msg.contains("unpaid"));
    }

    private void showResult(JSONObject json) {
        hideVisitExtras();
        setVisitWelcome(false);
        if (imgPhoto != null) {
            imgPhoto.setVisibility(View.VISIBLE);
        }
        boolean ok = XanderApi.ok(json);
        JSONObject person = json.optJSONObject("person");
        String status = json.optString("status", "");
        String name = person == null ? "" : person.optString("name", "");
        String extra = "";
        if (person != null) {
            String regno = person.optString("regno", "").trim();
            String cls = person.optString("class", person.optString("extra", "")).trim();
            if (regno.length() > 0 && cls.length() > 0 && regno.equalsIgnoreCase(cls)) {
                extra = cls;
            } else if (regno.length() > 0 && cls.length() > 0) {
                extra = regno + "  ·  " + cls;
            } else {
                extra = regno.length() > 0 ? regno : cls;
            }
        }
        String msg = XanderApi.message(json, "");
        tvName.setText(name.length() == 0 ? XanderApi.message(json, "Unknown") : name);
        tvMeta.setText(extra.length() == 0 ? msg : extra);
        hideFeePanel();
        int holdMs = RESULT_HOLD_MS;
        if (tvStatus != null) {
            tvStatus.setLetterSpacing(0.14f);
            tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 56);
        }
        boolean prepDuty = ok && "prep".equalsIgnoreCase(json.optString("kind", ""));
        boolean staffTap = ok && "staff".equalsIgnoreCase(json.optString("kind", ""));
        boolean cafeteriaEat = isCafeteriaLocation() && person != null && cafeteriaMayEat(person);
        boolean transportRide = ok && isTransportLocation() && person != null
                && XanderStore.isKnownDayScholar(person) && XanderStore.transportMayRide(person);
        boolean dormPass = isDormitoryLocation() && ok && person != null && !isDayScholarDenied(json);
        if ("once".equalsIgnoreCase(json.optString("denied", ""))) {
            tvStatus.setText("NOT ALLOWED");
            tvTime.setText("ONCE A DAY");
            tvMeta.setText(extra.length() == 0 ? "Already tapped today" : extra);
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
            }
            DeviceLights.fail(this);
            setScanLamp(-1);
            KioskSound.alert(this);
        } else if (prepDuty) {
            boolean already = isAlreadyIn(json);
            tvStatus.setText(already ? "ALREADY PRESENT" : "PRESENT");
            tvTime.setText("DUTY  " + json.optString("time", ""));
            tvMeta.setText(extra.length() == 0 ? "Invigilator" : extra);
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(already
                        ? R.drawable.bg_result_hold
                        : R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            if (already) {
                KioskSound.alreadyIn(this);
            } else {
                KioskSound.welcome(this);
            }
        } else if (staffTap) {
            boolean already = isAlreadyIn(json);
            boolean out = "OUT".equalsIgnoreCase(status);
            if (already && out) {
                tvStatus.setText("ALREADY OUT");
            } else if (out) {
                tvStatus.setText("GOODBYE");
            } else if (already) {
                tvStatus.setText("ALREADY IN");
            } else {
                tvStatus.setText("WELCOME");
            }
            tvTime.setText((out ? "OUT  " : "IN  ") + json.optString("time", ""));
            tvMeta.setText(extra.length() == 0 ? "Staff" : extra);
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(already
                        ? R.drawable.bg_result_hold
                        : (out ? R.drawable.bg_result_out : R.drawable.bg_result_in));
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            if (already) {
                KioskSound.alreadyIn(this);
            } else if (out) {
                KioskSound.goodbye(this);
            } else {
                KioskSound.welcome(this);
            }
        } else if (cafeteriaEat) {
            tvStatus.setText("ALLOWED TO EAT");
            String eatReason = cafeteriaAllowReason(person);
            tvTime.setText(eatReason);
            if (extra.length() > 0) {
                tvMeta.setText(extra + "\n" + eatReason);
            } else {
                tvMeta.setText(eatReason);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.allowedToEat(this);
            showOtherPayments(person, json);
            holdMs = FEEDING_CHECK_MS;
        } else if (transportRide) {
            tvStatus.setText("ALLOWED TO RIDE");
            String rideReason = transportAllowReason(person);
            tvTime.setText(rideReason);
            if (extra.length() > 0) {
                tvMeta.setText(extra + "\n" + rideReason);
            } else {
                tvMeta.setText(rideReason);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.allowedToRide(this);
            showOtherPayments(person, json);
            holdMs = FEEDING_CHECK_MS;
        } else if (dormPass) {
            String dorm = dormitoryLabel(person);
            if (tvStatus != null) {
                tvStatus.setLetterSpacing(0.02f);
                tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, dorm.length() > 16 ? 32 : 40);
            }
            tvStatus.setText(dorm.length() == 0 ? "WELCOME" : dorm);
            tvTime.setText("IN  " + json.optString("time", ""));
            if (extra.length() > 0) {
                tvMeta.setText(extra);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.welcome(this);
        } else if (ok && json.optInt("wait", 0) == 1) {
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
            boolean already = json.optInt("already", 0) == 1;
            tvStatus.setText(already ? "ALREADY" : "LUNCH");
            tvTime.setText("LUNCH  " + json.optString("time", ""));
            if (extra.length() > 0) {
                tvMeta.setText(extra);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_out);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            if (already) {
                KioskSound.alreadyIn(this);
            } else {
                KioskSound.lunch(this);
            }
        } else if (ok && "BACK".equalsIgnoreCase(status)) {
            boolean already = json.optInt("already", 0) == 1;
            tvStatus.setText(already ? "ALREADY" : "BACK");
            tvTime.setText("BACK  " + json.optString("time", ""));
            if (extra.length() > 0) {
                tvMeta.setText(extra);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            if (already) {
                KioskSound.alreadyIn(this);
            } else {
                KioskSound.backFromLunch(this);
            }
        } else if (isAlreadyIn(json)) {
            boolean out = "OUT".equalsIgnoreCase(status);
            tvStatus.setText(cafeteriaFeedingPaid(person) ? "FEEDING PAID" : (out ? "ALREADY OUT" : "ALREADY IN"));
            tvTime.setText((out ? "OUT  " : "IN  ") + json.optString("time", ""));
            if (extra.length() > 0) {
                tvMeta.setText(extra);
            } else {
                tvMeta.setText(msg.length() == 0 ? "Already IN" : msg);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.alreadyIn(this);
        } else if (person != null && !XanderStore.isBoarding(person)
                && isCafeteriaLocation()
                && (isFeedingDenied(json) || shouldBlockUnpaidFeeding(person))) {
            tvStatus.setText("NOT ALLOWED");
            tvTime.setText("FEEDING NOT PAID");
            if (extra.length() > 0) {
                tvMeta.setText(extra + "\nFEEDING NOT PAID");
            } else {
                tvMeta.setText(msg.length() == 0 ? "Feeding not paid" : msg);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
            }
            DeviceLights.fail(this);
            setScanLamp(-1);
            KioskSound.alert(this);
            holdMs = FEEDING_CHECK_MS;
            showOtherPayments(person, json);
        } else if (person != null && XanderStore.isKnownDayScholar(person)
                && isTransportLocation()
                && (isTransportDenied(json) || shouldBlockUnpaidTransport(person))) {
            tvStatus.setText("NOT ALLOWED");
            tvTime.setText("TRANSPORT NOT PAID");
            if (extra.length() > 0) {
                tvMeta.setText(extra + "\nTRANSPORT NOT PAID");
            } else {
                tvMeta.setText(msg.length() == 0 ? "Transport not paid" : msg);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
            }
            DeviceLights.fail(this);
            setScanLamp(-1);
            KioskSound.alert(this);
            holdMs = FEEDING_CHECK_MS;
            showOtherPayments(person, json);
        } else if (person != null && XanderStore.isBoarding(person)
                && ((isBoardingDenied(json) && isSchoolGateLocation())
                || (shouldBlockBoarding(person) && !ok))) {
            tvStatus.setText("NOT ALLOWED");
            tvTime.setText("DAY SCHOLARS ONLY");
            if (extra.length() > 0) {
                tvMeta.setText(extra);
            } else {
                tvMeta.setText(msg.length() == 0 ? "Boarding students cannot swipe at the school gate" : msg);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
            }
            DeviceLights.fail(this);
            setScanLamp(-1);
            KioskSound.alert(this);
        } else if (person != null && XanderStore.isKnownDayScholar(person)
                && ((isDayScholarDenied(json) && isDormitoryLocation())
                || (shouldBlockDayScholar(person) && !ok))) {
            tvStatus.setText("NOT ALLOWED");
            tvTime.setText("BOARDING ONLY");
            if (extra.length() > 0) {
                tvMeta.setText(extra);
            } else {
                tvMeta.setText(msg.length() == 0 ? "Day scholars cannot swipe at the dormitory" : msg);
            }
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
            }
            DeviceLights.fail(this);
            setScanLamp(-1);
            KioskSound.alert(this);
        } else if (ok && "IN".equalsIgnoreCase(status)) {
            tvStatus.setText(cafeteriaFeedingPaid(person) ? "FEEDING PAID" : "WELCOME");
            tvTime.setText("IN  " + json.optString("time", ""));
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_in);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.welcome(this);
        } else if (ok && "OUT".equalsIgnoreCase(status)) {
            tvStatus.setText(cafeteriaFeedingPaid(person) ? "FEEDING PAID" : "GOODBYE");
            tvTime.setText("OUT  " + json.optString("time", ""));
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_out);
            }
            DeviceLights.pass(this);
            setScanLamp(1);
            KioskSound.goodbye(this);
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
        if ("prep".equalsIgnoreCase(json.optString("kind", "")) && sid > 0) {
            sid = XanderStore.PREP_PHOTO_BASE + sid;
        } else if ("staff".equalsIgnoreCase(json.optString("kind", "")) && sid > 0
                && sid < XanderStore.STAFF_PHOTO_BASE) {
            sid = XanderStore.STAFF_PHOTO_BASE + sid;
        }
        String photo = person == null ? "" : person.optString("photo", "");
        flashResult(holdMs);
        photos.bind(imgPhoto, sid, photo);
        updateSyncLabel();
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
        JSONArray onCard = visitors;
        if (!"preview".equals(action)) {
            String scanned = lastCard != null && lastCard.length() >= 4
                    ? lastCard
                    : json.optString("card", "");
            JSONArray matched = visitorsOnThisCard(visitors, scanned);
            if (matched != null && matched.length() > 0) {
                onCard = matched;
            }
            students = store.studentsForVisitors(onCard);
            if (students == null || students.length() == 0) {
                students = json.optJSONArray("students");
            }
        } else {
            students = json.optJSONArray("students");
            if (students == null || students.length() == 0) {
                students = store.studentsForVisitors(visitors);
            }
        }
        visitors = visitorsForRoster(students, onCard, visitors);
        JSONArray displayVisitors = store.uniqueVisitorsForDisplay(visitors);
        if (displayVisitors != null && displayVisitors.length() > 0) {
            visitors = displayVisitors;
        }
        JSONObject primaryStudent = json.optJSONObject("student");
        if (!"preview".equals(action) && students != null && students.length() > 0) {
            primaryStudent = students.optJSONObject(0);
        } else if (primaryStudent == null && students != null && students.length() > 0) {
            primaryStudent = students.optJSONObject(0);
        }
        String cardHolder = "";
        if (visitors != null && visitors.length() > 0) {
            JSONObject first = visitors.optJSONObject(0);
            if (first != null) {
                cardHolder = first.optString("names", first.optString("name", "")).trim();
            }
        }
        String msg = XanderApi.message(json, ok ? "Visitor verified" : "Visitor not allowed");
        String clock = json.optString("time_label", "");
        if (clock.length() > 5 && clock.contains(":")) {
            int sp = clock.lastIndexOf(' ');
            if (sp > 0 && clock.length() - sp >= 5) {
                clock = clock.substring(sp + 1, Math.min(clock.length(), sp + 6));
            }
        }
        hideFeePanel();
        if (imgPhoto != null) {
            imgPhoto.setVisibility(View.GONE);
        }
        tvName.setVisibility(View.GONE);
        tvMeta.setVisibility(ok ? View.GONE : View.VISIBLE);
        tvMeta.setText(ok ? "" : msg);

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
            if (resultPanel != null) {
                resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
            }
            DeviceLights.fail(this);
            setScanLamp(-1);
            KioskSound.alert(this);
        }

        handler.removeCallbacks(hideResult);
        visitResultPinned = true;
        visitPreviewOnly = preview;
        int roster = visitors == null ? 0 : visitors.length();
        lastRosterCount = roster;
        if (tvStatus != null) {
            tvStatus.setTextSize(roster >= 3 ? 34f : 48f);
        }
        if (tvTime != null) {
            tvTime.setTextSize(roster >= 3 ? 28f : 40f);
        }
        bindVisitVisitors(visitors, lastCard, cardHolder);
        bindVisitStudents(students, primaryStudent);
        if (tvVisitStaffHint != null) {
            if (preview) {
                tvVisitStaffHint.setText(visitors != null && visitors.length() > 0
                        ? "Registered parents"
                        : "No registered parents");
                tvVisitStaffHint.setVisibility(View.VISIBLE);
            } else {
                tvVisitStaffHint.setText("");
                tvVisitStaffHint.setVisibility(View.GONE);
            }
        }
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(1f);
        setVisitWelcome(false);
        updateSyncLabel();
        if (etWedge != null && !passwordEntryOpen) {
            etWedge.requestFocus();
        }
    }

    private void flashResult(int holdMs) {
        resultCard.animate().cancel();
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(1f);
        handler.removeCallbacks(hideResult);
        handler.postDelayed(hideResult, holdMs);
    }

    private final Runnable hideResult = new Runnable() {
        @Override
        public void run() {
            resultCard.animate().cancel();
            resultCard.setVisibility(View.INVISIBLE);
            resultCard.setAlpha(1f);
            hideFeePanel();
            hideVisitExtras();
            DeviceLights.idle(KioskActivity.this);
            setScanLamp(0);
            tvHint.setText(defaultHintText());
            if (tvReady != null) {
                tvReady.setText("READY");
            }
            refreshVisitBanner();
            focusWedge();
        }
    };

    private void fail(String msg, boolean unknownCard) {
        tvStatus.setText("FAILED");
        tvName.setText(msg);
        tvMeta.setText(unknownCard ? "This card is not registered" : "");
        tvTime.setText("");
        imgPhoto.setImageResource(R.drawable.bg_photo_placeholder);
        if (resultPanel != null) {
            resultPanel.setBackgroundResource(R.drawable.bg_result_hold);
        }
        DeviceLights.fail(this);
        setScanLamp(-1);
        KioskSound.alert(this);
        flashResult(RESULT_HOLD_MS);
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
        passwordEntryOpen = true;
        if (usbWedge != null) {
            usbWedge.reset();
        }
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Password");
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        try {
            input.setShowSoftInputOnFocus(true);
        } catch (Exception ignored) {
        }
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        AlertDialog dialog = new AlertDialog.Builder(this)
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
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        passwordEntryOpen = false;
                        hideIme();
                    }
                })
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                }
                input.requestFocus();
                showIme(input);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (passwordEntryOpen) {
                            input.requestFocus();
                            showIme(input);
                        }
                    }
                }, 200);
            }
        });
        dialog.show();
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
                    ? "Dayscholar card records a visit and shows the registered parents."
                    : (isMorningPrepLocation() || isEveningPrepLocation()
                    ? "Use the bottom reader for student, staff, and invigilator cards"
                    : "Use the USB reader for student, staff, and visitor cards"));
        }
        refreshVisitBanner();
    }

    private void refreshVisitBanner() {
        boolean visiting = prefs != null && prefs.visitingOnly();
        if (!visiting && visitPreviewOnly) {
            visitPreviewOnly = false;
            visitResultPinned = false;
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
                                    KioskLog.i("Kiosk", "visit mode " + current + " -> " + mode);
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

    /** Pick up a visitor card assigned on the website while this phone is online. */
    private final Runnable visitorCardPoll = new Runnable() {
        @Override
        public void run() {
            if (NetWatch.online(KioskActivity.this) && prefs != null && prefs.schoolId() > 0) {
                SyncService.requestCards(KioskActivity.this);
            }
            handler.postDelayed(this, 4000);
        }
    };

    /** Every visitor registered to these students, including people with no card. */
    private JSONArray visitorsForRoster(JSONArray students, JSONArray onCard, JSONArray payload) {
        JSONArray merged = new JSONArray();
        java.util.HashSet<Integer> seenIds = new java.util.HashSet<Integer>();
        java.util.HashSet<String> seenNames = new java.util.HashSet<String>();
        java.util.HashSet<Integer> studentIds = new java.util.HashSet<Integer>();
        if (students != null) {
            for (int i = 0; i < students.length(); i++) {
                JSONObject student = students.optJSONObject(i);
                int sid = student == null ? 0 : student.optInt("id", student.optInt("student_id", 0));
                if (sid > 0) {
                    studentIds.add(Integer.valueOf(sid));
                }
            }
        }
        collectStudentIds(onCard, studentIds);
        collectStudentIds(payload, studentIds);
        for (Integer sid : studentIds) {
            appendRoster(merged, seenIds, seenNames, store.visitorsForStudent(sid.intValue()));
        }
        appendRoster(merged, seenIds, seenNames, payload);
        appendRoster(merged, seenIds, seenNames, onCard);
        if (merged.length() > 0) {
            return merged;
        }
        return payload == null ? new JSONArray() : payload;
    }

    private static void collectStudentIds(JSONArray visitors, java.util.HashSet<Integer> studentIds) {
        for (int i = 0; visitors != null && i < visitors.length(); i++) {
            JSONObject visitor = visitors.optJSONObject(i);
            int sid = visitor == null ? 0 : visitor.optInt("student_id", 0);
            if (sid > 0) {
                studentIds.add(Integer.valueOf(sid));
            }
        }
    }

    private static void appendRoster(JSONArray merged, java.util.HashSet<Integer> seenIds,
                                     java.util.HashSet<String> seenNames, JSONArray visitors) {
        for (int i = 0; visitors != null && i < visitors.length(); i++) {
            JSONObject visitor = visitors.optJSONObject(i);
            if (visitor == null) {
                continue;
            }
            String name = visitor.optString("names", visitor.optString("name", "")).trim();
            if (name.length() == 0) {
                continue;
            }
            int vid = visitor.optInt("id", visitor.optInt("visitor_id", 0));
            if (vid > 0) {
                if (!seenIds.add(Integer.valueOf(vid))) {
                    continue;
                }
            } else if (!seenNames.add(name.toLowerCase(Locale.US))) {
                continue;
            }
            merged.put(visitor);
        }
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

    private JSONArray visitorsOnThisCard(JSONArray visitors, String scannedCard) {
        JSONArray matched = onlyRegisteredOnCard(visitors, scannedCard);
        if (matched.length() > 0) {
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

    /** Show father, mother, and guardian. Does not write a visit. */
    private void showDayscholarParents(JSONArray seed) {
        JSONArray parents = new JSONArray();
        JSONObject person = null;
        java.util.HashSet<Integer> seenStudents = new java.util.HashSet<Integer>();
        java.util.HashSet<Integer> seenParents = new java.util.HashSet<Integer>();
        for (int i = 0; seed != null && i < seed.length(); i++) {
            JSONObject visitor = seed.optJSONObject(i);
            if (visitor == null) {
                continue;
            }
            int studentId = visitor.optInt("student_id", 0);
            if (studentId <= 0 || !seenStudents.add(Integer.valueOf(studentId))) {
                continue;
            }
            if (person == null) {
                person = store.findStudentById(studentId);
            }
            JSONArray linked = store.visitorsForStudent(studentId);
            for (int p = 0; linked != null && p < linked.length(); p++) {
                JSONObject parent = linked.optJSONObject(p);
                if (parent == null) {
                    continue;
                }
                int vid = parent.optInt("id", 0);
                if (vid > 0 && !seenParents.add(Integer.valueOf(vid))) {
                    continue;
                }
                parents.put(parent);
            }
        }
        if (parents.length() == 0) {
            parents = registeredParents(seed);
        }
        JSONObject local = new JSONObject();
        JSONArray students = new JSONArray();
        if (person != null) {
            students.put(person);
        }
        try {
            local.put("success", 1);
            local.put("allowed", true);
            local.put("action", "preview");
            local.put("visitors", parents);
            local.put("students", students);
            if (person != null) {
                local.put("student", person);
            }
            local.put("message", parents.length() > 0 ? "Registered parents" : "No registered parents");
        } catch (Exception ignored) {
        }
        showParentVisitResult(local);
    }

    /** Visitors already on the phone are shown and clocked at once. The upload follows. */
    private void openParentVisit(final String uid, final JSONArray localVisitors) {
        if (localVisitors != null && localVisitors.length() > 0) {
            verifyParentVisitLocal(uid, localVisitors);
            return;
        }
        if (!NetWatch.online(this)) {
            verifyParentVisitLocal(uid, localVisitors);
            return;
        }
        tvStatus.setText("CHECKING");
        tvName.setText("Verifying card");
        tvName.setVisibility(View.VISIBLE);
        tvMeta.setText("");
        tvTime.setText("");
        if (resultCard != null) {
            resultCard.setVisibility(View.VISIBLE);
        }
        setVisitWelcome(false);
        new Thread(new Runnable() {
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
                        if (live != null && (XanderApi.ok(live) || live.optBoolean("allowed", false))) {
                            if (live.has("visit_mode")) {
                                prefs.saveVisitMode(live.optString("visit_mode", "normal"));
                            }
                            store.cacheVisitorsFromScan(uid, live);
                            showParentVisitResult(live);
                            return;
                        }
                        verifyParentVisitLocal(uid, localVisitors);
                    }
                });
            }
        }, "usb-live-visit").start();
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
            JSONArray roster = store.visitorsForStudent(studentId);
            last.put("visitors", roster != null && roster.length() > 0 ? roster : parents);
            JSONArray students = new JSONArray();
            students.put(person);
            last.put("students", students);
            last.put("student", person);
        } catch (Exception ignored) {
        }
        showParentVisitResult(last);
        queueFlush();
    }

    private void showAllowedVisitorsForStudent(JSONObject person) {
        JSONArray seed = new JSONArray();
        if (person != null) {
            JSONObject row = new JSONObject();
            try {
                row.put("student_id", person.optInt("id", 0));
            } catch (Exception ignored) {
            }
            seed.put(row);
        }
        showDayscholarParents(seed);
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
        visitPreviewOnly = false;
        visitResultPinned = false;
    }

    private void bindVisitVisitors(JSONArray visitors, String scannedCard, String cardHolderName) {
        if (visitVisitorsRow == null) {
            return;
        }
        visitVisitorsRow.removeAllViews();
        JSONArray unique = store.uniqueVisitorsForDisplay(visitors);
        int added = 0;
        int total = unique == null ? 0 : unique.length();
        for (int i = 0; unique != null && i < unique.length(); i++) {
            JSONObject visitor = unique.optJSONObject(i);
            if (visitor == null) {
                continue;
            }
            String name = visitor.optString("names", visitor.optString("name", "")).trim();
            if (name.length() == 0) {
                continue;
            }
            String rel = visitor.optString("relationship", "").trim();
            boolean scanned = isScannedVisitor(visitor, scannedCard, cardHolderName);
            visitVisitorsRow.addView(makeVisitVisitorTile(name, rel, scanned, total));
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
        int compactPad = total >= 3 ? (int) (8 * density) : pad;
        tile.setPadding(compactPad, compactPad, compactPad, (int) (6 * density));
        tile.setAlpha(1f);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFFFFFFFF);
        int nameSp = one ? 26 : (total >= 4 ? 16 : (few ? 20 : 17));
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
        chip.setTextSize(one ? 14 : (total >= 4 ? 12 : 13));
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
        int total = Math.max(1, list.length());
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
                    if (photo.length() < 4) {
                        photo = localStudent.optString("photo", "");
                    }
                }
            }
            if (name.length() == 0 && studentId <= 0) {
                continue;
            }
            visitStudentsRow.addView(makeVisitStudentTile(studentId, name, cls, photo, total));
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
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int side = (int) (36 * density);
        int tileW = total <= 1
                ? Math.max((int) (220 * density), screenW - side * 2)
                : (int) ((total == 2 ? 220 : 180) * density);
        int photoSize = total <= 1
                ? Math.min((int) (150 * density), tileW - (int) (48 * density))
                : (int) ((total == 2 ? 120 : 96) * density);
        int pad = (int) (12 * density);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        tile.setBackgroundResource(R.drawable.bg_visit_student_tile);
        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(tileW,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tileLp.setMargins(pad, 0, pad, 0);
        tile.setLayoutParams(tileLp);
        tile.setAlpha(1f);
        if (lastRosterCount >= 3 && total <= 1) {
            photoSize = (int) (92 * density);
        }
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
        nameView.setTextSize(lastRosterCount >= 3 ? 18 : (total <= 1 ? 22 : 18));
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
            keepNameWhole(classView);
            LinearLayout.LayoutParams classLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            classView.setLayoutParams(classLp);
            tile.addView(classView);
        }
        return tile;
    }

    private String defaultHintText() {
        if (prefs != null && prefs.visitingOnly()) {
            return "Tap a dayscholar or visiting card";
        }
        if (isMorningPrepLocation() || isEveningPrepLocation()) {
            return "Tap student, staff, or invigilator card";
        }
        return "Tap student, staff, or visitor card";
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

    private void queueFlush() {
        SyncService.request(this);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateSyncLabel();
            }
        }, 800);
    }

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
        if (DkUsbCardReader.dkDevicePresent(this)) {
            if (dkUsb == null) {
                dkUsb = new DkUsbCardReader();
            }
            dkUsb.start(this, listen);
        } else {
            android.util.Log.i("XanderDkUsb", "DK USB not attached — plug the NFC dongle");
        }
        if (usbHid == null) {
            usbHid = new UsbHidCardReader(this);
        }
        usbHid.start(listen);
    }

    private void refreshScheduledLocation() {
        if (prefs == null) {
            return;
        }
        boolean switched = prefs.applyScheduledLocation();
        String want = prefs.areaName() == null ? "" : prefs.areaName();
        String shown = "";
        if (tvLocation != null && tvLocation.getText() != null) {
            shown = tvLocation.getText().toString();
        }
        if (switched || (want.length() > 0 && !want.equals(shown) && !prefs.visitingOnly())) {
            applyModeUi();
        }
    }

    private void backgroundLocationSync() {
        if (locationSyncing || prefs == null || !NetWatch.online(this)) {
            return;
        }
        locationSyncing = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject json = XanderApi.syncLocations(prefs.serverUrl(), prefs.schoolId());
                    if (XanderApi.ok(json)) {
                        JSONArray locs = json.optJSONArray("locations");
                        if (locs != null) {
                            prefs.saveLocations(locs);
                        }
                    }
                } catch (Exception ignored) {
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        locationSyncing = false;
                        refreshScheduledLocation();
                    }
                });
            }
        }).start();
    }

    private void backgroundCardSync() {
        try {
            SyncService.requestCards(this);
        } catch (Exception ignored) {
        }
    }

    private void backgroundSync() {
        long now = System.currentTimeMillis();
        try {
            if (now - lastPeopleSync > 120000) {
                lastPeopleSync = now;
                SyncService.requestForcePeople(this);
            } else {
                SyncService.request(this);
            }
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
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event);
        }
        if (passwordEntryOpen) {
            // On-screen keyboard must type the password. Drop USB-reader keys only.
            if (UsbWedge.isPhysicalKeyboard(event)) {
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (usbWedge != null && UsbWedge.isPhysicalKeyboard(event)) {
            usbWedge.onKey(event);
            return true;
        }
        if (usbWedge != null && event != null && event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            usbWedge.onKey(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            startCardReader();
            focusWedge();
        }
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
        if (passwordEntryOpen) {
            return;
        }
        hideIme();
    }

    private void showIme(View target) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && target != null) {
                imm.showSoftInput(target, InputMethodManager.SHOW_FORCED);
            }
        } catch (Exception ignored) {
        }
    }

    private void hideIme() {
        if (passwordEntryOpen) {
            return;
        }
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View v = getWindow() != null ? getWindow().getDecorView() : etWedge;
            if (imm != null && v != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
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
        if (prefs != null) {
            prefs.applyScheduledLocation();
        }
        applyModeUi();
        setSchoolName(prefs.schoolName());
        photos.bindLogo(imgLogo, prefs.schoolLogo());
        DeviceLights.on(this);
        startCardReader();
        KioskSound.prepare(this);
        enableNfc();
        if (!netRegistered) {
            XanderCompat.register(this, netRx, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION), false);
            netRegistered = true;
        }
        if (!modeRegistered) {
            XanderCompat.register(this, modeRx, new IntentFilter("com.xander.student.VISIT_MODE"), false);
            XanderCompat.register(this, locationRx, new IntentFilter("com.xander.student.LOCATION_CHANGED"), false);
            modeRegistered = true;
        }
        backgroundLocationSync();
        handler.removeCallbacks(liveModePoll);
        handler.post(liveModePoll);
        if (!usbAttachRegistered) {
            XanderCompat.register(this, usbAttachRx, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED), true);
            usbAttachRegistered = true;
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
        if (modeRegistered) {
            try {
                unregisterReceiver(modeRx);
            } catch (Exception ignored) {
            }
            try {
                unregisterReceiver(locationRx);
            } catch (Exception ignored) {
            }
            modeRegistered = false;
        }
        if (usbAttachRegistered) {
            try {
                unregisterReceiver(usbAttachRx);
            } catch (Exception ignored) {
            }
            usbAttachRegistered = false;
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
        super.onDestroy();
    }
}
