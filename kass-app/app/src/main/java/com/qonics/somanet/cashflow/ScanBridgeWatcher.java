package com.qonics.somanet.cashflow;

import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.qonics.somanet.R;

import org.json.JSONObject;

/**
 * Opens the scanner only when the web form starts a new Smart Scan session.
 */
public final class ScanBridgeWatcher {

    private static final String PREF_SEEN = "cf_scan_seen_token";
    private static final String CHANNEL_ID = "cash_flow_scan_open";
    private static final int NOTIFY_ID = 8820;

    private ScanBridgeWatcher() {
    }

    public static void consider(Context context, JSONObject object) {
        if (context == null || object == null || !object.optBoolean("pending", false)) return;
        String token = object.optString("token", "");
        if (token.isEmpty()) return;
        if (token.equals(DocumentScanActivity.activeBridgeToken)) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        if (token.equals(prefs.getString(PREF_SEEN, ""))) return;
        prefs.edit().putString(PREF_SEEN, token).apply();
        open(context.getApplicationContext(), token);
    }

    private static void open(Context app, String token) {
        Intent intent = new Intent(app, DocumentScanActivity.class);
        intent.putExtra(DocumentScanActivity.EXTRA_MODE, DocumentScanActivity.MODE_BRIDGE);
        intent.putExtra(DocumentScanActivity.EXTRA_TOKEN, token);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (isForeground(app)) {
            NotificationManagerCompat.from(app).cancel(NOTIFY_ID);
            app.startActivity(intent);
            return;
        }
        bringForward(app, intent);
    }

    private static void bringForward(Context app, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(app, NOTIFY_ID, intent, flags);
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Smart Scan", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Opens the camera when the web form asks for a scan");
            manager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Smart Scan")
                .setContentText("The web form is waiting. Camera is opening.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setFullScreenIntent(pending, true);
        try {
            NotificationManagerCompat.from(app).notify(NOTIFY_ID, builder.build());
        } catch (SecurityException ignored) {
        }
        try {
            app.startActivity(intent);
        } catch (Exception ignored) {
        }
        try {
            Vibrator vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(180);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isForeground(Context app) {
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        java.util.List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return false;
        String name = app.getPackageName();
        for (ActivityManager.RunningAppProcessInfo proc : procs) {
            if (name.equals(proc.processName)) {
                return proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            }
        }
        return false;
    }
}
