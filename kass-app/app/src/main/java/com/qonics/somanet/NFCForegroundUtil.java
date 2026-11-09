package com.qonics.somanet;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Modern NFC capture for student RFID cards.
 * Prefer Reader Mode (Android 4.4+) so blank MIFARE tags are delivered even when
 * they are not NDEF. Foreground dispatch is kept as a fallback.
 */
public class NFCForegroundUtil {

    public interface TagListener {
        void onTagDiscovered(Tag tag);
    }

    private static final String TAG = "NFCForegroundUtil";

    private final NfcAdapter nfc;
    private final Activity activity;
    private IntentFilter[] intentFiltersArray;
    private PendingIntent pendingIntent;
    private String[][] techListsArray;
    private TagListener tagListener;
    private boolean readerModeActive = false;

    public NFCForegroundUtil(Activity activity) {
        this.activity = activity;
        nfc = NfcAdapter.getDefaultAdapter(activity.getApplicationContext());
        buildForegroundDispatch();
    }

    public void setTagListener(TagListener listener) {
        this.tagListener = listener;
    }

    private void buildForegroundDispatch() {
        // Must be MUTABLE so the system can attach EXTRA_TAG on modern Android.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Pre-12: UPDATE_CURRENT is enough; avoid IMMUTABLE (blocks tag extras).
            flags |= PendingIntent.FLAG_UPDATE_CURRENT;
        }

        pendingIntent = PendingIntent.getActivity(
                activity,
                0,
                new Intent(activity, activity.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
        );

        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter techDetected = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        IntentFilter ndefDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndefDetected.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException ignored) {
        }
        intentFiltersArray = new IntentFilter[]{tagDetected, techDetected, ndefDetected};

