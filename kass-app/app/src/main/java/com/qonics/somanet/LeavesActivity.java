package com.qonics.somanet;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.qonics.Adapter.LeaveAdapter;
import com.qonics.model.Leave;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.MenuClearanceHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LeavesActivity extends AppCompatActivity {
    Button button;
    TextView txtinfo,name,kpiWait,kpiOut,kpiBack,kpiUnused;
    SwipeRefreshLayout swipe;
    SharedPreferences preferences;
    private List<Leave> leaveList = new ArrayList<>();
    private RecyclerView recyclerView;
    private LeaveAdapter mAdapter;

    HttpRequest req;
    String server = MainActivity.SERVER;
    boolean customerCare;
    boolean canManage;
    boolean manageMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaves);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        manageMode = getIntent().getBooleanExtra("manage", false);
        TextView header = findViewById(R.id.textView3);
        if (header != null) {
            header.setText(manageMode ? R.string.das_leave_management : R.string.das_leave);
        }
        txtinfo = findViewById(R.id.txtinfo);
        txtinfo.setText(preferences.getString("soma_name","")+"\n"
                +preferences.getString("soma_post_title",""));

        name = findViewById(R.id.name);
        kpiWait = findViewById(R.id.kpiWait);
        kpiOut = findViewById(R.id.kpiOut);
        kpiBack = findViewById(R.id.kpiBack);
        kpiUnused = findViewById(R.id.kpiUnused);
        swipe = findViewById(R.id.swipe);

        button = findViewById(R.id.btnrequest);
        button.setVisibility(manageMode ? View.GONE : View.VISIBLE);
        button.setOnClickListener(view -> openRequest());
        if (kpiWait.getParent() instanceof View) {
            ((View) kpiWait.getParent()).setVisibility(manageMode ? View.VISIBLE : View.GONE);
        }

        recyclerView = findViewById(R.id.recycler_view);

        mAdapter = new LeaveAdapter(leaveList,LeavesActivity.this) {
            @Override
            public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return super.onCreateViewHolder(parent, viewType);
            }
        };

        swipe.setOnRefreshListener(() -> {
            getLeaves();
        });
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);

        getLeaves();

    }
    private boolean isMine(Leave row) {
        String me = preferences.getString("soma_name", "");
        if (me == null) return false;
        String staff = row.getStaff().replaceAll("\\s+", " ").trim();
        String mine = me.replaceAll("\\s+", " ").trim();
        return !mine.isEmpty() && staff.equalsIgnoreCase(mine);
    }

    private void openRequest() {
        Intent intent = new Intent(LeavesActivity.this, RequestActivity.class);
        intent.putExtra("customer_care", customerCare);
        startActivityForResult(intent, 0);
    }

    private void getLeaves() {
        req = new HttpRequest(this);
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("actor_id", preferences.getInt("soma_id", 0));
        swipe.setRefreshing(true);
        req.post(server + "staff_leave_board", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                swipe.setRefreshing(false);
                if (object == null) {
                    Toast.makeText(LeavesActivity.this, getString(R.string.lbl_error) + status, Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    if (object.has("error")) {
                        Toast.makeText(LeavesActivity.this, object.getString("error"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    String postTitle = preferences.getString("soma_post_title", "");
                    if (postTitle == null) postTitle = "";
                    customerCare = object.optInt("customer_care", 0) == 1
                            || postTitle.toLowerCase().contains("customer care");
                    canManage = manageMode && (object.optInt("can_manage", 0) == 1
                            || MenuClearanceHelper.canOpenLeaveMenu(preferences));
                    mAdapter.setManage(canManage, new LeaveAdapter.LeaveAction() {
                        @Override
                        public void approve(Leave leave) {
                            decide(leave, 1, "");
                        }

                        @Override
                        public void deny(Leave leave) {
                            askDeny(leave);
                        }
                    });
                    leaveList.clear();
                    JSONArray leaves = object.optJSONArray("leaves");
                    if (leaves != null) {
                        for (int a = 0; a < leaves.length(); a++) {
                            JSONObject leave = leaves.getJSONObject(a);
                            int days = (int) Math.round(leave.optDouble("days", 0));
                            Leave row = new Leave(leave.getInt("id"), leave.getInt("type"), leave.optString("reason"), days,
                                    leave.getInt("fromDate"), leave.getInt("toDate"), leave.optString("address"), leave.getInt("status"));
                            row.setPeople(leave.optString("staff"), leave.optString("approver"), leave.optString("email"),
                                    leave.optString("trip"), leave.optString("went_out_at"), leave.optString("returned_at"));
                            if (manageMode || customerCare || isMine(row)) {
                                leaveList.add(row);
                            }
                        }
                    }
                    mAdapter.notifyDataSetChanged();
                    int waiting = 0, out = 0, back = 0, unused = 0;
                    for (Leave row : leaveList) {
                        if (row.getStatus() == 0) waiting++;
                        else if ("out".equals(row.getTrip())) out++;
                        else if ("back".equals(row.getTrip())) back++;
                        else if ("unused".equals(row.getTrip())) unused++;
                    }
                    kpiWait.setText(String.valueOf(waiting));
                    kpiOut.setText(String.valueOf(out));
                    kpiBack.setText(String.valueOf(back));
                    kpiUnused.setText(String.valueOf(unused));
                } catch (JSONException e) {
                    Log.e("leave", "board", e);
                }
            }
        });
    }

    private void askDeny(Leave leave) {
        EditText input = new EditText(this);
        input.setHint(R.string.lbl_reason);
        new AlertDialog.Builder(this)
                .setTitle(R.string.leave_deny)
                .setView(input)
                .setPositiveButton(R.string.leave_deny, (dialog, which) -> decide(leave, 2, input.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void decide(Leave leave, int decision, String reason) {
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("actor_id", preferences.getInt("soma_id", 0));
        map.put("leave_id", leave.getId());
        map.put("decision", decision);
        map.put("deny_reason", reason);
        new HttpRequest(this).post(server + "staff_leave_decide", map, (object, status_code, status) -> {
            if (object == null) {
                Toast.makeText(this, getString(R.string.lbl_error) + status, Toast.LENGTH_LONG).show();
                return;
            }
            if (object.has("error")) {
                Toast.makeText(this, object.optString("error"), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, object.optString("success"), Toast.LENGTH_LONG).show();
            getLeaves();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK){
            getLeaves();
        }
    }
}

