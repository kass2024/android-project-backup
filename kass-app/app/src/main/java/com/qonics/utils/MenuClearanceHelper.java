package com.qonics.utils;

import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import com.qonics.somanet.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Applies admin Level Clearance (post_menu_clearance) to SmartSMS home tiles.
 */
public final class MenuClearanceHelper {

    public static final String PREF_APP_MENUS = "app_menus_json";
    public static final String PREF_MENU_KEYS = "menu_keys_json";
    public static final String PREF_MENU_FULL = "menu_full_access";

    private MenuClearanceHelper() {}

    public static void saveFromLogin(SharedPreferences.Editor editor, JSONObject object) {
        if (object == null) return;
        editor.putBoolean(PREF_MENU_FULL, object.optBoolean("menu_full_access", false));
        JSONObject menus = object.optJSONObject("app_menus");
        if (menus != null) {
            editor.putString(PREF_APP_MENUS, menus.toString());
        }
        JSONArray keys = object.optJSONArray("menu_keys");
        if (keys != null) {
            editor.putString(PREF_MENU_KEYS, keys.toString());
        }
    }

    public static void clear(SharedPreferences.Editor editor) {
        editor.remove(PREF_APP_MENUS).remove(PREF_MENU_KEYS).remove(PREF_MENU_FULL);
    }