        techListsArray = new String[][]{
                new String[]{NfcA.class.getName()},
                new String[]{NfcB.class.getName()},
                new String[]{NfcF.class.getName()},
                new String[]{NfcV.class.getName()},
                new String[]{IsoDep.class.getName()},
                new String[]{MifareClassic.class.getName()},
                new String[]{MifareUltralight.class.getName()},
                new String[]{Ndef.class.getName()},
                new String[]{NdefFormatable.class.getName()},
        };
    }

    public void enableForeground() {
        if (nfc == null || !nfc.isEnabled()) {
            Log.w(TAG, "NFC adapter missing or disabled");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Skip NDEF check so blank RFID student cards still fire.
                int flags = NfcAdapter.FLAG_READER_NFC_A
                        | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_NFC_F
                        | NfcAdapter.FLAG_READER_NFC_V
                        | NfcAdapter.FLAG_READER_NFC_BARCODE
                        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                        | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;
                Bundle extras = new Bundle();
                // 0 = report the UID as soon as the card is in the field.
                extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 0);
                nfc.enableReaderMode(activity, tag -> {
                    Log.d(TAG, "ReaderMode tag: " + bytesToHex(tag.getId()));
                    if (tagListener != null) {
                        activity.runOnUiThread(() -> tagListener.onTagDiscovered(tag));
                    }
                }, flags, extras);
                readerModeActive = true;
                Log.d(TAG, "NFC ReaderMode enabled");
            } else {
                nfc.enableForegroundDispatch(activity, pendingIntent, intentFiltersArray, techListsArray);
                readerModeActive = false;
                Log.d(TAG, "NFC ForegroundDispatch enabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "enableForeground failed: " + e.getMessage(), e);
            try {
                nfc.enableForegroundDispatch(activity, pendingIntent, intentFiltersArray, techListsArray);
                readerModeActive = false;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Location attendance only. A presence delay of 0 keeps the first card forever on Samsung,
     * so the next card never arrives. This session notices when the card leaves.
     * Behavior scan stays on enableForeground().
     */
    public void enableScanReader() {
        if (nfc == null || !nfc.isEnabled()) {
            Log.w(TAG, "NFC adapter missing or disabled");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                int flags = NfcAdapter.FLAG_READER_NFC_A
                        | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_NFC_F
                        | NfcAdapter.FLAG_READER_NFC_V
                        | NfcAdapter.FLAG_READER_NFC_BARCODE
                        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                        | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;
                Bundle extras = new Bundle();
                extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 100);
                nfc.enableReaderMode(activity, tag -> {
                    releaseTag(tag);
                    Log.d(TAG, "ScanReader tag: " + bytesToHex(tag.getId()));
                    if (tagListener != null) {
                        activity.runOnUiThread(() -> tagListener.onTagDiscovered(tag));
                    }
                }, flags, extras);
                readerModeActive = true;
                Log.d(TAG, "NFC scan reader enabled");
            } else {
                enableForeground();
            }
        } catch (Exception e) {
            Log.e(TAG, "enableScanReader failed: " + e.getMessage(), e);
            enableForeground();
        }
    }

    /** Tell the reader this card is finished so the next card can be read. */
    private void releaseTag(Tag tag) {
        if (tag == null || nfc == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        try {
            nfc.ignore(tag, 150, null, null);
        } catch (Exception e) {
            Log.w(TAG, "ignore tag failed: " + e.getMessage());
        }
    }

    public void disableForeground() {
        if (nfc == null) {
            return;
        }
        try {
            if (readerModeActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                nfc.disableReaderMode(activity);
            } else {
                nfc.disableForegroundDispatch(activity);
            }
        } catch (Exception e) {
            Log.e(TAG, "disableForeground failed: " + e.getMessage());
        }
        readerModeActive = false;
    }

    public NfcAdapter getNfc() {
        return nfc;
    }

    /** Reader-order hex (as Tag.getId()). Web also accepts this via lookup variants. */
    public static String bytesToHex(byte[] src) {
        if (src == null || src.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(src.length * 2);
        for (byte b : src) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Byte-pair reverse — matches web normalize_card_uid / assign-card storage form.
     * Example reader AABBCCDD → stored DDCCBBAA.
     */
    public static String reverseBytePairs(String hex) {
        if (hex == null) {
            return "";
        }
        String uid = hex.toUpperCase().replaceAll("[^A-F0-9]", "");
        if (uid.length() < 4 || uid.length() % 2 != 0) {
            return uid;
        }
        StringBuilder out = new StringBuilder(uid.length());
        for (int i = uid.length() - 2; i >= 0; i -= 2) {
            out.append(uid, i, i + 2);
        }
        return out.toString();
    }

    /**
     * Same UID forms as web card_uid_lookup_variants(): cleaned hex, byte-reversed
     * storage form, short-hex padding, and a decimal wedge converted to hex.
     */
    public static String[] lookupVariants(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String hex = raw == null ? "" : raw.toUpperCase(Locale.US).replaceAll("[^A-F0-9]", "");
        if (hex.length() % 2 == 1) {
            hex = "0" + hex;
        }
        addUid(out, hex);
        if (hex.length() > 0 && hex.length() < 8) {
            addUid(out, String.format(Locale.US, "%8s", hex).replace(' ', '0'));
        }
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() >= 5 && digits.length() <= 13) {
            addUid(out, decimalToHex(digits));
        }
        return out.toArray(new String[0]);
    }

    /** Assign-card storage form: clean hex, then reverse byte pairs once. */
    public static String storageForm(String readerHex) {
        String clean = readerHex == null ? "" : readerHex.toUpperCase(Locale.US).replaceAll("[^A-F0-9]", "");
        if (clean.length() % 2 == 1) {
            clean = "0" + clean;
        }
        if (clean.length() < 4 || clean.length() % 2 != 0) {
            return "";
        }
        return reverseBytePairs(clean);
    }

    private static void addUid(LinkedHashSet<String> out, String uid) {
        if (uid == null) {
            return;
        }
        uid = uid.toUpperCase(Locale.US).replaceAll("[^A-F0-9]", "");
        if (uid.length() < 4) {
            return;
        }
        if (uid.length() % 2 == 1) {
            uid = "0" + uid;
        }
        out.add(uid);
        String rev = reverseBytePairs(uid);
        if (rev.length() >= 4) {
            out.add(rev);
        }
    }

    private static String decimalToHex(String digits) {
        try {
            String trimmed = digits.replaceFirst("^0+", "");
            if (trimmed.isEmpty()) {
                return "00000000";
            }
            String hex = new BigInteger(trimmed, 10).toString(16).toUpperCase(Locale.US);
            if (hex.length() % 2 == 1) {
                hex = "0" + hex;
            }
            if (hex.length() < 8) {
                hex = String.format(Locale.US, "%8s", hex).replace(' ', '0');
            }
            return hex;
        } catch (Exception e) {
            return "";
        }
    }
}
