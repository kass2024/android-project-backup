package com.qonics.utils;

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

/** Mirror of somanet.NFCForegroundUtil for legacy imports. */
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
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
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
                int flags = NfcAdapter.FLAG_READER_NFC_A
                        | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_NFC_F
                        | NfcAdapter.FLAG_READER_NFC_V
                        | NfcAdapter.FLAG_READER_NFC_BARCODE
                        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                        | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;
                Bundle extras = new Bundle();
                extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
                nfc.enableReaderMode(activity, tag -> {
                    if (tagListener != null) {
                        activity.runOnUiThread(() -> tagListener.onTagDiscovered(tag));
                    }
                }, flags, extras);
                readerModeActive = true;
            } else {
                nfc.enableForegroundDispatch(activity, pendingIntent, intentFiltersArray, techListsArray);
                readerModeActive = false;
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
}
