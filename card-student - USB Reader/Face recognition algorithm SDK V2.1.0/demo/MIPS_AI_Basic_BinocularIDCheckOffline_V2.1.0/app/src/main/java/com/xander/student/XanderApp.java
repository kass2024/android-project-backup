package com.xander.student;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Auto-upload pending taps from every location as soon as internet is back,
 * even if the kiosk screen is idle.
 */
public class XanderApp extends Application {
    private static final String TAG = "XanderApp";
    private static final long PULSE_MS = 12000L;
    private boolean netRegistered;
    private final Handler pulse = new Handler(Looper.getMainLooper());

    private final Runnable pulseTick = new Runnable() {
        @Override
        public void run() {
            if (NetWatch.online(XanderApp.this)) {
                SyncService.request(XanderApp.this);
            }
            pulse.postDelayed(this, PULSE_MS);
        }
    };

    private final BroadcastReceiver netRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (NetWatch.online(context)) {
                Log.i(TAG, "network back — sync all locations");
                SyncService.request(context);
            }
        }
    };

    private final ConnectivityManager.NetworkCallback netCb = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            Log.i(TAG, "network available — sync all locations");
            SyncService.request(XanderApp.this);
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
        watchNetwork();
        pulse.postDelayed(pulseTick, 2500L);
        SyncService.request(this);
    }

    private void watchNetwork() {
        if (netRegistered) {
            return;
        }
        try {
            registerReceiver(netRx, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            netRegistered = true;
        } catch (Throwable t) {
            Log.w(TAG, "net register: " + t.getMessage());
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 24) {
                cm.registerDefaultNetworkCallback(netCb);
            } else {
                cm.registerNetworkCallback(new NetworkRequest.Builder().build(), netCb);
            }
        } catch (Throwable t) {
            Log.w(TAG, "net callback: " + t.getMessage());
        }
    }
}
