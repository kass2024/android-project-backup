package com.xander.student;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    private XanderPrefs prefs;
    private XanderStore store;
    private PhotoCache photos;
    private TextView tvSchool;
    private TextView tvPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xander_settings);
        prefs = new XanderPrefs(this);
        store = new XanderStore(this, prefs);
        photos = new PhotoCache(this);
        tvSchool = (TextView) findViewById(R.id.tv_school);
        tvPending = (TextView) findViewById(R.id.tv_pending);
        refreshSummary();

        findViewById(R.id.bt_sync).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resync();
            }
        });
        findViewById(R.id.bt_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goAttendance();
            }
        });
        findViewById(R.id.bt_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goAttendance();
            }
        });
        findViewById(R.id.bt_sound).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KioskSound.prepare(SettingsActivity.this);
                KioskSound.welcome(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, "Test: Staff IN chime", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.bt_logout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.logout();
                Intent i = new Intent(SettingsActivity.this, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            }
        });
        findViewById(R.id.bt_exit_app).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitToDesktop();
            }
        });
    }

    private void refreshSummary() {
        int pending = store.pendingCount();
        tvSchool.setText(prefs.schoolName() + "  ·  " + prefs.schoolAcronym()
                + "\nStaff card attendance only"
                + "\n" + store.staffWithCards() + " recorded staff cards");
        if (tvPending != null) {
            if (pending > 0) {
                tvPending.setText(pending + " tap" + (pending == 1 ? "" : "s") + " waiting to upload");
            } else {
                tvPending.setText(NetWatch.online(this) ? "Online — queue is empty" : "Offline — taps will upload later");
            }
            tvPending.setVisibility(View.VISIBLE);
        }
    }

    private void goAttendance() {
        Toast.makeText(this, "Back to scanner", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, KioskActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void resync() {
        Toast.makeText(this, "Syncing staff cards…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    store.flush();
                    final JSONObject json = XanderApi.sync(prefs.serverUrl(), prefs.schoolId());
                    if (XanderApi.ok(json)) {
                        prefs.saveBootstrap(json);
                        JSONArray staff = json.optJSONArray("staff");
                        store.replaceStaff(staff);
                        store.replaceBlockedCards(json.optJSONArray("students"), json.optJSONArray("visitors"));
                        photos.prefetchWait(staff);
                        try {
                            JSONObject dash = XanderApi.staffToday(prefs.serverUrl(), prefs.schoolId());
                            if (XanderApi.ok(dash)) {
                                prefs.saveDashboard(dash);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshSummary();
                            Toast.makeText(SettingsActivity.this,
                                    XanderApi.ok(json)
                                            ? "Synced " + store.staffWithCards() + " staff cards"
                                            : XanderApi.message(json, "Sync failed"),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(SettingsActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
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
}
