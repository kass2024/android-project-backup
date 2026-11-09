package com.qonics.somanet_attendance;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.fgtit.fpcore.FPMatch;
import com.qonics.app.ActivityList;
import com.qonics.app.Fingerprint;
import com.qonics.app.LocatorServer;
import com.qonics.app.LogsList;
import com.qonics.app.SocketServer;
import com.qonics.app.StudentsList;
import com.qonics.app.UserItem;
import com.qonics.app.UsersList;
import com.qonics.utils.DatabaseHandler;
import com.qonics.utils.ExtApi;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class InOutAttendanceActivity extends Activity {
    final static public boolean ALPS_MODEL = false;
    final static public String serverIp = "41.220.236.45";
    //    final static public String serverIp = "192.168.8.10";
//    final static public String serverIp = "jofarschool.com";
//    final static public String SERVER = "http://" + serverIp + "/somanet/public/api/";
    final static public String SERVER = "http://" + serverIp + "/api/";
    final static public String SK_ID = "H_SK_ID"; //schoolID
    final static public String SK_NAME = "H_SK_NAME";
    public static final String UPDATE_VER_ST = "SOMA_UV_ST";
    public static final String UPDATE_VER_STAFF = "SOMA_UV_SF";
    private static final boolean AUTO_HIDE = true;
    public static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    public static final int AUTO_IMAGE_DELAY_MILLIS = 10;

    private View topLineLayout;

    private Timer picTimer;
    private TimerTask picTask;
    private Handler picHandler;
    private int ipicIndex = 0;
    private int ipicCount = 0;
    private int iShowCount = 0;
    private boolean bLoadExt = false;
    private ArrayList<String> picList = new ArrayList<String>();

    private DialogResult fpDialog = null;
    private Timer AutoCloseTimer;
    private TimerTask AutoCloseTask;
    private Handler AutoCloseHandler;
    private int AutoCloseCount = 0;
    private PowerManager.WakeLock wakeLock;
    private TextView tvDate, tvTime,txtname,txttype,txtother;
    private int mDay = -1;

    private Timer startTimer;
    private TimerTask startTask;
    private Handler startHandler;
    private Bitmap fpImage;
    private TextView tvFpStatus;

    private SoundPool soundPool;
    private int soundId;
    private int soundThank;
    private int soundDenied;
    private boolean soundflag = false;
    Timer sync_timer;

    private Handler handler = new Handler();

    //NFC
    private NfcAdapter nfcAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;

    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    TextView txtinfo;
    SQLiteDatabase db;
    private SharedPreferences preferences;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            android.app.ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                //actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
            topLineLayout.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private SyncReceiver syncReceiver;

    @SuppressLint("InvalidWakeLockTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
/*        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
        this.getWindow().setFlags(FLAG_HOMEKEY_DISPATCHED, FLAG_HOMEKEY_DISPATCHED);*/
        setContentView(R.layout.activity_main);
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        db = this.openOrCreateDatabase("somanet_attendance_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean("FIRST_RUN",true)){
            startActivity(new Intent(this,SchoolActivity.class));
            finish();
            return;
        }
        if (preferences.getInt("attendance_type",0)==1){
            //face attendance
//            startActivity(new Intent(this, DetectorActivity.class));
//            finish();
//            return;
        }
        mVisible = true;
        txtinfo = findViewById(R.id.txtinfo);
        txtname = findViewById(R.id.txtname);
        txttype = findViewById(R.id.txttype);
        txtother = findViewById(R.id.txtother);
        mControlsView = findViewById(R.id.lnlstaff);
        mContentView = findViewById(R.id.fullscreen_content);
        topLineLayout = findViewById(R.id.topLineLayout);
        txtinfo.setVisibility(View.VISIBLE);
        mControlsView.setVisibility(View.GONE);
//
//        imageSwitcher = findViewById(R.id.imageSwitcher);

        tvDate = findViewById(R.id.textView1);
        tvTime = findViewById(R.id.textView2);
        tvFpStatus = findViewById(R.id.tvFpStatus);
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mControlsView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        ShowDateTime();

        // Set up the user interaction to manually show or hide the system UI.
//        mContentView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                toggle();
//            }
//        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        //findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
//        findViewById(R.id.dummy_button).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                //ActivityList.getInstance().AutoResultStart();
//                //StatusBarDisable(false);
//                Fingerprint.getInstance().Cancel();
//                Fingerprint.getInstance().setHandler(null);
//
//                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
//                startActivityForResult(intent, 0);
//                overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
//            }
//        });

        txtinfo.setOnLongClickListener(view -> {
            Intent intent = new Intent(InOutAttendanceActivity.this, LoginActivity.class);
            startActivityForResult(intent, 0);
            overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
            return true;
        });
//        ActivityList.getInstance().setMainContext(this);
//        ActivityList.getInstance().CreateDir();
//        ActivityList.getInstance().LoadConfig();
//        ActivityList.getInstance().SetAutoResult();
        UsersList.getInstance().LoadAll(this);
        StudentsList.getInstance().LoadAll(this);
        LogsList.getInstance().Init(this);


        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "sc");
        wakeLock.acquire();

//        FPMatch.getInstance().InitMatch();

        InitReadCard();
//        Fingerprint.getInstance().setContext(this);
//        Fingerprint.getInstance().setHandler(fingerprintHandler);
//        Fingerprint.getInstance().Open();
//        Fingerprint.getInstance().Process();

        soundPool = new SoundPool(2, AudioManager.STREAM_NOTIFICATION, 0);
        soundId = soundPool.load(this, R.raw.dong, 1);
        soundThank = soundPool.load(this, R.raw.thank, 1);
        soundDenied = soundPool.load(this, R.raw.access, 1);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                soundflag = true;
            }
        });

