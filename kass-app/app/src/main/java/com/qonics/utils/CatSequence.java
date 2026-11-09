package com.qonics.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Same growth rule as the web marks page: finished quizzes, tests, and
 * homework stay editable, and only the next number is offered.
 */
public final class CatSequence {

    private static final Pattern CODE = Pattern.compile("^([QTH])(\\d+)$");

    public static final class Option {
        public final String code;
        public final String label;

        public Option(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    private CatSequence() {}

    public static List<Option> options(Iterable<String> filledCodes) {
        Map<String, Map<Integer, Boolean>> byPrefix = new HashMap<>();
        byPrefix.put("Q", new HashMap<Integer, Boolean>());
        byPrefix.put("T", new HashMap<Integer, Boolean>());
        byPrefix.put("H", new HashMap<Integer, Boolean>());
        if (filledCodes != null) {
            for (String raw : filledCodes) {
                if (raw == null) continue;
                Matcher m = CODE.matcher(raw.trim().toUpperCase(Locale.US));
                if (m.matches()) {
                    byPrefix.get(m.group(1)).put(Integer.parseInt(m.group(2)), true);
                }
            }
        }
        List<Option> out = new ArrayList<>();
        addFamily(out, "Q", "Quiz", byPrefix.get("Q"));
        addFamily(out, "T", "Test", byPrefix.get("T"));
        addFamily(out, "H", "Homework", byPrefix.get("H"));
        return out;
    }

    public static boolean isAllowed(String code, Iterable<String> filledCodes) {
        if (code == null) return false;
        String want = code.trim().toUpperCase(Locale.US);
        for (Option option : options(filledCodes)) {
            if (option.code.equals(want)) return true;
        }
        return false;
    }

    private static void addFamily(List<Option> out, String prefix, String stem, Map<Integer, Boolean> nums) {
        int maxExisting = 0;
        for (Integer n : nums.keySet()) {
            if (n != null && n > maxExisting) maxExisting = n;
        }
        int contiguous = 0;
        for (int i = 1; i <= maxExisting; i++) {
            if (!Boolean.TRUE.equals(nums.get(i))) break;
            contiguous = i;
        }
        int openThrough = contiguous + 1;
        int showMax = Math.max(maxExisting, openThrough);
        for (int i = 1; i <= showMax; i++) {
            boolean has = Boolean.TRUE.equals(nums.get(i));
            if (!has && i > openThrough) continue;
            out.add(new Option(prefix + i, stem + " " + i));
        }
    }
}
