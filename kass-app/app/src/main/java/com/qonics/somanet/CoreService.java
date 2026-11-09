package com.qonics.somanet;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;


import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class CoreService extends Service {
    Handler handler = new Handler();
    Message msg = new Message();
    Timer timer = new Timer();
    TimerTask task;
    SharedPreferences prefManager;
    Calendar c;
    BroadcastReceiver receiver;
    private boolean receiverRegistered = false;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Handler scanHandler = new Handler();
    private boolean scanChecking;
    private final Runnable scanWatch = new Runnable() {
        @Override
        public void run() {
            watchWebScan();
            scanHandler.postDelayed(this, 1500);
        }
    };

    public CoreService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureReceiverRegistered() {
        if (receiver == null) {
            receiver = new SyncReceiver();
        }
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter("sync.qonics.com.somanet");
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void requestSync() {
        ensureReceiverRegistered();
        Intent syncIntent = new Intent("sync.qonics.com.somanet");
        syncIntent.setPackage(getPackageName());
        sendBroadcast(syncIntent);
        try {
            com.qonics.somanet.cashflow.CashFlowAlerter.check(this);
        } catch (Exception ignored) {
        }
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.e("CORE SERVICE", "Network available — syncing");
                    requestSync();
                }
            };
            try {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                cm.registerNetworkCallback(request, networkCallback);
            } catch (Exception e) {
                Log.e("CORE SERVICE", "network callback failed: " + e.getMessage());
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefManager = PreferenceManager.getDefaultSharedPreferences(this);
        c = Calendar.getInstance();
        ensureReceiverRegistered();
        registerNetworkCallback();
        scanHandler.removeCallbacks(scanWatch);
        scanHandler.postDelayed(scanWatch, 1500);
        if (task == null) {
            task = new TimerTask() {
                @Override
                public void run() {
                    Log.e("CORE SERVICE", "Refresh " + Calendar.getInstance().getTimeInMillis() / 1000L);
                    requestSync();
                }
            };
            // Default every 60s (was 30s — reduced race windows)
            timer.schedule(task, 2000, prefManager.getInt("sync_interval", 60) * 1000L);
        }
        return START_STICKY;
    }

    private void watchWebScan() {
        if (scanChecking || prefManager == null) return;
        int staffId = prefManager.getInt("soma_id", 0);
        if (staffId <= 0) return;
        scanChecking = true;
        String url = MainActivity.SERVER + "cash_flow_scan_pending/" + staffId;
        new com.qonics.utils.HttpRequest(this).timeouts(3, 4).get(url, org.json.JSONObject.class, new com.qonics.utils.Callback() {
            @Override
            public void HttpCallback(org.json.JSONObject object, int status_code, String status) {
                scanChecking = false;
                com.qonics.somanet.cashflow.ScanBridgeWatcher.consider(CoreService.this, object);
            }
        });
    }

    @Override
    public void onDestroy() {
        scanHandler.removeCallbacks(scanWatch);
        super.onDestroy();
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                try {
                    cm.unregisterNetworkCallback(networkCallback);
                } catch (Exception ignored) {
                }
            }
            networkCallback = null;
        }
        if (receiverRegistered && receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
    }
}
