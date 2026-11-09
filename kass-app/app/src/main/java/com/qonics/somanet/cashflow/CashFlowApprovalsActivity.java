package com.qonics.somanet.cashflow;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.qonics.Adapter.CashFlowRequestAdapter;
import com.qonics.model.CashFlowRequest;
import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CashFlowApprovalsActivity extends AppCompatActivity implements CashFlowRequestAdapter.ApprovalListener {

    private List<CashFlowRequest> items = new ArrayList<>();
    private CashFlowRequestAdapter adapter;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_flow_list);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        TextView title = findViewById(R.id.txtCfTitle);
        title.setText(R.string.cf_approvals);
        findViewById(R.id.btnCfNew).setVisibility(android.view.View.GONE);

        SwipeRefreshLayout swipe = findViewById(R.id.swipeCf);
        RecyclerView rv = findViewById(R.id.recyclerCf);
        adapter = new CashFlowRequestAdapter(items, true);
        adapter.setApprovalListener(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        swipe.setOnRefreshListener(this::load);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        int staffId = preferences.getInt("soma_id", 0);
        SwipeRefreshLayout swipe = findViewById(R.id.swipeCf);
        swipe.setRefreshing(true);
        String url = MainActivity.SERVER + "get_cash_flow_pending/" + schoolId + "/" + staffId;
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                swipe.setRefreshing(false);
                items.clear();
                if (object != null && object.has("requests")) {
                    try {
                        JSONArray arr = object.getJSONArray("requests");
                        for (int i = 0; i < arr.length(); i++) {
                            items.add(CashFlowRequest.fromJson(arr.getJSONObject(i)));
                        }
                    } catch (Exception ignored) {}
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onApprove(CashFlowRequest req, String action) {
        boolean needsReason = "return".equals(action) || "reject".equals(action);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.cf_confirm_action)
                .setMessage(req.getRequestNo())
                .setNegativeButton(R.string.cancel, null);
        if (needsReason) {
            EditText input = new EditText(this);
            input.setHint(R.string.cf_reason_hint);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            input.setPadding(pad, pad, pad, pad);
            builder.setView(input);
            builder.setPositiveButton(R.string.ok, (d, w) -> {
                String reason = input.getText().toString().trim();
                if (reason.isEmpty()) {
                    Toast.makeText(this, R.string.cf_reason_required, Toast.LENGTH_LONG).show();
                    return;
                }
                doAction(req, action, reason);
            });
        } else {
            builder.setPositiveButton(R.string.ok, (d, w) -> doAction(req, action, ""));
        }
        builder.show();
    }

    private void doAction(CashFlowRequest req, String action, String comment) {
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", String.valueOf(preferences.getInt(MainActivity.SK_ID, 0)));
        map.put("staff_id", String.valueOf(preferences.getInt("soma_id", 0)));
        map.put("request_id", String.valueOf(req.getId()));
        map.put("action", action);
        map.put("comment", comment == null ? "" : comment);
        new HttpRequest(this).post(MainActivity.SERVER + "cash_flow_request_action", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object != null && object.optBoolean("success", false)) {
                    Toast.makeText(CashFlowApprovalsActivity.this, R.string.cf_action_done, Toast.LENGTH_LONG).show();
                    load();
                } else {
                    String err = object != null ? object.optString("error", status) : status;
                    Toast.makeText(CashFlowApprovalsActivity.this, err, Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