    public static Map<String, Boolean> loadAppMenus(SharedPreferences prefs) {
        Map<String, Boolean> map = new HashMap<>();
        if (prefs.getBoolean(PREF_MENU_FULL, false)) {
            // Full access posts: show everything
            return map; // empty map = treat as allow-all in apply()
        }
        String json = prefs.getString(PREF_APP_MENUS, "");
        if (json == null || json.isEmpty()) {
            return map;
        }
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, o.optBoolean(k, false));
            }
        } catch (Exception ignored) {}
        return map;
    }

    public static Set<String> loadMenuKeys(SharedPreferences prefs) {
        Set<String> out = new HashSet<>();
        if (prefs == null) return out;
        String json = prefs.getString(PREF_MENU_KEYS, "");
        if (json == null || json.isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String key = arr.optString(i, "").trim();
                if (!key.isEmpty()) {
                    out.add(key);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static boolean hasAnyKey(SharedPreferences prefs, String... keys) {
        if (prefs == null) return true;
        if (prefs.getBoolean(PREF_MENU_FULL, false)) {
            return true;
        }
        Set<String> saved = loadMenuKeys(prefs);
        if (saved.isEmpty()) {
            return true;
        }
        if (keys == null || keys.length == 0) {
            return false;
        }
        for (String key : keys) {
            if (key != null && saved.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** Empty map or full-access → show all. Otherwise hide when flag is false. */
    public static void applyTile(View tile, Map<String, Boolean> menus, String key) {
        if (tile == null) return;
        if (menus == null || menus.isEmpty()) {
            tile.setVisibility(View.VISIBLE);
            return;
        }
        boolean show = Boolean.TRUE.equals(menus.get(key));
        tile.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public static void applyAll(SharedPreferences prefs, Map<String, View> tiles) {
        if (tiles == null) return;
        boolean full = prefs.getBoolean(PREF_MENU_FULL, false);
        Map<String, Boolean> menus = loadAppMenus(prefs);
        for (Map.Entry<String, View> e : tiles.entrySet()) {
            if (full || menus.isEmpty()) {
                if (e.getValue() != null) e.getValue().setVisibility(View.VISIBLE);
            } else {
                applyTile(e.getValue(), menus, e.getKey());
            }
        }
        View clinic = tiles.get("clinic");
        if (clinic != null) {
            clinic.setVisibility(canOpenClinic(prefs) ? View.VISIBLE : View.GONE);
        }
        View discipline = tiles.get("discipline");
        if (discipline != null && isTeacherPost(prefs)) {
            discipline.setVisibility(View.GONE);
        }
        View permission = tiles.get("permission");
        if (permission != null && canOpenPermissionMenu(prefs)) {
            permission.setVisibility(View.VISIBLE);
        }
        View leaves = tiles.get("leaves");
        View leaveManage = tiles.get("leave_management");
        if (canOpenLeaveMenu(prefs)) {
            if (leaves != null) {
                leaves.setVisibility(View.VISIBLE);
                TextView label = leaves.findViewById(R.id.txtleaves);
                if (label != null) {
                    label.setText(R.string.das_leave);
                }
            }
            if (leaveManage != null) {
                leaveManage.setVisibility(View.VISIBLE);
            }
        }
        packVisibleTiles(tiles);
    }

    /** Drop hidden tiles out of the grid so the ones that remain sit side by side. */
    private static void packVisibleTiles(Map<String, View> tiles) {
        GridLayout grid = null;
        for (View tile : tiles.values()) {
            if (tile != null && tile.getParent() instanceof GridLayout) {
                grid = (GridLayout) tile.getParent();
                break;
            }
        }
        if (grid == null) return;
        Object tagged = grid.getTag();
        ArrayList<View> order;
        if (tagged instanceof ArrayList) {
            order = (ArrayList<View>) tagged;
        } else {
            order = new ArrayList<>();
            for (int i = 0; i < grid.getChildCount(); i++) {
                order.add(grid.getChildAt(i));
            }
            grid.setTag(order);
        }
        grid.setColumnCount(2);
        grid.setRowCount(GridLayout.UNDEFINED);
        grid.removeAllViews();
        int margin = Math.round(6 * grid.getResources().getDisplayMetrics().density);
        for (View tile : order) {
            if (tile.getVisibility() != View.VISIBLE) continue;
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
            lp.setGravity(Gravity.FILL_HORIZONTAL | Gravity.TOP);
            lp.setMargins(margin, margin, margin, margin);
            grid.addView(tile, lp);
        }
    }

    /**
     * Visiting menu follows Parent visiting clearance.
     * Also true when the saved login has no clearance yet, so the tile is not hidden by mistake.
     */
    public static boolean canOpenVisiting(SharedPreferences prefs) {
        if (prefs == null) return false;
        if (prefs.getBoolean(PREF_MENU_FULL, false)) return true;
        Map<String, Boolean> menus = loadAppMenus(prefs);
        if (menus.isEmpty()) return true;
        if (Boolean.TRUE.equals(menus.get("visiting"))) return true;
        Set<String> keys = loadMenuKeys(prefs);
        if (keys.isEmpty()) return true;
        return keys.contains("parent_visiting")
                || keys.contains("parent_visiting/assign")
                || keys.contains("parent_visiting/cards")
                || keys.contains("parent_visiting/verify")
                || keys.contains("parent_visiting/report");
    }

    /**
     * Leave Management on the phone. Shown even when Level Clearance does not send the tile.
     * Executive Principal, Director, and Customer Care. Not Deputy Director or Director of Finance.
     */
    public static boolean canOpenLeaveMenu(SharedPreferences prefs) {
        if (prefs == null) return false;
        int post = prefs.getInt("soma_post", 0);
        if (post == 15 || post == 29 || post == 32) return true;
        String t = postTitle(prefs);
        if (t.contains("customer care")) return true;
        if (t.equals("executive principal")) return true;
        return t.equals("director");
    }

    /**
     * Permission menu: Patron, Matron, Customer Care, and Deputy Head Teacher in charge of discipline.
     */
    public static boolean canOpenPermissionMenu(SharedPreferences prefs) {
        if (prefs == null) return false;
        int post = prefs.getInt("soma_post", 0);
        if (post == 5 || post == 6 || post == 32) return true;
        String t = postTitle(prefs);
        if (t.isEmpty()) return false;
        if (t.contains("patron") || t.contains("matron")) return true;
        if (t.contains("customer care")) return true;
        boolean deputy = t.contains("deputy") || t.contains("dht");
        return deputy && t.contains("discipline");
    }

    /**
     * School monitoring and location attendance.
     * Directors see every Wisdom school. Customer Care sees Wisdom School Musanze only.
     */
    public static boolean canOpenSchoolMonitor(SharedPreferences prefs) {
        if (prefs == null) return false;
        int post = prefs.getInt("soma_post", 0);
        if (post == 15 || post == 24 || post == 29 || post == 30 || post == 32) return true;
        String name = postTitle(prefs);
        if (name.isEmpty()) return false;
        if (name.contains("customer care")) return true;
        return name.equals("director")
                || name.equals("deputy director")
                || name.equals("executive principal")
                || name.equals("director of finance")
                || name.equals("director of finances");
    }

    public static boolean musanzeOnlyMonitor(SharedPreferences prefs) {
        if (prefs == null) return false;
        if (prefs.getInt("soma_post", 0) == 32) return true;
        return postTitle(prefs).contains("customer care");
    }

    /** Classroom Teacher. Head Teacher is a different post. */
    public static boolean isTeacherPost(SharedPreferences prefs) {
        return "teacher".equals(postTitle(prefs));
    }

    /**
     * Material Check and Dormitory: Patron, Matron, Customer Care, Director,
     * Head of Discipline, Deputy HT Discipline.
     */
    public static boolean canUseMaterialAndDormitory(SharedPreferences prefs) {
        String t = postTitle(prefs);
        if (t.isEmpty()) return false;
        if (t.contains("patron") || t.contains("matron")) return true;
        if (t.contains("customer care")) return true;
        if ("director".equals(t)) return true;
        if (t.contains("head of discipline") || t.contains("dean of discipline")) return true;
        if (t.contains("dht discipline")) return true;
        return t.contains("discipline") && (t.contains("deputy") || t.contains("dht"));
    }

    /** Clinic follows the website Level Clearance for Clinic → Daily report. */
    public static boolean canOpenClinic(SharedPreferences prefs) {
        return clearedByWeb(prefs, "clinic", "clinic", "clinic_daily");
    }

    public static boolean canOpenStock(SharedPreferences prefs) {
        return clearedByWeb(prefs, "stock",
                "stock_management", "stk_dashboard", "stk_entry", "stk_issue",
                "stk_report_daily", "stk_report_weekly", "stk_report_monthly");
    }

    public static boolean canOpenAssets(SharedPreferences prefs) {
        return clearedByWeb(prefs, "assets",
                "asset_management", "asset_dashboard", "asset_assets");
    }

    /**
     * Stock and assets follow the Level Clearance payload from the website.
     * A saved app_menus flag wins. Full-access posts match the website, which grants every key.
     * Missing clearance does not open these screens.
     */
    private static boolean clearedByWeb(SharedPreferences prefs, String appKey, String... menuKeys) {
        if (prefs == null) return false;
        if (prefs.getBoolean(PREF_MENU_FULL, false)) return true;
        String json = prefs.getString(PREF_APP_MENUS, "");
        if (json != null && !json.isEmpty()) {
            try {
                JSONObject menus = new JSONObject(json);
                if (menus.has(appKey)) {
                    return menus.optBoolean(appKey, false);
                }
            } catch (Exception ignored) {
            }
        }
        Set<String> keys = loadMenuKeys(prefs);
        if (menuKeys != null) {
            for (String key : menuKeys) {
                if (key != null && keys.contains(key)) return true;
            }
        }
        return false;
    }

    public static boolean blockUnlessClinic(android.app.Activity activity) {
        if (activity == null) return true;
        android.content.SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(activity);
        if (canOpenClinic(prefs)) return false;
        android.widget.Toast.makeText(activity, R.string.stock_not_cleared, android.widget.Toast.LENGTH_LONG).show();
        activity.finish();
        return true;
    }

    public static boolean blockUnless(android.app.Activity activity, boolean stock) {
        if (activity == null) return true;
        android.content.SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(activity);
        boolean allowed = stock ? canOpenStock(prefs) : canOpenAssets(prefs);
        if (allowed) return false;
        android.widget.Toast.makeText(activity, R.string.stock_not_cleared, android.widget.Toast.LENGTH_LONG).show();
        activity.finish();
        return true;
    }

    private static boolean canOpenAppMenu(SharedPreferences prefs, String appKey, String... menuKeys) {
        if (prefs == null) return false;
        if (prefs.getBoolean(PREF_MENU_FULL, false)) return true;
        Map<String, Boolean> menus = loadAppMenus(prefs);
        if (menus.isEmpty()) return true;
        if (Boolean.TRUE.equals(menus.get(appKey))) return true;
        Set<String> keys = loadMenuKeys(prefs);
        if (keys.isEmpty()) return true;
        if (menuKeys == null) return false;
        for (String key : menuKeys) {
            if (key != null && keys.contains(key)) return true;
        }
        return false;
    }

    private static String postTitle(SharedPreferences prefs) {
        if (prefs == null) return "";
        String raw = prefs.getString("soma_post_title", "");
        if (raw == null) return "";
        return raw.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
