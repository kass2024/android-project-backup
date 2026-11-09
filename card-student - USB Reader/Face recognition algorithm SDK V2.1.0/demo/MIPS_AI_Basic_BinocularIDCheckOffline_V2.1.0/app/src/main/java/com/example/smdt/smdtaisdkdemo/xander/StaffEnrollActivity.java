package com.example.smdt.smdtaisdkdemo.xander;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smdt.smdtaisdkdemo.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Synced staff list. Live capture and school-photo enroll both return to the
 * kiosk FacePass engine (a second engine here used to crash Settings).
 */
public class StaffEnrollActivity extends Activity {
    private XanderPrefs prefs;
    private XanderStore store;
    private ListView list;
    private EditText etSearch;
    private StaffAdapter adapter;
    private ArrayList<JSONObject> all = new ArrayList<JSONObject>();
    private ArrayList<JSONObject> shown = new ArrayList<JSONObject>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xander_staff);
        prefs = new XanderPrefs(this);
        store = new XanderStore(this, prefs);
        list = (ListView) findViewById(R.id.list_staff);
        etSearch = (EditText) findViewById(R.id.et_search);
        adapter = new StaffAdapter();
        list.setAdapter(adapter);
        loadLocal();
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        findViewById(R.id.bt_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        findViewById(R.id.bt_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        View kiosk = findViewById(R.id.bt_kiosk);
        if (kiosk != null) {
            kiosk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(StaffEnrollActivity.this, KioskActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    i.putExtra("attendance", true);
                    i.putExtra("mode", "attendance");
                    startActivity(i);
                    finish();
                }
            });
        }
        list.setItemsCanFocus(true);
        KioskLog.i("Staff", "saved list ready — Record live works offline");
        loadLocal();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLocal();
    }

    private void loadLocal() {
        LinkedHashMap<Integer, JSONObject> map = new LinkedHashMap<Integer, JSONObject>();
        try {
            JSONArray arr = new JSONArray(prefs.staffJson());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && o.optInt("id", 0) > 0) {
                    map.put(o.optInt("id", 0), o);
                }
            }
        } catch (Exception ignored) {
        }
        JSONArray local = store.listStaff();
        for (int i = 0; i < local.length(); i++) {
            JSONObject o = local.optJSONObject(i);
            if (o == null) {
                continue;
            }
            int id = o.optInt("id", 0);
            if (id > 0 && !map.containsKey(id)) {
                map.put(id, o);
            }
        }
        all.clear();
        all.addAll(map.values());
        filter();
    }

    private void filter() {
        String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim().toLowerCase();
        shown.clear();
        for (int i = 0; i < all.size(); i++) {
            JSONObject o = all.get(i);
            String name = o.optString("name", "").toLowerCase();
            String post = o.optString("post", "").toLowerCase();
            if (q.length() == 0 || name.contains(q) || post.contains(q)) {
                shown.add(o);
            }
        }
        adapter.notifyDataSetChanged();
        TextView tv = (TextView) findViewById(R.id.tv_count);
        if (shown.size() == 0) {
            tv.setText("No synced staff yet. Tap Refresh list.");
        } else {
            tv.setText(shown.size() + " synced staff  ·  Record live or Use school photo");
        }
    }

    private void refresh() {
        Toast.makeText(this, "Refreshing synced staff…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject json = XanderApi.sync(prefs.serverUrl(), prefs.schoolId());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!XanderApi.ok(json)) {
                                Toast.makeText(StaffEnrollActivity.this,
                                        XanderApi.message(json, "Refresh failed"), Toast.LENGTH_SHORT).show();
                                loadLocal();
                                return;
                            }
                            prefs.saveBootstrap(json);
                            store.replacePeople(json.optJSONArray("students"), json.optJSONArray("staff"));
                            dropRemoteRemovedFaces(json.optJSONArray("staff"));
                            loadLocal();
                            Toast.makeText(StaffEnrollActivity.this,
                                    shown.size() + " staff loaded", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String err = e.getMessage();
                            if (XanderHttps.isDnsFail(e)) {
                                err = "School DNS is down. Record live still works. Tap Refresh later.";
                            } else {
                                err = KioskLog.friendlyNet(e);
                            }
                            KioskLog.e("Staff", "refresh: " + e.getClass().getSimpleName() + " " + e.getMessage());
                            Toast.makeText(StaffEnrollActivity.this, err, Toast.LENGTH_LONG).show();
                            loadLocal();
                        }
                    });
                }
            }
        }).start();
    }

    private void openKioskEnroll(JSONObject staff, boolean usePhoto) {
        int id = staff.optInt("id", 0);
        if (id <= 0) {
            Toast.makeText(this, "This staff record has no id", Toast.LENGTH_SHORT).show();
            return;
        }
        String photo = staff.optString("photo", "");
        if (usePhoto && photo.length() < 8) {
            Toast.makeText(this, "No school photo. Use Record live.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, KioskActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("enroll_staff_id", id);
        i.putExtra("enroll_staff_name", staff.optString("name", ""));
        if (usePhoto) {
            i.putExtra("enroll_photo_url", photo);
        }
        startActivity(i);
    }

    private void dropRemoteRemovedFaces(JSONArray staff) {
        if (staff == null) {
            return;
        }
        for (int i = 0; i < staff.length(); i++) {
            JSONObject o = staff.optJSONObject(i);
            if (o == null) {
                continue;
            }
            int id = o.optInt("id", 0);
            if (id <= 0) {
                continue;
            }
            int remote = o.optInt("face_enrolled", 0);
            boolean hadWeb = prefs.webFaceWasEnrolled(id);
            prefs.setWebFaceFlag(id, remote == 1);
            if (hadWeb && remote == 0) {
                removeLocalFace(id, false);
            }
        }
    }

    private void confirmRemoveFace(final JSONObject staff) {
        final int id = staff.optInt("id", 0);
        final String name = staff.optString("name", "this staff");
        if (id <= 0) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Remove face?")
                .setMessage("Clear the enrolled face for " + name + "? They will need to record a new live face.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeFace(id, name);
                    }
                })
                .show();
    }

    private void removeFace(final int staffId, final String name) {
        removeLocalFace(staffId, true);
        loadLocal();
        Toast.makeText(this, "Removing face for " + name + "…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg = "Face removed on this kiosk";
                try {
                    JSONObject json = XanderApi.removeFace(prefs.serverUrl(), prefs.schoolId(), staffId);
                    if (!XanderApi.ok(json)) {
                        msg = XanderApi.message(json, msg);
                    } else {
                        msg = XanderApi.message(json, msg);
                        prefs.setWebFaceFlag(staffId, false);
                    }
                } catch (Exception e) {
                    msg = "Face cleared here. School update failed — tap Refresh later.";
                }
                final String toast = msg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadLocal();
                        Toast.makeText(StaffEnrollActivity.this, toast, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void removeLocalFace(int staffId, boolean unbindEngine) {
        if (unbindEngine && KioskActivity.liveEngine != null) {
            KioskActivity.liveEngine.removeStaffFace(staffId);
        }
        prefs.clearFaceTokens(staffId);
        prefs.setStaffFaceEnrolled(staffId, false);
    }

    class StaffAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return shown.get(position).optInt("id", position); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_xander_staff, parent, false);
            }
            final JSONObject o = shown.get(position);
            TextView name = (TextView) convertView.findViewById(R.id.tv_name);
            TextView meta = (TextView) convertView.findViewById(R.id.tv_meta);
            TextView badge = (TextView) convertView.findViewById(R.id.tv_badge);
            Button live = (Button) convertView.findViewById(R.id.bt_live);
            Button photo = (Button) convertView.findViewById(R.id.bt_photo);
            Button remove = (Button) convertView.findViewById(R.id.bt_remove);
            name.setText(o.optString("name", "Staff"));
            String post = o.optString("post", "");
            if (post.length() == 0) {
                post = o.optString("extra", "");
            }
            String shift = o.optString("shift", "");
            meta.setText((post.length() == 0 ? "Staff" : post) + (shift.length() == 0 ? "" : "  ·  " + shift));
            int id = o.optInt("id", 0);
            boolean enrolled = prefs.hasFaceToken(id);
            badge.setText(enrolled ? "FACE READY" : "NO FACE");
            live.setClickable(true);
            photo.setClickable(true);
            live.setFocusable(false);
            photo.setFocusable(false);
            if (remove != null) {
                remove.setClickable(true);
                remove.setFocusable(false);
                remove.setVisibility(enrolled ? View.VISIBLE : View.GONE);
                remove.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { confirmRemoveFace(o); }
                });
            }
            badge.setBackgroundResource(enrolled ? R.drawable.bg_chip_ok : R.drawable.bg_chip_off);
            live.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openKioskEnroll(o, false); }
            });
            photo.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openKioskEnroll(o, true); }
            });
            return convertView;
        }
    }
}
