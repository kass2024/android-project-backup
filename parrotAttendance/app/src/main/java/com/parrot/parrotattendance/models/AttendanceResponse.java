package com.parrot.parrotattendance.models;

public class AttendanceResponse {

    public String status;      // "success" or "error"
    public String message;     // API message

    public String time;        // check-in or check-out time (optional)
    public int worked_minutes; // for checkout
    public double job_hours;   // for checkout validation
    public int salary;         // daily salary

    public int distance;       // optional: distance from office
    public int allowed_radius; // office radius (optional)

    // Getter helpers (optional)
    public boolean isSuccess() {
        return status != null && status.equalsIgnoreCase("success");
    }
}
