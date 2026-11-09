package com.qonics.somanet.cashflow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
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

public class CashFlowRequestsActivity extends AppCompatActivity implements CashFlowRequestAdapter.ApprovalListener {

    private List<CashFlowRequest> items = new ArrayList<>();
    private CashFlowRequestAdapter adapter;
    private SwipeRefreshLayout swipe;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_flow_list);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        TextView title = findViewById(R.id.txtCfTitle);
        title.setText(R.string.cf_my_requests);

        swipe = findViewById(R.id.swipeCf);
        RecyclerView rv = findViewById(R.id.recyclerCf);
        adapter = new CashFlowRequestAdapter(items, true);
        adapter.setApprovalListener(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(new DefaultItemAnimator());
        rv.setAdapter(adapter);

        Button btnNew = findViewById(R.id.btnCfNew);
        int post = preferences.getInt("soma_post", 0);
        boolean canCreate = post == 8 || post == 9 || post == 28;
        btnNew.setVisibility(canCreate ? android.view.View.VISIBLE : android.view.View.GONE);
        btnNew.setOnClickListener(v -> startActivity(new Intent(this, CashFlowRequestActivity.class)));

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
        swipe.setRefreshing(true);
        String url = MainActivity.SERVER + "get_cash_flow_requests/" + schoolId + "/" + staffId;
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                swipe.setRefreshing(false);
                items.clear();
                if (object != null && object.has("requests")) {
                    try {
                        JSONArray arr = object.getJSONArray("requests");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            items.add(CashFlowRequest.fromJson(o));
                        }
                    } catch (Exception ignored) {}
                } else if (object != null && object.has("error")) {
                    Toast.makeText(CashFlowRequestsActivity.this, object.optString("error"), Toast.LENGTH_LONG).show();
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onApprove(CashFlowRequest req, String action) {
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", String.valueOf(preferences.getInt(MainActivity.SK_ID, 0)));
        map.put("staff_id", String.valueOf(preferences.getInt("soma_id", 0)));
        map.put("request_id", String.valueOf(req.getId()));
        map.put("action", action);
        map.put("comment", "Resubmitted from SmartSMS");
        new HttpRequest(this).post(MainActivity.SERVER + "cash_flow_request_action", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object != null && object.optBoolean("success", false)) {
                    Toast.makeText(CashFlowRequestsActivity.this, R.string.cf_action_done, Toast.LENGTH_LONG).show();
                    load();
                } else {
                    String err = object != null ? object.optString("error", status) : status;
                    Toast.makeText(CashFlowRequestsActivity.this, err, Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
