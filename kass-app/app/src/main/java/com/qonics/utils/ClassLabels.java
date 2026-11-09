package com.qonics.utils;

import java.util.Locale;

/** Holiday classes stay off the phone. */
public final class ClassLabels {
    private ClassLabels() {
    }

    public static boolean isHoliday(String label) {
        return label != null && label.toLowerCase(Locale.US).contains("holiday");
    }

    public static String visible(String label) {
        if (isHoliday(label)) {
            return "";
        }
        return label == null ? "" : label.trim().replaceAll("\\s+", " ");
    }
}
