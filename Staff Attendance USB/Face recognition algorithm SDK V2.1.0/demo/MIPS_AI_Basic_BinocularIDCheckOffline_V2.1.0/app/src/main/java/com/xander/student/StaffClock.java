package com.xander.student;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Mirrors PHP {@code App\Libraries\StaffShiftClock}: weekday 0=Mon … 6=Sun,
 * hours like 7.0 / 16.5, GRACE_SECONDS=59.
 */
public final class StaffClock {
    public static final int GRACE_SECONDS = 59;
    public static final int STAFF_OUT_AFTER_IN_SECONDS = 300;
    private static final String WAIT_MSG = "Already checked in — waiting for checkout";

    private StaffClock() {}

    public static JSONObject windowFor(String optionsJson, long nowMs) {
        long atTs = nowMs / 1000L;
        JSONArray options = parseOptions(optionsJson);
        JSONObject empty = emptyWindow(atTs);
        if (options.length() == 0) {
            return empty;
        }
        int[] offsets = new int[]{0, -1};
        for (int i = 0; i < offsets.length; i++) {
            long dayStart = startOfLocalDay(atTs + offsets[i] * 86400L);
            JSONObject parsed = parseDay(options, phpWeekdayToShiftDay(phpW(dayStart)), dayStart);
            if (parsed == null) {
                continue;
            }
            long openFrom = parsed.optLong("start_ts") - 2L * 3600L;
            long openTo = parsed.optLong("end_ts") + 4L * 3600L;
            if (atTs >= openFrom && atTs <= openTo) {
                putQuiet(parsed, "working", true);
                putQuiet(parsed, "look_from", openFrom);
                putQuiet(parsed, "look_to", openTo);
                return parsed;
            }
        }
        long dayStart = startOfLocalDay(atTs);
        JSONObject parsed = parseDay(options, phpWeekdayToShiftDay(phpW(dayStart)), dayStart);
        if (parsed != null) {
            putQuiet(parsed, "working", true);
            putQuiet(parsed, "look_from", dayStart);
            putQuiet(parsed, "look_to", dayStart + 86400L - 1L);
            return parsed;
        }
        return empty;
    }

    public static JSONObject decide(JSONObject staff, long nowMs) {
        JSONObject out = new JSONObject();
        long nowSec = nowMs / 1000L;
        String optionsJson = staff == null ? "" : staff.optString("shift_options", "");
        JSONObject window = windowFor(optionsJson, nowMs);
        boolean working = window.optBoolean("working", false);
        long endTs = window.optLong("end_ts", 0);
        String endLabel = window.optString("end_label", "");
        String day = dayKey(nowMs);
        long clockIn = staff == null ? 0L : staff.optLong("clock_in", 0L);
        long clockOut = staff == null ? 0L : staff.optLong("clock_out", 0L);
        String clockDay = staff == null ? "" : staff.optString("clock_day", "");
        boolean attendanceToday = clockIn > 0 && day.equals(clockDay);
        boolean alreadyOut = attendanceToday && clockOut > 0;
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(nowMs));
        putQuiet(out, "working", working);
        putQuiet(out, "time", timeStr);
        putQuiet(out, "can_out", false);
        putQuiet(out, "already", false);
        putQuiet(out, "status", "");
        putQuiet(out, "label", "");
        putQuiet(out, "ok", true);

        // Off-day (Sunday on a Mon–Fri shift) never IN or OUT.
        if (!working) {
            if (hasDays(optionsJson)) {
                putQuiet(out, "ok", false);
                putQuiet(out, "status", "");
                putQuiet(out, "message", "Not a working day for this shift");
                return out;
            }
            if (!attendanceToday) {
                putQuiet(out, "ok", true);
                putQuiet(out, "status", "IN");
                putQuiet(out, "already", false);
                putQuiet(out, "can_out", false);
                putQuiet(out, "label", "Checked in");
                putQuiet(out, "message", "Staff IN");
                return out;
            }
            return alreadyIn(out, WAIT_MSG);
        }

        if (!attendanceToday) {
            if (working && endTs > 0 && nowSec > endTs + GRACE_SECONDS) {
                int mins = (int) Math.round((nowSec - endTs) / 60.0);
                putQuiet(out, "ok", true);
                putQuiet(out, "status", "OUT");
                putQuiet(out, "already", false);
                putQuiet(out, "can_out", true);
                putQuiet(out, "label", "Extra time");
                putQuiet(out, "message", "Checkout · Extra time");
                putQuiet(out, "detail", mins + " min after " + endLabel);
                return out;
            }
            putQuiet(out, "ok", true);
            putQuiet(out, "status", "IN");
            putQuiet(out, "already", false);
            putQuiet(out, "can_out", false);
            putQuiet(out, "label", "Checked in");
            putQuiet(out, "message", "Staff IN");
            return out;
        }

        if (alreadyOut) {
            putQuiet(out, "ok", true);
            putQuiet(out, "status", "OUT");
            putQuiet(out, "already", true);
            putQuiet(out, "can_out", false);
            putQuiet(out, "label", "Checked out");
            putQuiet(out, "message", "Already checked out");
            return out;
        }

