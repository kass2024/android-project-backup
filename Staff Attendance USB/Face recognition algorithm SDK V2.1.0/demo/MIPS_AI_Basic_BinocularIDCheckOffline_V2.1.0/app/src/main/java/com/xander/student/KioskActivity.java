package com.xander.student;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;

public class KioskActivity extends Activity {
    private static final long LOGO_TAP_WINDOW_MS = 1800L;
    private static final long DEBOUNCE_MS = 700L;

    private XanderPrefs prefs;
    private XanderStore store;
    private PhotoCache photos;
    private CardReader cardReader;
    private UsbHidCardReader usbHid;
    private DkUsbCardReader dkUsb;
    private UsbWedge usbWedge;
    private TextView tvSchool;
    private TextView tvClock;
    private TextView tvDate;
    private TextView tvWait;
    private TextView tvName;
    private TextView tvPost;
    private TextView tvStatus;
    private TextView tvVerdict;
    private TextView tvHint;
    private TextView tvScanTime;
    private TextView tvSync;
    private View waitPane;
    private View resultPane;
    private ImageView imgWait;
    private ImageView imgPhoto;
    private ImageView imgLogo;
    private EditText etWedge;
    private Animation waitPulse;
    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPending;
    private final Handler handler = new Handler();
    private String lastCard;
    private long lastCardAt;
    private int logoTapCount;
    private long logoTapStartedAt;
    private boolean nfcReaderOn;
    private boolean netRegistered;
    private boolean usbAttachRegistered;
    private boolean passwordEntryOpen;
    private int displayedStaffId;
    private String pendingUid;
    private JSONObject lastLocalDecide;
    private String lastVoice;

