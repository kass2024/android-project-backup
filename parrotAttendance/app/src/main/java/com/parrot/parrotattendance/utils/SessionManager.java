package com.parrot.parrotattendance.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.parrot.parrotattendance.models.User;
import com.parrot.parrotattendance.models.Office;

public class SessionManager {

    private static final String PREF = "parrot_attendance_session";

    private static final String KEY_USER = "logged_user";
    private static final String KEY_OFFICE = "user_office";

    private static final Gson gson = new Gson();

    // -----------------------------
    // SAVE USER + OFFICE
    // -----------------------------
    public static void save(Context context, User user, Office office) {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_USER, gson.toJson(user))
                .putString(KEY_OFFICE, gson.toJson(office))
                .apply();
    }

    // -----------------------------
    // GET LOGGED USER
    // -----------------------------
    public static User getUser(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_USER, null);
        if (json == null) return null;
        return gson.fromJson(json, User.class);
    }

    // -----------------------------
    // GET USER OFFICE
    // -----------------------------
    public static Office getOffice(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_OFFICE, null);
        if (json == null) return null;
        return gson.fromJson(json, Office.class);
    }

    // -----------------------------
    // CHECK LOGIN STATE
    // -----------------------------
    public static boolean isLoggedIn(Context context) {
        return getUser(context) != null;
    }

    // -----------------------------
    // LOGOUT (CLEAR EVERYTHING)
    // -----------------------------
    public static void logout(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