        if (endTs > 0 && nowSec >= endTs - GRACE_SECONDS) {
            boolean extra = nowSec > endTs + 60L;
            int mins = extra ? (int) Math.round((nowSec - endTs) / 60.0) : 0;
            putQuiet(out, "ok", true);
            putQuiet(out, "status", "OUT");
            putQuiet(out, "already", false);
            putQuiet(out, "can_out", true);
            putQuiet(out, "label", extra ? "Extra time" : "Checked out");
            putQuiet(out, "message", extra ? "Checkout · Extra time" : "Staff OUT");
            if (extra) {
                putQuiet(out, "detail", mins + " min after " + endLabel);
            }
            return out;
        }
        return alreadyIn(out, WAIT_MSG);
    }

    private static JSONObject alreadyIn(JSONObject out, String message) {
        putQuiet(out, "ok", true);
        putQuiet(out, "status", "IN");
        putQuiet(out, "already", true);
        putQuiet(out, "can_out", false);
        putQuiet(out, "label", "Waiting for checkout");
        putQuiet(out, "message", message);
        return out;
    }

    public static boolean hasDays(String optionsJson) {
        JSONArray options = parseOptions(optionsJson);
        for (int i = 0; i < options.length(); i++) {
            String[] p = splitOpt(options.optString(i, ""));
            if (p.length >= 3) {
                try {
                    int day = Integer.parseInt(p[0]);
                    if (day >= 0 && day <= 6) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    static JSONArray parseOptions(String json) {
        if (json == null) {
            return new JSONArray();
        }
        String s = json.trim();
        if (s.length() == 0 || "null".equalsIgnoreCase(s)) {
            return new JSONArray();
        }
        try {
            if (s.startsWith("[")) {
                return new JSONArray(s);
            }
        } catch (Exception ignored) {
        }
        return new JSONArray();
    }

    private static JSONObject parseDay(JSONArray options, int shiftDay, long dayStart) {
        for (int i = 0; i < options.length(); i++) {
            String[] p = splitOpt(options.optString(i, ""));
            if (p.length < 3) {
                continue;
            }
            try {
                if (Integer.parseInt(p[0]) != shiftDay) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            int startSec = decimalToSeconds(p[1]);
            int endSec = decimalToSeconds(p[2]);
            boolean overnight = endSec <= startSec;
            long endTs = dayStart + endSec;
            if (overnight) {
                endTs += 86400L;
            }
            JSONObject o = new JSONObject();
            putQuiet(o, "overnight", overnight);
            putQuiet(o, "start_ts", dayStart + startSec);
            putQuiet(o, "end_ts", endTs);
            putQuiet(o, "start_label", hourLabel(p[1]));
            putQuiet(o, "end_label", hourLabel(p[2]));
            putQuiet(o, "working", false);
            return o;
        }
        return null;
    }

    static int decimalToSeconds(String hour) {
        if (hour == null) {
            hour = "0";
        }
        String[] parts = hour.split("\\.", 2);
        int hh = 0;
        try {
            hh = Integer.parseInt(parts[0]);
        } catch (Exception ignored) {
        }
        String frac = parts.length > 1 ? parts[1] : "0";
        int mm = ("0".equals(frac) || "00".equals(frac) || frac.length() == 0) ? 0 : 30;
        return (hh * 60 + mm) * 60;
    }

    private static String hourLabel(String hour) {
        int sec = decimalToSeconds(hour);
        int hh = sec / 3600;
        int mm = (sec % 3600) / 60;
        int h12 = hh % 12;
        if (h12 == 0) {
            h12 = 12;
        }
        String am = hh >= 12 ? "PM" : "AM";
        return String.format(Locale.US, "%d:%02d %s", h12, mm, am);
    }

    private static String[] splitOpt(String opt) {
        if (opt == null) {
            return new String[0];
        }
        return opt.trim().split("\\s+");
    }

    static int phpWeekdayToShiftDay(int phpW) {
        return phpW == 0 ? 6 : phpW - 1;
    }

    static int phpW(long epochSec) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(epochSec * 1000L);
        return c.get(Calendar.DAY_OF_WEEK) - 1;
    }

    static long startOfLocalDay(long epochSec) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(epochSec * 1000L);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() / 1000L;
    }

    public static String dayKey(long nowMs) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getDefault());
        return f.format(new Date(nowMs));
    }

    private static JSONObject emptyWindow(long atTs) {
        JSONObject o = new JSONObject();
        long today = startOfLocalDay(atTs);
        putQuiet(o, "title", "");
        putQuiet(o, "working", false);
        putQuiet(o, "overnight", false);
        putQuiet(o, "start_ts", 0L);
        putQuiet(o, "end_ts", 0L);
        putQuiet(o, "start_label", "");
        putQuiet(o, "end_label", "");
        putQuiet(o, "look_from", today);
        putQuiet(o, "look_to", today + 86400L - 1L);
        return o;
    }

    private static void putQuiet(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (Exception ignored) {
        }
    }
}
