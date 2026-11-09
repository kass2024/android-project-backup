package com.qonics.somanet.cashflow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

public class CashFlowActivity extends AppCompatActivity {

    private SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;
    private Runnable pollRunnable;
    private SwipeRefreshLayout swipe;
    private TextView txtKpiActive, txtKpiPending, txtKpiAwaitPay, txtKpiAwaitReceipt, txtKpiPaid, txtKpiMine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_flow);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        TextView txtinfo = findViewById(R.id.txtinfo);
        txtinfo.setText(preferences.getString("soma_name", "") + "\n"
                + preferences.getString("soma_post_title", ""));

        swipe = findViewById(R.id.swipeCashFlow);
        txtKpiActive = findViewById(R.id.txtKpiActive);
        txtKpiPending = findViewById(R.id.txtKpiPending);
        txtKpiAwaitPay = findViewById(R.id.txtKpiAwaitPay);
        txtKpiAwaitReceipt = findViewById(R.id.txtKpiAwaitReceipt);
        txtKpiPaid = findViewById(R.id.txtKpiPaid);
        txtKpiMine = findViewById(R.id.txtKpiMine);

        LinearLayout lnlScanner = findViewById(R.id.lnl_camscanner);
        LinearLayout lnlRequests = findViewById(R.id.lnl_requests);
        LinearLayout lnlApprovals = findViewById(R.id.lnl_approvals);
        LinearLayout lnlNew = findViewById(R.id.lnl_new_request);

        lnlScanner.setOnClickListener(v -> {
            Intent i = new Intent(this, DocumentScanActivity.class);
            i.putExtra("mode", "standalone");
            startActivity(i);
        });
        lnlRequests.setOnClickListener(v -> startActivity(new Intent(this, CashFlowRequestsActivity.class)));
        lnlApprovals.setOnClickListener(v -> startActivity(new Intent(this, CashFlowApprovalsActivity.class)));
        lnlNew.setOnClickListener(v -> startActivity(new Intent(this, CashFlowRequestActivity.class)));

        int post = preferences.getInt("soma_post", 0);
        boolean canCreate = post == 8 || post == 9 || post == 28;
        boolean canApprove = post == 1 || post == 18 || post == 24 || post == 25 || post == 26 || post == 28;
        lnlNew.setVisibility(canCreate ? android.view.View.VISIBLE : android.view.View.GONE);
        lnlApprovals.setVisibility(canApprove ? android.view.View.VISIBLE : android.view.View.GONE);
        if (post == 28) {
            txtinfo.append("\n" + getString(R.string.cf_role_chief));
        } else if (post == 24) {
            txtinfo.append("\n" + getString(R.string.cf_role_finance));
        } else if (post == 8 || post == 9) {
            txtinfo.append("\n" + getString(R.string.cf_role_accountant));
        }

        swipe.setOnRefreshListener(this::loadKpis);
        loadKpis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startScanPolling();
        loadKpis();
    }

    @Override
    protected void onPause() {
        stopScanPolling();
        super.onPause();
    }

    private void loadKpis() {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        int staffId = preferences.getInt("soma_id", 0);
        if (schoolId <= 0 || staffId <= 0) {
            if (swipe != null) swipe.setRefreshing(false);
            return;
        }
        String url = MainActivity.SERVER + "get_cash_flow_stats/" + schoolId + "/" + staffId;
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (swipe != null) swipe.setRefreshing(false);
                if (object == null || object.has("error")) {
                    // Fallback: derive basic KPIs from requests + pending lists
                    loadKpiFallback(schoolId, staffId);
                    return;
                }
                txtKpiActive.setText(String.valueOf(object.optInt("active_requests", 0)));
                txtKpiPending.setText(String.valueOf(object.optInt("pending_approvals", 0)));
                txtKpiAwaitPay.setText(String.valueOf(object.optInt("awaiting_payment", 0)));
                txtKpiAwaitReceipt.setText(String.valueOf(object.optInt("awaiting_receipt", 0)));
                NumberFormat nf = NumberFormat.getInstance(Locale.US);
                double paid = object.optDouble("total_paid", 0);
                txtKpiPaid.setText(nf.format(paid) + " RWF");
                txtKpiMine.setText(getString(R.string.cf_kpi_mine_fmt,
                        object.optInt("my_active", 0),
                        object.optInt("my_drafts", 0)));
            }
        });
    }

    private void loadKpiFallback(int schoolId, int staffId) {
        new HttpRequest(this).get(MainActivity.SERVER + "get_cash_flow_requests/" + schoolId + "/" + staffId,
                JSONObject.class, new Callback() {
                    @Override
                    public void HttpCallback(JSONObject mine, int status_code, String status) {
                        int myActive = 0, myDrafts = 0;
                        if (mine != null && mine.has("requests")) {
                            org.json.JSONArray arr = mine.optJSONArray("requests");
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    String st = arr.optJSONObject(i) != null
                                            ? arr.optJSONObject(i).optString("status", "") : "";
                                    if ("DRAFT".equals(st) || "RETURNED_TO_ACCOUNTANT".equals(st)) myDrafts++;
                                    else if (!"CLOSED".equals(st) && !"CANCELLED".equals(st)
                                            && !"REJECTED".equals(st) && !"VOIDED".equals(st)) myActive++;
                                }
                            }
                        }
                        final int fActive = myActive;
                        final int fDrafts = myDrafts;
                        new HttpRequest(CashFlowActivity.this).get(
                                MainActivity.SERVER + "get_cash_flow_pending/" + schoolId + "/" + staffId,
                                JSONObject.class, new Callback() {
                                    @Override
                                    public void HttpCallback(JSONObject pend, int sc, String st) {
                                        int pending = 0;
                                        if (pend != null && pend.has("requests")) {
                                            org.json.JSONArray a = pend.optJSONArray("requests");
                                            pending = a != null ? a.length() : 0;
                                        }
                                        txtKpiActive.setText(String.valueOf(fActive));
                                        txtKpiPending.setText(String.valueOf(pending));
                                        txtKpiAwaitPay.setText("—");
                                        txtKpiAwaitReceipt.setText("—");
                                        txtKpiPaid.setText("—");
                                        txtKpiMine.setText(getString(R.string.cf_kpi_mine_fmt, fActive, fDrafts));
                                    }
                                });
                    }
                });
    }

    private void startScanPolling() {
        if (polling) return;
        polling = true;
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                checkPendingScan();
                if (polling) handler.postDelayed(this, 1500);
            }
        };
        handler.post(pollRunnable);
    }

    private void stopScanPolling() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void checkPendingScan() {
        int staffId = preferences.getInt("soma_id", 0);
        if (staffId <= 0) return;
        String url = MainActivity.SERVER + "cash_flow_scan_pending/" + staffId;
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                ScanBridgeWatcher.consider(CashFlowActivity.this, object);
            }
        });
    }
}
