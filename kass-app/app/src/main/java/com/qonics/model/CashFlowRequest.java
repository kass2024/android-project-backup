package com.qonics.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CashFlowRequest {
    private int id;
    private String requestNo;
    private String status;
    private String payeeName;
    private String purpose;
    private double amount;
    private String budgetLine;
    private String approvalChain;
    private String approvalStepsLabel;
    private String schoolName;
    private List<String> actions = new ArrayList<>();

    public static CashFlowRequest fromJson(JSONObject o) throws Exception {
        CashFlowRequest r = new CashFlowRequest();
        r.id = o.getInt("id");
        r.requestNo = o.optString("request_no", "");
        r.status = o.optString("status", "");
        r.payeeName = o.optString("payee_name", "");
        r.purpose = o.optString("purpose", "");
        r.amount = o.optDouble("amount", o.optDouble("requested_amount", 0));
        r.budgetLine = o.optString("budget_line", "");
        r.approvalChain = o.optString("approval_chain", "");
        r.approvalStepsLabel = o.optString("approval_steps_label", "");
        r.schoolName = o.optString("school_name", "");
        JSONArray acts = o.optJSONArray("actions");
        if (acts != null) {
            for (int i = 0; i < acts.length(); i++) {
                r.actions.add(acts.getString(i));
            }
        }
        return r;
    }

    public int getId() { return id; }
    public String getRequestNo() { return requestNo; }
    public String getStatus() { return status; }
    public String getPayeeName() { return payeeName; }
    public String getPurpose() { return purpose; }
    public double getAmount() { return amount; }
    public String getBudgetLine() { return budgetLine; }
    public String getApprovalChain() { return approvalChain; }
    public String getApprovalStepsLabel() { return approvalStepsLabel; }
    public String getSchoolName() { return schoolName; }
    public List<String> getActions() { return actions; }
}
