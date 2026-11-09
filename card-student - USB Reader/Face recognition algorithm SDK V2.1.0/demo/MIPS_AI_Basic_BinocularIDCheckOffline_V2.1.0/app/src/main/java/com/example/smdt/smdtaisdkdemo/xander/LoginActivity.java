package com.example.smdt.smdtaisdkdemo.xander;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smdt.smdtaisdkdemo.R;

import org.json.JSONArray;
import org.json.JSONObject;

public class LoginActivity extends Activity {
    private XanderPrefs prefs;
    private XanderStore store;
    private EditText etServer;
    private EditText etAcronym;
    private Button btLogin;
    private ProgressBar progress;
    private TextView tvError;
    private boolean askedSpecial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new XanderPrefs(this);
        store = new XanderStore(this, prefs);
        setContentView(R.layout.activity_xander_login);
        etServer = (EditText) findViewById(R.id.et_server);
        etAcronym = (EditText) findViewById(R.id.et_acronym);
        btLogin = (Button) findViewById(R.id.bt_login);
        progress = (ProgressBar) findViewById(R.id.progress);
        tvError = (TextView) findViewById(R.id.tv_error);
        etServer.setText(prefs.serverUrl());
        if (prefs.schoolAcronym().length() > 0) {
            etAcronym.setText(prefs.schoolAcronym());
        }
        btLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLogin();
            }
        });
        XanderPermissions.requestRuntime(this);
        new DeviceUsb(this).requestMissing();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (XanderPermissions.hasCameraAndStorage(this) && !askedSpecial) {
            askedSpecial = true;
            if (!XanderPermissions.hasOverlay(this)) {
                Toast.makeText(this, "Allow display over other apps for the camera engine", Toast.LENGTH_LONG).show();
                XanderPermissions.requestOverlay(this);
                return;
            }
            if (!XanderPermissions.hasAllFiles(this)) {
                Toast.makeText(this, "Allow all-files access for the face license and photos", Toast.LENGTH_LONG).show();
                XanderPermissions.requestAllFiles(this);
                return;
            }
        }
        if (prefs.isLoggedIn() && XanderPermissions.hasAll(this) && store.studentCount() + store.staffCount() > 0) {
            startActivity(new Intent(this, KioskActivity.class));
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != XanderPermissions.REQ) {
            return;
        }
        boolean ok = true;
        if (grantResults == null || grantResults.length == 0) {
            ok = false;
        } else {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) {
            showError("Camera permission is required for face check-in.");
        }
    }

    private void doLogin() {
        if (!XanderPermissions.hasAll(this)) {
            askedSpecial = false;
            XanderPermissions.ensure(this);
            showError("Allow camera, overlay, and file access, then connect again.");
            return;
        }
        final String server = etServer.getText().toString().trim();
        final String acronym = etAcronym.getText().toString().trim().toUpperCase();
        if (server.length() == 0 || acronym.length() == 0) {
            showError("Enter the school acronym (example: WSR).");
            return;
        }
        prefs.setServerUrl(server);
        showError("");
        progress.setVisibility(View.VISIBLE);
        btLogin.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject json = XanderApi.openSchool(prefs.serverUrl(), acronym);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.setVisibility(View.GONE);
                            btLogin.setEnabled(true);
                            if (!XanderApi.ok(json)) {
                                if (prefs.isLoggedIn() && acronym.equalsIgnoreCase(prefs.schoolAcronym())
                                        && store.studentCount() + store.staffCount() > 0) {
                                    Toast.makeText(LoginActivity.this, "Offline — using saved " + prefs.schoolName(), Toast.LENGTH_LONG).show();
                                    startActivity(new Intent(LoginActivity.this, KioskActivity.class));
                                    finish();
                                    return;
                                }
                                showError(XanderApi.message(json, "School not found"));
                                return;
                            }
                            prefs.saveBootstrap(json);
                            store.replacePeople(json.optJSONArray("students"), json.optJSONArray("staff"));
                            JSONArray st = json.optJSONArray("students");
                            JSONArray sf = json.optJSONArray("staff");
                            int nStu = st == null ? 0 : st.length();
                            int nSta = sf == null ? 0 : sf.length();
                            Toast.makeText(LoginActivity.this,
                                    prefs.schoolName() + "  ·  " + nStu + " students, " + nSta + " staff",
                                    Toast.LENGTH_LONG).show();
                            startActivity(new Intent(LoginActivity.this, KioskActivity.class));
                            finish();
                        }
                    });
                } catch (final Exception e) {
                    XanderApi.logErr("openSchool", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.setVisibility(View.GONE);
                            btLogin.setEnabled(true);
                            if (prefs.isLoggedIn() && acronym.equalsIgnoreCase(prefs.schoolAcronym())
                                    && store.studentCount() + store.staffCount() > 0) {
                                Toast.makeText(LoginActivity.this, "Offline — using saved " + prefs.schoolName(), Toast.LENGTH_LONG).show();
                                startActivity(new Intent(LoginActivity.this, KioskActivity.class));
                                finish();
                                return;
                            }
                            showError("Cannot reach server: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(msg.length() == 0 ? View.GONE : View.VISIBLE);
    }
}
