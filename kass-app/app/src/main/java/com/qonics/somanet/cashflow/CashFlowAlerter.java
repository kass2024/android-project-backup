package com.qonics.somanet.cashflow;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Rings and vibrates for cash-flow work.
 * Offline: reminds from the last known queue. Online: fetches new items and alerts.
 */
public final class CashFlowAlerter {

    private static final String CHANNEL_ID = "cash_flow_ring";
    private static final int NOTIFY_ONLINE = 8810;
    private static final int NOTIFY_OFFLINE = 8811;
    private static final String PREF_IDS = "cf_alerted_ids";
    private static final String PREF_COUNT = "cf_alert_count";
    private static final String PREF_OFFLINE_SENT = "cf_offline_alert_sent";
    private static boolean checking = false;
    private static Ringtone activeRingtone;

    private CashFlowAlerter() {}

    public static void check(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        int staffId = prefs.getInt("soma_id", 0);
        int schoolId = prefs.getInt(MainActivity.SK_ID, 0);
        if (staffId <= 0 || schoolId <= 0) return;
        ensureChannel(app);
        if (!isOnline(app)) {
            alertOffline(app, prefs);
            return;
        }
        if (checking) return;
        checking = true;
        String url = MainActivity.SERVER + "get_cash_flow_pending/" + schoolId + "/" + staffId;
        new HttpRequest(app).timeouts(8, 12).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                checking = false;
                if (object == null || object.has("error") || !object.has("requests")) {
                    alertOffline(app, prefs);
                    return;
                }
                boolean wasOffline = prefs.getBoolean(PREF_OFFLINE_SENT, false);
                prefs.edit().putBoolean(PREF_OFFLINE_SENT, false).apply();
                JSONArray arr = object.optJSONArray("requests");
                int count = arr == null ? 0 : arr.length();
                StringBuilder ids = new StringBuilder();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.optJSONObject(i);
                        if (row == null) continue;
                        if (ids.length() > 0) ids.append(',');
                        ids.append(row.optInt("id", 0));
                    }
                }
                String previous = prefs.getString(PREF_IDS, "");
                prefs.edit().putString(PREF_IDS, ids.toString()).putInt(PREF_COUNT, count).apply();
                if (count <= 0) {
                    NotificationManagerCompat.from(app).cancel(NOTIFY_ONLINE);
                    return;
                }
                if (!wasOffline && ids.toString().equals(previous)) return;
                String title = count == 1 ? "Cash request needs you" : count + " cash requests need you";
                String body = "Open Cash Flow to review and approve.";
                showAlert(app, NOTIFY_ONLINE, title, body, true);
            }
        });
    }

    private static void alertOffline(Context app, SharedPreferences prefs) {
        int count = prefs.getInt(PREF_COUNT, 0);
        if (count <= 0 || prefs.getBoolean(PREF_OFFLINE_SENT, false)) return;
        prefs.edit().putBoolean(PREF_OFFLINE_SENT, true).apply();
        String title = count == 1 ? "1 cash request is waiting" : count + " cash requests are waiting";
        showAlert(app, NOTIFY_OFFLINE, title,
                "No internet. This phone will refresh the list as soon as you are online.", true);
    }

    private static void showAlert(Context app, int id, String title, String body, boolean ring) {
        Intent open = new Intent(app, CashFlowApprovalsActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(app, id, open, flags);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setVibrate(new long[]{0, 500, 200, 500, 200, 700});
        if (sound != null) {
            builder.setSound(sound);
        }
        try {
            NotificationManagerCompat.from(app).notify(id, builder.build());
        } catch (SecurityException ignored) {
        }
        if (ring) ringAndVibrate(app);
    }

    private static void ringAndVibrate(Context app) {
        try {
            Vibrator vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500, 200, 700}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 700}, -1);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (activeRingtone != null && activeRingtone.isPlaying()) {
                activeRingtone.stop();
            }
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            Ringtone ringtone = uri == null ? null : RingtoneManager.getRingtone(app, uri);
            if (ringtone != null) {
                activeRingtone = ringtone;
                ringtone.play();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        if (activeRingtone != null && activeRingtone.isPlaying()) {
                            activeRingtone.stop();
                        }
                    } catch (Exception ignored) {
                    }
                }, 4000);
            }
        } catch (Exception ignored) {
        }
    }

    private static void ensureChannel(Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Cash flow alerts", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Approval requests, with sound and vibration");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 700});
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (sound != null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(sound, attrs);
        }
        manager.createNotificationChannel(channel);
    }

    private static boolean isOnline(Context app) {
        try {
            ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }
}
