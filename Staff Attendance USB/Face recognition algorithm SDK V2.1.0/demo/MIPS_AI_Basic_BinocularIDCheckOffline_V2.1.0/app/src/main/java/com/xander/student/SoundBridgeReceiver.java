package com.xander.student;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Same SMDT speaker path as the card kiosk. HeyStar can trigger it over ADB
 * without leaving the face camera.
 */
public class SoundBridgeReceiver extends BroadcastReceiver {
    public static final String ACTION_OK = BuildConfig.APPLICATION_ID + ".SOUND_OK";
    public static final String ACTION_FAIL = BuildConfig.APPLICATION_ID + ".SOUND_FAIL";
    public static final String ACTION_PREP = BuildConfig.APPLICATION_ID + ".SOUND_PREP";
    public static final String ACTION_PHOTO = BuildConfig.APPLICATION_ID + ".SHOW_PHOTO";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) {
            return;
        }
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        if (ACTION_PHOTO.equals(action)) {
            String file = intent.getStringExtra("file");
            String label = intent.getStringExtra("label");
            PhotoOverlay.show(context, file, label);
            return;
        }
        KioskSound.prepare(context);
        if (ACTION_FAIL.equals(action)) {
            KioskSound.fail(context);
        } else if (ACTION_OK.equals(action)) {
            KioskSound.welcome(context);
        }
    }
}
