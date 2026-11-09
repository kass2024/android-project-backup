package com.qonics.somanet;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.PaymentUsbReader;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Pocket money balance. Samsung NFC stays in reader mode, and a USB reader
 * is accepted the same way the payment search accepts a wedge or DK dongle.
 */
public class PocketBalanceActivity extends AppCompatActivity {
    private static final String TAG = "PocketBalance";

    private SharedPreferences preferences;
    private NfcAdapter nfcAdapter;
    private NFCForegroundUtil nfcForegroundUtil;
    private PaymentUsbReader paymentUsb;
    private TextView txtNfc;
    private TextView txtUsb;
    private TextView txtName;
    private TextView txtMeta;
    private TextView txtKpiBalance;
    private TextView txtKpiCanteen;
    private TextView txtKpiReceived;
    private TextView txtKpiCash;
    private TextView txtError;
    private TextView txtSource;
    private ProgressBar prb;
    private SQLiteDatabase db;
    private CircleImageView imgStudent;
    private String lastUid = "";
    private long lastUidAt = 0;
    private String inFlightUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pocket_balance);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        txtNfc = findViewById(R.id.txtNfc);
        txtUsb = findViewById(R.id.txtUsb);
        txtName = findViewById(R.id.txtName);
        txtMeta = findViewById(R.id.txtMeta);
        txtKpiBalance = findViewById(R.id.txtKpiBalance);
        txtKpiCanteen = findViewById(R.id.txtKpiCanteen);
        txtKpiReceived = findViewById(R.id.txtKpiReceived);
        txtKpiCash = findViewById(R.id.txtKpiCash);
        txtError = findViewById(R.id.txtError);
        txtSource = findViewById(R.id.txtPocketSource);
        prb = findViewById(R.id.prb);
        imgStudent = findViewById(R.id.imgStudent);
        db = openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        refreshPocketCache();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            nfcForegroundUtil = new NFCForegroundUtil(this);
            nfcForegroundUtil.setTagListener(this::onNfcTag);
        }
        paintNfc();

        paymentUsb = new PaymentUsbReader();
        paymentUsb.start(this, new PaymentUsbReader.Listener() {
            @Override
            public void onReader(boolean connected) {
                if (isFinishing()) return;
                runOnUiThread(() -> txtUsb.setText(connected ? R.string.pocket_usb_ready : R.string.pocket_usb_wait));
            }

            @Override
            public void onCard(String uid) {
                if (isFinishing()) return;
                runOnUiThread(() -> lookup(uid));
            }
        });
    }

    private void paintNfc() {
        if (nfcAdapter == null) {
            txtNfc.setText(R.string.pocket_nfc_missing);
            return;
        }
        txtNfc.setText(nfcAdapter.isEnabled() ? R.string.pocket_nfc_ready : R.string.pocket_nfc_off);
    }

    @Override
    protected void onResume() {
        super.onResume();
        paintNfc();
        if (nfcAdapter != null && nfcForegroundUtil != null) {
            try {
                if (!nfcAdapter.isEnabled()) {
                    Toast.makeText(this, R.string.activate_nfc, Toast.LENGTH_LONG).show();
                }
                nfcForegroundUtil.enableScanReader();
            } catch (Exception e) {
                Log.e(TAG, "NFC enable failed", e);
            }
        }
        handleNfcIntent(getIntent());
    }

    @Override
    protected void onPause() {
        if (nfcForegroundUtil != null) {
            nfcForegroundUtil.disableForeground();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (paymentUsb != null) {
            paymentUsb.stop();
            paymentUsb = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNfcIntent(intent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (paymentUsb != null && PaymentUsbReader.isPhysicalKeyboard(event)) {
            paymentUsb.onKey(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void onNfcTag(Tag tag) {
        if (tag == null) return;
        String cardId = NFCForegroundUtil.bytesToHex(tag.getId());
        if (cardId.isEmpty()) return;
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(30);
        } catch (Exception ignored) {
        }
        lookup(cardId);
    }

    private void handleNfcIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            onNfcTag(tag);
        }
    }

    private void lookup(String uid) {
        if (uid == null) return;
        String cardId = uid.trim().toUpperCase(Locale.US);
        if (cardId.length() < 4) return;
        long now = SystemClock.elapsedRealtime();
        if (cardId.equals(inFlightUid)) return;
        if (cardId.equals(lastUid) && now - lastUidAt < 900) return;
        lastUid = cardId;
        lastUidAt = now;
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId <= 0) {
            showError(getString(R.string.das_school_unknown));
            return;
        }
        LocalStudent local = findLocalStudent(cardId);
        if (local != null) {
            showLocal(local);
        }
        if (!MainActivity.isNetworkAvailable(this)) {
            inFlightUid = "";
            prb.setVisibility(android.view.View.GONE);
            if (local == null) {
                showError(getString(R.string.pocket_card_missing));
            }
            return;
        }
        inFlightUid = cardId;
        prb.setVisibility(android.view.View.VISIBLE);
        if (local == null) txtError.setText("");
        Map<String, Object> map = new HashMap<>();
        map.put("card", cardId);
        map.put("school_id", schoolId);
        new HttpRequest(this).timeouts(8, 12).post(MainActivity.SERVER + "pocket_card_balance", map, (object, statusCode, status) -> {
            if (isFinishing()) return;
            inFlightUid = "";
            prb.setVisibility(android.view.View.GONE);
            JSONObject student = object == null ? null : object.optJSONObject("student");
            if (object != null && object.optBoolean("success", false) && student != null) {
                int studentId = student.optInt("id", local == null ? 0 : local.id);
                JSONObject kpi = object.optJSONObject("kpi");
                int balance = kpi == null ? object.optInt("balance", 0) : kpi.optInt("balance", object.optInt("balance", 0));
                int canteen = kpi == null ? 0 : kpi.optInt("canteen", 0);
                int received = kpi == null ? 0 : kpi.optInt("received", 0);
                int cash = kpi == null ? 0 : kpi.optInt("cash", 0);
                PocketStore.saveOne(db, studentId, balance, received, canteen, cash, object.optBoolean("disabled", false) ? 1 : 0, null);
                showStudent(student, object);
                paintSource(true);
                return;
            }
            if (local != null) {
                paintSource(false);
                return;
            }
            String error = object == null ? status : object.optString("error", status);
            if (error == null || error.trim().isEmpty()) {
                error = "No student found for this card.";
            }
            showError(error);
        });
    }

    private void refreshPocketCache() {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId < 1 || !MainActivity.isNetworkAvailable(this)) return;
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId);
        new HttpRequest(this).timeouts(20, 60).post(MainActivity.SERVER + "pocket_money_sync", map, (object, statusCode, status) -> {
            if (isFinishing() || object == null || !object.optBoolean("success", false)) return;
            PocketStore.replaceAll(db, object.optJSONArray("rows"), object.optString("date", ""));
        });
    }

    private LocalStudent findLocalStudent(String cardId) {
        String[] variants = NFCForegroundUtil.lookupVariants(cardId);
        if (variants.length == 0) return null;
        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < variants.length; i++) {
            if (i > 0) marks.append(',');
            marks.append('?');
        }
        Cursor c = db.rawQuery(
                "select id, name, regno, classe, photo from tb_student where ifnull(status,1)<>0 and upper(card) in (" + marks + ")",
                variants);
        LocalStudent student = null;
        if (c.moveToFirst()) {
            student = new LocalStudent();
            student.id = c.getInt(0);
            student.name = c.getString(1);
            student.regno = c.getString(2);
            student.classe = c.getString(3);
            student.photo = c.getString(4);
        }
        c.close();
        return student;
    }

    private void showLocal(LocalStudent student) {
        txtName.setText(student.name == null || student.name.length() == 0 ? "Student" : student.name);
        String meta = student.regno == null ? "" : student.regno;
        String classe = student.classe == null ? "" : student.classe.trim();
        if (!classe.isEmpty()) {
            meta = meta.isEmpty() ? classe : meta + " · " + classe;
        }
        txtMeta.setText(meta);
        int[] money = PocketStore.read(db, student.id);
        if (money == null) {
            String empty = getString(R.string.pocket_kpi_empty);
            txtKpiBalance.setText(empty);
            txtKpiCanteen.setText(empty);
            txtKpiReceived.setText(empty);
            txtKpiCash.setText(empty);
            txtError.setTextColor(Color.parseColor("#B45309"));
            txtError.setText(R.string.pocket_need_sync);
        } else {
            txtKpiBalance.setText(money(money[0]));
            txtKpiReceived.setText(money(money[1]));
            txtKpiCanteen.setText(money(money[2]));
            txtKpiCash.setText(money(money[3]));
            txtError.setText(money[4] == 1 ? "This student card is disabled." : "");
        }
        paintSource(false);
        StudentPhotoHelper.load(imgStudent, student.photo == null ? "" : student.photo);
    }

    private void paintSource(boolean live) {
        txtSource.setText(live ? R.string.pocket_live : R.string.pocket_saved);
        txtSource.setTextColor(Color.parseColor(live ? "#047857" : "#64748B"));
    }

    private void showStudent(JSONObject student, JSONObject payload) {
        txtName.setText(student.optString("name", "Student"));
        String meta = student.optString("regno", "");
        String classe = student.optString("class", "").trim();
        if (!classe.isEmpty()) {
            meta = meta.isEmpty() ? classe : meta + " · " + classe;
        }
        txtMeta.setText(meta);
        JSONObject kpi = payload.optJSONObject("kpi");
        int balance = kpi == null ? payload.optInt("balance", 0) : kpi.optInt("balance", payload.optInt("balance", 0));
        int canteen = kpi == null ? 0 : kpi.optInt("canteen", 0);
        int received = kpi == null ? 0 : kpi.optInt("received", 0);
        int cash = kpi == null ? 0 : kpi.optInt("cash", 0);
        txtKpiBalance.setText(money(balance));
        txtKpiCanteen.setText(money(canteen));
        txtKpiReceived.setText(money(received));
        txtKpiCash.setText(money(cash));
        boolean disabled = payload.optBoolean("disabled", false);
        txtError.setText(disabled ? payload.optString("message", "") : "");
        StudentPhotoHelper.load(imgStudent, student.optString("photo", ""));
        paintSource(true);
    }

    private void showError(String message) {
        txtError.setText(message);
        String empty = getString(R.string.pocket_kpi_empty);
        txtKpiBalance.setText(empty);
        txtKpiCanteen.setText(empty);
        txtKpiReceived.setText(empty);
        txtKpiCash.setText(empty);
    }

    private static String money(int amount) {
        return String.format(Locale.US, "%,d RWF", amount);
    }

    private static final class LocalStudent {
        int id;
        String name;
        String regno;
        String classe;
        String photo;
    }
}
