package com.qonics.somanet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class StaffEditActivity extends AppCompatActivity {

    private EditText edtFname;
    private EditText edtLname;
    private EditText edtPhone;
    private EditText edtEmail;
    private int staffId;
    private String postTitle;
    private String statusLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_edit);

        staffId = getIntent().getIntExtra("staff_id", 0);
        postTitle = getIntent().getStringExtra("post_title");
        statusLabel = getIntent().getStringExtra("status_label");

        edtFname = findViewById(R.id.edtFname);
        edtLname = findViewById(R.id.edtLname);
        edtPhone = findViewById(R.id.edtPhone);
        edtEmail = findViewById(R.id.edtEmail);
        TextView meta = findViewById(R.id.txtStaffEditMeta);
        Button save = findViewById(R.id.btnSaveStaff);

        edtFname.setText(nz(getIntent().getStringExtra("fname")));
        edtLname.setText(nz(getIntent().getStringExtra("lname")));
        edtPhone.setText(nz(getIntent().getStringExtra("phone")));
        edtEmail.setText(nz(getIntent().getStringExtra("email")));

        String extra = "";
        if (postTitle != null && !postTitle.isEmpty()) {
            extra = postTitle;
        }
        if (statusLabel != null && !statusLabel.isEmpty()) {
            extra = extra.isEmpty() ? statusLabel : extra + "  ·  " + statusLabel;
        }
        if (meta != null) {
            meta.setText(extra);
        }

        save.setOnClickListener(v -> saveStaff());
    }

    private void saveStaff() {
        String fname = edtFname.getText().toString().trim();
        String lname = edtLname.getText().toString().trim();
        String phone = edtPhone.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        if (fname.isEmpty() || lname.isEmpty()) {
            Toast.makeText(this, R.string.staff_name_required, Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("staff_id", staffId);
        map.put("fname", fname);
        map.put("lname", lname);
        map.put("phone", phone);
        map.put("email", email);
        Toast.makeText(this, R.string.staff_saving, Toast.LENGTH_SHORT).show();
        new HttpRequest(this).post(MainActivity.SERVER + "update_staff_profile", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object != null && object.has("success")) {
                    Intent data = new Intent();
                    data.putExtra("staff_id", staffId);
                    data.putExtra("fname", fname);
                    data.putExtra("lname", lname);
                    data.putExtra("phone", phone);
                    data.putExtra("email", email);
                    setResult(RESULT_OK, data);
                    Toast.makeText(StaffEditActivity.this, R.string.staff_saved, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String err = object != null ? object.optString("error", status) : status;
                    Toast.makeText(StaffEditActivity.this, err, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
