package com.qonics.somanet;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AssetMonitorActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<JSONObject> rows = new ArrayList<>();
    private AssetAdapter adapter;
    private Runnable pending;
    private int searchGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.qonics.utils.MenuClearanceHelper.blockUnless(this, false)) return;
        setContentView(R.layout.activity_asset_monitor);
        ListView list = findViewById(R.id.list_assets);
        EditText search = findViewById(R.id.edt_search);
        adapter = new AssetAdapter();
        list.setAdapter(adapter);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        UiMotion.rise(findViewById(R.id.hero), 0);
        UiMotion.rise(findViewById(R.id.stats), 90);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { schedule(s.toString()); }
        });
        load("");
    }

    private void schedule(String query) {
        if (pending != null) handler.removeCallbacks(pending);
        pending = () -> load(query);
        handler.postDelayed(pending, 280);
    }

    private void load(String query) {
        int gen = ++searchGen;
        int staffId = StockFormat.staffId(this);
        String url = MainActivity.SERVER + "kass/assets/" + staffId + "?q=" + android.net.Uri.encode(query == null ? "" : query.trim());
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (isFinishing() || gen != searchGen) return;
                TextView note = findViewById(R.id.txt_note);
                String error = StockFormat.errorOf(object);
                rows.clear();
                if (object == null || error != null) {
                    note.setText(error != null ? error : getString(R.string.stock_try_again));
                    note.setOnClickListener(v -> load(query));
                    adapter.notifyDataSetChanged();
                    return;
                }
                note.setOnClickListener(null);
                ((TextView) findViewById(R.id.txt_total)).setText(object.optInt("total") + "\n" + getString(R.string.asset_total));
                ((TextView) findViewById(R.id.txt_use)).setText(object.optInt("inUse") + "\n" + getString(R.string.asset_in_use));
                ((TextView) findViewById(R.id.txt_attention)).setText(object.optInt("attention") + "\n" + getString(R.string.asset_attention));
                JSONArray list = object.optJSONArray("assets");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject row = list.optJSONObject(i);
                        if (row != null) rows.add(row);
                    }
                }
                if (rows.isEmpty()) {
                    note.setText(query == null || query.trim().isEmpty() ? getString(R.string.asset_empty) : getString(R.string.asset_none));
                } else if (object.optBoolean("truncated")) {
                    note.setText(getString(R.string.asset_truncated));
                } else {
                    note.setText("");
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    private class AssetAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public JSONObject getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView : LayoutInflater.from(AssetMonitorActivity.this).inflate(R.layout.item_asset_row, parent, false);
            JSONObject row = rows.get(position);
            ((TextView) view.findViewById(R.id.txtAssetName)).setText(row.optString("name"));
            TextView condition = view.findViewById(R.id.txtAssetCondition);
            String label = row.optString("condition");
            if (label.isEmpty()) label = row.optString("status");
            condition.setText(label);
            condition.setTextColor(tone(label + " " + row.optString("status")));
            String next = row.optString("next");
            ((TextView) view.findViewById(R.id.txtAssetMeta)).setText(row.optString("code") + "  ·  " + row.optString("category")
                    + (next.isEmpty() ? "" : "  ·  " + getString(R.string.asset_next, next)));
            ((TextView) view.findViewById(R.id.txtAssetPlace)).setText(row.optString("place") + "  ·  " + row.optString("status"));
            ((TextView) view.findViewById(R.id.txtAssetHolder)).setText(row.optString("holder"));
            return view;
        }
    }

    private int tone(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.US);
        if (value.contains("poor") || value.contains("damage") || value.contains("broken") || value.contains("critical") || value.contains("out of service")) {
            return Color.parseColor("#B42318");
        }
        if (value.contains("fair") || value.contains("maintenance") || value.contains("repair")) {
            return Color.parseColor("#C2410C");
        }
        return Color.parseColor("#1B7A3A");
    }
}
