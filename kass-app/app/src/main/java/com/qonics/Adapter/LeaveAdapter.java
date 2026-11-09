package com.qonics.Adapter;

import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.qonics.model.Leave;
import com.qonics.somanet.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

public abstract class LeaveAdapter extends RecyclerView.Adapter<LeaveAdapter.MyViewHolder> {
    private List<Leave> requestList;
    private Context mContext;
    SharedPreferences preferences;

    public class MyViewHolder extends RecyclerView.ViewHolder {
        public TextView name,type,reason,day_request,from_date,to_date,address,status,approver,gate;
        public View actions;
        public View btnApprove, btnDeny;


        public MyViewHolder(View view) {
            super(view);

            name = view.findViewById(R.id.name);
            type = view.findViewById(R.id.type);
            reason = view.findViewById(R.id.reason);
            day_request = view.findViewById(R.id.days);
            from_date = view.findViewById(R.id.dFrom);
            to_date = view.findViewById(R.id.dTo);
            address = view.findViewById(R.id.address);
            status = view.findViewById(R.id.status);
            approver = view.findViewById(R.id.approver);
            gate = view.findViewById(R.id.gate);
            actions = view.findViewById(R.id.leaveActions);
            btnApprove = view.findViewById(R.id.btnApprove);
            btnDeny = view.findViewById(R.id.btnDeny);
        }
    }

    public interface LeaveAction {
        void approve(Leave leave);
        void deny(Leave leave);
    }

    private boolean canManage;
    private LeaveAction action;

    public void setManage(boolean canManage, LeaveAction action) {
        this.canManage = canManage;
        this.action = action;
    }


    public LeaveAdapter(List<Leave> requestList,Context context) {
        this.requestList = requestList;
        mContext = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.leave_data_item, parent, false);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        Leave leave = requestList.get(position);
        String who = leave.getStaff();
        holder.name.setText(who.isEmpty() ? preferences.getString("soma_name","") : who);
        holder.type.setText(leave.getTypeStr(mContext));
        holder.day_request.setText(leave.isHourly() ? "" : (leave.getDay_request() + " days"));
        holder.reason.setText(leave.getReason());
        holder.from_date.setText(leave.getWindow());
        holder.to_date.setText("");
        holder.address.setText(leave.getAddress());
        holder.status.setText(leave.getStatusStr(mContext));
        holder.status.setTextColor(leave.getStatusColor());
        if (leave.getStatus() == 0 || leave.getApprover().isEmpty()) {
            holder.approver.setText("");
        } else {
            holder.approver.setText(mContext.getString(R.string.leave_approved_by) + ": " + leave.getApprover());
        }
        String trip = leave.getTrip();
        if ("out".equals(trip)) {
            holder.gate.setText(mContext.getString(R.string.leave_still_out) + (leave.getWentOut().isEmpty() ? "" : " · " + leave.getWentOut()));
        } else if ("back".equals(trip)) {
            holder.gate.setText(mContext.getString(R.string.leave_back) + (leave.getReturned().isEmpty() ? "" : " · " + leave.getReturned()));
        } else if ("unused".equals(trip)) {
            holder.gate.setText(mContext.getString(R.string.leave_not_used));
        } else {
            holder.gate.setText("");
        }
        boolean open = canManage && leave.getStatus() == 0;
        holder.actions.setVisibility(open ? View.VISIBLE : View.GONE);
        holder.btnApprove.setOnClickListener(v -> {
            if (action != null) action.approve(leave);
        });
        holder.btnDeny.setOnClickListener(v -> {
            if (action != null) action.deny(leave);
        });
    }

    @Override
    public int getItemCount() {
        return requestList.size();
    }


}