//        SocketServer.getInstance().setContext(this);
//        LocatorServer.getInstance().setContext(this);
//        LocatorServer.getInstance().setHandler(handler);
//        LocatorServer.getInstance().startUdpServer();
        //     StatusBarDisable(true);
        //      setNavigationBarState(true);
/*        IntentFilter filter = new IntentFilter();
        filter.addAction(ETHERNET_IFACE_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(ETHERNETReceiver,filter);*/
//        ActivityList.getInstance().addActivity(this);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            wakeLock.release();
            soundPool.release();
            soundPool = null;
//            Fingerprint.getInstance().Close();
//            ActivityList.getInstance().unRegisterReceiver();
        } catch (Exception e) {
//            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(syncReceiver);
        if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
        sync_timer.purge();
        sync_timer.cancel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncReceiver = new SyncReceiver();
        registerReceiver(syncReceiver,new IntentFilter("sync.qonics.com.somanet"));
        if (nfcAdapter != null)
            nfcAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, null);
        force_sync();

    }

    public void force_sync(){
        sync_timer = new Timer();
        sync_timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendBroadcast(new Intent("sync.qonics.com.somanet"));//send force sync receiver
            }
        },2000,10*60*1000);
    }
    public void InitReadCard() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "Device does not support NFC!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            Toast.makeText(this, "Enable the NFC function in the system settings!",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        mFilters = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)};
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processIntent(intent);
    }

    private void processIntent(Intent intent) {
        String cardsn = bytesToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));
        Log.e("card sn",cardsn);
        UserItem ui = StudentsList.getInstance().FindUserItemByCard(cardsn);
        if (ui != null) {
            fpImage = BitmapFactory.decodeResource(getResources(), R.drawable.rfid);

            List<String> mData = new ArrayList<String>();
            mData.add(ui.name);
            mData.add(String.valueOf(ui.userid));
            mData.add(ui.usertype);
            LogsList.getInstance().Append(ui.userid, 0);

//            ActivityList.getInstance().OpenDoor();

            ShowStudentInfo(ui.name, ui.usertype, fpImage, mData);
        } else {
            fpImage = BitmapFactory.decodeResource(getResources(), R.drawable.rfid);
            ShowStudentError();
        }
    }
    private String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }

        char[] buffer = new char[2];
        for (int i = 0; i < src.length; i++) {
            buffer[0] = Character.forDigit((src[i] >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(src[i] & 0x0F, 16);
            System.out.println(buffer);
            stringBuilder.append(buffer);
        }
//        stringBuilder.append("000");
        return stringBuilder.toString();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            //Fingerprint.getInstance().Close();
            finish();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_HOME) {

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void ShowDateTime() {
        /*
        Time t=new Time(); // or Time t=new Time("GMT+8");
        t.setToNow();
        int year = t.year;
        int month = t.month+1;
        int date = t.monthDay;
        int hour = t.hour; // 0-23
        int minute = t.minute;
        */
        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getDefault());
        int day = c.get(Calendar.DAY_OF_MONTH);
        if (mDay != day) {
            mDay = day;
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH) + 1;
            int weekd = c.get(Calendar.DAY_OF_WEEK);
            int weeky = c.get(Calendar.WEEK_OF_YEAR) + 1;
            String mWay = "";
            switch (weekd) {
                case 1:
                    mWay = getString(R.string.txt_week1);
                    break;
                case 2:
                    mWay = getString(R.string.txt_week2);
                    break;
                case 3:
                    mWay = getString(R.string.txt_week3);
                    break;
                case 4:
                    mWay = getString(R.string.txt_week4);
                    break;
                case 5:
                    mWay = getString(R.string.txt_week5);
                    break;
                case 6:
                    mWay = getString(R.string.txt_week6);
                    break;
                case 7:
                    mWay = getString(R.string.txt_week7);
                    break;
            }
            tvDate.setText(String.format("%d-%02d-%02d %s %02d", year, month, day, mWay, weeky));
        }
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        tvTime.setText(String.format("%02d : %02d", hour, minute));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
            delayedHide(AUTO_HIDE_DELAY_MILLIS);
        }
    }

    private void hide() {
        // Hide UI first
        android.app.ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        txtinfo.setText("Welcome");
        txtinfo.setVisibility(View.VISIBLE);
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        txtinfo.setText("Please wait...");
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private void ShowStaffInfo(String title, String message, Bitmap image, List<String> listdata) {
        soundPool.play(soundThank, 1.0f, 1.0f, 1, 0, 1.0f);
        mVisible = true;
        txtinfo.setVisibility(View.GONE);
        txtname.setText(title);
        txtother.setText("Function: "+message);
        txttype.setText("Type: Staff");
        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
//        AutoCloseCount = 0;
//        AutoCloseStart();
    }
    private void ShowStaffError() {
        soundPool.play(soundDenied, 1.0f, 1.0f, 1, 0, 1.0f);
        txtinfo.setVisibility(View.VISIBLE);
        txtinfo.setText("Staff not found");
    }
    private void ShowStudentInfo(String title, String message, Bitmap image, List<String> listdata) {
        soundPool.play(soundThank, 1.0f, 1.0f, 1, 0, 1.0f);
        mVisible = true;
        txtinfo.setVisibility(View.GONE);
        txtname.setText(title);
        txtother.setText("Class: "+message);
        txttype.setText("Type: Student");
        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
//        AutoCloseCount = 0;
//        AutoCloseStart();
        delayedHide(AUTO_HIDE_DELAY_MILLIS);
    }
    private void ShowStudentError() {
        soundPool.play(soundDenied, 1.0f, 1.0f, 1, 0, 1.0f);
        txtinfo.setVisibility(View.VISIBLE);
        txtinfo.setText("Student not found");
        delayedHide(AUTO_HIDE_DELAY_MILLIS);
    }
    @SuppressLint("HandlerLeak")
    public void AutoCloseStart() {
        if (AutoCloseTimer != null) return;
        AutoCloseTimer = new Timer();
        AutoCloseHandler = new Handler() {
            @SuppressLint("HandlerLeak")
            @Override
            public void handleMessage(Message msg) {
                AutoCloseCount++;
                if (AutoCloseCount >= 3) {
                    AutoCloseCount = 0;
                    AutoCloseStop();
                    if (fpDialog != null) {
                        fpDialog.dismiss();
                        fpDialog = null;
                    }
                }
                super.handleMessage(msg);
            }
        };
        AutoCloseTask = new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                AutoCloseHandler.sendMessage(message);
            }
        };
        AutoCloseTimer.schedule(AutoCloseTask, 1000, 1000);
    }

    public void AutoCloseStop() {
        if (AutoCloseTimer != null) {
            AutoCloseTimer.cancel();
            AutoCloseTimer = null;
            AutoCloseTask.cancel();
            AutoCloseTask = null;
        }
    }

    @SuppressLint("HandlerLeak")
    public void TimerStart() {
        if (startTimer == null) {
            startTimer = new Timer();
            startHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    TimeStop();
                    Fingerprint.getInstance().Process();
                }
            };
            startTask = new TimerTask() {
                @Override
                public void run() {
                    Message message = new Message();
                    message.what = 1;
                    startHandler.sendMessage(message);
                }
            };
            startTimer.schedule(startTask, 1000, 1000);
        }
    }

    public void TimeStop() {
        if (startTimer != null) {
            startTimer.cancel();
            startTimer = null;
            startTask.cancel();
            startTask = null;
        }
    }


    private Bitmap Bytes2Bimap(byte[] b) {
        if (b.length != 0) {
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } else {
            return null;
        }
    }
}
