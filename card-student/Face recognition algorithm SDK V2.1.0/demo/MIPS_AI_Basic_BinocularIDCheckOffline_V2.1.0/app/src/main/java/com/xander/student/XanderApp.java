package com.xander.student;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

/**
 * Keep Google TTS warm and auto-upload when the network returns, even if kiosk is paused.
 */
public class XanderApp extends Application {
    private static final String TAG = "XanderApp";
    private boolean netRegistered;

    private final BroadcastReceiver netRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (NetWatch.online(context)) {
                Log.i(TAG, "network back — sync");
                SyncService.requestCards(context);
                SyncService.request(context);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            KioskSound.prepare(this);
        } catch (Throwable t) {
            Log.w(TAG, "sound prep: " + t.getMessage());
        }
        if (!netRegistered) {
            try {
                registerReceiver(netRx, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                netRegistered = true;
            } catch (Throwable t) {
                Log.w(TAG, "net register: " + t.getMessage());
            }
        }
        SyncService.request(this);
    }
}
