package com.xander.student;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/**
 * Four day-scholar clocks for one school day.
 * Morning presence is any time and is the attendance mark.
 * Lunch and the return after lunch follow that class timetable.
 * The last clock-out opens at 14:00 and stays open after that.
 * It does not wait for the night timetable slot (20:00).
 */
public final class DayScholarClock {
    /** Last checkout. Earlier than a timetable night start, never later than 14:00. */
    private static final int LAST_CHECKOUT = 14 * 60;

    private DayScholarClock() {
    }

    public static final class Windows {
        public final int lunchStart;
        public final int lunchEnd;
        public final int backStart;
        public final int backEnd;
        public final int eveningStart;
        public final int eveningEnd;

        public Windows(int lunchStart, int lunchEnd, int backStart, int backEnd, int eveningStart, int eveningEnd) {
            this.lunchStart = lunchStart;
            this.lunchEnd = lunchEnd;
            this.backStart = backStart;
            this.backEnd = backEnd;
            this.eveningStart = eveningStart;
            this.eveningEnd = eveningEnd;
        }
    }

    public static final class Marks {
        public long morning;
        public long lunch;
        public long back;
        public long evening;

        public Marks copy() {
            Marks m = new Marks();
            m.morning = morning;
            m.lunch = lunch;
            m.back = back;
            m.evening = evening;
            return m;
        }
    }

    public static final class Decision {
        public final String status;
        public final boolean save;
        public final boolean already;
        public final boolean wait;
        public final String message;
        public final long shownEpoch;
        public final Marks marks;

        Decision(String status, boolean save, boolean already, boolean wait, String message, long shownEpoch, Marks marks) {
            this.status = status;
            this.save = save;
            this.already = already;
            this.wait = wait;
            this.message = message;
            this.shownEpoch = shownEpoch;
            this.marks = marks;
        }
    }

    public static String phaseForClass(String className) {
        String s = className == null ? "" : className.toLowerCase(Locale.US);
        if (s.contains("baby") || s.contains("middle") || s.contains("top") || s.contains("nursery")) {
            return "nursery";
        }
        if (s.contains("primary") || s.matches(".*\\bp\\s*[1-6]\\b.*")) {
            return "primary";
        }
        return "high_school";
    }

    public static Windows windowsFor(String dayClockJson, String className) {
        String phase = phaseForClass(className);
        Windows parsed = parsePhase(dayClockJson, phase);
        if (parsed != null) {
            return parsed;
        }
        if ("nursery".equals(phase)) {
            return new Windows(12 * 60, 13 * 60, 13 * 60, 13 * 60 + 45, 16 * 60, 17 * 60 + 15);
        }
        return new Windows(12 * 60, 13 * 60 + 10, 13 * 60 + 10, 13 * 60 + 55, 16 * 60, 17 * 60 + 15);
    }

	public static Decision decide(long nowEpoch, Windows windows, Marks current) {
        Marks marks = current == null ? new Marks() : current.copy();
        int minute = minuteOfDay(nowEpoch);
        if (marks.morning <= 0L && minute < windows.lunchStart - 5) {
            marks.morning = nowEpoch;
            return new Decision("IN", true, false, false, "IN time saved", nowEpoch, marks);
        }
        if (inside(minute, windows.lunchStart - 5, windows.lunchEnd + 5)) {
            if (marks.lunch > 0L) {
                return new Decision("LUNCH", false, true, false, "Lunch already clocked", marks.lunch, marks);
            }
            marks.lunch = nowEpoch;
            return new Decision("LUNCH", true, false, false, "Lunch clock saved", nowEpoch, marks);
        }
        if (inside(minute, windows.backStart, windows.backEnd)) {
            if (marks.back > 0L) {
                return new Decision("BACK", false, true, false, "Already back from lunch", marks.back, marks);
            }
            marks.back = nowEpoch;
            return new Decision("BACK", true, false, false, "Back from lunch", nowEpoch, marks);
        }
        int checkoutFrom = lastCheckoutOpen(windows);
        if (minute >= checkoutFrom) {
            if (marks.evening > 0L && nowEpoch - marks.evening < 60L) {
                return new Decision("OUT", false, true, false, "Evening already clocked", marks.evening, marks);
            }
            boolean again = marks.evening > 0L;
            marks.evening = nowEpoch;
            return new Decision("OUT", true, false, false, again ? "OUT time updated" : "Evening clock saved", nowEpoch, marks);
        }
        return new Decision("WAIT", false, false, true, waitMessage(minute, windows, marks), nowEpoch, marks);
    }

