package com.qonics.somanet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.qonics.somanet.cashflow.CashFlowActivity;
import com.qonics.somanet.cashflow.ScanBridgeWatcher;
import com.qonics.somanet.teacher.MarksEntryActivity;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.MenuClearanceHelper;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;

import android.os.Handler;
import android.os.Looper;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class MainActivity extends AppCompatActivity {
    // Xander School / SmartSMS production (same server & credentials as the web app)
    /** Live web app: https://schoolmis.xanderglobalacademy.com/ */
    final static public String serverIp = "schoolmis.xanderglobalacademy.com";
    // final static public String serverIp = "iotxad.com";
    //final static public String serverIp = "mis.kass.co.rw";
    // final static public String serverIp = "41.220.236.45";
    // final static public String serverIp = "185.190.142.28";

    // ✅ API base URL — matches https://schoolmis.xanderglobalacademy.com/api/
    final static public String SERVER = "https://" + serverIp + "/api/";

    // ✅ SharedPreference keys
    final static public String SK_ID = "H_SK_ID"; // schoolID
    final static public String SK_PHONE = "H_SK_PHONE"; // phone
    final static public String SK_EMAIL = "H_SK_EMAIL"; // email
    final static public String SK_NAME = "H_SK_NAME";
    public static final String LOGIN = "S0M@L0iN";

    Button btnlogout;
    LinearLayout lnldiscipline, lnlvernacular, lnlclinic, lnlvisiting, lnlpermission, lnlstudents, lnlstaff, lnlattendance,
            lnldailyattendance, lnldormattendance, lnllibrary, lnlmarks, lnltransport, lnlleaves, lnlleavemanage, lnlpaymentdrc, lnlcashflow, lnlpocket, lnlstock, lnlassets;
    TextView txtinfo, txtSchoolName, txtSchoolMeta, txtSchoolAcademic;

    boolean SCAN_CARD = false;
    public int currentSystem = 0;
    SharedPreferences preferences;
    SweetAlertDialog sAlertDialog;

    private static final String TAG = "MainActivity";
    private FontChangeCrawler changeFont;
    private Timer timer;
    private final Handler scanPollHandler = new Handler(Looper.getMainLooper());
    private boolean scanPolling = false;
    private Runnable scanPollRunnable;
    private ObjectAnimator monitorPulse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            startService(new Intent(this, CoreService.class));
        } catch (Exception e) {
            Log.e(TAG, "CoreService start blocked: " + e.getMessage());
        }
        SyncReceiver.requestSync(this);
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7701);
        }

        // ✅ Change font
        changeFont = new FontChangeCrawler(getAssets());
        changeFont.replaceFonts(this.findViewById(android.R.id.content));

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        sAlertDialog = new SweetAlertDialog(this, SweetAlertDialog.CUSTOM_IMAGE_TYPE);

        Log.e(TAG, "MainActivity initialized (Speedata removed, NFC only)");

        timer = new Timer();
        populateView();
    }

    private void populateView() {
        txtinfo = findViewById(R.id.txtinfo);
        txtSchoolName = findViewById(R.id.txtSchoolName);
        txtSchoolMeta = findViewById(R.id.txtSchoolMeta);
        txtSchoolAcademic = findViewById(R.id.txtSchoolAcademic);
        btnlogout = findViewById(R.id.btnlogout);

        lnldiscipline = findViewById(R.id.lnldiscipline);
        lnlvernacular = findViewById(R.id.lnlvernacular);
        lnlclinic = findViewById(R.id.lnlclinic);
        lnlvisiting = findViewById(R.id.lnlvisiting);
        lnlpermission = findViewById(R.id.lnlpermission);
        lnlstudents = findViewById(R.id.lnlstudent);
        lnlstaff = findViewById(R.id.lnlstaff);
        lnlattendance = findViewById(R.id.lnlattendance);
        lnldailyattendance = findViewById(R.id.lnldailyattendance);
        lnldormattendance = findViewById(R.id.lnldormattendance);
        lnlpaymentdrc = findViewById(R.id.lnlpayment_drc);
        lnlmarks = findViewById(R.id.lnlmarks);
        lnllibrary = findViewById(R.id.lnllibrary);
        lnltransport = findViewById(R.id.lnltransport);
        lnlleaves = findViewById(R.id.lnlleaves);
        lnlleavemanage = findViewById(R.id.lnlleavemanage);
        lnlcashflow = findViewById(R.id.lnlcashflow);
        lnlpocket = findViewById(R.id.lnlpocket);
        lnlstock = findViewById(R.id.lnlstock);
        lnlassets = findViewById(R.id.lnlassets);

        bindSchoolHeader();
        txtinfo.setText(
                preferences.getString("soma_name", "") + "\n"
                        + preferences.getString("soma_post_title", "")
        );

        btnlogout.setOnClickListener(view -> {
            SharedPreferences.Editor ed = preferences.edit();
            MenuClearanceHelper.clear(ed);
            ed.remove("soma_id")
                    .remove("remember_me")
                    .remove("remember_date")
                    .remove(LOGIN)
                    .remove("soma_academic")
                    .apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        findViewById(R.id.lnlMonitor).setOnClickListener(view -> {
            if (!canOpenSchoolMonitor()) return;
            startActivity(new Intent(this, SchoolMonitorActivity.class));
        });
        findViewById(R.id.lnlLocationAttendance).setOnClickListener(view -> {
            if (!canOpenSchoolMonitor()) return;
            startActivity(new Intent(this, LocationAttendanceActivity.class));
        });

        lnldiscipline.setOnClickListener(view ->
                startActivity(new Intent(this, BehaviorMenuActivity.class)));

        if (lnlvernacular != null) {
            lnlvernacular.setOnClickListener(view ->
                    startActivity(new Intent(this, VernacularActivity.class)));
        }
        if (lnlclinic != null) {
            lnlclinic.setOnClickListener(view -> {
                if (!MenuClearanceHelper.canOpenClinic(preferences)) return;
                startActivity(new Intent(this, ClinicActivity.class));
            });
        }

        lnlvisiting.setOnClickListener(view ->
                startActivity(new Intent(this, VisitingActivity.class)));

        lnlpermission.setOnClickListener(view ->
                startActivity(new Intent(this, PermissionMenuActivity.class)));

        lnlstudents.setOnClickListener(view -> {
            Intent intent = new Intent(this, StudentsMenuActivity.class);
            startActivity(intent);
        });

        lnlstaff.setOnClickListener(view ->
                startActivity(new Intent(this, StaffListActivity.class)));

        lnllibrary.setOnClickListener(view -> {
            Intent intent = new Intent(this, SearchStudentActivity.class);
            intent.putExtra("who", 4);
            startActivity(intent);
        });

        lnlpaymentdrc.setOnClickListener(view -> {
            Intent intent = new Intent(this, SearchStudentActivity.class);
            intent.putExtra("who", 5);
            startActivity(intent);
        });

        lnltransport.setOnClickListener(view -> {
            Intent intent = new Intent(this, StartTransportActivity.class);
            startActivity(intent);
        });

        lnlattendance.setOnClickListener(view -> {
            Intent intent = new Intent(this, LecturerActivity.class);
            intent.putExtra("who", 0);
            startActivity(intent);
        });

        lnlmarks.setOnClickListener(view -> {
            Intent intent = new Intent(this, MarksEntryActivity.class);
            intent.putExtra("who", 0);
            startActivity(intent);
        });

        // Daily attendance always uses NAMES (no CARD/NAMES chooser)
        lnldailyattendance.setOnClickListener(view -> {
            Intent intent = new Intent(this, LecturerActivity.class);
            intent.putExtra("who", 1);
            startActivity(intent);
        });

        if (lnldormattendance != null) {
            lnldormattendance.setOnClickListener(view ->
                    startActivity(new Intent(this, DormitoryAttendanceActivity.class)));
        }

        lnlleaves.setOnClickListener(view -> {
            Intent intent = new Intent(this, LeavesActivity.class);
            intent.putExtra("manage", false);
            startActivity(intent);
        });
        lnlleavemanage.setOnClickListener(view -> {
            Intent intent = new Intent(this, LeavesActivity.class);
            intent.putExtra("manage", true);
            startActivity(intent);
        });

        lnlcashflow.setOnClickListener(view -> {
            Intent intent = new Intent(this, CashFlowActivity.class);
            startActivity(intent);
        });

        if (lnlpocket != null) {
            lnlpocket.setOnClickListener(view ->
                    startActivity(new Intent(this, PocketBalanceActivity.class)));
        }
        if (lnlstock != null) {
            lnlstock.setOnClickListener(view -> startActivity(new Intent(this, StockMenuActivity.class)));
        }
        if (lnlassets != null) {
            lnlassets.setOnClickListener(view -> startActivity(new Intent(this, AssetMonitorActivity.class)));
        }

        applyMenuClearance();
        refreshMenuClearanceFromServer();
    }

    /** Fill header with school details saved at login (not SmartSMS branding). */
    private void bindSchoolHeader() {
        String schoolName = preferences.getString(SK_NAME, "").trim();
        if (schoolName.isEmpty()) {
            schoolName = getString(R.string.das_school_unknown);
        }
        txtSchoolName.setText(schoolName);

        String phone = preferences.getString(SK_PHONE, "").trim();
        String email = preferences.getString(SK_EMAIL, "").trim();
        String country = preferences.getString("school_country", "").trim();
        StringBuilder meta = new StringBuilder();
        if (!phone.isEmpty()) {
            meta.append(getString(R.string.das_school_phone, phone));
        }
        if (!email.isEmpty()) {
            if (meta.length() > 0) meta.append('\n');
            meta.append(getString(R.string.das_school_email, email));
        }
        if (!country.isEmpty()) {
            if (meta.length() > 0) meta.append('\n');
            meta.append(getString(R.string.das_school_country, country));
        }
        txtSchoolMeta.setText(meta.toString());
        txtSchoolMeta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);

        int termNumber = preferences.getInt("soma_term_number", 0);
        int academicId = preferences.getInt("soma_academic", 0);
        String academicType = preferences.getString("academic_type", "").trim();
        String academicTitle = resolveAcademicYearTitle(academicId);

        StringBuilder academic = new StringBuilder();
        if (termNumber > 0) {
            academic.append(getString(R.string.das_school_term, termNumber));
        }
        if (academicTitle != null && !academicTitle.isEmpty()) {
            if (academic.length() > 0) academic.append("  ·  ");
            academic.append(getString(R.string.das_school_academic, academicTitle));
        }
        if (!academicType.isEmpty()) {
            if (academic.length() > 0) academic.append("  ·  ");
            academic.append(academicType);
        }
        txtSchoolAcademic.setText(academic.toString());
        txtSchoolAcademic.setVisibility(academic.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private String resolveAcademicYearTitle(int academicId) {
        // Prefer title saved at login (real active year name, not id)
        String saved = preferences.getString("soma_academic_title", "").trim();
        if (!saved.isEmpty()) {
            return saved;
        }
        if (academicId <= 0) return "";
        try {
            android.database.sqlite.SQLiteDatabase db =
                    new com.qonics.dbhelper.DatabaseHandler(this).getReadableDatabase();
            android.database.Cursor c = db.rawQuery(
                    "SELECT title FROM tb_academic_year WHERE id=? LIMIT 1",
                    new String[]{String.valueOf(academicId)}
            );
            String title = "";
            if (c.moveToFirst()) {
                title = c.getString(0);
            }
            c.close();
            db.close();
            if (title != null && !title.trim().isEmpty()) return title.trim();
        } catch (Exception e) {
            Log.w(TAG, "Could not resolve academic year title", e);
        }
        // Never show bare numeric id as the year label
        return "";
    }

    /** Show/hide home tiles from Level Clearance saved at login. */
    private void applyMenuClearance() {
        if (lnlstock != null) {
            lnlstock.setVisibility(MenuClearanceHelper.canOpenStock(preferences) ? View.VISIBLE : View.GONE);
        }
        if (lnlassets != null) {
            lnlassets.setVisibility(MenuClearanceHelper.canOpenAssets(preferences) ? View.VISIBLE : View.GONE);
        }
        Map<String, View> tiles = new LinkedHashMap<>();
        tiles.put("discipline", lnldiscipline);
        tiles.put("clinic", lnlclinic);
        tiles.put("visiting", lnlvisiting);
        tiles.put("permission", lnlpermission);
        tiles.put("students", lnlstudents);
        tiles.put("staffs", lnlstaff);
        tiles.put("attendance", lnlattendance);
        tiles.put("daily_attendance", lnldailyattendance);
        tiles.put("payment", lnlpaymentdrc);
        tiles.put("library", lnllibrary);
        tiles.put("marks", lnlmarks);
        tiles.put("transport", lnltransport);
        tiles.put("leaves", lnlleaves);
        tiles.put("leave_management", lnlleavemanage);
        tiles.put("cashflow", lnlcashflow);
        tiles.put("pocket", lnlpocket);
        MenuClearanceHelper.applyAll(preferences, tiles);
        if (lnldormattendance != null) {
            lnldormattendance.setVisibility(MenuClearanceHelper.canUseMaterialAndDormitory(preferences)
                    ? View.VISIBLE : View.GONE);
        }
        if (lnlvernacular != null && lnldiscipline != null) {
            lnlvernacular.setVisibility(lnldiscipline.getVisibility());
        }
        if (lnlvisiting != null && MenuClearanceHelper.canOpenVisiting(preferences)) {
            lnlvisiting.setVisibility(View.VISIBLE);
        }
        applyMonitorAccess();
    }

    /** Directors see every school. Customer Care sees Wisdom School Musanze only. */
    private boolean canOpenSchoolMonitor() {
        return MenuClearanceHelper.canOpenSchoolMonitor(preferences);
    }

    private void applyMonitorAccess() {
        View card = findViewById(R.id.lnlMonitor);
        if (card == null) return;
        boolean allow = canOpenSchoolMonitor();
        card.setVisibility(allow ? View.VISIBLE : View.GONE);
        View locationCard = findViewById(R.id.lnlLocationAttendance);
        if (locationCard != null) {
            locationCard.setVisibility(allow ? View.VISIBLE : View.GONE);
        }
        if (!allow) stopMonitorPulse();
        else if (!isFinishing()) startMonitorPulse();
    }

    /** Re-fetch clearance so admin Level Clearance changes apply without reinstall. */
    private void refreshMenuClearanceFromServer() {
        int postId = preferences.getInt("soma_post", 0);
        int staffId = preferences.getInt("soma_id", 0);
        if (postId <= 0 && staffId <= 0) return;
        String url = SERVER + "menu_clearance/" + postId + (staffId > 0 ? ("?staff_id=" + staffId) : "");
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) return;
                SharedPreferences.Editor ed = preferences.edit();
                MenuClearanceHelper.saveFromLogin(ed, object);
                ed.apply();
                applyMenuClearance();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyMenuClearance();
        refreshMenuClearanceFromServer();
        SCAN_CARD = true;
        startHomeScanPolling();
        try {
            com.qonics.somanet.cashflow.CashFlowAlerter.check(this);
        } catch (Exception ignored) {
        }
        startMonitorPulse();
    }

    @Override
    public void onDestroy() {
        // ✅ No more dev.ReleaseDev() (Speedata removed)
        stopHomeScanPolling();
        if (timer != null) {
            timer.purge();
            timer.cancel();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        stopHomeScanPolling();
        stopMonitorPulse();
        super.onPause();
        SCAN_CARD = false;
    }

    private void startMonitorPulse() {
        View card = findViewById(R.id.lnlMonitor);
        if (card == null || card.getVisibility() != View.VISIBLE) return;
        View dot = findViewById(R.id.monitorPulse);
        if (dot == null) return;
        if (monitorPulse == null) {
            monitorPulse = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.25f);
            monitorPulse.setDuration(700);
            monitorPulse.setRepeatMode(ValueAnimator.REVERSE);
            monitorPulse.setRepeatCount(ValueAnimator.INFINITE);
        }
        if (!monitorPulse.isStarted()) monitorPulse.start();
    }

    private void stopMonitorPulse() {
        if (monitorPulse != null) monitorPulse.cancel();
    }

    /** Open the camera only after the web Smart Scan button starts a session. */
    private void startHomeScanPolling() {
        if (scanPolling) return;
        if (preferences.getInt("soma_id", 0) <= 0) return;
        scanPolling = true;
        scanPollRunnable = new Runnable() {
            @Override
            public void run() {
                checkPendingWebScan();
                if (scanPolling) scanPollHandler.postDelayed(this, 1500);
            }
        };
        scanPollHandler.post(scanPollRunnable);
    }

    private void stopHomeScanPolling() {
        scanPolling = false;
        scanPollHandler.removeCallbacksAndMessages(null);
    }

    private void checkPendingWebScan() {
        int staffId = preferences.getInt("soma_id", 0);
        if (staffId <= 0) return;
        String url = SERVER + "cash_flow_scan_pending/" + staffId;
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                ScanBridgeWatcher.consider(MainActivity.this, object);
            }
        });
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null) {
            Log.e("NFC", "STRING TAG ID: " + bytesToHexString(tag.getId()));
            Log.e("CURRENT SYSTEM", ": " + currentSystem);
        }
    }

    private String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }

        char[] buffer = new char[2];
        for (byte b : src) {
            buffer[0] = Character.forDigit((b >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(b & 0x0F, 16);
            stringBuilder.append(buffer);
        }
        stringBuilder.append("000");
        return stringBuilder.toString();
    }

    public static boolean isNetworkAvailable(Context context) {
        boolean isConnected = false;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        @SuppressLint("MissingPermission") NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if (activeNetwork != null) {
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                isConnected = true;
            } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                isConnected = true;
            }
        } else {
            isConnected = false;
        }
        return isConnected;
    }
}
