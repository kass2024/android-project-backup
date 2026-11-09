package com.qonics.somanet;

import android.content.Context;
import android.preference.PreferenceManager;

import org.json.JSONObject;

import java.util.Locale;

public final class StockFormat {
    private StockFormat() {}

    public static int staffId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt("soma_id", 0);
    }

    public static String qty(double value) {
        if (Double.isNaN(value)) return "0";
        long whole = (long) value;
        if (value == whole) return Long.toString(whole);
        String text = String.format(Locale.US, "%.3f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    public static String errorOf(JSONObject object) {
        if (object == null) return null;
        String error = object.optString("error", "").trim();
        return error.isEmpty() ? null : error;
    }
}
