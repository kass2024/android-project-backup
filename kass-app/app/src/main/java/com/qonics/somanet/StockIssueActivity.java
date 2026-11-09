package com.qonics.somanet;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.Locale;
import java.util.Map;

public class StockIssueActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ChoiceAdapter.Row> items = new ArrayList<>();
    private ChoiceAdapter staffAdapter;
    private StockLineAdapter itemAdapter;
    private Runnable staffSearch;
    private int receiverId;
    private int itemId;
    private double onHand;
    private boolean posting;
    private boolean filling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.qonics.utils.MenuClearanceHelper.blockUnless(this, true)) return;
        setContentView(R.layout.activity_stock_issue);
        EditText staffBox = findViewById(R.id.edt_staff);
        EditText itemBox = findViewById(R.id.edt_item);
        ListView staffList = findViewById(R.id.list_staff);
        ListView itemList = findViewById(R.id.list_items);
        TextView staffPicked = findViewById(R.id.txt_staff);
        TextView itemPicked = findViewById(R.id.txt_item);
        staffAdapter = new ChoiceAdapter(this);
        itemAdapter = new StockLineAdapter(this, true);
        itemAdapter.setListener(new StockLineAdapter.Listener() {
            @Override public void onPick(ChoiceAdapter.Row row) { selectItem(row, true); }
            @Override public void onStep(ChoiceAdapter.Row row, int delta) { stepItem(row, delta); }
        });
        staffList.setAdapter(staffAdapter);
        itemList.setAdapter(itemAdapter);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        UiMotion.rise(findViewById(R.id.txt_title), 0);
        staffBox.addTextChangedListener(simple(s -> {
            if (filling) return;
            receiverId = 0;
            staffPicked.setText(getString(R.string.stock_staff_hint));
            staffPicked.setTextColor(0xFF64748B);
            String query = s.toString().trim();
            if (query.isEmpty()) {
                staffAdapter.setRows(new ArrayList<>());
                showStaffResults(false);
                return;
            }
            showStaffResults(true);
            scheduleStaff(query);
        }));
        itemBox.addTextChangedListener(simple(s -> {
            if (filling) return;
            showItems(s.toString());
        }));
        ((EditText) findViewById(R.id.edt_qty)).addTextChangedListener(simple(s -> {
            if (filling) return;
            itemAdapter.setSelected(itemId, parseQty(s.toString()));
        }));
        ((EditText) findViewById(R.id.edt_purpose)).addTextChangedListener(simple(s -> {
            TextView count = findViewById(R.id.txt_purpose_count);
            if (count != null) count.setText(s.toString().length() + "/100");
        }));
        staffList.setOnItemClickListener((parent, view, position, id) -> {
            ChoiceAdapter.Row row = staffAdapter.getItem(position);
            filling = true;
            receiverId = row.id;
            staffBox.setText(row.title);
            filling = false;
            staffPicked.setTextColor(0xFF2563EB);
            staffPicked.setText(row.detail.isEmpty() ? row.title : row.title + "  ·  " + row.detail);
            UiMotion.pop(staffPicked);
            staffAdapter.setRows(new ArrayList<>());
            showStaffResults(false);
        });
        itemList.setOnItemClickListener((parent, view, position, id) -> selectItem(itemAdapter.getItem(position), true));
        findViewById(R.id.btn_post).setOnClickListener(v -> submit());
        findViewById(R.id.txt_note).setOnClickListener(v -> loadItems());
        loadItems();
    }

    /** Staff matches take the screen while a name is being typed. */
    private void showStaffResults(boolean searching) {
        findViewById(R.id.list_staff).setVisibility(searching ? android.view.View.VISIBLE : android.view.View.GONE);
        int rest = searching ? android.view.View.GONE : android.view.View.VISIBLE;
        findViewById(R.id.edt_item_row).setVisibility(rest);
        findViewById(R.id.hdr_items).setVisibility(rest);
        findViewById(R.id.list_items).setVisibility(rest);
        findViewById(R.id.dock).setVisibility(rest);
    }

    private void scheduleStaff(String query) {
        if (staffSearch != null) handler.removeCallbacks(staffSearch);
        staffSearch = () -> loadStaff(query);
        handler.postDelayed(staffSearch, 280);
    }

    private void loadStaff(String query) {
        int staffId = StockFormat.staffId(this);
        String url = MainActivity.SERVER + "kass/stock/staff/" + staffId + "?q=" + android.net.Uri.encode(query == null ? "" : query.trim());
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (isFinishing() || receiverId > 0) return;
                List<ChoiceAdapter.Row> rows = new ArrayList<>();
                JSONArray list = object == null ? null : object.optJSONArray("staff");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject row = list.optJSONObject(i);
                        if (row == null) continue;
                        rows.add(new ChoiceAdapter.Row(row.optInt("id"), row.optString("name"), row.optString("post"), false, 0));
                    }
                }
                staffAdapter.setRows(rows);
            }
        });
    }

    private void loadItems() {
        int staffId = StockFormat.staffId(this);
        new HttpRequest(this).get(MainActivity.SERVER + "kass/stock/" + staffId, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (isFinishing()) return;
                String error = StockFormat.errorOf(object);
                if (object == null || error != null) {
                    ((TextView) findViewById(R.id.txt_note)).setText(error != null ? error : getString(R.string.stock_try_again));
                    return;
                }
                items.clear();
                JSONArray rows = object.optJSONArray("items");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row == null) continue;
                        double qty = row.optDouble("onHand");
                        items.add(new ChoiceAdapter.Row(
                                row.optInt("id"),
                                row.optString("name"),
                                StockFormat.qty(qty) + " " + row.optString("unit") + " on hand",
                                row.optBoolean("low"),
                                qty
                        ));
                    }
                }
                showItems("");
            }
        });
    }

    private void showItems(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        List<ChoiceAdapter.Row> shown = new ArrayList<>();
        for (ChoiceAdapter.Row row : items) {
            if (needle.isEmpty() || row.title.toLowerCase(Locale.US).contains(needle)) {
                shown.add(row);
                if (shown.size() == 30) break;
            }
        }
        itemAdapter.setRows(shown);
        itemAdapter.setSelected(itemId, parseQty(((EditText) findViewById(R.id.edt_qty)).getText().toString()));
        TextView count = findViewById(R.id.txt_count);
        if (count != null) count.setText(getString(R.string.stock_shown, shown.size()));
    }

    private void selectItem(ChoiceAdapter.Row row, boolean resetQty) {
        if (row == null) return;
        boolean changed = itemId != row.id;
        itemId = row.id;
        onHand = row.amount;
        TextView itemPicked = findViewById(R.id.txt_item);
        itemPicked.setText(row.title + "  ·  " + row.detail);
        itemPicked.setVisibility(android.view.View.VISIBLE);
        if (changed && resetQty) {
            filling = true;
            ((EditText) findViewById(R.id.edt_qty)).setText("");
            filling = false;
        }
        itemAdapter.setSelected(itemId, parseQty(((EditText) findViewById(R.id.edt_qty)).getText().toString()));
    }

    private void stepItem(ChoiceAdapter.Row row, int delta) {
        if (row == null) return;
        EditText qtyBox = findViewById(R.id.edt_qty);
        double current = itemId == row.id ? parseQty(qtyBox.getText().toString()) : 0;
        current = Math.max(0, current + delta);
        if (itemId != row.id) {
            itemId = row.id;
            onHand = row.amount;
            TextView itemPicked = findViewById(R.id.txt_item);
            itemPicked.setText(row.title + "  ·  " + row.detail);
            itemPicked.setVisibility(android.view.View.VISIBLE);
        }
        filling = true;
        qtyBox.setText(current == 0 ? "" : StockFormat.qty(current));
        filling = false;
        itemAdapter.setSelected(itemId, current);
    }

    private double parseQty(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }

    private void submit() {
        if (posting) return;
        TextView note = findViewById(R.id.txt_note);
        EditText qtyBox = findViewById(R.id.edt_qty);
        EditText purpose = findViewById(R.id.edt_purpose);
        if (receiverId < 1) {
            note.setTextColor(0xFFB42318);
            note.setText(getString(R.string.stock_pick_staff));
            return;
        }
        if (itemId < 1) {
            note.setTextColor(0xFFB42318);
            note.setText(getString(R.string.stock_pick_item));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(qtyBox.getText().toString().trim().replace(',', '.'));
        } catch (Exception e) {
            amount = 0;
        }
        if (amount <= 0) {
            note.setTextColor(0xFFB42318);
            note.setText(getString(R.string.stock_qty_required));
            return;
        }
        if (amount > onHand) {
            note.setTextColor(0xFFB42318);
            note.setText(getString(R.string.stock_not_enough, StockFormat.qty(onHand)));
            return;
        }
        posting = true;
        Button post = findViewById(R.id.btn_post);
        post.setEnabled(false);
        Map<String, String> body = new HashMap<>();
        body.put("receiverId", String.valueOf(receiverId));
        body.put("itemId", String.valueOf(itemId));
        body.put("qty", String.valueOf(amount));
        body.put("purpose", purpose.getText().toString().trim());
        int staffId = StockFormat.staffId(this);
        new HttpRequest(this).post(MainActivity.SERVER + "kass/stock/issue/" + staffId, body, new Callback() {
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
                String person = object.optString("person");
                note.setTextColor(0xFF1B7A3A);
                note.setText(getString(R.string.stock_issue_saved, ref, person));
                Toast.makeText(StockIssueActivity.this, note.getText(), Toast.LENGTH_SHORT).show();
                receiverId = 0;
                itemId = 0;
                onHand = 0;
                TextView staffView = findViewById(R.id.txt_staff);
                TextView itemView = findViewById(R.id.txt_item);
                staffView.setText(getString(R.string.stock_staff_hint));
                staffView.setTextColor(0xFF64748B);
                itemView.setText("");
                itemView.setVisibility(android.view.View.GONE);
                filling = true;
                ((EditText) findViewById(R.id.edt_staff)).setText("");
                filling = false;
                qtyBox.setText("");
                purpose.setText("");
                TextView purposeCount = findViewById(R.id.txt_purpose_count);
                if (purposeCount != null) purposeCount.setText(getString(R.string.stock_purpose_count));
                loadItems();
                scheduleStaff("");
            }
        });
    }

    private TextWatcher simple(final After after) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int afterCount) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { after.run(s.toString()); }
        };
    }

    private interface After { void run(String text); }
}
