package com.example.smdt.smdtaisdkdemo.xander;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smdt.smdtaisdkdemo.MainActivity;
import com.example.smdt.smdtaisdkdemo.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class SettingsActivity extends Activity {
    private XanderPrefs prefs;
    private XanderStore store;
    private EditText etServer;
    private EditText etLic;
    private Spinner spLocation;
    private Spinner spRgb;
    private Spinner spIr;
    private Spinner spOrient;
    private JSONArray locations;
    private int[] locationIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xander_settings);
        prefs = new XanderPrefs(this);
        store = new XanderStore(this, prefs);

        etServer = (EditText) findViewById(R.id.et_server);
        etLic = (EditText) findViewById(R.id.et_lic);
        spLocation = (Spinner) findViewById(R.id.sp_location);
        spRgb = (Spinner) findViewById(R.id.sp_rgb);
        spIr = (Spinner) findViewById(R.id.sp_ir);
        spOrient = (Spinner) findViewById(R.id.sp_orient);
        TextView tvSchool = (TextView) findViewById(R.id.tv_school);
        tvSchool.setText(prefs.schoolName() + "  ·  " + prefs.schoolAcronym()
                + "\n" + store.studentCount() + " students, " + store.staffCount() + " staff"
                + (store.pendingCount() > 0 ? ("  ·  " + store.pendingCount() + " waiting to upload") : ""));
        etServer.setText(prefs.serverUrl());
        etLic.setText(prefs.licPath());

        fillLocations();
        fillCameraIds();

        ArrayAdapter<CharSequence> orAd = new ArrayAdapter<CharSequence>(this,
                R.layout.spinner_item_light,
                new String[]{"Landscape", "Portrait", "Reverse landscape", "Reverse portrait"});
        orAd.setDropDownViewResource(R.layout.spinner_dropdown_light);
        spOrient.setAdapter(orAd);
        int o = prefs.cameraOrientation();
        if (o < 0 || o > 3) {
            o = 3;
        }
        spOrient.setSelection(o);

        findViewById(R.id.bt_swap).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int rgb = spRgb.getSelectedItemPosition();
                int ir = spIr.getSelectedItemPosition();
                spRgb.setSelection(ir);
                spIr.setSelection(rgb);
                Toast.makeText(SettingsActivity.this, "Cameras swapped — save and return to apply", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.bt_flip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.flipPreview180();
                int o = prefs.cameraOrientation();
                if (o >= 0 && o <= 3) {
                    spOrient.setSelection(o);
                }
                Toast.makeText(SettingsActivity.this, "Image flipped " + prefs.previewRotation() + "° — save and return", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.bt_leds).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DeviceLights.off(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, "Fill light off. Green = match, red = unknown", Toast.LENGTH_SHORT).show();
            }
        });
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
                KioskSound.ok(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, "Speaker test — you should hear two beeps", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.bt_faces).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                startActivity(new Intent(SettingsActivity.this, StaffEnrollActivity.class));
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
        findViewById(R.id.bt_sdk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, MainActivity.class));
            }
        });
    }

    private void fillCameraIds() {
        int n = 0;
        try {
            n = Camera.getNumberOfCameras();
        } catch (Exception e) {
            n = 2;
        }
        if (n < 2) {
            n = 2;
        }
        ArrayList<String> ids = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            ids.add("Camera " + i);
        }
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, R.layout.spinner_item_light, ids);
        ad.setDropDownViewResource(R.layout.spinner_dropdown_light);
        spRgb.setAdapter(ad);
        spIr.setAdapter(ad);
        int rgb = prefs.rgbCameraId();
        int ir = prefs.irCameraId();
        if (rgb < 0 || rgb >= n) {
            rgb = 1 % n;
        }
        if (ir < 0 || ir >= n) {
            ir = 0;
        }
        spRgb.setSelection(rgb);
        spIr.setSelection(ir);
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
        spLocation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
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

    private void goAttendance() {
        save();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, KioskActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("attendance", true);
        i.putExtra("mode", "attendance");
        startActivity(i);
        finish();
    }

    private void save() {
        prefs.setServerUrl(etServer.getText().toString().trim());
        prefs.setLicPath(etLic.getText().toString().trim());
        prefs.setCameraIds(spRgb.getSelectedItemPosition(), spIr.getSelectedItemPosition());
        prefs.setCameraOrientation(spOrient.getSelectedItemPosition());
        int pos = spLocation.getSelectedItemPosition();
        if (locationIds != null && pos >= 0 && pos < locationIds.length) {
            Object item = spLocation.getSelectedItem();
            prefs.setArea(locationIds[pos], item == null ? "" : item.toString());
        }
    }

    private void resync() {
        Toast.makeText(this, "Syncing students and staff…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject json = XanderApi.sync(prefs.serverUrl(), prefs.schoolId());
                    if (XanderApi.ok(json)) {
                        prefs.saveBootstrap(json);
                        store.replacePeople(json.optJSONArray("students"), json.optJSONArray("staff"));
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(SettingsActivity.this,
                                    XanderApi.ok(json) ? "Synced " + store.studentCount() + " students, " + store.staffCount() + " staff"
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
}
