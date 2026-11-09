package com.qonics.somanet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qonics.model.StudentModel;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import cn.pedant.SweetAlert.SweetAlertDialog;
import de.hdodenhof.circleimageview.CircleImageView;

public class TransportActivity extends AppCompatActivity {
    private static final String TAG = TransportActivity.class.getSimpleName();

    TextView txtbus, txtroute, txtplace, txtroutedetails, txtstudents,
            txtstcount, txtinfo, txtclose;
    RelativeLayout rlvnofitication;
    LinearLayout lnlinfo, lnlstudent;
    ImageView imgcard;
    ListView lvstudent;
    ProgressBar prb;

    HttpRequest req;
    String server = MainActivity.SERVER;
    SharedPreferences preferences;

    ArrayList<StudentModel> students = new ArrayList<>();
    ArrayAdapter adapter;

    Object dev = null;   // placeholder so code compiles without Speedata libs
    Timer timer;
    TimerTask timerTask;
    boolean SCAN_CARD = false;
    boolean pending = false;
    NFCForegroundUtil nfcForegroundUtil = null;
    private NfcAdapter nfcAdapter;

    Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transport);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        Log.e(TAG, "begin to init");
        try {
            Log.e(TAG, "init ok");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            dev = null;
            Log.e(TAG, "current device is Not Somanet POS");
            nfcForegroundUtil = new NFCForegroundUtil(this);
            nfcForegroundUtil.setTagListener(tag -> {
                if (tag == null) return;
                String cardId = NFCForegroundUtil.bytesToHex(tag.getId());
                Log.e("NFC", "STRING TAG ID: " + cardId);
                if (SCAN_CARD && !pending) {
                    searchStudent(cardId);
                }
            });
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter == null) {
                Toast.makeText(this, getString(R.string.nfc_not_supported), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (!nfcAdapter.isEnabled()) {
                Toast.makeText(this, getString(R.string.sear_ebl_nfc), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }

        adapter = new StudentAdapter();
        populateView();
        cardSearch();
        getData();
    }

    private void populateView() {
        lvstudent = findViewById(R.id.lvstudent);
        txtbus = findViewById(R.id.txtbus);
        txtroute = findViewById(R.id.txtroute);
        txtplace = findViewById(R.id.txtplace);
        txtroutedetails = findViewById(R.id.txtroutedetails);
        txtstudents = findViewById(R.id.txtstudents);
        txtstcount = findViewById(R.id.txtstcount);
        txtinfo = findViewById(R.id.txtinfo);
        txtclose = findViewById(R.id.txtclose);
        rlvnofitication = findViewById(R.id.rlvnofitication);
        lnlinfo = findViewById(R.id.lnlinfo);
        lnlstudent = findViewById(R.id.lnlstudent);
        imgcard = findViewById(R.id.imgcard);
        prb = findViewById(R.id.prb);

        prb.setVisibility(View.GONE);
        lnlstudent.setVisibility(View.GONE);
        rlvnofitication.setVisibility(View.VISIBLE);

        txtbus.setText(getString(R.string.bus) + " " + getIntent().getStringExtra("bus_title"));
        txtroute.setText(getString(R.string.route) + " " + getIntent().getStringExtra("route_title"));
        txtplace.setText("#: " + getIntent().getIntExtra("bus_place", 0));
        txtroutedetails.setText(getIntent().getStringExtra("route_details"));
        lvstudent.setAdapter(adapter);

        txtstcount.setOnClickListener(view -> {
            rlvnofitication.setVisibility(View.GONE);
            lnlstudent.setVisibility(View.VISIBLE);
            SCAN_CARD = false;
        });

        txtclose.setOnClickListener(view -> {
            rlvnofitication.setVisibility(View.VISIBLE);
            lnlstudent.setVisibility(View.GONE);
            SCAN_CARD = true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dev == null && nfcForegroundUtil != null) {
            nfcForegroundUtil.enableForeground();
            if (!nfcForegroundUtil.getNfc().isEnabled()) {
                Toast.makeText(getApplicationContext(), getString(R.string.activate_nfc), Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null && nfcForegroundUtil != null)
            nfcForegroundUtil.disableForeground();
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

    public void delay_hide() {
        Timer hide_timer = new Timer();
        Handler handler = new Handler(msg -> {
            hideError();
            return false;
        });
        hide_timer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.sendEmptyMessage(0);
            }
        }, 3000);
    }

    public void cardSearch() {
        if (SCAN_CARD && dev != null) {
            timer = new Timer();
            final Handler handler = new Handler(msg -> {
                String id = msg.getData().getString("id");
                Log.e("CARD SEARCH", "CARD ID: " + id);
                if (!pending) searchStudent(id);
                return false;
            });
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    byte[] ID = null;
                    if (ID == null) return;
                    StringBuilder IDString = new StringBuilder();
                    for (byte a : ID) {
                        IDString.append(String.format("%02X", a));
                    }
                    Message msg = new Message();
                    Bundle data = new Bundle();
                    msg.setData(data);
                    data.putString("id", IDString.toString());
                    handler.sendMessage(msg);
                }
            };
            timer.schedule(timerTask, 0, 500);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;
        Log.e("NFC", "STRING TAG ID: " + bytesToHexString(tag.getId()));
        if (SCAN_CARD && !pending)
            searchStudent(bytesToHexString(tag.getId()));
    }

    private String bytesToHexString(byte[] src) {
        if (src == null || src.length <= 0) return null;
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : src) {
            stringBuilder.append(Character.forDigit((b >>> 4) & 0x0F, 16));
            stringBuilder.append(Character.forDigit(b & 0x0F, 16));
        }
        return stringBuilder.toString().toUpperCase();
    }

    public void showError(String message, boolean show) {
        message = message.length() == 0 ? getString(R.string.tap_card) : message;
        txtinfo.setText(message);
        if (show) {
            // ✅ Use existing colorError instead of missing error_stroke_color
            txtinfo.setTextColor(ContextCompat.getColor(this, R.color.colorError));
        } else {
            txtinfo.setTextColor(ContextCompat.getColor(this, R.color.accent));
        }
    }

    public void hideError() {
        showError("", false);
    }

    private void getData() {
        req = new HttpRequest(this);
        hideError();
        String url = server + "get_transport_records/" +
                preferences.getInt(MainActivity.SK_ID, 0) + "/" +
                getIntent().getIntExtra("bus", 0) + "/" +
                getIntent().getIntExtra("route", 0) + "/" +
                getIntent().getIntExtra("way", 0);
        Log.e("URL", url);
        showError(getString(R.string.please_wait), false);
        imgcard.setVisibility(View.GONE);

        req.progress(prb).get(url, JSONObject.class, (object, status_code, status) -> {
            imgcard.setVisibility(View.VISIBLE);
            if (object == null) {
                showError(getString(R.string.lbl_error) + status, true);
                delay_hide();
            } else {
                try {
                    if (object.has("error")) {
                        if (object.getString("error").equals("0")) {
                            SCAN_CARD = true;
                            pending = false;
                            hideError();
                        } else {
                            showError(getString(R.string.lbl_error) + object.getString("error"), true);
                            delay_hide();
                        }
                    } else {
                        hideError();
                        JSONArray st = object.getJSONArray("students");
                        int stcount = 0;
                        for (int a = 0; a < st.length(); a++) {
                            stcount++;
                            JSONObject data = st.getJSONObject(a);
                            students.add(new StudentModel(
                                    data.getInt("student_id"),
                                    data.getString("name"),
                                    data.getString("regno"),
                                    data.getString("classe"),
                                    data.getString("photo")));
                        }
                        txtstcount.setEnabled(stcount > 0);
                        if (stcount >= getIntent().getIntExtra("bus_place", 0)) {
                            showError(getString(R.string.bus_full), true);
                            imgcard.setImageResource(R.drawable.close);
                            SCAN_CARD = false;
                        } else {
                            SCAN_CARD = true;
                        }
                        txtstcount.setText(String.valueOf(stcount));
                        adapter.notifyDataSetChanged();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void searchStudent(String card) {
        vibrator.vibrate(100);
        req = new HttpRequest(this);
        showError(getString(R.string.please_wait), true);

        Map<String, Object> map = new HashMap<>();
        map.put("bus", getIntent().getIntExtra("bus", 0));
        map.put("bus_title", getIntent().getStringExtra("bus_title"));
        map.put("route", getIntent().getIntExtra("route", 0));
        map.put("way", getIntent().getIntExtra("way", 0));
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("operator", preferences.getInt("soma_id", 0));

        String url = server + "add_transport_records/" + card;
        imgcard.setVisibility(View.GONE);

        req.progress(prb).post(url, map, (object, status_code, status) -> {
            imgcard.setVisibility(View.VISIBLE);
            if (object == null) {
                showError("Error: " + status, true);
                delay_hide();
            } else {
                try {
                    if (object.has("error")) {
                        showError(getString(R.string.lbl_error) + object.getString("error"), true);
                        delay_hide();
                        vibrator.vibrate(500);
                    } else if (object.has("success")) {
                        pending = true;
                        hideError();
                        SweetAlertDialog sa = new SweetAlertDialog(
                                TransportActivity.this, SweetAlertDialog.SUCCESS_TYPE);
                        sa.setTitle(getString(R.string.good_job));
                        sa.setContentText(
                                getString(R.string.lbl_student) + " " + object.getString("name") +
                                        "\n" + getString(R.string.lbl_class) + object.getString("classe") +
                                        "\n" + getString(R.string.trans_remain) + object.getString("amount"));
                        sa.setCancelable(false);
                        sa.setConfirmButton("Close", sweetAlertDialog -> {
                            sweetAlertDialog.dismissWithAnimation();
                            pending = false;
                        }).show();

                        students.add(new StudentModel(
                                object.getInt("student_id"),
                                object.getString("name"),
                                object.getString("regno"),
                                object.getString("classe"),
                                object.getString("photo")));
                        adapter.notifyDataSetChanged();
                        txtstcount.setText(String.valueOf(students.size()));

                        if (students.size() >= getIntent().getIntExtra("bus_place", 0)) {
                            showError(getString(R.string.trans_full), true);
                            imgcard.setImageResource(R.drawable.close);
                            SCAN_CARD = false;
                        } else {
                            SCAN_CARD = true;
                        }
                        vibrator.vibrate(new long[]{100, 100, 200, 100}, -1);
                    } else {
                        vibrator.vibrate(500);
                        showError(getString(R.string.lbl_error) + object.getString("message"), true);
                        delay_hide();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private class StudentAdapter extends ArrayAdapter<StudentModel> {
        public StudentAdapter() {
            super(TransportActivity.this, R.layout.one_student, students);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(R.layout.one_student, parent, false);
            TextView txtname = v.findViewById(R.id.txtstname);
            TextView txtclass = v.findViewById(R.id.txtstclass);
            TextView txtregno = v.findViewById(R.id.txtstregno);
            CircleImageView img = v.findViewById(R.id.profile_image);

            StudentModel st = students.get(position);
            String photo = st.getPhoto();
            if (photo != null && photo.length() > 3) {
                StudentPhotoHelper.load(img, photo);
            } else {
                img.setImageResource(R.drawable.placeholder_image);
            }
            txtname.setText(st.getName());
            txtclass.setText(getString(R.string.lbl_class) + st.getClasse());
            txtregno.setText(st.getRegno());
            return v;
        }
    }
}
