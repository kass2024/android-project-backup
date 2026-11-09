package com.xander.pos.reader;

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
            // Exactly 8 digits from DK are hex — keep as hex (same as cleanHexOnly).
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

    /**
     * Value the discipline menu posts to the live server: reader-order hex from
     * the NFC UID bytes. No byte-pair reverse. The server matches this and the
     * stored reversed form itself.
     */
    public static String readerForm(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().replaceAll("\\s+", "");
        if (trimmed.length() == 0) {
            return "";
        }
        boolean digits = true;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                digits = false;
                break;
            }
        }
        if (digits && trimmed.length() != 8) {
            try {
                String hex = Long.toHexString(Long.parseLong(trimmed)).toUpperCase();
                if (hex.length() % 2 == 1) {
                    hex = "0" + hex;
                }
                while (hex.length() < 8) {
                    hex = "0" + hex;
                }
                return hex.length() >= 4 ? hex : "";
            } catch (Exception ignored) {
                return "";
            }
        }
        String hex = cleanHexOnly(trimmed);
        if (hex.length() % 2 == 1) {
            hex = "0" + hex;
        }
        return hex.length() >= 4 ? hex : "";
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
        // Undo 05A5EC42 ← mistaken decimal parse of hex 94760002
        String hex = cleanHexOnly(raw);
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
