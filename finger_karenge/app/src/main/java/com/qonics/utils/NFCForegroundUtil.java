package com.qonics.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.tech.NfcA;
import android.util.Log;

/**
 * Created by sarah on 10/12/2017.
 */

public class NFCForegroundUtil {

    private NfcAdapter nfc;


    private Activity activity;
    private IntentFilter intentFiltersArray[];
    private PendingIntent intent;
    private String techListsArray[][];

    public NFCForegroundUtil(Activity activity) {
        super();
        this.activity = activity;
        nfc = NfcAdapter.getDefaultAdapter(activity.getApplicationContext());

        intent = PendingIntent.getActivity(activity, 0, new Intent(activity,
                activity.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);

        try {
            ndef.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Unable to specify */* Mime Type", e);
        }
        intentFiltersArray = new IntentFilter[] { ndef };

        techListsArray = new String[][] { new String[] { NfcA.class.getName() } };
        //techListsArray = new String[][] { new String[] { NfcA.class.getName(), NfcB.class.getName() }, new String[] {NfcV.class.getName()} };
    }

    public void enableForeground()
    {
        Log.d("demo", "Foreground NFC dispatch enabled");
        nfc.enableForegroundDispatch(activity, intent, intentFiltersArray, techListsArray);

    }

    public void disableForeground()
    {
        Log.d("demo", "Foreground NFC dispatch disabled");
        nfc.disableForegroundDispatch(activity);
    }

    public NfcAdapter getNfc() {
        return nfc;
    }

}


