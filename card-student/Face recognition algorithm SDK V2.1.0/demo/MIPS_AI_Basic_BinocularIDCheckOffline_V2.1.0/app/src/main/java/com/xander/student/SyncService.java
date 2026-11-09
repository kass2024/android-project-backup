package com.xander.student;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background two-way sync: upload pending taps, download students/visitors + today's state.
 * Card UIDs are polled separately so newly assigned cards land within seconds.
 */
public class SyncService extends IntentService {
    private static final String TAG = "XanderSync";
    private static final Object LOCK = new Object();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile boolean again;
    private static volatile long lastPeopleSync;
    private static volatile long lastCardSync;
    private static volatile long lastVisitorSync;

    public SyncService() {
        super("XanderSync");
        setIntentRedelivery(true);
    }

    public static void request(Context ctx) {
        again = true;
        Intent i = new Intent(ctx, SyncService.class);
        try {
            ctx.startService(i);
        } catch (Throwable t) {
            Log.w(TAG, "startService: " + t.getMessage());
        }
    }

    /** Pull newly assigned visitor cards. Does not upload attendance or replace students. */
    public static void requestVisitors(Context ctx) {
        Intent i = new Intent(ctx, SyncService.class);
        i.putExtra("visitors_only", true);
        try {
            ctx.startService(i);
        } catch (Throwable t) {
            Log.w(TAG, "startService visitors: " + t.getMessage());
        }
    }

    public static void requestCards(Context ctx) {
        Intent i = new Intent(ctx, SyncService.class);
        i.putExtra("cards_only", true);
        try {
            ctx.startService(i);
        } catch (Throwable t) {
            Log.w(TAG, "startService cards: " + t.getMessage());
        }
    }

    public static void requestForcePeople(Context ctx) {
        Intent i = new Intent(ctx, SyncService.class);
        i.putExtra("force_people", true);
        try {
            ctx.startService(i);
        } catch (Throwable t) {
            Log.w(TAG, "startService people: " + t.getMessage());
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        again = true;
        if (!RUNNING.compareAndSet(false, true)) {
            return;
        }
        try {
            do {
                again = false;
                runOnce(intent);
                intent = null;
            } while (again);
        } finally {
            RUNNING.set(false);
            if (again) {
                request(this);
            }
        }
    }

    private void runOnce(Intent intent) {
        Context app = getApplicationContext();
        XanderPrefs prefs = new XanderPrefs(app);
        if (!prefs.isLoggedIn()) {
            return;
        }
        XanderStore store = new XanderStore(app, prefs);
        if (intent != null && intent.getBooleanExtra("visitors_only", false)) {
            syncVisitorCards(app, prefs, store);
            return;
        }
        PhotoCache photos = new PhotoCache(app);
        synchronized (LOCK) {
            try {
                int sent = store.flush();
                if (sent > 0) {
                    Log.i(TAG, "uploaded " + sent + " pending tap(s)");
                }
            } catch (Exception e) {
                Log.w(TAG, "flush: " + e.getMessage());
            }
            if (!NetWatch.online(app)) {
                return;
            }
            // Cards first. A full roster download must not block new UIDs or the upload above.
            syncCards(app, prefs, store);
            long now = System.currentTimeMillis();
            boolean forcePeople = intent != null && intent.getBooleanExtra("force_people", false);
            if (!forcePeople && now - lastPeopleSync < 60000) {
                return;
            }
            try {
                JSONObject json = XanderApi.sync(prefs.serverUrl(), prefs.schoolId());
                if (!XanderApi.ok(json)) {
                    Log.w(TAG, "sync fail: " + XanderApi.message(json, "error"));
                    return;
                }
                lastPeopleSync = now;
                prefs.saveBootstrap(json);
                JSONArray students = json.optJSONArray("students");
                JSONArray visitors = json.optJSONArray("visitors");
                if (students != null && students.length() > 0) {
                    store.replaceStudents(students);
                    photos.applyRoster(students);
                } else {
                    Log.w(TAG, "roster empty — kept " + store.studentCount() + " local students");
                }
                if (visitors != null && visitors.length() > 0) {
                    store.replaceVisitors(visitors);
                }
                store.replaceStaff(json.optJSONArray("staff"));
                store.applyAttendanceToday(json.optJSONArray("attendance_today"));
                store.applyVisitorVisitsToday(json.optJSONArray("visitor_visits_today"));
                // Card poll is the source of truth for UIDs; re-apply in case the roster omitted one.
                lastCardSync = 0;
                syncCards(app, prefs, store);
                Log.i(TAG, "synced " + store.studentCount() + " students and "
                        + store.visitorCount() + " visitors, pending=" + store.pendingCount());
            } catch (Exception e) {
                Log.w(TAG, "people sync: " + e.getMessage());
            }
        }
    }

    private void syncVisitorCards(Context app, XanderPrefs prefs, XanderStore store) {
        if (!NetWatch.online(app)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastVisitorSync < 1500) {
            return;
        }
        lastVisitorSync = now;
        try {
            JSONObject json = XanderApi.syncCards(prefs.serverUrl(), prefs.schoolId());
            if (!XanderApi.ok(json)) {
                Log.w(TAG, "visitor cards fail: " + XanderApi.message(json, "error"));
                return;
            }
            int changed = store.applyVisitorCardIndex(json.optJSONArray("visitors"));
            if (json.has("visit_mode")) {
                prefs.saveVisitMode(json.optString("visit_mode", "normal"));
            }
            Log.i(TAG, "visitor cards synced changed=" + changed + " visitors=" + store.visitorCount());
        } catch (Exception e) {
            Log.w(TAG, "visitor cards: " + e.getMessage());
        }
    }

    private void syncCards(Context app, XanderPrefs prefs, XanderStore store) {
        if (!NetWatch.online(app)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCardSync < 2000) {
            return;
        }
        lastCardSync = now;
        try {
            JSONObject json = XanderApi.syncCards(prefs.serverUrl(), prefs.schoolId());
            if (!XanderApi.ok(json)) {
                Log.w(TAG, "cards fail: " + XanderApi.message(json, "error"));
                return;
            }
            int changed = store.applyPeopleCards(json);
            if (json.has("visit_mode")) {
                prefs.saveVisitMode(json.optString("visit_mode", "normal"));
            }
            Log.i(TAG, "cards synced students=" + store.studentCount()
                    + " visitors=" + store.visitorCount()
                    + " changed=" + changed);
        } catch (Exception e) {
            Log.w(TAG, "cards sync: " + e.getMessage());
        }
    }
}
