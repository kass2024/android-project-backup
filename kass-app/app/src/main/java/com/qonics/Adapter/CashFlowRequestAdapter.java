package com.qonics.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qonics.model.CashFlowRequest;
import com.qonics.somanet.R;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CashFlowRequestAdapter extends RecyclerView.Adapter<CashFlowRequestAdapter.Holder> {

    public interface ApprovalListener {
        void onApprove(CashFlowRequest req, String action);
    }

    private final List<CashFlowRequest> items;
    private final boolean showActions;
    private ApprovalListener approvalListener;

    public CashFlowRequestAdapter(List<CashFlowRequest> items, boolean showActions) {
        this.items = items;
        this.showActions = showActions;
    }

    public void setApprovalListener(ApprovalListener l) {
        this.approvalListener = l;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cash_flow_request, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        CashFlowRequest r = items.get(position);
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        h.txtNo.setText(r.getRequestNo());
        String statusLabel = r.getStatus();
        if (r.getSchoolName() != null && !r.getSchoolName().isEmpty()) {
            statusLabel = r.getSchoolName() + " · " + statusLabel;
        }
        h.txtStatus.setText(statusLabel);
        h.txtPayee.setText(r.getPayeeName());
        h.txtPurpose.setText(r.getPurpose());
        String line = r.getBudgetLine();
        if (r.getApprovalStepsLabel() != null && !r.getApprovalStepsLabel().isEmpty()) {
            line = (line == null || line.isEmpty() ? "" : line + "\n") + r.getApprovalStepsLabel();
        }
        h.txtLine.setText(line);
        h.txtAmount.setText(nf.format(r.getAmount()) + " RWF");
        h.actionRow.removeAllViews();
        if (showActions && !r.getActions().isEmpty()) {
            h.actionRow.setVisibility(View.VISIBLE);
            for (String action : r.getActions()) {
                Button b = new Button(h.itemView.getContext());
                b.setText(prettyAction(action));
                b.setAllCaps(false);
                b.setOnClickListener(v -> {
                    if (approvalListener != null) approvalListener.onApprove(r, action);
                });
                h.actionRow.addView(b);
            }
        } else {
            h.actionRow.setVisibility(View.GONE);
        }
    }

    private String prettyAction(String action) {
        if ("headteacher_approve".equals(action)) return "Approve — Head Teacher";
        if ("chief_accountant_approve".equals(action)) return "Approve — Chief Accountant";
        if ("final_approve".equals(action)) return "Final approval — Finance";
        if ("return".equals(action) || "reject".equals(action)) return "Send back with a reason";
        if ("submit".equals(action)) return "Submit again";
        if ("pay".equals(action)) return "Mark paid";
        return action.replace('_', ' ');
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView txtNo, txtStatus, txtPayee, txtPurpose, txtLine, txtAmount;
        LinearLayout actionRow;

        Holder(View itemView) {
            super(itemView);
            txtNo = itemView.findViewById(R.id.txtReqNo);
            txtStatus = itemView.findViewById(R.id.txtReqStatus);
            txtPayee = itemView.findViewById(R.id.txtReqPayee);
            txtPurpose = itemView.findViewById(R.id.txtReqPurpose);
            txtLine = itemView.findViewById(R.id.txtReqLine);
            txtAmount = itemView.findViewById(R.id.txtReqAmount);
            actionRow = itemView.findViewById(R.id.lnlReqActions);
        }
    }
}
