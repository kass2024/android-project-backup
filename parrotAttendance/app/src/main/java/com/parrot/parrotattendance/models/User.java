package com.parrot.parrotattendance.models;

public class User {

    public int id;
    public String username;
    public String first_name;
    public String last_name;
    public String email;
    public String phone_number;
    public String full_name;
    public String role;
    public String profile_photo;

    public double salary_per_minute;
    public int allowed_break_minutes;
    public int work_days_per_week;

    // ⭐ FULL OFFICE OBJECT (required)
    public Office office;
}
