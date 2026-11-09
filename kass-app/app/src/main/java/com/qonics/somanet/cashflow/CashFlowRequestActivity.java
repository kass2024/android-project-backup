package com.qonics.somanet.cashflow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Create / submit a cash flow request (mirrors web form fields).
 */
public class CashFlowRequestActivity extends AppCompatActivity {

    private static final int REQ_SCAN = 4201;

    private SharedPreferences preferences;
    private Spinner spBudgetLine, spPaymentMethod, spUrgency;
    private EditText edPayee, edPurpose, edAmount;
    private TextView txtApprovalChain, txtBudgetTitle, txtDocStatus;

    private int budgetId;
    private final List<BudgetLineItem> budgetLines = new ArrayList<>();
    private String documentPath;
    private final android.os.Handler chainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable chainRunnable;

    private static class BudgetLineItem {
        final int id;
        final String label;
        final double available;

        BudgetLineItem(int id, String label, double available) {
            this.id = id;
            this.label = label;
            this.available = available;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_flow_request);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        spBudgetLine = findViewById(R.id.spBudgetLine);
        edPayee = findViewById(R.id.edPayee);
        edPurpose = findViewById(R.id.edPurpose);
        edAmount = findViewById(R.id.edAmount);
        txtApprovalChain = findViewById(R.id.txtApprovalChain);
        spPaymentMethod = findViewById(R.id.spPaymentMethod);
        spUrgency = findViewById(R.id.spUrgency);
        txtBudgetTitle = findViewById(R.id.txtBudgetTitle);
        txtDocStatus = findViewById(R.id.txtDocStatus);
        Button btnAttach = findViewById(R.id.btnAttachScan);
        Button btnSave = findViewById(R.id.btnSaveRequest);
        Button btnSubmit = findViewById(R.id.btnSubmitRequest);

        setupPaymentAndUrgency();
        applyDocumentFromIntent(getIntent());

        btnAttach.setOnClickListener(v -> {
            Intent i = new Intent(this, DocumentScanActivity.class);
            i.putExtra(DocumentScanActivity.EXTRA_MODE, DocumentScanActivity.MODE_STANDALONE);
            startActivityForResult(i, REQ_SCAN);
        });
        btnSave.setOnClickListener(v -> save(false));
        btnSubmit.setOnClickListener(v -> save(true));

        edAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleChainPreview();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadForm();
    }

