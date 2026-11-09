package com.qonics.somanet;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import cn.pedant.SweetAlert.SweetAlertDialog;
import de.hdodenhof.circleimageview.CircleImageView;

public class StudentActivity extends AppCompatActivity {
    private static final String TAG = StudentActivity.class.getSimpleName();

    TextView txtcountdown, txtstname, txtstclass, txtstregno, txtstphone;
    Button btnimage, btnassigncard;
    ProgressBar prb;
    LinearLayout lnlcard, lnlphone;
    CircleImageView profile_image;
    HttpRequest req;
    String server = MainActivity.SERVER;
    SharedPreferences preferences;
    String card = "";

    Timer timer;
    TimerTask timerTask;
    boolean SCAN_CARD = false;
    boolean pending = false;
    int count = 0;

    NFCForegroundUtil nfcForegroundUtil = null;
    private NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Initialize NFC only (Speedata R6 removed)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, getString(R.string.nfc_not_supported), Toast.LENGTH_LONG).show();
        } else {
            nfcForegroundUtil = new NFCForegroundUtil(this);
            nfcForegroundUtil.setTagListener(tag -> {
                if (tag == null) return;
                String cardId = NFCForegroundUtil.bytesToHex(tag.getId());
                Log.e("NFC", "STRING TAG ID: " + cardId);
                if (!pending) {
                    // Web assign-card expects reader order and normalizes itself
                    assign_card(cardId);
                    lnlcard.setVisibility(View.GONE);
                }
            });
        }

        requestRuntimePermissions();

        populateView();
    }

    private void requestRuntimePermissions() {
        java.util.ArrayList<String> needed = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), 0);
        }
    }

    private void populateView() {
        txtcountdown = findViewById(R.id.txtcountdown);
        btnassigncard = findViewById(R.id.btnassign);
        btnimage = findViewById(R.id.btnimage);
        prb = findViewById(R.id.prb);
        lnlcard = findViewById(R.id.lnlcard);
        lnlphone = findViewById(R.id.lnlphone);
        profile_image = findViewById(R.id.profile_image);
        txtstname = findViewById(R.id.txtstname);
        txtstclass = findViewById(R.id.txtstclass);
        txtstregno = findViewById(R.id.txtstregno);
        txtstphone = findViewById(R.id.txtstphone);

        prb.setVisibility(View.GONE);

        StudentPhotoHelper.load(profile_image, getIntent().getStringExtra("photo"));
        txtstregno.setText(getIntent().getStringExtra("regno"));
        txtstname.setText(getIntent().getStringExtra("name"));
        txtstclass.setText(getIntent().getStringExtra("class"));

        if (getIntent().getStringExtra("phone") != null && getIntent().getStringExtra("phone").length() > 5) {
            lnlphone.setVisibility(View.VISIBLE);
            txtstphone.setText(getIntent().getStringExtra("phone"));
        }

        lnlcard.setVisibility(View.GONE);
        card = getIntent().getStringExtra("card");

        btnimage.setOnClickListener(view -> {
            Intent intent = new Intent(StudentActivity.this, CameraExActivity.class);
            intent.putExtra("id", getIntent().getIntExtra("student_id", 0));
            startActivityForResult(intent, 1);
        });

        // Student Photo menu: open camera flow (Change Picture) immediately
        if (getIntent().getBooleanExtra("focus_photo", false)) {
            btnassigncard.setVisibility(View.GONE);
            btnimage.postDelayed(() -> {
                if (!isFinishing()) {
                    btnimage.performClick();
                }
            }, 350);
        }

        if (card != null && card.length() > 3) {
            btnassigncard.setText(getString(R.string.change_card));
        }

        if (nfcAdapter == null) btnassigncard.setEnabled(false);

        btnassigncard.setOnClickListener(view -> {
            if (card != null && card.length() > 3) {
                SweetAlertDialog sa = new SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE);
                sa.setTitle(getString(R.string.confirm_card_change));
                sa.setContentText(getString(R.string.stud_ask) + " " + getIntent().getStringExtra("name"));
                sa.setConfirmButton(getString(R.string.change_card), sweetAlertDialog -> {
                    sweetAlertDialog.dismissWithAnimation();
                    start_assign();
                });
                sa.setCancelButton(getString(R.string.cancel), SweetAlertDialog::dismissWithAnimation);
                sa.show();
            } else {
                start_assign();
            }
        });
    }

    public void start_assign() {
        lnlcard.setVisibility(View.VISIBLE);
        count = 0;
        txtcountdown.setText("0");
        pending = false;
        btnassigncard.setEnabled(false);
        SCAN_CARD = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SCAN_CARD = true;
        if (nfcAdapter != null) {
            try {
                nfcForegroundUtil.enableForeground();
                if (!nfcAdapter.isEnabled()) {
                    btnassigncard.setEnabled(false);
                } else {
                    btnassigncard.setEnabled(true);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SCAN_CARD = false;
        if (nfcAdapter != null) nfcForegroundUtil.disableForeground();
    }

    @Override
    protected void onDestroy() {
        try {
            if (timer != null) {
                timer.purge();
                timer.cancel();
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;

        String cardId = bytesToHexString(tag.getId());
        Log.e("NFC", "STRING TAG ID: " + cardId);

        if (!pending) {
            assign_card(cardId);
            lnlcard.setVisibility(View.GONE);
        }
    }

    private String bytesToHexString(byte[] src) {
        if (src == null || src.length <= 0) return null;
        StringBuilder sb = new StringBuilder();
        for (byte b : src) sb.append(String.format("%02X", b));
        return sb.toString().toUpperCase();
    }

    private void assign_card(String card_id) {
        pending = true;
        Map<String, Object> map = new HashMap<>();
        map.put("card", card_id);
        map.put("operator", preferences.getInt("soma_id", 0));
        map.put("student_id", getIntent().getIntExtra("student_id", 0));
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));

        req = new HttpRequest(this);
        req.progress(prb).post(server + "/assign_card", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                pending = false;
                btnassigncard.setEnabled(true);
                if (object == null) {
                    Snackbar.make(btnassigncard, getString(R.string.lbl_error) + status, Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        if (object.has("error")) {
                            Snackbar.make(btnassigncard, getString(R.string.lbl_error) + object.getString("error"), Snackbar.LENGTH_LONG).show();
                        } else {
                            card = card_id;
                            btnassigncard.setText(getString(R.string.change_card));
                            Toast.makeText(StudentActivity.this, getString(R.string.card_assigned), Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1 && data != null) {
            String photo = data.getStringExtra("photo");
            if (photo != null && photo.length() > 3) {
                getIntent().putExtra("photo", photo);
                StudentPhotoHelper.load(profile_image, photo, true);
            }
        }
    }
}
