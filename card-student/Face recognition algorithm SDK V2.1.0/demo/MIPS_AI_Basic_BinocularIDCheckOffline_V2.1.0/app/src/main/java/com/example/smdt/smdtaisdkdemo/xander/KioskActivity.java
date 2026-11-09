package com.example.smdt.smdtaisdkdemo.xander;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smdt.smdtaisdkdemo.R;
import com.example.smdt.smdtaisdkdemo.xander.facepass.FacePassEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class KioskActivity extends Activity {
    private XanderPrefs prefs;
    private XanderStore store;
    private DeviceUsb usb;
    private CardReader cardReader;
    private UsbHidCardReader usbHid;
    private DkUsbCardReader dkUsb;
    private FacePassEngine faceEngine;
    public static volatile FacePassEngine liveEngine;
    private SurfaceView surfaceRgb;
    private SurfaceView surfaceIr;
    private SurfaceHolder holderRgb;
    private SurfaceHolder holderIr;
    private TextView tvHint;
    private TextView tvLocation;
    private TextView tvStatus;
    private TextView tvName;
    private TextView tvMeta;
    private TextView tvTime;
    private TextView tvClock;
    private TextView tvDate;
    private TextView tvSchool;
    private TextView tvNfc;
    private TextView tvCardDebug;
    private FaceTrackerView faceTracker;
    private FaceGuideView faceGuide;
    private ImageView imgPhoto;
    private View resultCard;
    private Button btCapture;
    private View btSettings;
    private View enrollBar;
    private View clockBar;
    private Button btEnrollBack;
    private TextView tvEnrollName;
    private TextView tvEnrollProgress;
    private EditText etWedge;
    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPending;
    private IntentFilter[] nfcFilters;
    private final StringBuilder keyBuf = new StringBuilder();
    private final Handler handler = new Handler();
    private Bitmap lastFace;
    private boolean enrollMode;
    private int enrollStaffId;
    private String enrollStaffName;
    private String pendingPhotoUrl;
    private boolean started;
    private boolean paused;
    private boolean capturing;
    private long enrollFullSince;
    private long lastShotAt;
    private long enrollStarted;
    private int enrollGot;
    private boolean lastLive;
    private boolean lastSpoof;
    private float lastLiveScore;
    private static final int ENROLL_NEED = 3;
    private int camW = 1280;
    private int camH = 720;
    private int lastFaceStaff;
    private long lastFaceAt;
    private int presentStaffId;
    private long presentSeenAt;
    private boolean presentDidIn;
    private boolean faceInFrame;
    private int lastClockStaff;
    private long lastClockAt;
    private static final long FACE_GONE_MS = 400;
    private static final long CLOCK_GAP_MS = 1200;
    private String lastCard;
    private long lastCardAt;
    private ToneGenerator tone;
    private boolean nfcReaderOn;
    private int strangerFails;
    private long lastStrangerAt;

    private final NfcAdapter.ReaderCallback nfcReader = new NfcAdapter.ReaderCallback() {
        @Override
        public void onTagDiscovered(Tag tag) {
            if (tag == null) {
                return;
            }
            final String uid = bytesToHex(tag.getId());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onCard(uid);
                }
            });
        }
    };

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            if (tvClock != null) {
                tvClock.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now));
            }
            if (tvDate != null) {
                tvDate.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now));
            }
            DeviceLights.keepWhiteOff(KioskActivity.this);
            if (now.getSeconds() % 8 == 0) {
                KioskSound.prepare(KioskActivity.this);
            }
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

    private final Runnable enrollPulse = new Runnable() {
        @Override
        public void run() {
            if (!enrollMode) {
                return;
            }
            long now = System.currentTimeMillis();
            if (!capturing && enrollGot < ENROLL_NEED && !lastSpoof
                    && now - enrollStarted >= 400
                    && now - lastShotAt >= 400) {
                captureEnroll();
            }
            handler.postDelayed(this, 280);
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
        if (!prefs.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        applyEnrollIntent(getIntent());
        if (!XanderPermissions.hasAll(this)) {
            Toast.makeText(this, "Allow camera, overlay and file access first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_xander_kiosk);

        tvHint = (TextView) findViewById(R.id.tv_hint);
        tvLocation = (TextView) findViewById(R.id.tv_location);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvName = (TextView) findViewById(R.id.tv_name);
        tvMeta = (TextView) findViewById(R.id.tv_meta);
        tvTime = (TextView) findViewById(R.id.tv_time);
        tvClock = (TextView) findViewById(R.id.tv_clock);
        tvDate = (TextView) findViewById(R.id.tv_date);
        tvSchool = (TextView) findViewById(R.id.tv_school);
        tvNfc = (TextView) findViewById(R.id.tv_nfc);
        tvCardDebug = (TextView) findViewById(R.id.tv_card_debug);
        if (tvCardDebug != null) {
            tvCardDebug.setVisibility(View.GONE);
        }
        faceTracker = (FaceTrackerView) findViewById(R.id.face_tracker);
        faceGuide = (FaceGuideView) findViewById(R.id.face_guide);
        imgPhoto = (ImageView) findViewById(R.id.img_photo);
        resultCard = findViewById(R.id.result_card);
        btCapture = (Button) findViewById(R.id.bt_capture);
        btSettings = findViewById(R.id.bt_settings);
        enrollBar = findViewById(R.id.enroll_bar);
        clockBar = findViewById(R.id.clock_bar);
        btEnrollBack = (Button) findViewById(R.id.bt_enroll_back);
        tvEnrollName = (TextView) findViewById(R.id.tv_enroll_name);
        tvEnrollProgress = (TextView) findViewById(R.id.tv_enroll_progress);
        KioskLog.i("Kiosk", "Xander Attendance 3.1");
        if (btSettings != null) {
            btSettings.bringToFront();
            btSettings.setClickable(true);
            btSettings.setFocusable(true);
        }
        if (faceGuide != null) {
            faceGuide.setClickable(false);
            faceGuide.setFocusable(false);
        }
        etWedge = (EditText) findViewById(R.id.et_nfc_wedge);
        if (etWedge != null) {
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
                        if (uid.length() >= 4) {
                            if (cardReader != null) {
                                cardReader.ingestHid(uid);
                            } else {
                                onCard(uid);
                            }
                        }
                    } else if (raw.length() >= 4) {
                        handler.removeCallbacks(hidFlush);
                        handler.postDelayed(hidFlush, 400);
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
                        if (uid.length() >= 4) {
                            if (cardReader != null) {
                                cardReader.ingestHid(uid);
                            } else {
                                onCard(uid);
                            }
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
        surfaceRgb = (SurfaceView) findViewById(R.id.surfaceViewCamera);
        surfaceIr = (SurfaceView) findViewById(R.id.surfaceViewCameraIR);
        holderRgb = surfaceRgb.getHolder();
        holderIr = surfaceIr.getHolder();
        holderRgb.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holderIr.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holderRgb.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                openCameras();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (!enrollMode) {
                    openCameras();
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });
        hideSystemUi();

        tvSchool.setText(prefs.schoolName());
        refreshEnrollUi();

        btSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        if (tvSchool != null) {
            tvSchool.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!enrollMode) {
                        openSettings();
                    }
                }
            });
        }
        if (tvClock != null) {
            tvClock.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (!enrollMode) {
                        openSettings();
                        return true;
                    }
                    return false;
                }
            });
        }
        btCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureEnroll();
            }
        });
        if (btEnrollBack != null) {
            btEnrollBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    leaveEnrollMode();
                }
            });
        }

        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (Exception e) {
            tone = null;
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcPending = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        nfcFilters = new IntentFilter[]{
                new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        };
        updateNfcLabel();

        applyBoard();
        KioskSound.prepare(this);
        startCardReader();
        faceEngine = new FacePassEngine(this, prefs);
        liveEngine = faceEngine;
        faceEngine.setListener(new FacePassEngine.Listener() {
            @Override
            public void onStatus(final String msg) {
                showEngineError(msg);
            }

            @Override
            public void onReady() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        afterStart();
                    }
                });
                runPendingPhotoEnroll();
            }

            @Override
            public void onRecognized(final int staffId) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (enrollMode) {
                            return;
                        }
                        strangerFails = 0;
                        noteFacePresent(staffId);
                    }
                });
            }

            @Override
            public void onUnknown() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!enrollMode) {
                            noteStranger();
                        }
                    }
                });
            }

            @Override
            public void onSpoof() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        lastSpoof = true;
                        lastLive = false;
                        if (enrollMode) {
                            lastSpoof = true;
                            lastLive = false;
                            if (faceGuide != null) {
                                faceGuide.setState(FaceGuideView.BAD);
                            }
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (enrollMode) {
                                        lastSpoof = false;
                                    }
                                }
                            }, 900);
                            return;
                        }
                        if (presentStaffId > 0 || faceInFrame) {
                            return;
                        }
                        if (faceGuide != null) {
                            faceGuide.setState(FaceGuideView.BAD);
                        }
                    }
                });
            }

            @Override
            public void onLiveness(final boolean live, final boolean spoof, final float score, final boolean dual3d) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        lastLive = live;
                        lastSpoof = spoof;
                        lastLiveScore = score;
                        if (enrollMode && spoof) {
                            KioskLog.e("Enroll", "PHOTO BLOCKED liveness=" + score);
                        }
                    }
                });
            }

            @Override
            public void onFaces(final android.graphics.Rect[] boxes, final int imgW, final int imgH, final boolean live3d) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (faceTracker != null) {
                            faceTracker.setFaces(boxes, imgW, imgH, live3d, false);
                        }
                        updateFaceGuide(boxes, imgW, imgH, false);
                        keepPresence(boxes);
                    }
                });
            }

            @Override
            public void onPreviewFace(Bitmap bmp) {
                lastFace = bmp;
            }
        });
        usb = new DeviceUsb(this);
        usb.start(new DeviceUsb.Listener() {
            @Override
            public void onUsbReady() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        startEngine();
                    }
                });
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startEngine();
            }
        }, 900);
        handler.post(clockTick);
        backgroundSync();
    }

    private void startEngine() {
        if (started || faceEngine == null) {
            return;
        }
        started = true;
        applyBoard();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String auth = faceEngine.initSdk();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (auth == null || !auth.startsWith("ok:")) {
                                showEngineError(auth);
                            }
                            if (!paused) {
                                openCameras();
                            }
                        }
                    });
                } catch (final Throwable e) {
                    started = false;
                    android.util.Log.e("XanderKiosk", "startEngine", e);
                    showEngineError(e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    private void openCameras() {
        if (paused || faceEngine == null || holderRgb == null) {
            return;
        }
        if (holderRgb.getSurface() == null || !holderRgb.getSurface().isValid()) {
            handler.removeCallbacks(retryCameras);
            handler.postDelayed(retryCameras, 350);
            return;
        }
        try {
            if (faceEngine.camerasRunning() && !faceEngine.needsCameraRestart()) {
                if (!enrollMode) {
                    faceEngine.resumeMatching();
                } else {
                    faceEngine.setSearchEnabled(false);
                }
                return;
            }
            int rgb = prefs.rgbCameraId();
            int ir = prefs.irCameraId();
            if (ir == rgb) {
                ir = rgb == 0 ? 1 : 0;
            }
            faceEngine.startCameras(holderRgb, holderIr, rgb, ir, prefs.previewRotation());
            if (!enrollMode) {
                faceEngine.resumeMatching();
            } else {
                faceEngine.setSearchEnabled(false);
            }
        } catch (Throwable t) {
            showEngineError(t.getMessage() != null ? t.getMessage() : "Camera open failed");
        }
    }

    private final Runnable retryCameras = new Runnable() {
        @Override
        public void run() {
            openCameras();
        }
    };

    private void afterStart() {
        applyPreviewRotation();
        DeviceLights.applyTorch(faceEngine == null ? null : faceEngine.rgbHardware(), false);
        if (faceEngine != null) {
            faceEngine.setSearchEnabled(!enrollMode);
        }
        if (!enrollMode && tvHint != null) {
            tvHint.setText("Staff: look at the camera\nStudents: tap your card");
        }
        if (!enrollMode && tvNfc != null && faceEngine != null && faceEngine.isReady()) {
            tvNfc.setText(faceEngine.isDual()
                    ? "3D sensor on  ·  tap card at the bottom"
                    : "Staff face ready  ·  tap card at the bottom");
            tvNfc.setTextColor(0xFF4ADE80);
        }
    }

    private void noteStranger() {
        if (faceInFrame && presentStaffId > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastStrangerAt < 2000) {
            return;
        }
        lastStrangerAt = now;
        strangerFails++;
        if (strangerFails < 8) {
            return;
        }
        strangerFails = 0;
        fail("Not registered");
    }

    private final Runnable faceGone = new Runnable() {
        @Override
        public void run() {
            if (enrollMode) {
                return;
            }
            long wait = FACE_GONE_MS - (System.currentTimeMillis() - presentSeenAt);
            if (wait > 50 && (presentStaffId > 0 || faceInFrame)) {
                handler.postDelayed(this, wait);
                return;
            }
            presentStaffId = 0;
            presentDidIn = false;
            presentSeenAt = 0;
            faceInFrame = false;
        }
    };

    private void keepPresence(android.graphics.Rect[] boxes) {
        if (enrollMode) {
            return;
        }
        boolean seen = false;
        if (boxes != null) {
            for (int i = 0; i < boxes.length; i++) {
                if (boxes[i] != null && Math.abs(boxes[i].width() * boxes[i].height()) > 20) {
                    seen = true;
                    break;
                }
            }
        }
        faceInFrame = seen;
        if (seen) {
            presentSeenAt = System.currentTimeMillis();
            handler.removeCallbacks(faceGone);
        } else if (presentStaffId > 0) {
            handler.removeCallbacks(faceGone);
            handler.postDelayed(faceGone, FACE_GONE_MS);
        }
    }

    private void noteFacePresent(int staffId) {
        long now = System.currentTimeMillis();
        presentSeenAt = now;
        faceInFrame = true;
        handler.removeCallbacks(faceGone);
        if (resultCard != null && resultCard.getVisibility() == View.VISIBLE) {
            return;
        }
        if (staffId == lastClockStaff && now - lastClockAt < CLOCK_GAP_MS && lastClockAt > 0) {
            return;
        }
        presentStaffId = staffId;
        presentDidIn = true;
        lastClockStaff = staffId;
        lastClockAt = now;
        clockStaff(staffId, "");
    }

    private final Runnable unlockPresence = new Runnable() {
        @Override
        public void run() {
            presentStaffId = 0;
            presentDidIn = false;
            lastClockAt = 0;
            if (!enrollMode && faceEngine != null) {
                faceEngine.resumeMatching();
                faceEngine.setSearchEnabled(true);
            }
        }
    };

    private void clockStaff(final int staffId, final String status) {
        JSONObject person = store.findStaff(staffId);
        String name = person == null ? prefs.staffName(staffId) : person.optString("name", "");
        if (name == null || name.length() == 0) {
            name = "Staff";
        }
        String extra = person == null ? "" : person.optString("extra", "");
        String photo = person == null ? "" : person.optString("photo", "");
        JSONObject local = store.clockLocal("staff", staffId, 0, "", name, extra, photo, "face", status);
        showResult(local);
        if (faceEngine != null && !enrollMode) {
            faceEngine.resumeMatching();
            faceEngine.setSearchEnabled(true);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    store.flush();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void onStaffFace(final int staffId) {
        noteFacePresent(staffId);
    }

    private void onCard(final String rawUid) {
        if (enrollMode) {
            return;
        }
        final String uid = CardUid.toStorage(rawUid);
        if (uid.length() < 4) {
            return;
        }
        long now = System.currentTimeMillis();
        if (uid.equals(lastCard) && now - lastCardAt < 1800) {
            return;
        }
        lastCard = uid;
        lastCardAt = now;
        lastStrangerAt = now;
        strangerFails = 0;
        JSONObject localPerson = store.findByCard(uid);
        if (localPerson != null && "staff".equals(localPerson.optString("kind", ""))) {
            fail("Staff use face, not card");
            return;
        }
        tvHint.setText("Student card " + uid);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject json = XanderApi.scanCard(prefs.serverUrl(), prefs.schoolId(), uid, prefs.areaId());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showResult(json);
                        }
                    });
                    store.flush();
                } catch (final Exception e) {
                    JSONObject person = store.findByCard(uid);
                    if (person == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                fail("Card not found: " + uid);
                            }
                        });
                        return;
                    }
                    String kind = person.optString("kind", "student");
                    if (!"student".equals(kind)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                fail("Staff use face, not card");
                            }
                        });
                        return;
                    }
                    int pid = person.optInt("id", 0);
                    int area = prefs.areaId();
                    if (area <= 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                fail("Set a student location in Settings");
                            }
                        });
                        return;
                    }
                    final JSONObject local = store.clockLocal("student", pid, area, uid,
                            person.optString("name", ""),
                            person.optString("extra", ""),
                            person.optString("photo", ""),
                            "card");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showResult(local);
                        }
                    });
                }
            }
        }).start();
    }

    private void captureEnroll() {
        if (capturing || !enrollMode) {
            return;
        }
        if (enrollStaffId <= 0) {
            Toast.makeText(this, "No staff selected", Toast.LENGTH_LONG).show();
            return;
        }
        if (faceEngine == null || !faceEngine.isReady()) {
            android.util.Log.w("Enroll", "engine not ready");
            return;
        }
        if (lastSpoof) {
            android.util.Log.w("Enroll", "skip shot — spoof frame");
            return;
        }
        capturing = true;
        lastShotAt = System.currentTimeMillis();
        final int shot = enrollGot + 1;
        KioskLog.i("Enroll", "shot " + shot + "/" + ENROLL_NEED + " live=" + lastLive
                + " spoof=" + lastSpoof + " 3D=" + (faceEngine != null && faceEngine.isDual()));
        if (btCapture != null) {
            btCapture.setEnabled(false);
            btCapture.setText("Saving " + shot + " / " + ENROLL_NEED + "…");
        }
        if (tvHint != null) {
            tvHint.setText("3D encoding shot " + shot + " of " + ENROLL_NEED);
        }
        final int staffId = enrollStaffId;
        final String staffName = enrollStaffName;
        final boolean first = enrollGot == 0;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String err = faceEngine.enrollShot(staffId, first);
                    if (err != null) {
                        KioskLog.e("Enroll", err);
                        throw new Exception(err);
                    }
                    KioskLog.i("Enroll", "shot " + shot + " encoded for staff " + staffId);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            enrollGot++;
                            capturing = false;
                            enrollFullSince = 0;
                            KioskSound.tick(KioskActivity.this);
                            updateEnrollProgress();
                            if (enrollGot >= ENROLL_NEED) {
                                finishEnrollUpload(staffId, staffName);
                            } else if (tvHint != null) {
                                tvHint.setText("Got " + enrollGot + " of " + ENROLL_NEED + " — hold still for the next");
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            capturing = false;
                            enrollFullSince = 0;
                            String err = e.getMessage();
                            if (err == null || err.length() == 0) {
                                err = "Shot failed";
                            }
                            Toast.makeText(KioskActivity.this, err, Toast.LENGTH_LONG).show();
                            updateEnrollProgress();
                            if (tvHint != null && enrollMode) {
                                tvHint.setText("Hold still in the circle — auto-capturing 3D live face");
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void finishEnrollUpload(final int staffId, final String staffName) {
        capturing = true;
        if (btCapture != null) {
            btCapture.setText("SAVED 3 / 3");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String b64 = null;
                try {
                    Bitmap bmp = faceEngine == null ? lastFace : faceEngine.uprightSnapshot();
                    if (bmp == null) {
                        bmp = lastFace;
                    }
                    if (bmp != null) {
                        File dir = new File(Environment.getExternalStorageDirectory(), "xander/faces");
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        File dest = new File(dir, staffId + ".jpg");
                        FileOutputStream fos = new FileOutputStream(dest);
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                        fos.close();
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                        b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
                    }
                } catch (Exception ignored) {
                }
                String serverMsg = "";
                try {
                    JSONObject json = XanderApi.enrollFace(prefs.serverUrl(), prefs.schoolId(), staffId, b64);
                    if (!XanderApi.ok(json)) {
                        serverMsg = XanderApi.message(json, "School photo not uploaded");
                    } else {
                        prefs.setWebFaceFlag(staffId, true);
                    }
                } catch (Exception net) {
                    serverMsg = KioskLog.friendlyNet(net);
                    KioskLog.e("Enroll", "upload: " + serverMsg);
                }
                final String extra = serverMsg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        capturing = false;
                        KioskSound.ok(KioskActivity.this);
                        String msg = "3 live faces saved for " + staffName;
                        if (extra != null && extra.length() > 0) {
                            msg = msg + " — " + extra;
                        }
                        Toast.makeText(KioskActivity.this, msg, Toast.LENGTH_LONG).show();
                        leaveEnrollMode();
                    }
                });
            }
        }).start();
    }

    private void showResult(JSONObject json) {
        boolean ok = XanderApi.ok(json);
        JSONObject person = json.optJSONObject("person");
        String kind = json.optString("kind", "");
        String status = json.optString("status", "");
        String name = person == null ? "" : person.optString("name", "");
        String extra = "";
        if ("student".equals(kind) && person != null) {
            extra = person.optString("regno", "") + "  " + person.optString("class", "");
        } else if ("staff".equals(kind) && person != null) {
            extra = person.optString("post", "");
            JSONObject shift = json.optJSONObject("shift");
            if (shift != null) {
                extra = extra + "  ·  " + shift.optString("title", "") + " " + shift.optString("hours", "");
            }
            JSONObject verdict = json.optJSONObject("verdict");
            if (verdict != null && verdict.optString("label", "").length() > 0) {
                extra = extra + "  ·  " + verdict.optString("label", "");
            }
        }
        if (json.optInt("offline", 0) == 1 && extra.length() == 0 && person != null) {
            extra = person.optString("extra", "");
        }
        tvName.setText(name.length() == 0 ? XanderApi.message(json, "Unknown") : name);
        String msg = XanderApi.message(json, "");
        tvMeta.setText(extra.length() == 0 ? msg : extra);
        tvTime.setText(json.optString("time", ""));
        if (ok && "IN".equalsIgnoreCase(status)) {
            tvStatus.setText("WELCOME");
            resultCard.setBackgroundColor(0xE016A34A);
        } else if (ok && "OUT".equalsIgnoreCase(status)) {
            tvStatus.setText("GOODBYE");
            resultCard.setBackgroundColor(0xE0B91C1C);
            if (msg.length() > 0) {
                tvMeta.setText(msg + (extra.length() > 0 ? "  ·  " + extra : ""));
            }
        } else {
            tvStatus.setText("HOLD");
            resultCard.setBackgroundColor(0xE07F1D1D);
            tvMeta.setText(msg.length() == 0 ? "Not accepted" : msg);
        }
        resultCard.setVisibility(View.VISIBLE);
        loadPhoto(person == null ? "" : person.optString("photo", ""));
        tvHint.setText(ok ? (msg.length() > 0 ? msg : (("student".equals(kind) ? "Student " : "Staff ") + status)) : (msg.length() == 0 ? "Try again" : msg));
        if (ok) {
            DeviceLights.pass(this);
            KioskSound.ok(this);
        } else {
            DeviceLights.fail(this);
            KioskSound.fail(this);
        }
        handler.removeCallbacks(hideResult);
        handler.postDelayed(hideResult, 2200);
    }

    private void loadPhoto(final String url) {
        imgPhoto.setImageBitmap(null);
        if (url == null || url.length() < 8) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bmp = BitmapFactory.decodeStream(XanderApi.openStream(url));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (bmp != null) {
                                imgPhoto.setImageBitmap(bmp);
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private final Runnable hideResult = new Runnable() {
        @Override
        public void run() {
            resultCard.setVisibility(View.GONE);
            DeviceLights.idle(KioskActivity.this);
            if (faceTracker != null) {
                faceTracker.clear();
            }
            if (faceGuide != null && !enrollMode) {
                faceGuide.setState(FaceGuideView.IDLE);
            }
            if (!enrollMode) {
                tvHint.setText("Staff: look at the camera\nStudents: tap your card");
            }
            unlockPresence.run();
            focusWedge();
        }
    };

    private void applyBoard() {
        DeviceLights.on(this);
        DeviceLights.fixCameras(this, prefs.rgbCameraId(), prefs.rgbCameraId(), 90);
        DeviceLights.hideSystemChrome(this);
    }

    private void applyPreviewRotation() {
        if (surfaceRgb == null) {
            return;
        }
        surfaceRgb.setRotation(0);
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
    }

    private void fail(String msg) {
        tvStatus.setText("HOLD");
        tvName.setText(msg);
        tvMeta.setText("");
        tvTime.setText("");
        imgPhoto.setImageBitmap(null);
        resultCard.setBackgroundColor(0xE07F1D1D);
        resultCard.setVisibility(View.VISIBLE);
        DeviceLights.fail(this);
        KioskSound.fail(this);
        if (faceTracker != null && msg != null && msg.toLowerCase(Locale.US).contains("photo")) {
            faceTracker.setFaces(new android.graphics.Rect[]{new android.graphics.Rect(80, 160, 640, 900)}, 720, 1280, true, true);
        }
        handler.removeCallbacks(hideResult);
        handler.postDelayed(hideResult, 3500);
    }

    private void showEngineError(final String msg) {
        final String text = (msg == null || msg.trim().length() == 0)
                ? "Face camera failed. Card tap still works."
                : msg;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvNfc != null) {
                    tvNfc.setText(text);
                    tvNfc.setTextColor(text.toLowerCase(Locale.US).contains("ok:") || text.toLowerCase(Locale.US).contains("card tap")
                            ? 0xFFA3E635 : 0xFFFACC15);
                }
                if (tvHint != null && !enrollMode) {
                    tvHint.setText("Staff: look at the camera\nStudents: tap your card");
                }
            }
        });
    }

    private void updateFaceGuide(android.graphics.Rect[] boxes, int imgW, int imgH, boolean spoof) {
        if (faceGuide == null) {
            return;
        }
        if (spoof || (enrollMode && lastSpoof)) {
            faceGuide.setState(FaceGuideView.BAD);
            enrollFullSince = 0;
            updateCaptureReady(false);
            return;
        }
        android.graphics.Rect best = null;
        int bestArea = 0;
        if (boxes != null) {
            for (int i = 0; i < boxes.length; i++) {
                android.graphics.Rect r = boxes[i];
                if (r == null) {
                    continue;
                }
                int a = Math.abs(r.width() * r.height());
                if (a > bestArea) {
                    bestArea = a;
                    best = r;
                }
            }
        }
        if (best == null || imgH <= 0) {
            if (enrollMode && lastFace != null) {
                best = new android.graphics.Rect(0, 0, 1, imgH);
            } else {
                faceGuide.setState(FaceGuideView.IDLE);
                enrollFullSince = 0;
                updateCaptureReady(false);
                return;
            }
        }
        float hRatio = Math.abs(best.height()) / (float) Math.max(1, imgH);
        boolean full = !enrollMode ? hRatio >= 0.28f : hRatio >= 0.12f;
        if (enrollMode) {
            if (lastSpoof) {
                faceGuide.setState(FaceGuideView.BAD);
                updateCaptureReady(false);
                return;
            }
            boolean wasFull = faceGuide.getState() == FaceGuideView.FULL;
            faceGuide.setState(FaceGuideView.FULL);
            faceGuide.setLabel(enrollGot >= ENROLL_NEED ? "Saved" : ("Capturing " + (enrollGot + 1) + " / " + ENROLL_NEED));
            if (!wasFull) {
                KioskSound.tick(this);
                enrollFullSince = System.currentTimeMillis();
            }
            updateCaptureReady(true);
            long now = System.currentTimeMillis();
            if (!capturing && enrollGot < ENROLL_NEED
                    && now - enrollFullSince >= 350
                    && now - lastShotAt >= 400) {
                captureEnroll();
            }
            return;
        }
        if (full) {
            boolean wasFull = faceGuide.getState() == FaceGuideView.FULL;
            faceGuide.setState(FaceGuideView.FULL);
            if (!wasFull) {
                KioskSound.tick(this);
                enrollFullSince = System.currentTimeMillis();
            }
            updateCaptureReady(true);
            long now = System.currentTimeMillis();
            if (enrollMode && !capturing && enrollGot < ENROLL_NEED
                    && enrollFullSince > 0 && now - enrollFullSince >= 700
                    && now - lastShotAt >= 900) {
                captureEnroll();
            }
        } else {
            enrollFullSince = 0;
            faceGuide.setState(FaceGuideView.FACE);
            updateCaptureReady(false);
        }
    }

    private void updateCaptureReady(boolean ready) {
        if (!enrollMode || btCapture == null || capturing) {
            return;
        }
        btCapture.setEnabled(true);
        btCapture.setAlpha(ready ? 1f : 0.7f);
        btCapture.setText(ready
                ? ("AUTO " + (enrollGot + 1) + " / " + ENROLL_NEED)
                : "LOOK AT THE CAMERA");
    }

    private void updateEnrollProgress() {
        if (faceGuide != null) {
            faceGuide.setProgress(enrollGot, ENROLL_NEED);
        }
        if (tvEnrollProgress != null) {
            String live = lastLive ? "3D LIVE" : "waiting 3D";
            tvEnrollProgress.setText(live + "   auto-capture  " + enrollGot + " / " + ENROLL_NEED);
        }
        if (btCapture != null && !capturing) {
            updateCaptureReady(faceGuide != null && faceGuide.getState() == FaceGuideView.FULL && lastLive);
        }
    }

    private void openSettings() {
        startActivity(new Intent(KioskActivity.this, SettingsActivity.class));
    }

    private void applyEnrollIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (intent.getBooleanExtra("attendance", false)
                || "attendance".equals(intent.getStringExtra("mode"))) {
            enterAttendanceMode();
            return;
        }
        int id = intent.getIntExtra("enroll_staff_id", 0);
        if (id <= 0) {
            return;
        }
        enrollStaffId = id;
        enrollStaffName = intent.getStringExtra("enroll_staff_name");
        if (enrollStaffName == null) {
            enrollStaffName = "";
        }
        pendingPhotoUrl = intent.getStringExtra("enroll_photo_url");
        enrollMode = pendingPhotoUrl == null || pendingPhotoUrl.length() < 8;
        KioskLog.i("Enroll", "start " + enrollStaffName + " id=" + enrollStaffId
                + (enrollMode ? " LIVE 3-shot" : " school photo"));
        refreshEnrollUi();
        if (pendingPhotoUrl != null && pendingPhotoUrl.length() >= 8) {
            runPendingPhotoEnroll();
        }
    }

    private void refreshEnrollUi() {
        if (tvHint == null) {
            return;
        }
        capturing = false;
        enrollFullSince = 0;
        lastShotAt = 0;
        enrollGot = 0;
        lastLive = false;
        lastSpoof = false;
        lastLiveScore = 0f;
        handler.removeCallbacks(enrollPulse);
        handler.removeCallbacks(faceGone);
        presentStaffId = 0;
        presentDidIn = false;
        presentSeenAt = 0;
        faceInFrame = false;
        if (faceEngine != null) {
            faceEngine.setSearchEnabled(!enrollMode);
        }
        if (enrollMode) {
            enrollStarted = System.currentTimeMillis();
            handler.postDelayed(enrollPulse, 400);
            tvHint.setText("Look at the camera — 3 quick live shots");
            if (tvLocation != null) {
                tvLocation.setText("Staff face enrollment");
            }
            if (tvEnrollName != null) {
                tvEnrollName.setText(enrollStaffName);
            }
            if (enrollBar != null) {
                enrollBar.setVisibility(View.VISIBLE);
                enrollBar.bringToFront();
            }
            if (clockBar != null) {
                clockBar.setVisibility(View.GONE);
            }
            if (btCapture != null) {
                btCapture.setVisibility(View.VISIBLE);
                btCapture.setEnabled(true);
                btCapture.setAlpha(1f);
                btCapture.setText("LOOK AT THE CAMERA");
                btCapture.bringToFront();
            }
            if (btSettings != null) {
                btSettings.setVisibility(View.GONE);
            }
            if (tvCardDebug != null) {
                tvCardDebug.setVisibility(View.GONE);
            }
            if (tvNfc != null) {
                tvNfc.setVisibility(View.GONE);
            }
            if (faceTracker != null) {
                faceTracker.setVisibility(View.GONE);
            }
            if (faceGuide != null) {
                faceGuide.setVisibility(View.VISIBLE);
                faceGuide.bringToFront();
                faceGuide.setEnroll(true);
                faceGuide.setProgress(0, ENROLL_NEED);
                faceGuide.setState(FaceGuideView.IDLE);
            }
            if (enrollBar != null) {
                enrollBar.bringToFront();
            }
            if (btCapture != null) {
                btCapture.bringToFront();
            }
            updateEnrollProgress();
            stopCardReaders();
        } else {
            String loc = prefs.areaName();
            if (tvLocation != null) {
                tvLocation.setText(loc.length() == 0 ? "Set student location in Settings" : loc);
            }
            tvHint.setText("Staff: look at the camera\nStudents: tap your card");
            if (enrollBar != null) {
                enrollBar.setVisibility(View.GONE);
            }
            if (clockBar != null) {
                clockBar.setVisibility(View.VISIBLE);
            }
            if (btCapture != null) {
                btCapture.setVisibility(View.GONE);
            }
            if (btSettings != null) {
                btSettings.setVisibility(View.VISIBLE);
                btSettings.bringToFront();
            }
            if (tvCardDebug != null) {
                tvCardDebug.setVisibility(View.GONE);
            }
            if (tvNfc != null) {
                tvNfc.setVisibility(View.VISIBLE);
            }
            if (faceTracker != null) {
                faceTracker.setVisibility(View.VISIBLE);
            }
            if (faceGuide != null) {
                faceGuide.setVisibility(View.VISIBLE);
                faceGuide.setEnroll(false);
                faceGuide.setState(FaceGuideView.IDLE);
            }
        }
    }

    private void enterAttendanceMode() {
        enrollMode = false;
        enrollStaffId = 0;
        enrollStaffName = "";
        pendingPhotoUrl = null;
        capturing = false;
        refreshEnrollUi();
        startCardReader();
        resumeAttendanceScan();
    }

    private void resumeAttendanceScan() {
        if (enrollMode || faceEngine == null) {
            return;
        }
        paused = false;
        faceEngine.resumeMatching();
        faceEngine.setSearchEnabled(true);
        faceEngine.rebindInBackground();
        openCameras();
        handler.removeCallbacks(retryCameras);
        handler.postDelayed(retryCameras, 500);
    }

    private void leaveEnrollMode() {
        enrollMode = false;
        enrollStaffId = 0;
        enrollStaffName = "";
        pendingPhotoUrl = null;
        capturing = false;
        if (faceEngine != null) {
            faceEngine.resumeMatching();
            faceEngine.setSearchEnabled(true);
        }
        refreshEnrollUi();
        startCardReader();
        startActivity(new Intent(this, StaffEnrollActivity.class));
    }

    private void stopCardReaders() {
        if (cardReader != null) {
            cardReader.stop();
        }
        if (dkUsb != null) {
            dkUsb.stop();
        }
        if (usbHid != null) {
            usbHid.stop();
        }
    }

    private void runPendingPhotoEnroll() {
        final int staffId = enrollStaffId;
        final String photo = pendingPhotoUrl;
        final String name = enrollStaffName;
        if (staffId <= 0 || photo == null || photo.length() < 8) {
            return;
        }
        if (faceEngine == null || !faceEngine.isReady()) {
            return;
        }
        pendingPhotoUrl = null;
        enrollMode = false;
        refreshEnrollUi();
        Toast.makeText(this, "Enrolling school photo for " + name + "…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bmp = BitmapFactory.decodeStream(XanderApi.openStream(photo));
                    if (bmp == null) {
                        throw new Exception("Could not read the school photo");
                    }
                    String err = faceEngine.enrollStaff(staffId, bmp);
                    if (err != null) {
                        throw new Exception(err);
                    }
                    XanderApi.enrollFace(prefs.serverUrl(), prefs.schoolId(), staffId, null);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(KioskActivity.this, "Face saved for " + name, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String m = e.getMessage();
                            Toast.makeText(KioskActivity.this,
                                    m == null ? "Photo enroll failed" : m, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void startCardReader() {
        if (enrollMode) {
            return;
        }
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
        dkUsb.start(this, listen);
        if (usbHid == null) {
            usbHid = new UsbHidCardReader(this);
        }
        usbHid.start(listen);
    }

    private void beep() {
        KioskSound.ok(this);
    }

    private void backgroundSync() {
        if (enrollMode) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    store.flush();
                    JSONObject json = XanderApi.sync(prefs.serverUrl(), prefs.schoolId());
                    if (XanderApi.ok(json)) {
                        prefs.saveBootstrap(json);
                        store.replacePeople(json.optJSONArray("students"), json.optJSONArray("staff"));
                        applyRemoteFaceFlags(json.optJSONArray("staff"));
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (enrollMode || event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || event.getUnicodeChar() == '\n' || event.getUnicodeChar() == '\r') {
            handler.removeCallbacks(hidFlush);
            String uid = CardReader.clean(keyBuf.toString());
            keyBuf.setLength(0);
            if (etWedge != null) {
                etWedge.setText("");
            }
            if (uid.length() >= 4) {
                if (cardReader != null) {
                    cardReader.ingestHid(uid);
                } else {
                    onCard(uid);
                }
                return true;
            }
        } else {
            int ch = event.getUnicodeChar();
            if ((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')) {
                keyBuf.append((char) ch);
                if (keyBuf.length() > 24) {
                    keyBuf.setLength(0);
                }
                handler.removeCallbacks(hidFlush);
                handler.postDelayed(hidFlush, 400);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyEnrollIntent(intent);
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
        onCard(bytesToHex(tag.getId()));
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
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
            updateNfcLabel();
            return;
        }
        try {
            nfcAdapter.enableReaderMode(this, nfcReader,
                    NfcAdapter.FLAG_READER_NFC_A
                            | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_NFC_F
                            | NfcAdapter.FLAG_READER_NFC_V
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null);
            nfcReaderOn = true;
        } catch (Exception e) {
            nfcReaderOn = false;
        }
        try {
            nfcAdapter.enableForegroundDispatch(this, nfcPending, nfcFilters, null);
        } catch (Exception ignored) {
        }
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
        tvNfc.setText("No Android NFC — Wiegand at the bottom");
        tvNfc.setTextColor(0xFFA3E635);
    }

    private void applyRemoteFaceFlags(JSONArray staff) {
        if (staff == null) {
            return;
        }
        for (int i = 0; i < staff.length(); i++) {
            JSONObject o = staff.optJSONObject(i);
            if (o == null) {
                continue;
            }
            int id = o.optInt("id", 0);
            if (id <= 0) {
                continue;
            }
            int remote = o.optInt("face_enrolled", 0);
            boolean hadWeb = prefs.webFaceWasEnrolled(id);
            prefs.setWebFaceFlag(id, remote == 1);
            if (hadWeb && remote == 0) {
                if (faceEngine != null) {
                    faceEngine.removeStaffFace(id);
                } else {
                    prefs.clearFaceTokens(id);
                }
            }
        }
    }

    private void enrollStaffPhotos() {
        if (enrollMode || faceEngine == null || !faceEngine.isReady()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONArray staff = store.listStaff();
                for (int i = 0; i < staff.length(); i++) {
                    JSONObject o = staff.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    int id = o.optInt("id", 0);
                    String photo = o.optString("photo", "");
                    if (id <= 0 || prefs.hasFaceToken(id) || photo.length() < 8 || photo.contains("fallback-avatar")) {
                        continue;
                    }
                    try {
                        Bitmap bmp = BitmapFactory.decodeStream(XanderApi.openStream(photo));
                        if (bmp != null) {
                            faceEngine.enrollStaff(id, bmp);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    private void focusWedge() {
        if (etWedge != null && !enrollMode) {
            etWedge.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        applyPreviewRotation();
        if (!enrollMode) {
            String loc = prefs.areaName();
            tvLocation.setText(loc.length() == 0 ? "Set student location in Settings" : loc);
            tvSchool.setText(prefs.schoolName());
        }
        DeviceLights.on(this);
        DeviceLights.hideSystemChrome(this);
        if (!enrollMode) {
            resumeAttendanceScan();
        } else if (faceEngine != null) {
            faceEngine.setSearchEnabled(false);
        }
        startCardReader();
        KioskSound.prepare(this);
        enableNfc();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                enableNfc();
                focusWedge();
            }
        }, 1200);
        focusWedge();
    }

    @Override
    public void onBackPressed() {
        if (enrollMode) {
            leaveEnrollMode();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
        handler.removeCallbacks(retryCameras);
        if (faceEngine != null) {
            faceEngine.stopCameras();
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
            dkUsb = null;
        }
        if (usbHid != null) {
            usbHid.stop();
        }
        if (usb != null) {
            usb.stop();
        }
        if (faceEngine != null) {
            faceEngine.release();
        }
        if (liveEngine == faceEngine) {
            liveEngine = null;
        }
        if (tone != null) {
            tone.release();
        }
        super.onDestroy();
    }
}
