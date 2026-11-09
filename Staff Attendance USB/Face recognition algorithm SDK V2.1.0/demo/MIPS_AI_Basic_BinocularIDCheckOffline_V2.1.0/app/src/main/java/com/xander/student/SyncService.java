package com.xander.student;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Upload pending taps, then refresh staff roster (shifts, cards, deletions)
 * from the school whenever the device is online.
 */
public class SyncService extends IntentService {
    private static final String TAG = "XanderSync";
    public static final String ACTION_ROSTER = BuildConfig.APPLICATION_ID + ".ROSTER_UPDATED";
    private static final Object LOCK = new Object();
    private static volatile long lastLiteSync;
    private static volatile long lastFullSync;
    private static volatile boolean liteBusy;

    public SyncService() {
        super("XanderSync");
        setIntentRedelivery(true);
    }

    public static void request(Context ctx) {
        request(ctx, true);
    }

    public static void request(Context ctx, boolean liteRoster) {
        Intent i = new Intent(ctx, SyncService.class);
        i.putExtra("lite", liteRoster);
        try {
            ctx.startService(i);
        } catch (Throwable t) {
            Log.w(TAG, "startService: " + t.getMessage());
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Context app = getApplicationContext();
        XanderPrefs prefs = new XanderPrefs(app);
        if (!prefs.isLoggedIn()) {
            return;
        }
        if (!NetWatch.online(app)) {
            return;
        }
        XanderStore store = new XanderStore(app, prefs);
        PhotoCache photos = new PhotoCache(app);
        synchronized (LOCK) {
            try {
                int sent = store.flush();
                if (sent > 0) {
                    Log.i(TAG, "uploaded " + sent + " pending tap(s)");
                    notifyRoster(app);
                }
            } catch (Exception e) {
                Log.w(TAG, "flush: " + e.getMessage());
            }
            long now = System.currentTimeMillis();
            boolean lite = intent == null || intent.getBooleanExtra("lite", true);
            if (lite) {
                if (liteBusy || now - lastLiteSync < 8000L) {
                    return;
                }
                liteBusy = true;
                try {
                    JSONObject json = XanderApi.staffRoster(prefs.serverUrl(), prefs.schoolId());
                    if (!XanderApi.ok(json)) {
                        Log.w(TAG, "roster fail: " + XanderApi.message(json, "error"));
                        return;
                    }
                    JSONArray staff = json.optJSONArray("staff");
                    if (staff == null) {
                        return;
                    }
                    lastLiteSync = System.currentTimeMillis();
                    store.replaceStaff(staff);
                    photos.prefetch(staff);
                    notifyRoster(app);
                    Log.i(TAG, "roster " + store.staffWithCards() + " cards, pending=" + store.pendingCount());
                } catch (Exception e) {
                    Log.w(TAG, "roster: " + e.getMessage());
                } finally {
                    liteBusy = false;
                }
                return;
            }
            if (now - lastFullSync < 5L * 60L * 1000L) {
                return;
            }
            try {
                JSONObject json = XanderApi.sync(prefs.serverUrl(), prefs.schoolId());
                if (!XanderApi.ok(json)) {
                    Log.w(TAG, "sync fail: " + XanderApi.message(json, "error"));
                    return;
                }
                lastFullSync = now;
                lastLiteSync = now;
                prefs.saveBootstrap(json);
                JSONArray staff = json.optJSONArray("staff");
                store.replaceStaff(staff);
                store.replaceBlockedCards(json.optJSONArray("students"), json.optJSONArray("visitors"));
                photos.prefetch(staff);
                notifyRoster(app);
                Log.i(TAG, "full sync " + store.staffWithCards() + " staff cards");
            } catch (Exception e) {
                Log.w(TAG, "people sync: " + e.getMessage());
            }
        }
    }

    private static void notifyRoster(Context app) {
        try {
            app.sendBroadcast(new Intent(ACTION_ROSTER));
        } catch (Exception ignored) {
        }
    }
}
