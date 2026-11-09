package com.xander.student;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Background two-way sync: upload pending taps from every location, then
 * refresh students / visitors / location list when internet is up.
 */
public class SyncService extends JobIntentService {
    private static final String TAG = "XanderSync";
    private static final int JOB_ID = 4217;
    private static final Object LOCK = new Object();
    private static volatile long lastPeopleSync;
    private static volatile long lastCardSync;

    public static void request(Context ctx) {
        Intent i = new Intent(ctx, SyncService.class);
        enqueue(ctx, i);
    }

    public static void requestCards(Context ctx) {
        Intent i = new Intent(ctx, SyncService.class);
        i.putExtra("cards_only", true);
        enqueue(ctx, i);
    }

    public static void requestLocations(Context ctx) {
        Intent i = new Intent(ctx, SyncService.class);
        i.putExtra("locations_only", true);
        enqueue(ctx, i);
    }

    public static void requestForcePeople(Context ctx) {
        Intent i = new Intent(ctx, SyncService.class);
        i.putExtra("force_people", true);
        enqueue(ctx, i);
    }

    private static void enqueue(Context ctx, Intent i) {
        try {
            enqueueWork(ctx.getApplicationContext(), SyncService.class, JOB_ID, i);
        } catch (Throwable t) {
            Log.w(TAG, "enqueue: " + t.getMessage());
            try {
                ctx.getApplicationContext().startService(i);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onHandleWork(Intent intent) {
        Context app = getApplicationContext();
        XanderPrefs prefs = new XanderPrefs(app);
        if (!prefs.isLoggedIn()) {
            return;
        }
        if (!NetWatch.online(app)) {
            return;
        }
        XanderStore store = new XanderStore(app, prefs);
        PhotoCache photos = PhotoCache.get(app);
        photos.setBaseUrl(prefs.serverUrl());
        boolean cardsOnly = intent != null && intent.getBooleanExtra("cards_only", false);
        synchronized (LOCK) {
            try {
                int sent = store.flush();
                if (sent > 0) {
                    Log.i(TAG, "uploaded " + sent + " pending tap(s) remaining=" + store.pendingCount());
                }
            } catch (Exception e) {
                Log.w(TAG, "flush: " + e.getMessage());
            }
            if (cardsOnly) {
                syncCards(app, prefs, store);
                return;
            }
            if (intent != null && intent.getBooleanExtra("locations_only", false)) {
                syncLocations(prefs);
                return;
            }
            long now = System.currentTimeMillis();
            boolean forcePeople = intent != null && intent.getBooleanExtra("force_people", false);
            if (!forcePeople && now - lastPeopleSync < 45000) {
                syncCards(app, prefs, store);
                return;
            }
            try {
                JSONObject json = XanderApi.sync(prefs.serverUrl(), prefs.schoolId());
                if (!XanderApi.ok(json)) {
                    Log.w(TAG, "sync fail: " + XanderApi.message(json, "error"));
                    syncCards(app, prefs, store);
                    return;
                }
                lastPeopleSync = now;
                prefs.saveBootstrap(json);
                notifyLocation(app);
                if (json.has("visit_mode")) {
                    prefs.saveVisitMode(json.optString("visit_mode", "normal"));
                    notifyVisitMode(app, json.optString("visit_mode", "normal"));
                }
                JSONArray students = json.optJSONArray("students");
                JSONArray visitors = json.optJSONArray("visitors");
                store.replaceStudents(students);
                store.replaceVisitors(visitors);
                store.replaceStaff(json.optJSONArray("staff"));
                store.replacePrepRota(json.optJSONObject("prep_rota"));
                store.applyAttendanceToday(json.optJSONArray("attendance_today"));
                store.applyVisitorVisitsToday(json.optJSONArray("visitor_visits_today"));
                photos.prefetch(store.rosterWithPrepPhotos(students));
                lastCardSync = now;
                Log.i(TAG, "synced " + store.studentCount() + " students and "
                        + store.visitorCount() + " visitors, pending=" + store.pendingCount());
            } catch (Exception e) {
                Log.w(TAG, "people sync: " + e.getMessage());
                syncCards(app, prefs, store);
            }
        }
    }

    private void syncCards(Context app, XanderPrefs prefs, XanderStore store) {
        if (!NetWatch.online(app)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCardSync < 1200) {
            return;
        }
        try {
            JSONObject json = XanderApi.syncCards(prefs.serverUrl(), prefs.schoolId());
            if (!XanderApi.ok(json)) {
                Log.w(TAG, "cards fail: " + XanderApi.message(json, "error"));
                return;
            }
            lastCardSync = now;
            if (json.has("visit_mode")) {
                prefs.saveVisitMode(json.optString("visit_mode", "normal"));
                notifyVisitMode(app, json.optString("visit_mode", "normal"));
            }
            int changed = store.applyPeopleCards(json);
            Log.i(TAG, "cards synced students=" + store.studentCount()
                    + " visitors=" + store.visitorCount()
                    + " changed=" + changed);
        } catch (Exception e) {
            Log.w(TAG, "cards sync: " + e.getMessage());
        }
    }

    private void syncLocations(XanderPrefs prefs) {
        try {
            JSONObject json = XanderApi.syncLocations(prefs.serverUrl(), prefs.schoolId());
            if (!XanderApi.ok(json)) {
                Log.w(TAG, "locations fail: " + XanderApi.message(json, "error"));
                return;
            }
            JSONArray locs = json.optJSONArray("locations");
            if (locs == null) {
                return;
            }
            prefs.saveLocations(locs);
            notifyLocation(getApplicationContext());
            Log.i(TAG, "locations synced count=" + locs.length() + " area=" + prefs.areaName());
        } catch (Exception e) {
            Log.w(TAG, "locations sync: " + e.getMessage());
        }
    }

    private static void notifyLocation(Context app) {
        Intent intent = new Intent("com.xander.student.LOCATION_CHANGED");
        intent.setPackage(app.getPackageName());
        app.sendBroadcast(intent);
    }

    private static void notifyVisitMode(Context app, String mode) {
        Intent intent = new Intent("com.xander.student.VISIT_MODE");
        intent.setPackage(app.getPackageName());
        intent.putExtra("visit_mode", "visiting".equalsIgnoreCase(mode) ? "visiting" : "normal");
        app.sendBroadcast(intent);
    }
}
