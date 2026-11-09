package com.xander.student;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class LoginActivity extends Activity {
    private XanderPrefs prefs;
    private XanderStore store;
    private PhotoCache photos;
    private EditText etServer;
    private EditText etAcronym;
    private Button btLogin;
    private ProgressBar progress;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new XanderPrefs(this);
        store = new XanderStore(this, prefs);
        photos = PhotoCache.get(this);
        photos.setBaseUrl(prefs.serverUrl());
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
        findViewById(R.id.bt_exit_desktop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitToDesktop();
            }
        });
        KioskSound.prepare(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.isLoggedIn() && store.studentCount() > 0) {
            startActivity(new Intent(this, KioskActivity.class));
            finish();
        }
    }

    private void doLogin() {
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
                                        && store.studentCount() > 0) {
                                    Toast.makeText(LoginActivity.this, "Offline — using saved " + prefs.schoolName(), Toast.LENGTH_LONG).show();
                                    goKiosk();
                                    return;
                                }
                                showError(XanderApi.message(json, "School not found"));
                                return;
                            }
                            prefs.saveBootstrap(json);
                            JSONArray students = json.optJSONArray("students");
                            JSONArray visitors = json.optJSONArray("visitors");
                            store.replaceStudents(students);
                            store.replaceVisitors(visitors);
                            store.applyAttendanceToday(json.optJSONArray("attendance_today"));
                            store.applyVisitorVisitsToday(json.optJSONArray("visitor_visits_today"));
                            photos.setBaseUrl(prefs.serverUrl());
                            photos.prefetch(students);
                            photos.warmRam();
                            photos.bindLogo(null, prefs.schoolLogo());
                            int n = students == null ? 0 : students.length();
                            int v = visitors == null ? 0 : visitors.length();
                            Toast.makeText(LoginActivity.this,
                                    prefs.schoolName() + "  ·  " + n + " students · " + v + " visitors ready",
                                    Toast.LENGTH_LONG).show();
                            goKiosk();
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
                                    && store.studentCount() > 0) {
                                Toast.makeText(LoginActivity.this, "Offline — using saved " + prefs.schoolName(), Toast.LENGTH_LONG).show();
                                goKiosk();
                                return;
                            }
                            showError("Wi‑Fi too slow to reach server (try again or move closer to router):\n"
                                    + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void goKiosk() {
        startActivity(new Intent(this, KioskActivity.class));
        finish();
    }

    private void exitToDesktop() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask();
        } else {
            finishAffinity();
        }
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(msg.length() == 0 ? View.GONE : View.VISIBLE);
    }
}
