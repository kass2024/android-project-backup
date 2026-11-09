package com.xander.student;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Exact same rules as public/assets/js/card-uid.js + card_uid_helper.php.
 *
 * Keyboard wedge (web): decimal digits → hex pad8 → reverse for storage.
 * DK / NFC: already hex bytes — use {@link #cleanHexOnly}; never re-parse as decimal
 * (94760002 hex must not become 05A5EC42).
 */
public final class CardUid {
    private CardUid() {}

    /** Strip to hex only — no decimal conversion (for DK/NFC binary UIDs). */
    public static String cleanHexOnly(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toUpperCase(raw.charAt(i));
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                b.append(c);
            }
        }
        String uid = b.toString();
        return uid.length() >= 4 ? uid : "";
    }

    /** Clean reader input: all-digit wedge → hex (pad 8), strip non-hex. No reverse. */
    public static String cleanRaw(String raw) {
        if (raw == null) {
            return "";
        }
        String uid = raw.trim().replaceAll("\\s+", "");
        if (uid.length() == 0) {
            return "";
        }
        boolean digits = true;
        for (int i = 0; i < uid.length(); i++) {
            char c = uid.charAt(i);
            if (c < '0' || c > '9') {
                digits = false;
                break;
            }
        }
        if (digits) {
            // Keyboard wedge decimal (e.g. 0033584788).
            // Exactly 8 digits are left unchanged here. DK readers emit 8 hex
            // characters (94280002). Nyamasheke's USB reader emits 8-digit
            // decimals (33560193 = stored card 02001681). lookupVariants tries both.
            // Converting every 8-digit string here used to work for Nyamasheke
            // and broke real hex UIDs. Keeping only the hex reading broke Nyamasheke.
            if (uid.length() != 8) {
                try {
                    uid = Long.toHexString(Long.parseLong(uid)).toUpperCase();
                    while (uid.length() < 8) {
                        uid = "0" + uid;
                    }
                } catch (Exception ignored) {
                    return "";
                }
            }
        }
        return cleanHexOnly(uid);
    }

    /** Reverse byte pairs: AABBCCDD → DDCCBBAA. */
    public static String reverseBytes(String hex) {
        hex = cleanHexOnly(hex);
        if (hex.length() == 0 || hex.length() % 2 != 0) {
            return hex;
        }
        StringBuilder b = new StringBuilder();
        for (int i = hex.length() - 2; i >= 0; i -= 2) {
            b.append(hex.substring(i, i + 2));
        }
        return b.toString();
    }

    /** Same as web CardUid.toStorage / verify normalizeUID. */
    public static String toStorage(String raw) {
        return reverseBytes(cleanRaw(raw));
    }

    /** Storage form when raw is already hex from DK/NFC (no decimal step). */
    public static String toStorageHex(String hexRaw) {
        return reverseBytes(cleanHexOnly(hexRaw));
    }

    public static String forScan(String raw) {
        return cleanRaw(raw);
    }

    /** Storage / web form used by Assign Staff Card. */
    public static String webNormalize(String raw) {
        return toStorage(raw);
    }

    /** PHP card_uid_lookup_variants + undo of old DK decimal bug. */
    public static String[] lookupVariants(String raw) {
        Set<String> out = new LinkedHashSet<String>();
        addPair(out, cleanRaw(raw));
        addPair(out, cleanHexOnly(raw));
        // USB wedges often print the UID as decimal. An 8-digit value is also
        // valid hex (94280002), so keep that reading and add the decimal one.
        // 33701350 decimal is stored card 02029D66; treating it as hex only misses.
        addDecimalHex(out, digitsOnly(raw));
        String swappedDigits = reverseBytes(digitsOnly(raw));
        if (digitsOnly(swappedDigits).equals(swappedDigits)) {
            addDecimalHex(out, swappedDigits);
        }
        String hex = cleanHexOnly(raw);
        addTrimmedUid(out, hex);
        addLengthPrefixed(out, hex);
        // Undo 05A5EC42 ← mistaken decimal parse of hex 94760002
        hex = cleanHexOnly(raw);
        if (hex.length() == 8) {
            try {
                long n = Long.parseLong(hex, 16);
                String asDigits = Long.toString(n);
                while (asDigits.length() < 8) {
                    asDigits = "0" + asDigits;
                }
                if (asDigits.length() == 8) {
                    addPair(out, asDigits);
                }
            } catch (Exception ignored) {
            }
        }
        return out.toArray(new String[out.size()]);
    }

    /** All-digit reader text, 5–13 chars, as an 8-byte-padded hex UID. */
    private static void addDecimalHex(Set<String> out, String digits) {
        if (digits.length() < 5 || digits.length() > 13) {
            return;
        }
        try {
            String trimmed = digits.replaceFirst("^0+", "");
            if (trimmed.length() == 0) {
                return;
            }
            long n = Long.parseLong(trimmed);
            String hex = Long.toHexString(n).toUpperCase();
            if (hex.length() % 2 == 1) {
                hex = "0" + hex;
            }
            while (hex.length() < 8) {
                hex = "0" + hex;
            }
            addPair(out, hex);
        } catch (Exception ignored) {
        }
    }

    /** 4/7/10-byte UID, a short decimal wedge, or the same value with one extra length byte. */
    public static boolean isPlausibleUid(String raw) {
        String hex = cleanHexOnly(raw);
        int n = hex.length();
        if (n == 8 || n == 10 || n == 12 || n == 14 || n == 16 || n == 18 || n == 20 || n == 22) {
            return true;
        }
        String digits = digitsOnly(raw);
        return digits.length() >= 5 && digits.length() <= 13;
    }

    /** Drop a leading or trailing 00 byte, and a 04/07/0A length byte on either end. */
    private static void addTrimmedUid(Set<String> out, String hex) {
        if (hex == null) {
            return;
        }
        String trim = hex;
        for (int i = 0; i < 4 && trim.length() > 8; i++) {
            if (trim.startsWith("00")) {
                trim = trim.substring(2);
            } else if (trim.endsWith("00")) {
                trim = trim.substring(0, trim.length() - 2);
            } else {
                break;
            }
            if (trim.length() == 8 || trim.length() == 14 || trim.length() == 20) {
                addPair(out, trim);
                break;
            }
        }
    }

    private static void addLengthPrefixed(Set<String> out, String hex) {
        if (hex == null || hex.length() < 4 || hex.length() % 2 != 0) {
            return;
        }
        String head = hex.substring(0, 2);
        String tail = hex.substring(hex.length() - 2);
        int headLen = lengthPrefixBytes(head);
        int tailLen = lengthPrefixBytes(tail);
        if (headLen > 0 && hex.length() == headLen + 2) {
            addPair(out, hex.substring(2));
        }
        if (tailLen > 0 && hex.length() == tailLen + 2) {
            addPair(out, hex.substring(0, hex.length() - 2));
        }
    }

    private static int lengthPrefixBytes(String byteHex) {
        if ("04".equals(byteHex)) {
            return 8;
        }
        if ("07".equals(byteHex)) {
            return 14;
        }
        if ("0A".equals(byteHex)) {
            return 20;
        }
        return 0;
    }

    private static String digitsOnly(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                b.append(c);
            } else if (c != ' ' && c != ':' && c != '-') {
                return "";
            }
        }
        return b.toString();
    }

    private static void addPair(Set<String> out, String clean) {
        if (clean == null || clean.length() < 4) {
            return;
        }
        out.add(clean);
        String rev = reverseBytes(clean);
        if (rev.length() >= 4) {
            out.add(rev);
        }
    }
}