    private void setupPaymentAndUrgency() {
        ArrayAdapter<String> payAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"bank", "cash", "mobile_money", "cheque"});
        spPaymentMethod.setAdapter(payAdapter);

        ArrayAdapter<String> urgAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"low", "normal", "high", "urgent"});
        spUrgency.setAdapter(urgAdapter);
        spUrgency.setSelection(1);
    }

    private void applyDocumentFromIntent(Intent intent) {
        if (intent == null) return;
        String path = intent.getStringExtra(DocumentScanActivity.EXTRA_DOCUMENT_PATH);
        if (path == null || path.isEmpty()) path = intent.getStringExtra("scan_path");
        if (path == null || path.isEmpty()) path = intent.getStringExtra("attachment_path");
        if (path != null && !path.isEmpty()) {
            setDocumentPath(path);
        }
    }

    private void setDocumentPath(String path) {
        documentPath = path;
        File f = new File(path);
        if (f.exists()) {
            txtDocStatus.setText(getString(R.string.cf_doc_attached_fmt, f.getName()));
            txtDocStatus.setTextColor(0xFF0A8FA7);
        } else {
            txtDocStatus.setText(R.string.cf_doc_none);
        }
    }

    private void loadForm() {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        int staffId = preferences.getInt("soma_id", 0);
        String url = MainActivity.SERVER + "get_cash_flow_form/" + schoolId + "/" + staffId;
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                budgetLines.clear();
                if (object == null || object.has("error")) {
                    String err = object != null ? object.optString("error", getString(R.string.cf_no_budget))
                            : getString(R.string.cf_no_budget);
                    Toast.makeText(CashFlowRequestActivity.this, err, Toast.LENGTH_LONG).show();
                    txtBudgetTitle.setText(R.string.cf_no_budget);
                    spBudgetLine.setAdapter(new ArrayAdapter<>(CashFlowRequestActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, budgetLines));
                    return;
                }
                budgetId = object.optInt("budget_id", 0);
                String title = object.optString("budget_title", "");
                txtBudgetTitle.setText(getString(R.string.cf_budget_title_fmt, title));

                JSONArray lines = object.optJSONArray("lines");
                if (lines != null) {
                    for (int i = 0; i < lines.length(); i++) {
                        JSONObject ln = lines.optJSONObject(i);
                        if (ln == null) continue;
                        int id = ln.optInt("id", 0);
                        String cat = ln.optString("category", "Line " + id);
                        String section = ln.optString("section", "");
                        double avail = ln.optDouble("available", 0);
                        String label = (section.isEmpty() ? cat : section + " · " + cat)
                                + String.format(Locale.US, " (%.0f RWF)", avail);
                        budgetLines.add(new BudgetLineItem(id, label, avail));
                    }
                }
                if (budgetLines.isEmpty()) {
                    Toast.makeText(CashFlowRequestActivity.this, R.string.cf_no_budget, Toast.LENGTH_LONG).show();
                }
                spBudgetLine.setAdapter(new ArrayAdapter<>(CashFlowRequestActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, budgetLines));
            }
        });
    }

    private void scheduleChainPreview() {
        if (chainRunnable != null) chainHandler.removeCallbacks(chainRunnable);
        chainRunnable = this::previewApprovalChain;
        chainHandler.postDelayed(chainRunnable, 450);
    }

    private void previewApprovalChain() {
        String amountStr = edAmount.getText() != null ? edAmount.getText().toString().trim() : "";
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (Exception e) {
            txtApprovalChain.setText(R.string.cf_chain_hint);
            return;
        }
        if (amount <= 0) {
            txtApprovalChain.setText(R.string.cf_chain_hint);
            return;
        }
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        int staffId = preferences.getInt("soma_id", 0);
        String url = MainActivity.SERVER + "get_cash_flow_approval_chain/" + schoolId + "/" + staffId
                + "?amount=" + amount;
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) {
                    txtApprovalChain.setText(R.string.cf_chain_hint);
                    return;
                }
                String chain = object.optString("chain", "");
                String steps = object.optString("steps_label", object.optString("label", chain));
                JSONArray stepArr = object.optJSONArray("steps");
                if (stepArr != null && stepArr.length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < stepArr.length(); i++) {
                        if (i > 0) sb.append(" → ");
                        sb.append(stepArr.optString(i));
                    }
                    if (sb.length() > 0) steps = sb.toString();
                }
                txtApprovalChain.setText(getString(R.string.cf_chain_preview_fmt, chain, steps));
            }
        });
    }

    private void save(boolean submitNow) {
        String payee = edPayee.getText() != null ? edPayee.getText().toString().trim() : "";
        String purpose = edPurpose.getText() != null ? edPurpose.getText().toString().trim() : "";
        String amountStr = edAmount.getText() != null ? edAmount.getText().toString().trim() : "";
        if (payee.isEmpty() || purpose.isEmpty()) {
            Toast.makeText(this, R.string.cf_payee_purpose_required, Toast.LENGTH_LONG).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (Exception e) {
            Toast.makeText(this, R.string.cf_amount_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(this, R.string.cf_amount_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        if (budgetId <= 0 || budgetLines.isEmpty() || spBudgetLine.getSelectedItem() == null) {
            Toast.makeText(this, R.string.cf_no_budget, Toast.LENGTH_LONG).show();
            return;
        }
        BudgetLineItem line = (BudgetLineItem) spBudgetLine.getSelectedItem();
        if (submitNow && amount > line.available && line.available > 0) {
            Toast.makeText(this, R.string.cf_amount_exceeds, Toast.LENGTH_LONG).show();
            return;
        }
        if (submitNow && (documentPath == null || documentPath.isEmpty() || !new File(documentPath).exists())) {
            Toast.makeText(this, R.string.cf_doc_required, Toast.LENGTH_LONG).show();
            return;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("school_id", String.valueOf(preferences.getInt(MainActivity.SK_ID, 0)));
        map.put("staff_id", String.valueOf(preferences.getInt("soma_id", 0)));
        map.put("budget_id", String.valueOf(budgetId));
        map.put("budget_line_id", String.valueOf(line.id));
        map.put("amount", String.valueOf(amount));
        map.put("payee_name", payee);
        map.put("purpose", purpose);
        map.put("payment_method", String.valueOf(spPaymentMethod.getSelectedItem()));
        map.put("urgency", String.valueOf(spUrgency.getSelectedItem()));
        map.put("submit_now", submitNow ? "1" : "0");

        if (documentPath != null && !documentPath.isEmpty()) {
            File doc = new File(documentPath);
            if (doc.exists()) {
                try {
                    byte[] bytes = ScanDocumentHelper.fileToBytes(doc);
                    map.put("filename", doc.getName());
                    map.put("image_base64", ScanDocumentHelper.toBase64(bytes));
                    map.put("document_base64", ScanDocumentHelper.toBase64(bytes));
                } catch (Exception ignored) {
                }
            }
        }

        new HttpRequest(this).post(MainActivity.SERVER + "save_cash_flow_request", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object != null && (object.has("success") || object.optInt("request_id", 0) > 0)
                        && !object.has("error")) {
                    String msg = object.optString("success", getString(R.string.cf_action_done));
                    Toast.makeText(CashFlowRequestActivity.this, msg, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    String err = object != null
                            ? object.optString("error", object.optString("message", status))
                            : status;
                    Toast.makeText(CashFlowRequestActivity.this,
                            err != null && !err.isEmpty() ? err : getString(R.string.lbl_error),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCAN && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra(DocumentScanActivity.EXTRA_DOCUMENT_PATH);
            if (path != null && !path.isEmpty()) setDocumentPath(path);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyDocumentFromIntent(intent);
    }
}
