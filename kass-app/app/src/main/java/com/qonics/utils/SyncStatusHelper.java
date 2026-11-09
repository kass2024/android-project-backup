package com.qonics.utils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.qonics.somanet.R;
import com.qonics.somanet.SyncReceiver;

/**
 * Auto-sync + live "Syncing..." status for student menus (same pipeline as CoreService).
 */
public class SyncStatusHelper {
    public static final String ACTION_PROGRESS = "sync.qonics.com.somanet.progress";
    public static final String ACTION_SUCCESS = "sync.qonics.com.somanet.success";

    private final Activity activity;
    private final TextView syncView;
    private BroadcastReceiver receiver;
    private boolean registered;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean autoStarted;

    public SyncStatusHelper(Activity activity, TextView syncView) {
        this.activity = activity;
        this.syncView = syncView;
    }

    public void onResume(boolean autoSync) {
        ensureReceiver();
        if (SyncReceiver.isSyncing()) {
            setSyncingUi();
        } else {
            setIdleUi();
        }
        if (autoSync && !autoStarted) {
            autoStarted = true;
            triggerSync();
        }
    }

    public void onPause() {
        if (registered && receiver != null) {
            try {
                activity.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            registered = false;
        }
    }

    public void triggerSync() {
        setSyncingUi();
        Intent syncIntent = new Intent("sync.qonics.com.somanet");
        syncIntent.setPackage(activity.getPackageName());
        activity.sendBroadcast(syncIntent);
        // Fallback if already locked / no network callback
        handler.postDelayed(() -> {
            if (!SyncReceiver.isSyncing() && syncView != null
                    && activity.getString(R.string.syncing).contentEquals(syncView.getText())) {
                setIdleUi();
            }
        }, 120000);
    }

    private void ensureReceiver() {
        if (registered) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                if (ACTION_PROGRESS.equals(intent.getAction())) {
                    if (intent.getBooleanExtra("syncing", false)) {
                        setSyncingUi();
                    }
                } else if (ACTION_SUCCESS.equals(intent.getAction())) {
                    int status = intent.getIntExtra("status", 1);
                    if (status == 2) {
                        setCompleteUi();
                        handler.postDelayed(SyncStatusHelper.this::setIdleUi, 1800);
                    } else {
                        setErrorUi();
                        handler.postDelayed(SyncStatusHelper.this::setIdleUi, 2200);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PROGRESS);
        filter.addAction(ACTION_SUCCESS);
        ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        registered = true;
    }

    private void setSyncingUi() {
        if (syncView == null) return;
        syncView.setEnabled(false);
        syncView.setText(R.string.syncing);
        syncView.setAlpha(0.85f);
    }

    private void setIdleUi() {
        if (syncView == null) return;
        syncView.setEnabled(true);
        syncView.setText(R.string.sync_now);
        syncView.setAlpha(1f);
    }

    private void setCompleteUi() {
        if (syncView == null) return;
        syncView.setEnabled(true);
        syncView.setText(R.string.sync_complete);
        syncView.setAlpha(1f);
    }

    private void setErrorUi() {
        if (syncView == null) return;
        syncView.setEnabled(true);
        syncView.setText(R.string.sync_failed_retry);
        syncView.setAlpha(1f);
    }
}
