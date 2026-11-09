package com.parrot.parrotattendance.models;

public class MonthlyReportResponse {

    // -----------------------------
    // API Core Fields
    // -----------------------------
    public String status;      // "success" or "error"
    public String message;     // API message

    // -----------------------------
    // Monthly Summary Data
    // -----------------------------
    public int total_days;     // Total days worked in the month
    public int total_minutes;  // Raw total minutes (optional but sent by API)
    public int total_hours;    // Computed hours (rounded)
    public int total_salary;   // Total salary for the month
    public int late_count;     // Number of late check-ins
    public int missed_days;    // Number of days absent

    // -----------------------------
    // Helper Methods
    // -----------------------------
    public boolean isSuccess() {
        return status != null && status.equalsIgnoreCase("success");
    }

    public int getTotalDays() {
        return Math.max(total_days, 0);
    }

    public int getTotalHours() {
        return Math.max(total_hours, 0);
    }

    public int getTotalSalary() {
        return Math.max(total_salary, 0);
    }

    public int getLateCount() {
        return Math.max(late_count, 0);
    }

    public int getMissedDays() {
        return Math.max(missed_days, 0);
    }
}
