package com.example.smdt.smdtaisdkdemo.xander;

/**
 * Same RFID UID rules as public/assets/js/card-uid.js and card_uid_helper.php.
 *
 * Reader order (USB wedge / DK):  6C0477CD
 * Storage / Assign card display:  CD77046C  (byte pairs reversed)
 *
 * Assign-card saves toStorage(); lookups try both orders on the server.
 */
public final class CardUid {
    private CardUid() {}

    /** Clean reader input: decimal → hex (8 chars), strip non-hex. No reverse. */
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
            if (uid.charAt(i) < '0' || uid.charAt(i) > '9') {
                digits = false;
                break;
            }
        }
        if (digits) {
            try {
                uid = Long.toHexString(Long.parseLong(uid)).toUpperCase();
                while (uid.length() < 8) {
                    uid = "0" + uid;
                }
            } catch (Exception ignored) {
                return "";
            }
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < uid.length(); i++) {
            char c = Character.toUpperCase(uid.charAt(i));
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                b.append(c);
            }
        }
        uid = b.toString();
        return uid.length() >= 4 ? uid : "";
    }

    /** Reverse byte pairs: AABBCCDD → DDCCBBAA. */
    public static String reverseBytes(String hex) {
        hex = cleanRaw(hex);
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
     * Canonical form shown on Assign RFID Card (e.g. 6B18616D).
     * Send this to /api/device_scan so registered student cards match.
     */
    public static String toStorage(String raw) {
        return reverseBytes(cleanRaw(raw));
    }

    /** Scan form — clean only. Server still tries both orders. */
    public static String forScan(String raw) {
        return cleanRaw(raw);
    }
}
