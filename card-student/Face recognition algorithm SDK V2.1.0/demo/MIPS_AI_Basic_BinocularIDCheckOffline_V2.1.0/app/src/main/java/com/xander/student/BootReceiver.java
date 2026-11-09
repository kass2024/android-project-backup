package com.xander.student;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Auto-launch the student kiosk after device boot / app update.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "XanderBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        Log.i(TAG, "boot action=" + action);
        try {
            SmdtBridge.setBootApp(context.getPackageName());
            SmdtBridge.forceLoudspeaker();
        } catch (Throwable t) {
            Log.w(TAG, "boot setup: " + t.getMessage());
        }
        try {
            SyncService.request(context);
        } catch (Throwable t) {
            Log.w(TAG, "sync start: " + t.getMessage());
        }
        try {
            Intent launch = new Intent(context, LoginActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(launch);
        } catch (Throwable t) {
            Log.e(TAG, "launch failed: " + t.getMessage());
        }
    }
}
