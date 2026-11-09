package com.example.smdt.smdtaisdkdemo.xander;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import java.util.ArrayList;

/**
 * Android 6+ runtime grants. Required once targetSdk is 23 or higher.
 */
public class XanderPermissions {
    public static final int REQ = 91;

    public static boolean hasCameraAndStorage(Activity a) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        if (a.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 30) {
            return a.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static boolean hasOverlay(Activity a) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return Settings.canDrawOverlays(a);
    }

    public static boolean hasAllFiles(Activity a) {
        if (Build.VERSION.SDK_INT < 30) {
            return true;
        }
        return Environment.isExternalStorageManager();
    }

    public static boolean hasAll(Activity a) {
        return hasCameraAndStorage(a) && hasOverlay(a) && hasAllFiles(a);
    }

    public static void requestRuntime(Activity a) {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        ArrayList<String> need = new ArrayList<String>();
        if (a.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT < 30) {
            if (a.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        } else {
            if (a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (need.size() > 0) {
            String[] arr = new String[need.size()];
            need.toArray(arr);
            a.requestPermissions(arr, REQ);
        }
    }

    public static void requestOverlay(Activity a) {
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(a)) {
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
        } catch (Exception e) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    public static void requestAllFiles(Activity a) {
        if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) {
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            i.setData(Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
        } catch (Exception e) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    /**
     * Ask for the next missing grant. Returns true when everything is already allowed.
     */
    public static boolean ensure(Activity a) {
        if (!hasCameraAndStorage(a)) {
            requestRuntime(a);
            return false;
        }
        if (!hasOverlay(a)) {
            requestOverlay(a);
            return false;
        }
        if (!hasAllFiles(a)) {
            requestAllFiles(a);
            return false;
        }
        return true;
    }
}