    /** 14:00, or earlier when the class timetable already opens checkout before then. */
    private static int lastCheckoutOpen(Windows windows) {
        if (windows == null || windows.eveningStart <= 0 || windows.eveningStart > LAST_CHECKOUT) {
            return LAST_CHECKOUT;
        }
        return windows.eveningStart;
    }

    public static String pack(Marks marks) {
        if (marks == null) {
            return "M=0;L=0;B=0;E=0";
        }
        return "M=" + marks.morning + ";L=" + marks.lunch + ";B=" + marks.back + ";E=" + marks.evening;
    }

    public static Marks unpack(String packed) {
        Marks marks = new Marks();
        if (packed == null || packed.length() == 0) {
            return marks;
        }
        String[] parts = packed.split(";");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            long value = 0L;
            try {
                value = Long.parseLong(part.substring(eq + 1).trim());
            } catch (Exception ignored) {
            }
            String key = part.substring(0, eq).trim();
            if ("M".equals(key)) {
                marks.morning = value;
            } else if ("L".equals(key)) {
                marks.lunch = value;
            } else if ("B".equals(key)) {
                marks.back = value;
            } else if ("E".equals(key)) {
                marks.evening = value;
            }
        }
        return marks;
    }

    public static String hhmm(int minute) {
        int m = minute;
        if (m < 0) {
            m = 0;
        }
        int h = (m / 60) % 24;
        int min = m % 60;
        return String.format(Locale.US, "%02d:%02d", h, min);
    }

    private static String waitMessage(int minute, Windows windows, Marks marks) {
        if (minute < windows.lunchStart) {
            return "Already in. Lunch clock " + hhmm(windows.lunchStart) + "–" + hhmm(windows.lunchEnd);
        }
        if (minute <= windows.lunchEnd + 5 && marks.lunch <= 0L) {
            return "Lunch clock " + hhmm(windows.lunchStart) + "–" + hhmm(windows.lunchEnd);
        }
        if (marks.back <= 0L && minute <= windows.backEnd) {
            return "Clock back after lunch " + hhmm(windows.backStart) + "–" + hhmm(windows.backEnd);
        }
        return "Evening clock from " + hhmm(lastCheckoutOpen(windows));
    }

    private static boolean inside(int minute, int start, int end) {
        return minute >= start && minute <= end;
    }

    private static int minuteOfDay(long epochSeconds) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochSeconds * 1000L);
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    private static Windows parsePhase(String dayClockJson, String phase) {
        if (dayClockJson == null || dayClockJson.length() == 0) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(dayClockJson);
            JSONObject row = root.optJSONObject(phase);
            if (row == null) {
                return null;
            }
            int lunchStart = row.optInt("lunch_start", -1);
            int lunchEnd = row.optInt("lunch_end", -1);
            int backStart = row.optInt("back_start", -1);
            int backEnd = row.optInt("back_end", -1);
            int eveningStart = row.optInt("evening_start", -1);
            int eveningEnd = row.optInt("evening_end", -1);
            if (lunchStart < 0 || lunchEnd <= lunchStart || eveningEnd <= eveningStart) {
                return null;
            }
            if (backStart < 0) {
                backStart = lunchEnd;
            }
            if (backEnd <= backStart) {
                backEnd = backStart + 45;
            }
            return new Windows(lunchStart, lunchEnd, backStart, backEnd, eveningStart, eveningEnd);
        } catch (Exception ignored) {
            return null;
        }
    }
}
