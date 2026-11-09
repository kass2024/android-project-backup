package com.qonics.somanet;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.PaymentUsbReader;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Card-only vernacular speaking sanction. The tap shows the student and the
 * fixed punishment. Marks, the 20,000 Frw fee, and the parent SMS wait for
 * the director to confirm.
 */
public class VernacularActivity extends AppCompatActivity {
    private static final String TAG = "Vernacular";
    private static final int CALL_REQUEST = 41;

    private SharedPreferences preferences;
    private NfcAdapter nfcAdapter;
    private NFCForegroundUtil nfcForegroundUtil;
    private PaymentUsbReader paymentUsb;
    private TextView txtNfc;
    private TextView txtUsb;
    private TextView txtName;
    private TextView txtMeta;
    private TextView txtBehavior;
    private TextView txtMarks;
    private TextView txtFine;
    private TextView txtError;
    private ProgressBar prb;
    private Button btnConfirm;
    private LinearLayout boxParents;
    private CircleImageView imgStudent;
    private String lastUid = "";
    private long lastUidAt = 0;
    private String inFlightUid = "";
    private int studentId = 0;
    private String pendingDial = "";
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vernacular);
        db = openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        txtNfc = findViewById(R.id.txtNfc);
        txtUsb = findViewById(R.id.txtUsb);
        txtName = findViewById(R.id.txtName);
        txtMeta = findViewById(R.id.txtMeta);
        txtBehavior = findViewById(R.id.txtBehavior);
        txtMarks = findViewById(R.id.txtMarks);
        txtFine = findViewById(R.id.txtFine);
        txtError = findViewById(R.id.txtError);
        prb = findViewById(R.id.prb);
        btnConfirm = findViewById(R.id.btnConfirm);
        boxParents = findViewById(R.id.boxParents);
        imgStudent = findViewById(R.id.imgStudent);
        btnConfirm.setOnClickListener(v -> confirm());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

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
                nfcForegroundUtil.enableForeground();
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
        if (!MainActivity.isNetworkAvailable(this)) {
            if (local != null) {
                showOfflineStudent(local);
            } else {
                showError(getString(R.string.pocket_card_missing));
            }
            return;
        }
        inFlightUid = cardId;
        studentId = 0;
        btnConfirm.setEnabled(false);
        prb.setVisibility(View.VISIBLE);
        txtError.setText("");
        Map<String, Object> map = new HashMap<>();
        map.put("card", cardId);
        map.put("school_id", schoolId);
        new HttpRequest(this).timeouts(8, 12).post(MainActivity.SERVER + "vernacular_card", map, (object, statusCode, status) -> {
            if (isFinishing()) return;
            inFlightUid = "";
            prb.setVisibility(View.GONE);
            JSONObject student = object == null ? null : object.optJSONObject("student");
            if (object != null && object.optBoolean("success", false) && student != null) {
                showStudent(student, object);
                return;
            }
            if (local != null) {
                showOfflineStudent(local);
                return;
            }
            String error = object == null ? status : object.optString("error", status);
            if (error == null || error.trim().isEmpty()) {
                error = "No student found for this card.";
            }
            showError(error);
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
                "select id, name, regno, classe, photo, phone from tb_student where ifnull(status,1)<>0 and upper(card) in (" + marks + ")",
                variants);
        LocalStudent student = null;
        if (c.moveToFirst()) {
            student = new LocalStudent();
            student.id = c.getInt(0);
            student.name = c.getString(1);
            student.regno = c.getString(2);
            student.classe = c.getString(3);
            student.photo = c.getString(4);
            student.phone = c.getString(5);
        }
        c.close();
        return student;
    }

    private void showOfflineStudent(LocalStudent student) {
        studentId = student.id;
        txtName.setText(student.name == null || student.name.length() == 0 ? "Student" : student.name);
        String meta = student.regno == null ? "" : student.regno;
        String classe = student.classe == null ? "" : student.classe.trim();
        if (!classe.isEmpty()) meta = meta.isEmpty() ? classe : meta + " · " + classe;
        txtMeta.setText(meta);
        txtMarks.setText("−20");
        txtFine.setText("20,000 Frw");
        txtBehavior.setText(R.string.vernacular_offline_student);
        boxParents.removeAllViews();
        if (student.phone != null && student.phone.trim().length() >= 8) {
            JSONArray parents = new JSONArray();
            JSONObject parent = new JSONObject();
            try {
                parent.put("role", "Parent");
                parent.put("phone", student.phone.trim());
                parents.put(parent);
            } catch (Exception ignored) {
            }
            paintParents(parents);
        }
        txtError.setTextColor(0xFFB45309);
        txtError.setText(R.string.pocket_saved);
        btnConfirm.setEnabled(true);
        btnConfirm.setText(R.string.vernacular_confirm);
        StudentPhotoHelper.load(imgStudent, student.photo == null ? "" : student.photo);
        inFlightUid = "";
        prb.setVisibility(View.GONE);
    }

    private void showStudent(JSONObject student, JSONObject payload) {
        studentId = student.optInt("id", 0);
        txtName.setText(student.optString("name", "Student"));
        String meta = student.optString("regno", "");
        String classe = student.optString("class", "").trim();
        if (!classe.isEmpty()) {
            meta = meta.isEmpty() ? classe : meta + " · " + classe;
        }
        txtMeta.setText(meta);
        JSONObject sanction = payload.optJSONObject("sanction");
        int marks = sanction == null ? 20 : sanction.optInt("marks", 20);
        int amount = sanction == null ? 20000 : sanction.optInt("amount", 20000);
        txtMarks.setText(String.format(Locale.US, "−%d", marks));
        txtFine.setText(String.format(Locale.US, "%,d Frw", amount));
        JSONObject behavior = payload.optJSONObject("behavior");
        if (behavior != null) {
            txtBehavior.setText(getString(
                    R.string.vernacular_behavior_left,
                    behavior.optInt("after", 0),
                    behavior.optInt("max", 0)
            ));
        } else {
            txtBehavior.setText("");
        }
        paintParents(payload.optJSONArray("parents"));
        txtError.setText("");
        btnConfirm.setEnabled(studentId > 0);
        btnConfirm.setText(R.string.vernacular_confirm);
        StudentPhotoHelper.load(imgStudent, student.optString("photo", ""));
    }

    private void paintParents(JSONArray parents) {
        boxParents.removeAllViews();
        if (parents == null || parents.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.vernacular_no_phone);
            empty.setTextColor(0xFF64748B);
            empty.setPadding(8, 12, 8, 4);
            boxParents.addView(empty);
            return;
        }
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < parents.length(); i++) {
            JSONObject parent = parents.optJSONObject(i);
            if (parent == null) continue;
            String phone = parent.optString("phone", "").trim();
            if (phone.isEmpty()) continue;
            String role = parent.optString("role", "Parent");
            String name = parent.optString("name", "").trim();
            String label = name.isEmpty() ? role + " · " + phone : role + " · " + name + " · " + phone;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            row.setBackgroundResource(R.drawable.menu_tile_bg);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowLp.topMargin = Math.round(8 * getResources().getDisplayMetrics().density);
            row.setLayoutParams(rowLp);

            TextView text = new TextView(this);
            text.setText(label);
            text.setTextColor(0xFF0F172A);
            text.setTextSize(14);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            text.setLayoutParams(textLp);

            Button call = new Button(this);
            call.setText(R.string.vernacular_call);
            call.setAllCaps(false);
            call.setTextColor(0xFFFFFFFF);
            call.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0284C7));
            call.setOnClickListener(v -> callParent(phone));

            row.addView(text);
            row.addView(call);
            boxParents.addView(row);
        }
    }

    private void confirm() {
        if (studentId <= 0 || !btnConfirm.isEnabled()) return;
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId <= 0) {
            showError(getString(R.string.das_school_unknown));
            return;
        }
        if (!MainActivity.isNetworkAvailable(this)) {
            queueVernacular(schoolId);
            readyForNext(getString(R.string.vernacular_saved));
            SyncReceiver.requestSync(this);
            return;
        }
        btnConfirm.setEnabled(false);
        prb.setVisibility(View.VISIBLE);
        txtError.setText("");
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", schoolId);
        map.put("student_id", studentId);
        map.put("operator", preferences.getInt("soma_id", 0));
        new HttpRequest(this).timeouts(12, 25).post(MainActivity.SERVER + "vernacular_confirm", map, (object, statusCode, status) -> {
            if (isFinishing()) return;
            prb.setVisibility(View.GONE);
            if (object != null && object.optBoolean("success", false)) {
                String message = object.optString("message", "Confirmed.");
                readyForNext(message);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                return;
            }
            if (object == null) {
                queueVernacular(schoolId);
                readyForNext(getString(R.string.vernacular_saved));
                SyncReceiver.requestSync(this);
                return;
            }
            String error = object.optString("error", status);
            if (error == null || error.trim().isEmpty()) {
                error = "Could not confirm this sanction.";
            }
            txtError.setTextColor(0xFFB42318);
            txtError.setText(error);
            btnConfirm.setEnabled(studentId > 0);
        });
    }

    private void queueVernacular(int schoolId) {
        Cursor existing = db.rawQuery(
                "select id from tb_vernacular_pending where student_id=? and sync=1 limit 1",
                new String[]{String.valueOf(studentId)});
        boolean queued = existing.moveToFirst();
        existing.close();
        if (queued) return;
        db.execSQL(
                "insert into tb_vernacular_pending (id,student_id,school_id,operator,sync,created_at) values (?,?,?,?,?,?)",
                new Object[]{
                        UUID.randomUUID().toString(),
                        studentId,
                        schoolId,
                        preferences.getInt("soma_id", 0),
                        1,
                        System.currentTimeMillis()
                });
    }

    private void readyForNext(String message) {
        studentId = 0;
        lastUid = "";
        inFlightUid = "";
        txtName.setText(R.string.vernacular_next);
        txtMeta.setText("");
        txtBehavior.setText("");
        txtMarks.setText("−20");
        txtFine.setText("20,000 Frw");
        txtError.setTextColor(0xFF047857);
        txtError.setText(message);
        btnConfirm.setText(R.string.vernacular_confirm);
        btnConfirm.setEnabled(false);
        boxParents.removeAllViews();
        imgStudent.setImageResource(R.drawable.placeholder_image);
    }

    private static final class LocalStudent {
        int id;
        String name;
        String regno;
        String classe;
        String photo;
        String phone;
    }

    private void showError(String message) {
        studentId = 0;
        txtError.setTextColor(0xFFB42318);
        txtError.setText(message);
        btnConfirm.setEnabled(false);
        boxParents.removeAllViews();
    }

    private void callParent(String phone) {
        String dial = phone == null ? "" : phone.replaceAll("[^0-9+]", "");
        if (dial.length() < 9) {
            Toast.makeText(this, R.string.vernacular_no_phone, Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingDial = dial;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, CALL_REQUEST);
            return;
        }
        placeCall(dial);
    }

    private void placeCall(String dial) {
        try {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + dial)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dial)));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CALL_REQUEST || pendingDial.isEmpty()) return;
        String dial = pendingDial;
        pendingDial = "";
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            placeCall(dial);
            return;
        }
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dial)));
    }
}
