package com.qonics.somanet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.SoundPool;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class DailyAttendanceActivity extends AppCompatActivity {
    private static final String TAG = DailyAttendanceActivity.class.getSimpleName();
    SharedPreferences preferences;
    LinearLayout lnlinfo, lnlstudent;
    TextView txtstatus, txttime, txtclass, txtname, txtregno, txtmaintitle;
    ImageView imgclose, imgstudent;
    HttpRequest req;
    String server = MainActivity.SERVER;

    Timer timer;
    TimerTask timerTask;
    int who = 0; //0: default,1: permission,2: students,3: payment
    boolean SCAN_CARD = false;
    boolean pending = false;

    private final SimpleDateFormat _WatchTime = new SimpleDateFormat("HH:mm");
    private SoundPool soundPool;
    private int soundIdThank;
    private boolean soundflag = false;

    BroadcastReceiver _timeReceiver;
    private int currentApiVersion;
    private PowerManager.WakeLock wakeLock;
    private Timer clearTimer;
    NFCForegroundUtil nfcForegroundUtil = null;
    private NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        currentApiVersion = Build.VERSION.SDK_INT;
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        if (currentApiVersion >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(flags);
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(flags);
                }
            });
        }

        setContentView(R.layout.activity_daily_attendance);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "SOMANET:wakelock.");
        wakeLock.acquire();

        // ✅ Only NFC initialization (Speedata removed)
        Log.e(TAG, "init NFC mode only");
        nfcForegroundUtil = new NFCForegroundUtil(this);
        nfcForegroundUtil.setTagListener(tag -> {
            if (tag == null) return;
            String cardId = NFCForegroundUtil.bytesToHex(tag.getId());
            Log.e("NFC", "STRING TAG ID: " + cardId);
            if (!pending) {
                searchStudent(cardId);
            }
        });
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, getString(R.string.dai_allowed), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            Toast.makeText(this, getString(R.string.dai_enable), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        who = getIntent().getIntExtra("who", 0);
        soundPool = new SoundPool(20, AudioManager.STREAM_MUSIC, 0);
        soundIdThank = soundPool.load(this, R.raw.dong, 1);
        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> soundflag = true);

        populateView();
    }

    private void populateView() {
        txttime = findViewById(R.id.txttime);
        txtclass = findViewById(R.id.txtclass);
        txtmaintitle = findViewById(R.id.txtmaintitle);
        txtname = findViewById(R.id.txtname);
        txtstatus = findViewById(R.id.txtstatus);
        txtregno = findViewById(R.id.txtregno);
        lnlstudent = findViewById(R.id.lnlstudent);
        lnlinfo = findViewById(R.id.lnlinfo);
        imgstudent = findViewById(R.id.imgstudent);
        imgclose = findViewById(R.id.imgclose);

        lnlstudent.setVisibility(View.GONE);
        lnlinfo.setVisibility(View.VISIBLE);
        txtstatus.setText(getString(R.string.tap_card));
        txttime.setText(_WatchTime.format(new Date()));

        imgclose.setOnClickListener(view -> {
            lnlinfo.setVisibility(View.VISIBLE);
            lnlstudent.setVisibility(View.GONE);
            txtstatus.setText(getString(R.string.tap_card));
        });

        SCAN_CARD = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        _timeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_TIME_TICK.equals(intent.getAction())) {
                    txttime.setText(_WatchTime.format(new Date()));
                }
            }
        };
        ContextCompat.registerReceiver(
                this,
                _timeReceiver,
                new IntentFilter(Intent.ACTION_TIME_TICK),
                ContextCompat.RECEIVER_EXPORTED
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        SCAN_CARD = true;
        wakeLock.acquire();

        if (nfcForegroundUtil != null) {
            nfcForegroundUtil.enableForeground();
            if (!nfcForegroundUtil.getNfc().isEnabled()) {
                Toast.makeText(getApplicationContext(),
                        getString(R.string.activate_nfc),
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (_timeReceiver != null) {
            unregisterReceiver(_timeReceiver);
        }
        wakeLock.release();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SCAN_CARD = false;
        wakeLock.release();
        if (nfcAdapter != null && nfcForegroundUtil != null) {
            nfcForegroundUtil.disableForeground();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (timer != null) {
                timer.purge();
                timer.cancel();
            }
        } catch (NullPointerException ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onNewIntent(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null) {
            String cardId = bytesToHexString(tag.getId());
            Log.e("NFC", "STRING TAG ID: " + cardId);
            if (!pending) {
                searchStudent(cardId);
            }
        }
    }

    private String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) return null;

        char[] buffer = new char[2];
        for (byte b : src) {
            buffer[0] = Character.forDigit((b >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(b & 0x0F, 16);
            stringBuilder.append(buffer);
        }
        return stringBuilder.toString().toUpperCase();
    }

    private void searchStudent(String card) {
        pending = true;
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.vibrate(200);

        req = new HttpRequest(this);
        Map<String, Object> map = new HashMap<>();
        txtstatus.setText(getString(R.string.please_wait));
        map.put("card", card);
        map.put("term", preferences.getInt("soma_term", 0));
        map.put("school", preferences.getInt(MainActivity.SK_ID, 0));

        req.post(server + "take_daily_attendance", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                pending = false;
                txtstatus.setText(getString(R.string.tap_card));
                if (object == null) {
                    Toast.makeText(DailyAttendanceActivity.this, status, Toast.LENGTH_LONG).show();
                } else {
                    try {
                        if (object.has("error")) {
                            txtstatus.setText(getString(R.string.lbl_error) + object.getString("error"));
                            start_timer();
                        } else if (object.has("success")) {
                            txtmaintitle.setVisibility(View.GONE);
                            lnlinfo.setVisibility(View.GONE);
                            lnlstudent.setVisibility(View.VISIBLE);
                            txtname.setText(object.getString("name"));
                            txtregno.setText(getString(R.string.lbl_regno) + object.getString("regno"));
                            txtclass.setText(getString(R.string.lbl_class) + object.getString("class"));

                            StudentPhotoHelper.load(imgstudent, object.getString("photo"), true);

                            if (soundflag) {
                                soundPool.play(soundIdThank, 1, 1, 0, 0, 1);
                            }
                            start_timer();
                        } else {
                            Toast.makeText(DailyAttendanceActivity.this,
                                    getString(R.string.system_error),
                                    Toast.LENGTH_LONG).show();
                            Log.e("ERROR", object.getString("message"));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void start_timer() {
        Handler handler = new Handler(msg -> {
            lnlinfo.setVisibility(View.VISIBLE);
            lnlstudent.setVisibility(View.GONE);
            txtmaintitle.setVisibility(View.VISIBLE);
            txtstatus.setText(getString(R.string.tap_card));
            return false;
        });

        clearTimer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                handler.sendEmptyMessage(0);
            }
        };
        clearTimer.schedule(task, 6000);
    }
}
