package com.xander.student;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class SettingsActivity extends Activity {
    private XanderPrefs prefs;
    private XanderStore store;
    private PhotoCache photos;
    private Spinner spMode;
    private Spinner spLocationMode;
    private Spinner spLocation;
    private TextView tvModeHelp;
    private TextView tvLocationHelp;
    private TextView tvSchool;
    private TextView tvPending;
    private JSONArray locations;
    private int[] locationIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xander_settings);
        prefs = new XanderPrefs(this);
        store = new XanderStore(this, prefs);
        photos = PhotoCache.get(this);
        photos.setBaseUrl(prefs.serverUrl());
        spMode = (Spinner) findViewById(R.id.sp_mode);
        spLocationMode = (Spinner) findViewById(R.id.sp_location_mode);
        spLocation = (Spinner) findViewById(R.id.sp_location);
        tvModeHelp = (TextView) findViewById(R.id.tv_mode_help);
        tvLocationHelp = (TextView) findViewById(R.id.tv_location_help);
        tvSchool = (TextView) findViewById(R.id.tv_school);
        tvPending = (TextView) findViewById(R.id.tv_pending);
        refreshSummary();
        fillModes();
        fillLocationMode();
        fillLocations();

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
                Toast.makeText(SettingsActivity.this, "Test: Welcome", Toast.LENGTH_SHORT).show();
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
                + "\nSmart scan: student attendance + parent visiting"
                + "\n" + store.studentCount() + " students · " + store.visitorCount() + " visitors ready");
        if (tvPending != null) {
            if (pending > 0) {
                tvPending.setText(pending + " tap" + (pending == 1 ? "" : "s") + " waiting to upload");
                tvPending.setVisibility(View.VISIBLE);
            } else {
                tvPending.setText(NetWatch.online(this) ? "Online — queue is empty" : "Offline — taps will upload later");
                tvPending.setVisibility(View.VISIBLE);
            }
        }
    }

    private void fillModes() {
        ArrayList<String> labels = new ArrayList<String>();
        labels.add("Automatic (students + parent visiting)");
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, R.layout.spinner_item_light, labels);
        ad.setDropDownViewResource(R.layout.spinner_dropdown_light);
        spMode.setAdapter(ad);
        spMode.setSelection(0);
        spMode.setEnabled(false);
        spMode.setAlpha(0.7f);
        refreshModeUi();
    }

    private void fillLocationMode() {
        if (spLocationMode == null) {
            return;
        }
        ArrayList<String> labels = new ArrayList<String>();
        labels.add("Automatic — follow daily start times");
        labels.add("Manual — keep the location I pick");
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, R.layout.spinner_item_light, labels);
        ad.setDropDownViewResource(R.layout.spinner_dropdown_light);
        spLocationMode.setAdapter(ad);
        spLocationMode.setSelection(prefs.locationFollowsSchedule() ? 0 : 1);
        spLocationMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean automatic = position == 0;
                prefs.setLocationFollowsSchedule(automatic);
                if (automatic) {
                    prefs.applyScheduledLocation();
                }
                refreshLocationModeUi();
                fillLocations();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        refreshLocationModeUi();
    }

    private void refreshLocationModeUi() {
        boolean automatic = prefs.locationFollowsSchedule();
        if (tvLocationHelp != null) {
            tvLocationHelp.setText(automatic
                    ? "This device changes location when each daily start time is reached, and stays there until the next one. It keeps working offline after a sync."
                    : "This device stays on the location you pick. Daily start times on the school website do not move it.");
        }
        if (spLocation != null) {
            spLocation.setEnabled(!automatic);
            spLocation.setAlpha(automatic ? 0.55f : 1f);
        }
    }

    private void fillLocations() {
        ArrayList<String> names = new ArrayList<String>();
        try {
            locations = new JSONArray(prefs.locationsJson());
        } catch (Exception e) {
            locations = new JSONArray();
        }
        locationIds = new int[locations.length()];
        int selected = 0;
        for (int i = 0; i < locations.length(); i++) {
            JSONObject o = locations.optJSONObject(i);
            if (o == null) {
                continue;
            }
            locationIds[i] = o.optInt("id", 0);
            names.add(o.optString("name", "Location " + locationIds[i]));
            if (locationIds[i] == prefs.areaId()) {
                selected = names.size() - 1;
            }
        }
        if (names.size() == 0) {
            names.add("No locations — add them in School Settings");
            locationIds = new int[]{0};
        }
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, R.layout.spinner_item_light, names);
        ad.setDropDownViewResource(R.layout.spinner_dropdown_light);
        spLocation.setAdapter(ad);
        spLocation.setSelection(selected);
        refreshLocationModeUi();
        spLocation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (prefs.locationFollowsSchedule()) {
                    return;
                }
                if (locationIds != null && position >= 0 && position < locationIds.length) {
                    String name = parent.getItemAtPosition(position).toString();
                    prefs.setArea(locationIds[position], name);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void refreshModeUi() {
        if (tvModeHelp != null) {
            tvModeHelp.setText("This kiosk automatically checks student attendance cards and parent visiting cards, including offline saved scans.");
        }
        if (spLocation != null) {
            spLocation.setEnabled(true);
            spLocation.setAlpha(1f);
        }
    }

    private void goAttendance() {
        save();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, KioskActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void save() {
        if (spLocationMode != null) {
            prefs.setLocationFollowsSchedule(spLocationMode.getSelectedItemPosition() == 0);
        }
        if (prefs.locationFollowsSchedule()) {
            prefs.applyScheduledLocation();
            return;
        }
        int pos = spLocation.getSelectedItemPosition();
        if (locationIds != null && pos >= 0 && pos < locationIds.length) {
            Object item = spLocation.getSelectedItem();
            prefs.setArea(locationIds[pos], item == null ? "" : item.toString());
        }
    }

    private void resync() {
        Toast.makeText(this, "Syncing students…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    store.flush();
                    final JSONObject json = XanderApi.sync(prefs.serverUrl(), prefs.schoolId());
                    if (XanderApi.ok(json)) {
                        prefs.saveBootstrap(json);
                        JSONArray students = json.optJSONArray("students");
                        JSONArray visitors = json.optJSONArray("visitors");
                        store.replaceStudents(students);
                        store.replaceVisitors(visitors);
                        store.applyAttendanceToday(json.optJSONArray("attendance_today"));
                        store.applyVisitorVisitsToday(json.optJSONArray("visitor_visits_today"));
                        photos.prefetch(students);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshSummary();
                            fillLocations();
                            Toast.makeText(SettingsActivity.this,
                                    XanderApi.ok(json) ? "Synced " + store.studentCount() + " students and "
                                            + store.visitorCount() + " visitors"
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
        save();
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