    private final BroadcastReceiver usbAttachRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                startCardReader();
            }
        }
    };

    private final BroadcastReceiver netRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSyncLabel();
            if (NetWatch.online(KioskActivity.this)) {
                SyncService.request(KioskActivity.this, true);
            }
        }
    };

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            if (tvClock != null) {
                tvClock.setText(new SimpleDateFormat("hh:mm:ss", Locale.getDefault()).format(now));
            }
            if (tvDate != null) {
                tvDate.setText(new SimpleDateFormat("a  •  EEEE, d MMMM", Locale.getDefault()).format(now));
            }
            if (!DeviceLights.holding()) {
                DeviceLights.keepWhiteOff(KioskActivity.this);
            }
            if (now.getSeconds() % 8 == 0) {
                KioskSound.prepare(KioskActivity.this);
            }
            if (now.getSeconds() % 12 == 0) {
                startCardReader();
            }
            if (now.getSeconds() % 10 == 0 && NetWatch.online(KioskActivity.this)) {
                SyncService.request(KioskActivity.this, true);
            }
            if (now.getMinutes() % 5 == 0 && now.getSeconds() == 5 && NetWatch.online(KioskActivity.this)) {
                SyncService.request(KioskActivity.this, false);
            }
            updateSyncLabel();
            handler.postDelayed(this, 1000);
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
        photos = new PhotoCache(this);
        if (!prefs.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_xander_kiosk);

        tvSchool = (TextView) findViewById(R.id.tv_school);
        tvClock = (TextView) findViewById(R.id.tv_clock);
        tvDate = (TextView) findViewById(R.id.tv_date);
        tvWait = (TextView) findViewById(R.id.tv_wait);
        tvName = (TextView) findViewById(R.id.tv_name);
        tvPost = (TextView) findViewById(R.id.tv_post);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvVerdict = (TextView) findViewById(R.id.tv_verdict);
        tvHint = (TextView) findViewById(R.id.tv_hint);
        tvScanTime = (TextView) findViewById(R.id.tv_scan_time);
        tvSync = (TextView) findViewById(R.id.tv_sync);
        waitPane = findViewById(R.id.wait_pane);
        resultPane = findViewById(R.id.result_pane);
        imgWait = (ImageView) findViewById(R.id.img_wait);
        imgPhoto = (ImageView) findViewById(R.id.img_photo);
        imgLogo = (ImageView) findViewById(R.id.img_logo);
        etWedge = (EditText) findViewById(R.id.et_nfc_wedge);
        waitPulse = AnimationUtils.loadAnimation(this, R.anim.pulse_wait);
        if (Build.VERSION.SDK_INT >= 21 && imgPhoto != null) {
            imgPhoto.setClipToOutline(true);
            imgPhoto.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }
        usbWedge = new UsbWedge(new UsbWedge.Listener() {
            @Override
            public void onUid(String uid) {
                onCard(uid);
            }
        });
        bindWedge();
        hideSystemUi();

        if (tvSchool != null) {
            tvSchool.setText(prefs.schoolName());
        }
        photos.bindLogo(imgLogo, prefs.schoolLogo());
        idleUi();

        View.OnClickListener settingsTap = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleLogoTap();
            }
        };
        if (imgLogo != null) {
            imgLogo.setOnClickListener(settingsTap);
        }
        if (tvSchool != null) {
            tvSchool.setOnClickListener(settingsTap);
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcPending = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                XanderCompat.pendingMutable());
        DeviceLights.on(this);
        KioskSound.prepare(this);
        UsbPermissionHelper.ensure(this);
        startCardReader();
        handler.post(clockTick);
        backgroundSync();
        photos.prefetch(store.listStaff());
        store.warmIndex();
        XanderNet.warm(1500);
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

    private void onCard(final String rawUid) {
        if (rawUid == null || passwordEntryOpen) {
            return;
        }
        final String localUid = CardUid.webNormalize(rawUid);
        final String scanUid = CardUid.forScan(rawUid);
        String uid = localUid.length() >= 4 ? localUid : scanUid;
        if (uid.length() < 4) {
            return;
        }
        long now = System.currentTimeMillis();
        if (uid.equals(lastCard) && now - lastCardAt < DEBOUNCE_MS) {
            return;
        }
        lastCard = uid;
        lastCardAt = now;
        KioskLog.i("Kiosk", "staff tap raw=" + rawUid + " storage=" + localUid + " scan=" + scanUid);

        JSONObject staff = store.findStaffByCard(rawUid);
        JSONObject decide = null;
        boolean online = NetWatch.online(this);
        if (staff != null) {
            String stored = staff.optString("card_stored", staff.optString("card", ""));
            if (stored.length() >= 4) {
                uid = stored;
            }
            decide = StaffClock.decide(staff, System.currentTimeMillis());
            lastLocalDecide = decide;
            showLocalStaff(staff, decide);
        } else {
            String blocked = store.blockedKind(rawUid);
            if ("student".equals(blocked)) {
                showError("This is a student card. Use Student IN/OUT Attendance.");
                return;
            }
            if ("visitor".equals(blocked)) {
                showError("This is a parent visitor card.");
                return;
            }
            displayedStaffId = 0;
            showWait();
            if (tvWait != null) {
                tvWait.setText("Reading card…");
            }
            if (tvHint != null) {
                tvHint.setText("Looking up this card");
            }
            setReady("busy");
        }

        final String clockUid = uid;
        pendingUid = clockUid;
        if (staff != null) {
            boolean fresh = decide != null && decide.optBoolean("ok", false) && !decide.optBoolean("already", false);
            if (fresh) {
                queueFromDecide(clockUid, decide);
                if (!online) {
                    if (tvHint != null) {
                        tvHint.setText("Saved offline — will sync when online");
                    }
                    if (tvScanTime != null) {
                        tvScanTime.setText("Queued");
                    }
                }
            }
            if (online) {
                queueFlush();
            } else {
                updateSyncLabel();
            }
            return;
        }
        if (!online) {
            showError("Staff card not found");
            return;
        }
        verifyOnline(rawUid, clockUid, staff, decide);
    }

    private JSONObject showLocalStaff(JSONObject staff) {
        JSONObject decide = StaffClock.decide(staff, System.currentTimeMillis());
        showLocalStaff(staff, decide);
        return decide;
    }

    private void showChecking(JSONObject staff) {
        int id = staff == null ? 0 : staff.optInt("id", 0);
        displayedStaffId = id;
        showResult();
        tvName.setText(staff == null ? "Staff" : staff.optString("name", "Staff"));
        tvPost.setText(staff == null ? "" : staff.optString("post", ""));
        photos.bind(imgPhoto, id, staff == null ? "" : staff.optString("photo", ""));
        tvStatus.setText("CHECKING…");
        tvStatus.setBackgroundResource(0);
        tvStatus.setTextColor(0xFF1D4ED8);
        resultPane.setBackgroundResource(R.drawable.bg_result_hold);
        tvVerdict.setVisibility(View.VISIBLE);
        tvVerdict.setText("Updating shift from school");
        tvVerdict.setBackgroundResource(R.drawable.bg_verdict_none);
        tvVerdict.setTextColor(0xFF64748B);
        if (tvScanTime != null) {
            tvScanTime.setText("");
        }
        if (tvHint != null) {
            tvHint.setText("Updating shift from school");
        }
        setReady("busy");
    }

    private void showLocalStaff(JSONObject staff, JSONObject decide) {
        if (decide == null) {
            decide = StaffClock.decide(staff, System.currentTimeMillis());
        }
        int id = staff.optInt("id", 0);
        persistClock(id, staff, decide);
        String status = decide.optString("status", "");
        if (id > 0 && status.length() > 0) {
            prefs.setLastClock(id, status);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("success", decide.optBoolean("ok", false) ? 1 : 0);
            json.put("status", status);
            json.put("already", decide.optBoolean("already", false));
            json.put("can_out", decide.optBoolean("can_out", false));
            json.put("message", decide.optString("message", ""));
            json.put("time", decide.optString("time", ""));
            JSONObject person = new JSONObject();
            person.put("id", id);
            person.put("name", staff.optString("name", "Staff"));
            person.put("post", staff.optString("post", ""));
            person.put("photo", staff.optString("photo", ""));
            json.put("staff", person);
            json.put("person", person);
            JSONObject verdict = new JSONObject();
            verdict.put("label", decide.optString("label", ""));
            verdict.put("detail", decide.optString("detail", ""));
            json.put("verdict", verdict);
            JSONObject shift = new JSONObject();
            shift.put("working", decide.optBoolean("working", false));
            json.put("shift", shift);
        } catch (Exception ignored) {
        }
        showStaff(json, false);
    }

    private void persistClock(int id, JSONObject staff, JSONObject decide) {
        if (id <= 0 || decide == null || !decide.optBoolean("ok", false)) {
            return;
        }
        if (decide.optBoolean("already", false)) {
            return;
        }
        String status = decide.optString("status", "");
        String day = StaffClock.dayKey(System.currentTimeMillis());
        long now = System.currentTimeMillis() / 1000L;
        try {
            if ("IN".equalsIgnoreCase(status)) {
                store.saveClock(id, now, 0, day);
                staff.put("clock_in", now);
                staff.put("clock_out", 0);
                staff.put("clock_day", day);
            } else if ("OUT".equalsIgnoreCase(status) && decide.optBoolean("can_out", false)) {
                long in = staff.optLong("clock_in", now);
                if (in <= 0) {
                    in = now;
                }
                store.saveClock(id, in, now, day);
                staff.put("clock_out", now);
                staff.put("clock_day", day);
            }
        } catch (Exception ignored) {
        }
    }

    private void queueFromDecide(String uid, JSONObject decide) {
        if (decide == null || !decide.optBoolean("ok", false) || decide.optBoolean("already", false)) {
            return;
        }
        String status = decide.optString("status", "");
        if (status.length() == 0) {
            return;
        }
        store.enqueueCard(uid, System.currentTimeMillis() / 1000L, status);
    }

    private void verifyOnline(final String rawUid, final String uid, final JSONObject localStaff, final JSONObject localDecide) {
        if (localStaff == null) {
            setReady("busy");
            if (tvHint != null) {
                tvHint.setText("Reading card…");
            }
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject refreshed = localStaff;
                    if (refreshed == null) {
                        refreshed = refreshAndFind(rawUid);
                    }
                    if (refreshed != null && localStaff == null) {
                        final JSONObject found = refreshed;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                JSONObject d = showLocalStaff(found);
                                boolean fresh = d != null && d.optBoolean("ok", false) && !d.optBoolean("already", false);
                                if (fresh) {
                                    String stored = found.optString("card", uid);
                                    queueFromDecide(stored.length() >= 4 ? stored : uid, d);
                                }
                                queueFlush();
                                updateSyncLabel();
                            }
                        });
                        return;
                    }
                    String wanted = "";
                    if (localDecide != null && localDecide.optBoolean("ok", false)) {
                        wanted = localDecide.optString("status", "");
                    }
                    String[] tries = scanCandidates(rawUid, uid);
                    JSONObject result = null;
                    for (int i = 0; i < tries.length; i++) {
                        JSONObject attempt = XanderApi.scanStaffCard(prefs.serverUrl(), prefs.schoolId(), tries[i], 0, wanted);
                        result = attempt;
                        if (isIdentified(attempt)) {
                            break;
                        }
                    }
                    final JSONObject finalResult = result;
                    final JSONObject shownLocal = refreshed;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            applyScan(finalResult, shownLocal, uid);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (localStaff != null) {
                                if (localDecide != null && !localDecide.optBoolean("ok", false)) {
                                    showLocalStaff(localStaff, localDecide);
                                    return;
                                }
                                queueFromDecide(uid, localDecide);
                                if (tvHint != null && localDecide != null && localDecide.optBoolean("ok", false)
                                        && !localDecide.optBoolean("already", false)) {
                                    tvHint.setText("Saved offline — will sync when online");
                                }
                                if (tvScanTime != null && localDecide != null && localDecide.optBoolean("ok", false)
                                        && !localDecide.optBoolean("already", false)) {
                                    tvScanTime.setText("Queued");
                                }
                                queueFlush();
                            } else {
                                showError(KioskLog.friendlyNet(e));
                            }
                        }
                    });
                }
            }
        }, "staff-scan").start();
    }

    /** Pull the school roster, then match this tap against the cards just saved. */
    private JSONObject refreshAndFind(String rawUid) {
        try {
            JSONObject json = XanderApi.staffRoster(prefs.serverUrl(), prefs.schoolId());
            if (!XanderApi.ok(json)) {
                return null;
            }
            JSONArray staff = json.optJSONArray("staff");
            if (staff == null) {
                return null;
            }
            store.replaceStaff(staff);
            photos.prefetch(staff);
            return store.findStaffByCard(rawUid);
        } catch (Exception e) {
            KioskLog.e("Kiosk", "roster refresh: " + e.getMessage());
            return null;
        }
    }

    /** Raw reader text first so the server can convert an 8-digit decimal UID. */
    private String[] scanCandidates(String rawUid, String uid) {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        if (rawUid != null && rawUid.trim().length() >= 4) {
            set.add(rawUid.trim());
        }
        if (uid != null && uid.length() >= 4) {
            set.add(uid);
        }
        String[] vars = CardUid.lookupVariants(rawUid);
        for (int i = 0; i < vars.length && set.size() < 4; i++) {
            if (vars[i] != null && vars[i].length() >= 4) {
                set.add(vars[i]);
            }
        }
        return set.toArray(new String[set.size()]);
    }

    private boolean isIdentified(JSONObject json) {
        if (json == null) {
            return false;
        }
        if (XanderApi.ok(json) || "staff".equalsIgnoreCase(json.optString("kind", ""))) {
            return true;
        }
        JSONObject person = json.optJSONObject("staff");
        if (person == null) {
            person = json.optJSONObject("person");
        }
        if (person != null && person.optInt("id", 0) > 0) {
            return true;
        }
        String msg = XanderApi.message(json, "").toLowerCase(Locale.US);
        return msg.contains("student card")
                || msg.contains("visitor card")
                || msg.contains("parent visitor");
    }

    private void applyScan(JSONObject json, JSONObject localStaff, String uid) {
        JSONObject person = json == null ? null : json.optJSONObject("staff");
        if (person == null && json != null) {
            person = json.optJSONObject("person");
        }
        int id = person == null ? 0 : person.optInt("id", 0);
        String status = json == null ? "" : json.optString("status", "");
        if (json != null && lastLocalDecide != null
                && lastLocalDecide.optBoolean("already", false)
                && "IN".equalsIgnoreCase(lastLocalDecide.optString("status", ""))
                && "OUT".equalsIgnoreCase(status)) {
            try {
                json.put("status", "IN");
                json.put("already", true);
                json.put("can_out", false);
                if (json.optString("message", "").length() == 0) {
                    json.put("message", "Already checked in — waiting for checkout");
                }
            } catch (Exception ignored) {
            }
            status = "IN";
        }
        if (pendingUid != null && uid != null && !uid.equalsIgnoreCase(pendingUid)) {
            if (id > 0 && status.length() > 0) {
                prefs.setLastClock(id, status);
            }
            prefs.saveDashboard(json);
            return;
        }
        if (localStaff != null && resultPane != null && resultPane.getVisibility() != View.VISIBLE) {
            if (id > 0 && status.length() > 0) {
                prefs.setLastClock(id, status);
            }
            prefs.saveDashboard(json);
            return;
        }
        prefs.saveDashboard(json);
        boolean alreadyShown = displayedStaffId > 0 && displayedStaffId == id
                && resultPane != null && resultPane.getVisibility() == View.VISIBLE;
        if (XanderApi.ok(json) || person != null) {
            showStaff(json, alreadyShown);
            return;
        }
        showError(XanderApi.message(json, "Staff card not found"));
    }

    private void showStaff(JSONObject json, boolean alreadyShown) {
        JSONObject person = json.optJSONObject("staff");
        if (person == null) {
            person = json.optJSONObject("person");
        }
        String name = person == null ? "" : person.optString("name", "");
        String post = person == null ? "" : person.optString("post", "");
        String status = json.optString("status", "");
        JSONObject verdict = json.optJSONObject("verdict");
        int id = person == null ? 0 : person.optInt("id", 0);
        boolean already = json.optBoolean("already", false);
        displayedStaffId = id;
        showResult();
        if (!alreadyShown) {
            tvName.setText(name.length() == 0 ? "Staff" : name);
            tvPost.setText(post);
            String photo = person == null ? "" : person.optString("photo", "");
            photos.bind(imgPhoto, id, photo);
        }
        if (json.optString("time", "").length() > 0) {
            tvScanTime.setText(json.optString("time", ""));
        }
        boolean ok = XanderApi.ok(json);
        JSONObject shiftInfo = json.optJSONObject("shift");
        if (shiftInfo != null && !shiftInfo.optBoolean("working", true)) {
            ok = false;
            if (json.optString("message", "").length() == 0) {
                try {
                    json.put("message", "Not a working day for this shift");
                } catch (Exception ignored) {
                }
            }
        }
        persistFromScan(id, status, ok, already);
        if (!ok) {
            String fail = XanderApi.message(json, "Cannot record attendance");
            String low = fail.toLowerCase(Locale.US);
            boolean offDay = low.contains("working day")
                    || (shiftInfo != null && !shiftInfo.optBoolean("working", true));
            tvStatus.setBackgroundResource(0);
            tvStatus.setTextColor(0xFFDC2626);
            resultPane.setBackgroundResource(R.drawable.bg_result_hold);
            if (offDay) {
                tvStatus.setText("NOT A WORKING DAY");
                tvVerdict.setText("Today is not in this staff shift");
                speakScreen(name, "Not a working day", null);
            } else if (low.contains("shift already ended") || low.contains("after shift")) {
                tvStatus.setText("SHIFT ENDED");
                tvVerdict.setText(fail);
                speakScreen(name, fail, null);
            } else {
                tvStatus.setText("NOT RECORDED");
                tvVerdict.setText(fail.length() == 0 ? "Attendance was not saved" : fail);
                speakScreen(name, fail.length() == 0 ? "Attendance was not saved" : fail, null);
            }
            tvVerdict.setVisibility(View.VISIBLE);
            tvVerdict.setBackgroundResource(R.drawable.bg_verdict_late);
            tvVerdict.setTextColor(0xFFC2410C);
            if (tvHint != null) {
                tvHint.setText(fail);
            }
            setReady("ready");
            handler.removeCallbacks(idleLater);
            handler.postDelayed(idleLater, 3500);
            updateSyncLabel();
            return;
        }
        if ("IN".equalsIgnoreCase(status) && already) {
            tvStatus.setText("ALREADY CHECKED IN");
            if (!alreadyShown) {
                KioskSound.sayNow(this, "Already in");
            }
            tvStatus.setBackgroundResource(0);
            tvStatus.setTextColor(0xFF059669);
            resultPane.setBackgroundResource(R.drawable.bg_result_in);
            tvVerdict.setVisibility(View.VISIBLE);
            tvVerdict.setText("Waiting for checkout");
            tvVerdict.setBackgroundResource(R.drawable.bg_verdict_none);
            tvVerdict.setTextColor(0xFF64748B);
            if (!alreadyShown) {
                DeviceLights.pass(this);
            }
        } else if ("IN".equalsIgnoreCase(status)) {
            tvStatus.setText("CHECKED IN");
            if (!alreadyShown) {
                KioskSound.sayNow(this, "Welcome");
            }
            tvStatus.setBackgroundResource(0);
            tvStatus.setTextColor(0xFF059669);
            resultPane.setBackgroundResource(R.drawable.bg_result_in);
            if (ok && !alreadyShown) {
                DeviceLights.pass(this);
            }
            applyVerdict(verdict, "IN");
        } else if ("OUT".equalsIgnoreCase(status)) {
            tvStatus.setText("CHECKED OUT");
            if (!alreadyShown && !already) {
                KioskSound.sayNow(this, "Goodbye");
            }
            tvStatus.setBackgroundResource(0);
            tvStatus.setTextColor(0xFFDC2626);
            resultPane.setBackgroundResource(R.drawable.bg_result_out);
            if (ok && !alreadyShown) {
                DeviceLights.pass(this);
            }
            applyVerdict(verdict, "OUT");
        } else {
            String hold = status.length() == 0 ? "HOLD" : status;
            tvStatus.setText(hold);
            speakScreen(name, hold, verdict);
            tvStatus.setBackgroundResource(0);
            resultPane.setBackgroundResource(R.drawable.bg_result_hold);
            applyVerdict(verdict, status);
        }
        hideConflictingVerdict();
        if (id > 0 && status.length() > 0) {
            prefs.setLastClock(id, status);
        }
        String msg = XanderApi.message(json, "");
        if (already && "IN".equalsIgnoreCase(status)) {
            tvHint.setText(msg.length() > 0 ? msg : "Already checked in — waiting for checkout");
        } else if (msg.length() > 0 && already) {
            tvHint.setText(msg);
        } else if (!XanderApi.ok(json) && msg.length() > 0) {
            tvHint.setText(msg);
        } else {
            tvHint.setText("Ready for next card");
        }
        setReady("ready");
        handler.removeCallbacks(idleLater);
        handler.postDelayed(idleLater, 3500);
        updateSyncLabel();
    }

    private void persistFromScan(int id, String status, boolean ok, boolean already) {
        if (!ok || already || id <= 0 || status == null || status.length() == 0) {
            return;
        }
        String day = StaffClock.dayKey(System.currentTimeMillis());
        long now = System.currentTimeMillis() / 1000L;
        if ("IN".equalsIgnoreCase(status)) {
            store.saveClock(id, now, 0, day);
        } else if ("OUT".equalsIgnoreCase(status)) {
            JSONObject s = store.findStaffById(id);
            long in = s == null ? now : s.optLong("clock_in", now);
            if (in <= 0) {
                in = now;
            }
            store.saveClock(id, in, now, day);
        }
    }

    private void applyVerdict(JSONObject verdict, String status) {
        if (verdict == null || verdict.optString("label", "").length() == 0) {
            tvVerdict.setVisibility(View.GONE);
            return;
        }
        String label = verdict.optString("label", "");
        String detail = verdict.optString("detail", "");
        String text = detail.length() == 0 ? label : label + " — " + detail;
        String low = text.toLowerCase(Locale.US);
        if ("IN".equalsIgnoreCase(status) && (low.contains("checked out") || "checked out".equals(low))) {
            tvVerdict.setVisibility(View.GONE);
            return;
        }
        if ("OUT".equalsIgnoreCase(status) && low.contains("checked in")) {
            tvVerdict.setVisibility(View.GONE);
            return;
        }
        if ("IN".equalsIgnoreCase(status) && (low.equals("checked in") || low.equals("staff in"))) {
            tvVerdict.setVisibility(View.GONE);
            return;
        }
        if ("OUT".equalsIgnoreCase(status) && (low.equals("checked out") || low.equals("staff out"))) {
            tvVerdict.setVisibility(View.GONE);
            return;
        }
        tvVerdict.setVisibility(View.VISIBLE);
        tvVerdict.setText(text);
        String code = verdict.optString("code", "none");
        if ("late".equals(code) || "early_leave".equals(code)) {
            tvVerdict.setBackgroundResource(R.drawable.bg_verdict_late);
            tvVerdict.setTextColor(0xFFC2410C);
        } else if ("ontime".equals(code) || "early".equals(code)) {
            tvVerdict.setBackgroundResource(R.drawable.bg_verdict_ok);
            tvVerdict.setTextColor(0xFF059669);
        } else {
            tvVerdict.setBackgroundResource(R.drawable.bg_verdict_none);
            tvVerdict.setTextColor(0xFF64748B);
        }
    }

    private void hideConflictingVerdict() {
        if (tvVerdict == null || tvStatus == null || tvVerdict.getVisibility() != View.VISIBLE) {
            return;
        }
        String st = String.valueOf(tvStatus.getText()).toLowerCase(Locale.US);
        String vd = String.valueOf(tvVerdict.getText()).toLowerCase(Locale.US);
        boolean statusIn = st.contains("checked in");
        boolean statusOut = st.contains("checked out");
        boolean verdictIn = vd.contains("checked in");
        boolean verdictOut = vd.contains("checked out");
        if ((statusIn && verdictOut) || (statusOut && verdictIn) || (statusIn && statusOut)) {
            tvVerdict.setVisibility(View.GONE);
        }
    }

    private void showError(String msg) {
        displayedStaffId = 0;
        showResult();
        resultPane.setBackgroundResource(R.drawable.bg_result_hold);
        tvName.setText(msg);
        tvPost.setText("");
        tvStatus.setText("TRY AGAIN");
        speakScreen("", msg == null || msg.length() == 0 ? "Try again" : msg, null);
        tvStatus.setBackgroundResource(0);
        tvStatus.setTextColor(0xFFDC2626);
        tvVerdict.setVisibility(View.GONE);
        tvScanTime.setText("");
        imgPhoto.setImageResource(R.drawable.ic_nfc_card);
        if (tvHint != null) {
            tvHint.setText("Card not recognized");
        }
        setReady("error");
        DeviceLights.fail(this);
        handler.removeCallbacks(idleLater);
        handler.postDelayed(idleLater, 2500);
    }

    private final Runnable idleLater = new Runnable() {
        @Override
        public void run() {
            idleUi();
        }
    };

    private void idleUi() {
        displayedStaffId = 0;
        pendingUid = null;
        lastLocalDecide = null;
        lastVoice = null;
        showWait();
        if (tvWait != null) {
            tvWait.setText("Waiting for card");
        }
        if (tvHint != null) {
            tvHint.setText("Tap your staff card on the reader");
        }
        setReady("ready");
        DeviceLights.idle(this);
        focusWedge();
    }

    private void showWait() {
        if (waitPane != null) {
            waitPane.setVisibility(View.VISIBLE);
        }
        if (resultPane != null) {
            resultPane.setVisibility(View.GONE);
        }
        startWaitPulse();
    }

    private void showResult() {
        stopWaitPulse();
        if (waitPane != null) {
            waitPane.setVisibility(View.GONE);
        }
        if (resultPane != null) {
            resultPane.setVisibility(View.VISIBLE);
        }
    }

    private void startWaitPulse() {
        if (imgWait != null && waitPulse != null) {
            imgWait.startAnimation(waitPulse);
        }
    }

    private void stopWaitPulse() {
        if (imgWait != null) {
            imgWait.clearAnimation();
        }
    }

    private void setReady(String state) {
        if (tvWait != null && waitPane != null && waitPane.getVisibility() == View.VISIBLE) {
            if ("busy".equals(state) && "Waiting for card".equals(String.valueOf(tvWait.getText()))) {
                tvWait.setText("Reading card…");
            } else if ("ready".equals(state) && waitPane.getVisibility() == View.VISIBLE) {
                if (tvWait.getText() == null || tvWait.getText().toString().startsWith("Reading")) {
                    tvWait.setText("Waiting for card");
                }
            }
        }
    }

    private void speakScreen(String name, String line, JSONObject verdict) {
        String who = name == null ? "" : name.trim();
        String spoken = line == null ? "" : line.trim();
        if (verdict != null) {
            String label = verdict.optString("label", "").trim();
            String detail = verdict.optString("detail", "").trim();
            if (label.length() > 0
                    && !label.equalsIgnoreCase(spoken)
                    && !label.equalsIgnoreCase("Checked in")
                    && !label.equalsIgnoreCase("Checked out")) {
                spoken = spoken + ". " + label;
            }
            if (detail.length() > 0) {
                spoken = spoken + ". " + detail;
            }
        }
        if (who.length() > 0) {
            spoken = who + ". " + spoken;
        }
        KioskSound.sayNow(this, spoken);
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

    private void startCardReader() {
        UsbPermissionHelper.ensure(this);
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
        }
        if (usbHid == null) {
            usbHid = new UsbHidCardReader(this);
        }
        usbHid.start(listen);
    }

    private void backgroundSync() {
        SyncService.request(this, true);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                photos.bindLogo(imgLogo, prefs.schoolLogo());
                updateSyncLabel();
            }
        }, 800);
    }

    private void updateSyncLabel() {
        if (tvSync == null) {
            return;
        }
        int pending = store.pendingCount();
        int cards = store.staffWithCards();
        int pics = photos.cachedCount();
        boolean online = NetWatch.online(this);
        if (pending > 0) {
            tvSync.setText(online ? pending + " waiting · sending…" : pending + " saved offline");
        } else if (online) {
            tvSync.setText("Online · " + cards + " cards · " + pics + " photos");
        } else {
            tvSync.setText("Offline · " + cards + " cards · " + pics + " photos");
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
                input.requestFocus();
                showIme(input);
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event);
        }
        if (passwordEntryOpen) {
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
        if (intent != null) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                KioskLog.i("Kiosk", "ignored Android NFC intent to keep USB reader encoding");
            }
        }
    }

    private void enableNfc() {
        nfcReaderOn = false;
        focusWedge();
    }

    private void disableNfc() {
        if (nfcAdapter == null) {
            return;
        }
        try {
            nfcAdapter.disableForegroundDispatch(this);
        } catch (Exception ignored) {
        }
        nfcReaderOn = false;
    }

    private void focusWedge() {
        if (passwordEntryOpen) {
            return;
        }
        hideIme();
        if (etWedge != null) {
            etWedge.requestFocus();
        }
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
        if (tvSchool != null) {
            tvSchool.setText(prefs.schoolName());
        }
        photos.bindLogo(imgLogo, prefs.schoolLogo());
        DeviceLights.on(this);
        startCardReader();
        KioskSound.prepare(this);
        enableNfc();
        if (!netRegistered) {
            XanderCompat.register(this, netRx, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION), false);
            netRegistered = true;
        }
        if (!usbAttachRegistered) {
            XanderCompat.register(this, usbAttachRx, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED), true);
            usbAttachRegistered = true;
        }
        updateSyncLabel();
        backgroundSync();
        focusWedge();
        if (waitPane != null && waitPane.getVisibility() == View.VISIBLE) {
            startWaitPulse();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopWaitPulse();
        disableNfc();
        if (netRegistered) {
            try {
                unregisterReceiver(netRx);
            } catch (Exception ignored) {
            }
            netRegistered = false;
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
        stopWaitPulse();
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
