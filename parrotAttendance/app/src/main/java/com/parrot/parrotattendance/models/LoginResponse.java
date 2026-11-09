package com.parrot.parrotattendance.models;

public class LoginResponse {
    public String status;
    public User user;
    public Office office;   // ← added to support dynamic office loading
}
