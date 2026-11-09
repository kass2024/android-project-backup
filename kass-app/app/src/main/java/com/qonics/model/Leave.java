package com.qonics.model;

import android.content.Context;
import android.graphics.Color;

import androidx.fragment.app.FragmentManager;

import com.qonics.somanet.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Leave {
    int id,status,type,day_request,from_date,to_date;
    private String reason,address,staff,approver,email,trip,wentOut,returned;
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    Calendar c;
    public Leave(int id,int type, String reason, int day_request, int from_date, int to_date, String address,int status) {
        c=Calendar.getInstance();
        this.type = type;
        this.reason = reason;
        this.day_request = day_request;
        this.from_date = from_date;
        this.to_date = to_date;
        this.address = address;
        this.id = id;
        this.status = status;
    }

    public int getType() {
        return type;
    }
    public String getTypeStr(Context context) {
        switch (type){
            case 1: return context.getString(R.string.annual_leave);
            case 2: return context.getString(R.string.sick_leave);
            case 3: return context.getString(R.string.maternity_leave);
            case 4: return context.getString(R.string.unpaid_leave);
            case 5: return context.getString(R.string.short_permission);
        }
        return type+"";
    }
    public boolean isHourly() {
        int span = to_date - from_date;
        return type == 5 && span > 0 && span <= 12 * 3600;
    }
    public String getWindow() {
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US);
        java.text.SimpleDateFormat clock = new java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.US);
        c.setTimeInMillis(from_date * 1000L);
        String from = isHourly() ? clock.format(c.getTime()) : day.format(c.getTime());
        c.setTimeInMillis(to_date * 1000L);
        String to = isHourly() ? new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(c.getTime()) : day.format(c.getTime());
        if (isHourly()) {
            int hours = Math.round((to_date - from_date) / 3600f);
            return from + " – " + to + " · " + hours + " h";
        }
        return from + " – " + to;
    }
    public void setPeople(String staff, String approver, String email, String trip, String wentOut, String returned) {
        this.staff = staff;
        this.approver = approver;
        this.email = email;
        this.trip = trip;
        this.wentOut = wentOut;
        this.returned = returned;
    }
    public String getStaff() { return staff == null ? "" : staff; }
    public String getApprover() { return approver == null ? "" : approver; }
    public String getEmail() { return email == null ? "" : email; }
    public String getTrip() { return trip == null ? "" : trip; }
    public String getWentOut() { return wentOut == null ? "" : wentOut; }
    public String getReturned() { return returned == null ? "" : returned; }
    public String getReason() {
        return reason;
    }

    public int getDay_request() {
        return day_request;
    }

    public String getFrom_date() {
        c.setTimeInMillis(from_date*1000L);
        return df.format(c.getTime());
    }

    public String getTo_date() {
        c.setTimeInMillis(to_date*1000L);
        return df.format(c.getTime());
    }

    public String getAddress() {
        return address;
    }

    public int getId() {
        return id;
    }
    public int getStatus() {
        return status;
    }
    public String getStatusStr(Context context) {
        switch (status){
            case 0: return context.getString(R.string.pending);
            case 1: return context.getString(R.string.accepted);
            case 2: return context.getString(R.string.rejected);
        }
        return status+"";
    }
    public int getStatusColor() {
        switch (status){
            case 0: return Color.parseColor( "#FF5722");
            case 1: return Color.parseColor("#0C9911");
            case 2: return Color.parseColor("#A22B06");
        }
        return Color.parseColor("#000000");
    }
}
