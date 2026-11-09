package com.qonics.somanet;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;   // ✅ THIS IS REQUIRED
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.qonics.somanet.prints.UsbThermalPrinter;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class PermissionActivity extends AppCompatActivity
        implements DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {

    EditText etxtterm, etxtdestination, etxtreason, etxtcomment;
    CircleImageView profile_image;
    TextView txtleave, txtreturn, txtstname, txtstclass, txtstregno, txttitle,
            txtpdestination, txtpreason, txtpleavetime, txtpreturntime, txtpissued;
    ImageView imgleave, imgreturn;
    LinearLayout lnlviewpermission, lnlpermission, lnljustify, lnlprinted;
    Button btnsave, btnjustify, btnclose, btncopy;
    CheckBox chknotify;
    ImageView imgprint;
    ProgressBar prb;

    HttpRequest req;
    String server = MainActivity.SERVER;
    SharedPreferences preferences;

    String leavetime = "", returntime = "";
    int dateType = 0; // 0: leave time, 1: return time
    Calendar c = Calendar.getInstance();
    int permission_id = 0;

    public boolean isPrinted = false;
    private UsbThermalPrinter usbPrinter;
    private boolean usbPrintClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        usbPrinter = new UsbThermalPrinter(this);
        usbPrinter.setListener((printed, message) -> runOnUiThread(() -> {
            if (!printed) {
                if (message != null && message.length() > 0) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
                return;
            }
            isPrinted = true;
            btncopy.setEnabled(true);
            if (usbPrintClose) {
                lnlprinted.setVisibility(View.VISIBLE);
                lnlpermission.setVisibility(View.GONE);
            }
            new AlertDialog.Builder(this)
                    .setTitle("Success")
                    .setMessage("Permission saved and printed.")
                    .setPositiveButton("OK", null)
                    .show();
        }));

        usbPrinter.start(this);
        populateView();
        check_permission();
    }

    // ---------------------------
    // UI population
    // ---------------------------
    private void populateView() {
        prb = findViewById(R.id.prb);
        imgprint = findViewById(R.id.imgprint);
        btncopy = findViewById(R.id.btncopy);
        btnclose = findViewById(R.id.btnclose);
        btnjustify = findViewById(R.id.btnjustify);
        lnlprinted = findViewById(R.id.lnlprinted);
        lnljustify = findViewById(R.id.lnljustify);
        lnlpermission = findViewById(R.id.lnlpermission);
        lnlviewpermission = findViewById(R.id.lnlviewpermission);
        txtpissued = findViewById(R.id.txtpissued);
        txtpreturntime = findViewById(R.id.txtpreturntime);
        txtpleavetime = findViewById(R.id.txtpleavetime);
        txtpreason = findViewById(R.id.txtpreason);
        txtpdestination = findViewById(R.id.txtpdestination);
        txttitle = findViewById(R.id.txttitle);
        etxtcomment = findViewById(R.id.etxtcomment);
        etxtdestination = findViewById(R.id.etxtdestination);
        etxtreason = findViewById(R.id.etxtreason);
        etxtterm = findViewById(R.id.etxtterm);
        btnsave = findViewById(R.id.btnsave);
        chknotify = findViewById(R.id.chknotify);
        profile_image = findViewById(R.id.profile_image);
        txtstname = findViewById(R.id.txtstname);
        txtstclass = findViewById(R.id.txtstclass);
        txtstregno = findViewById(R.id.txtstregno);
        txtleave = findViewById(R.id.txtleave);
        txtreturn = findViewById(R.id.txtreturn);
        imgleave = findViewById(R.id.imgleave);
        imgreturn = findViewById(R.id.imgreturn);

        prb.setVisibility(View.GONE);

        etxtterm.setText(getIntent().getStringExtra("term"));
        txtstregno.setText(getIntent().getStringExtra("regno"));
        txtstname.setText(getIntent().getStringExtra("name"));
        txtstclass.setText(getIntent().getStringExtra("class"));
        StudentPhotoHelper.load(profile_image, getIntent().getStringExtra("photo"));

        lnlprinted.setVisibility(View.GONE);
        lnljustify.setVisibility(View.GONE);
        lnlpermission.setVisibility(View.GONE);
        lnlviewpermission.setVisibility(View.GONE);

        btnclose.setOnClickListener(view -> finish());

        imgleave.setOnClickListener(view -> {
            dateType = 0;
            Calendar now = Calendar.getInstance();
            DatePickerDialog dpd = DatePickerDialog.newInstance(
                    PermissionActivity.this,
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH)
            );
            dpd.setMinDate(now);
            dpd.setVersion(DatePickerDialog.Version.VERSION_2);
            dpd.show(getSupportFragmentManager(), "Leave date");
        });

        imgreturn.setOnClickListener(view -> {
            if (leavetime.length() == 0) {
                Toast.makeText(this, getString(R.string.per_time_first), Toast.LENGTH_LONG).show();
            }
            dateType = 1;
            Calendar now = Calendar.getInstance();
            DatePickerDialog dpd = DatePickerDialog.newInstance(
                    PermissionActivity.this,
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH)
            );
            dpd.setMinDate(c);
            c.add(Calendar.MONTH, 1);
            dpd.setMaxDate(c);
            c.add(Calendar.MONTH, -1);
            dpd.setVersion(DatePickerDialog.Version.VERSION_2);
            dpd.show(getSupportFragmentManager(), "Return date");
        });

        btnsave.setOnClickListener(view -> {
            String reason = etxtreason.getText().toString().trim();
            String destination = etxtdestination.getText().toString().trim();
            boolean isError = false;
            if (reason.length() < 5) {
                etxtreason.setError(getString(R.string.dis_reason_err));
                isError = true;
            }
            if (destination.length() < 3) {
                etxtreason.setError(getString(R.string.per_destination_err));
                isError = true;
            }
            if (leavetime.length() < 3) {
                Toast.makeText(this, getString(R.string.per_leav_time_er), Toast.LENGTH_SHORT).show();
                isError = true;
            }
            if (returntime.length() < 3) {
                Toast.makeText(this, getString(R.string.per_return_time_er), Toast.LENGTH_SHORT).show();
                isError = true;
            }
            if (!isError) {
                save(destination, reason);
            }
        });

        btnjustify.setOnClickListener(view -> {
            String comment = etxtcomment.getText().toString().trim();
            if (permission_id == 0) {
                Toast.makeText(this, getString(R.string.per_data_inv), Toast.LENGTH_LONG).show();
                return;
            }
            if (comment.length() < 5) {
                etxtcomment.setError(getString(R.string.invalid_comment));
            } else {
                save_justification(comment);
            }
        });
    }

    // ---------------------------
    // Date / Time picker callbacks
    // ---------------------------
    @Override
    public void onDateSet(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth) {
        String text = getString(R.string.lbl_leave_time);
        String m = String.format("%02d", (monthOfYear + 1));
        String d = String.format("%02d", dayOfMonth);

        if (dateType == 0) {
            leavetime = year + "-" + m + "-" + d;
            c.set(year, monthOfYear, dayOfMonth);
        } else {
            returntime = year + "-" + m + "-" + d;
            text = getString(R.string.lbl_return_time);
        }

        TimePickerDialog tpd = TimePickerDialog.newInstance(
                PermissionActivity.this, true
        );
        tpd.setMinTime(7, 0, 0);
        tpd.setMaxTime(18, 0, 0);
        tpd.setVersion(TimePickerDialog.Version.VERSION_2);
        tpd.show(getSupportFragmentManager(), text);
    }

    @Override
    public void onTimeSet(TimePickerDialog view, int hourOfDay, int minute, int second) {
        String h = String.format("%02d", hourOfDay);
        String m = String.format("%02d", minute);
        if (dateType == 0) {
            returntime = "";
            leavetime += " " + h + ":" + m;
        } else {
            returntime += " " + h + ":" + m;
        }
        txtleave.setText(leavetime);
        txtreturn.setText(returntime);
    }

    // ---------------------------
    // Lifecycle
    // ---------------------------
    @Override
    protected void onDestroy() {
        if (usbPrinter != null) usbPrinter.stop(this);
        super.onDestroy();
    }

    @SuppressLint("SimpleDateFormat")
    private void print(String std_name, String dest, String lvTime, String rtTime,
                       String reason, String school_phone, String operator, boolean close) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm");
        Date curDate = new Date(System.currentTimeMillis());
        String date = formatter.format(curDate);

        String givenBy = preferences.getString("soma_name", "");
        if (givenBy == null || givenBy.trim().length() == 0) givenBy = operator;
        UsbThermalPrinter.Slip slip = new UsbThermalPrinter.Slip();
        slip.logoUrl = preferences.getString("school_logo", "");
        slip.school = preferences.getString(MainActivity.SK_NAME, "");
        slip.slogan = preferences.getString("school_slogan", "");
        slip.address = preferences.getString("school_address", "");
        slip.pobox = preferences.getString("school_pobox", "");
        slip.phone = school_phone;
        slip.email = preferences.getString(MainActivity.SK_EMAIL, "");
        slip.website = preferences.getString("school_website", "");
        slip.student = std_name;
        slip.destination = dest;
        slip.reason = reason;
        slip.leave = lvTime;
        slip.ret = rtTime;
        slip.givenBy = givenBy;
        slip.printedOn = date;

        if (usbPrinter != null && usbPrinter.tryPrint(this, slip)) {
            usbPrintClose = close;
            return;
        }
        Toast.makeText(this, "Connect the USB printer, then print again.", Toast.LENGTH_LONG).show();
    }

    // ---------------------------
    // Permission API Calls
    // ---------------------------
    private void check_permission() {
        req = new HttpRequest(this);
        req.progress(prb).get(server + "check_permission/" +
                        getIntent().getIntExtra("student_id", 0),
                JSONObject.class, new Callback() {
                    @Override
                    public void HttpCallback(JSONObject object, int status_code, String status) {
                        if (object == null) {
                            Toast.makeText(PermissionActivity.this, status, Toast.LENGTH_LONG).show();
                        } else {
                            try {
                                if (object.has("error")) {
                                    if (object.getString("error").equals("0")) {
                                        lnlpermission.setVisibility(View.VISIBLE);
                                        lnlviewpermission.setVisibility(View.GONE);
                                        lnljustify.setVisibility(View.GONE);
                                    } else {
                                        Toast.makeText(PermissionActivity.this,
                                                getString(R.string.lbl_error) + object.getString("error"),
                                                Toast.LENGTH_LONG).show();
                                    }
                                } else {
                                    Toast.makeText(PermissionActivity.this,
                                            getString(R.string.per_pending_err), Toast.LENGTH_LONG).show();
                                    imgprint.setOnClickListener(view -> {
                                        try {
                                            print(object.getString("name"),
                                                    object.getString("destination"),
                                                    object.getString("leave_time"),
                                                    object.getString("return_time"),
                                                    object.getString("reason"),
                                                    object.getString("school_phone"),
                                                    object.getString("operator"), false);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    permission_id = object.getInt("permission_id");
                                    txttitle.setText(getString(R.string.per_just));
                                    lnlpermission.setVisibility(View.GONE);
                                    lnlviewpermission.setVisibility(View.VISIBLE);
                                    lnljustify.setVisibility(View.VISIBLE);
                                    txtpdestination.setText(getString(R.string.lbl_destination) + " " +
                                            object.getString("destination"));
                                    txtpreason.setText(getString(R.string.lbl_reason) + " " +
                                            object.getString("reason"));
                                    txtpleavetime.setText(getString(R.string.lbl_leave_time) + " \n" +
                                            object.getString("leave_time"));
                                    txtpreturntime.setText(getString(R.string.lbl_return_time) + " \n" +
                                            object.getString("return_time"));
                                    txtpissued.setText(getString(R.string.lbl_issued_by) + "" +
                                            object.getString("operator"));
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
    }

    private void save(String destination, String reason) {
        Map map = new HashMap();
        map.put("destination", destination);
        map.put("reason", reason);
        map.put("leave", leavetime);
        map.put("return", returntime);
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("operator", preferences.getInt("soma_id", 0));
        map.put("student_id", getIntent().getIntExtra("student_id", 0));
        map.put("notify_parent", chknotify.isChecked() ? 1 : 0);

        req = new HttpRequest(this);
        req.progress(prb).hide(btnsave).post(server + "/save_permission", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    Snackbar.make(btnsave, getString(R.string.lbl_error) + status, Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        if (object.has("error")) {
                            Snackbar.make(btnsave, getString(R.string.lbl_error) +
                                    object.getString("error"), Snackbar.LENGTH_LONG).show();
                        } else {
                            String school_phone = object.optString("school_phone", "");
                            preferences.edit()
                                    .putString("school_logo", object.optString("school_logo", ""))
                                    .putString("school_slogan", object.optString("school_slogan", ""))
                                    .putString("school_address", object.optString("school_address", ""))
                                    .putString("school_pobox", object.optString("school_pobox", ""))
                                    .putString("school_website", object.optString("school_website", ""))
                                    .putString(MainActivity.SK_PHONE, school_phone)
                                    .putString(MainActivity.SK_EMAIL, object.optString("school_email", ""))
                                    .putString(MainActivity.SK_NAME, object.optString("school_name",
                                            preferences.getString(MainActivity.SK_NAME, "")))
                                    .apply();
                            btncopy.setOnClickListener(view -> {
                                print(getIntent().getStringExtra("name"), destination, leavetime, returntime,
                                        reason, school_phone, preferences.getString("soma_name", ""), true);
                            });
                            print(getIntent().getStringExtra("name"), destination, leavetime, returntime,
                                    reason, school_phone, preferences.getString("soma_name", ""), true);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void save_justification(String comment) {
        Map map = new HashMap();
        map.put("comment", comment);
        map.put("operator", preferences.getInt("soma_id", 0));
        map.put("permission_id", permission_id);

        req = new HttpRequest(this);
        req.progress(prb).hide(btnsave).post(server + "/save_justification", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    Snackbar.make(btnsave, getString(R.string.lbl_error) + status, Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        if (object.has("error")) {
                            Snackbar.make(btnsave, getString(R.string.lbl_error) +
                                    object.getString("error"), Snackbar.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(PermissionActivity.this,
                                    getString(R.string.per_just_saved), Toast.LENGTH_LONG).show();
                            txttitle.setText(getString(R.string.per_entry));
                            lnljustify.setVisibility(View.GONE);
                            lnlviewpermission.setVisibility(View.GONE);
                            lnlpermission.setVisibility(View.VISIBLE);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
