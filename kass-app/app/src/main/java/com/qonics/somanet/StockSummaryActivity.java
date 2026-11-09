package com.qonics.somanet;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

public class StockSummaryActivity extends AppCompatActivity {
    private static final int[] INK = {
            0xFF2563EB, 0xFF059669, 0xFFEA580C, 0xFF7C3AED, 0xFFDB2777
    };
    private static final int[] WASH = {
            0xFFDBEAFE, 0xFFD1FAE5, 0xFFFFEDD5, 0xFFEDE9FE, 0xFFFCE7F3
    };

    private final List<JSONObject> rows = new ArrayList<>();
    private final List<JSONObject> shown = new ArrayList<>();
    private SummaryAdapter adapter;
    private String period = "daily";
    private String query = "";
    private boolean failed;
    private TextView daily;
    private TextView weekly;
    private TextView monthly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.qonics.utils.MenuClearanceHelper.blockUnless(this, true)) return;
        setContentView(R.layout.activity_stock_summary);
        daily = findViewById(R.id.btn_daily);
        weekly = findViewById(R.id.btn_weekly);
        monthly = findViewById(R.id.btn_monthly);
        ListView list = findViewById(R.id.list_rows);
        adapter = new SummaryAdapter();
        list.setAdapter(adapter);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        UiMotion.rise(findViewById(R.id.txt_title), 0);
        UiMotion.rise(findViewById(R.id.chips), 70);
        daily.setOnClickListener(v -> { UiMotion.pop(v); load("daily"); });
        weekly.setOnClickListener(v -> { UiMotion.pop(v); load("weekly"); });
        monthly.setOnClickListener(v -> { UiMotion.pop(v); load("monthly"); });
        ((EditText) findViewById(R.id.edt_search)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                applyFilter();
            }
        });
        load("daily");
    }

    private void load(String next) {
        period = next;
        paintChips();
        int staffId = StockFormat.staffId(this);
        new HttpRequest(this).get(MainActivity.SERVER + "kass/stock/summary/" + staffId + "?period=" + next, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (isFinishing()) return;
                TextView note = findViewById(R.id.txt_note);
                TextView label = findViewById(R.id.txt_label);
                String error = StockFormat.errorOf(object);
                rows.clear();
                if (object == null || error != null) {
                    failed = true;
                    note.setTextColor(0xFFB42318);
                    note.setText(error != null ? error : getString(R.string.stock_try_again));
                    note.setOnClickListener(v -> load(period));
                    applyFilter();
                    return;
                }
                failed = false;
                note.setTextColor(0xFF64748B);
                note.setOnClickListener(null);
                label.setText(object.optString("label"));
                JSONArray list = object.optJSONArray("rows");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject row = list.optJSONObject(i);
                        if (row != null) rows.add(row);
                    }
                }
                applyFilter();
            }
        });
    }

    private void applyFilter() {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        shown.clear();
        for (JSONObject row : rows) {
            String name = row.optString("name").toLowerCase(Locale.US);
            if (needle.isEmpty() || name.contains(needle)) {
                shown.add(row);
            }
        }
        if (!failed) {
            ((TextView) findViewById(R.id.txt_note)).setText(rows.isEmpty() ? getString(R.string.stock_no_movement) : "");
        }
        adapter.notifyDataSetChanged();
    }

    private void paintChips() {
        style(daily, "daily".equals(period));
        style(weekly, "weekly".equals(period));
        style(monthly, "monthly".equals(period));
    }

    private void style(TextView chip, boolean on) {
        chip.setBackgroundResource(on ? R.drawable.btn_stock : R.drawable.bg_chip_off);
        chip.setTextColor(on ? Color.WHITE : Color.parseColor("#0F172A"));
    }

    private class SummaryAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public JSONObject getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView : LayoutInflater.from(StockSummaryActivity.this).inflate(R.layout.item_summary_row, parent, false);
            JSONObject row = shown.get(position);
            String name = row.optString("name");
            String unit = row.optString("unit");
            TextView mark = view.findViewById(R.id.txtSumMark);
            String letter = name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.US);
            mark.setText(letter);
            GradientDrawable disk = new GradientDrawable();
            disk.setShape(GradientDrawable.OVAL);
            disk.setColor(WASH[position % WASH.length]);
            mark.setBackground(disk);
            mark.setTextColor(INK[position % INK.length]);
            ((TextView) view.findViewById(R.id.txtSumName)).setText(name);
            ((TextView) view.findViewById(R.id.txtSumUnit)).setText(unit);
            ((TextView) view.findViewById(R.id.txtSumOpen)).setText(StockFormat.qty(row.optDouble("opening")));
            ((TextView) view.findViewById(R.id.txtSumIn)).setText(StockFormat.qty(row.optDouble("received")));
            ((TextView) view.findViewById(R.id.txtSumOut)).setText(StockFormat.qty(row.optDouble("issued")));
            ((TextView) view.findViewById(R.id.txtSumClose)).setText(StockFormat.qty(row.optDouble("closing")));
            return view;
        }
    }
}
