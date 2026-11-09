package com.parrot.parrotattendance.models;

public class DailyReportResponse {

    // -----------------------------
    // API Core Fields
    // -----------------------------
    public String status;         // "success" or "error"
    public String message;        // API message

    // -----------------------------
    // Daily Data
    // -----------------------------
    public String check_in_time;    // e.g. "2025-11-24 11:54:16"
    public String check_out_time;   // e.g. "2025-11-24 19:38:51"
    public int worked_minutes;      // e.g. 465
    public int salary;              // computed daily salary (safe fallback included in backend)

    // -----------------------------
    // Helper Methods
    // -----------------------------
    public boolean isSuccess() {
        return status != null && status.equalsIgnoreCase("success");
    }

    public String getCheckIn() {
        return check_in_time != null ? check_in_time : "N/A";
    }

    public String getCheckOut() {
        return check_out_time != null ? check_out_time : "N/A";
    }

    public int getWorkedMinutes() {
        return Math.max(worked_minutes, 0);
    }

    public int getSalary() {
        return Math.max(salary, 0);
    }
}
