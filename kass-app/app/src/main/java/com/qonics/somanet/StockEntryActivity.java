package com.qonics.somanet;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockEntryActivity extends AppCompatActivity {
    private final List<ChoiceAdapter.Row> items = new ArrayList<>();
    private StockLineAdapter adapter;
    private int itemId;
    private boolean posting;
    private boolean filling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.qonics.utils.MenuClearanceHelper.blockUnless(this, true)) return;
        setContentView(R.layout.activity_stock_entry);
        ListView list = findViewById(R.id.list_items);
        EditText search = findViewById(R.id.edt_search);
        TextView picked = findViewById(R.id.txt_picked);
        TextView note = findViewById(R.id.txt_note);
        EditText qty = findViewById(R.id.edt_qty);
        EditText cost = findViewById(R.id.edt_cost);
        EditText invoice = findViewById(R.id.edt_invoice);
        Button post = findViewById(R.id.btn_post);
        adapter = new StockLineAdapter(this, false);
        list.setAdapter(adapter);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (filling) return;
                showItems(s.toString());
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> selectItem(adapter.getItem(position)));
        post.setOnClickListener(v -> submit(qty, cost, invoice, note, post));
        UiMotion.rise(findViewById(R.id.txt_title), 0);
        UiMotion.rise(findViewById(R.id.dock), 80);
        load();
    }

    private void load() {
        int staffId = StockFormat.staffId(this);
        new HttpRequest(this).get(MainActivity.SERVER + "kass/stock/" + staffId, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (isFinishing()) return;
                String error = StockFormat.errorOf(object);
                TextView note = findViewById(R.id.txt_note);
                if (object == null || error != null) {
                    note.setTextColor(0xFFB42318);
                    note.setText(error != null ? error : getString(R.string.stock_try_again));
                    note.setOnClickListener(v -> load());
                    return;
                }
                note.setTextColor(0xFF64748B);
                note.setOnClickListener(null);
                items.clear();
                JSONArray rows = object.optJSONArray("items");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row == null) continue;
                        double onHand = row.optDouble("onHand");
                        String unit = row.optString("unit");
                        items.add(new ChoiceAdapter.Row(
                                row.optInt("id"),
                                row.optString("name"),
                                row.optString("code") + "  ·  " + StockFormat.qty(onHand) + " " + unit,
                                row.optBoolean("low"),
                                onHand
                        ));
                    }
                }
                note.setText(object.optString("store"));
                showItems("");
            }
        });
    }

    private void showItems(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<ChoiceAdapter.Row> shown = new ArrayList<>();
        for (ChoiceAdapter.Row row : items) {
            if (needle.isEmpty() || row.title.toLowerCase().contains(needle) || row.detail.toLowerCase().contains(needle)) {
                shown.add(row);
                if (shown.size() == 30) break;
            }
        }
        adapter.setRows(shown);
        adapter.setSelected(itemId, 0);
        TextView count = findViewById(R.id.txt_count);
        if (count != null) count.setText(getString(R.string.stock_shown, shown.size()));
    }

    private void selectItem(ChoiceAdapter.Row row) {
        if (row == null) return;
        itemId = row.id;
        TextView picked = findViewById(R.id.txt_picked);
        picked.setText(row.title + "  ·  " + row.detail);
        picked.setVisibility(android.view.View.VISIBLE);
        UiMotion.pop(picked);
        adapter.setSelected(itemId, 0);
    }

    private void submit(EditText qty, EditText cost, EditText invoice, TextView note, Button post) {
        if (posting) return;
        if (itemId < 1) {
            note.setTextColor(0xFFB42318);
            note.setText(getString(R.string.stock_pick_item));
            return;
        }
        double amount = parse(qty.getText().toString());
        double unitCost = cost.getText().toString().trim().isEmpty() ? 0 : parse(cost.getText().toString());
        if (amount <= 0 || unitCost < 0) {
            note.setTextColor(0xFFB42318);
            note.setText(getString(R.string.stock_qty_required));
            return;
        }
        posting = true;
        post.setEnabled(false);
        Map<String, String> body = new HashMap<>();
        body.put("itemId", String.valueOf(itemId));
        body.put("qty", String.valueOf(amount));
        body.put("unitCost", String.valueOf(unitCost));
        body.put("invoice", invoice.getText().toString().trim());
        body.put("remarks", "");
        int staffId = StockFormat.staffId(this);
        new HttpRequest(this).post(MainActivity.SERVER + "kass/stock/entry/" + staffId, body, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                posting = false;
                post.setEnabled(true);
                if (isFinishing()) return;
                String error = StockFormat.errorOf(object);
                if (object == null || error != null) {
                    note.setTextColor(0xFFB42318);
                    note.setText(error != null ? error : status);
                    return;
                }
                String ref = object.optString("ref");
                note.setTextColor(0xFF1B7A3A);
                note.setText(ref.isEmpty() ? getString(R.string.stock_entry_saved) : getString(R.string.stock_entry_saved_ref, ref));
                Toast.makeText(StockEntryActivity.this, note.getText(), Toast.LENGTH_SHORT).show();
                itemId = 0;
                TextView pickedView = findViewById(R.id.txt_picked);
                pickedView.setText("");
                pickedView.setVisibility(android.view.View.GONE);
                qty.setText("");
                cost.setText("");
                invoice.setText("");
                load();
            }
        });
    }

    private double parse(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }
}
