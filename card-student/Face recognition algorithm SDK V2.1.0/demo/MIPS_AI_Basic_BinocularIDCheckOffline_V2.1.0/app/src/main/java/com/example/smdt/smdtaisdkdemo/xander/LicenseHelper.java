package com.example.smdt.smdtaisdkdemo.xander;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * Native MIPS SDK returns -7 when it cannot read the .lic file (often /sdcard
 * blocked on Android 11). Copy a found license into app-private storage.
 */
public class LicenseHelper {
    private static final String TAG = "LicenseHelper";

    public static String resolve(Context ctx, String preferred) {
        ArrayList<String> tried = new ArrayList<String>();
        File found = firstExisting(preferred, tried);
        if (found == null) {
            found = firstExisting(null, tried);
        }
        if (found == null) {
            Log.w(TAG, "no license. tried " + tried);
            return preferred == null || preferred.length() == 0
                    ? XanderPrefs.DEFAULT_LIC : preferred;
        }
        File dest = new File(ctx.getFilesDir(), "mipsAi.lic");
        if (copy(found, dest) && dest.length() > 32) {
            Log.i(TAG, "license copied " + found.getAbsolutePath() + " -> " + dest.getAbsolutePath()
                    + " bytes=" + dest.length());
            return dest.getAbsolutePath();
        }
        Log.w(TAG, "copy failed, using " + found.getAbsolutePath());
        return found.getAbsolutePath();
    }

    public static String describeMissing() {
        StringBuilder b = new StringBuilder();
        String[] paths = candidates(null);
        for (int i = 0; i < paths.length; i++) {
            if (paths[i] == null || paths[i].length() == 0) {
                continue;
            }
            File f = new File(paths[i]);
            b.append(f.exists() ? "OK " : "NO ");
            b.append(paths[i]);
            if (f.exists()) {
                b.append(" (").append(f.length()).append("b)");
            }
            b.append('\n');
        }
        return b.toString();
    }

    private static File firstExisting(String preferred, ArrayList<String> tried) {
        String[] paths = candidates(preferred);
        for (int i = 0; i < paths.length; i++) {
            if (paths[i] == null || paths[i].length() == 0) {
                continue;
            }
            tried.add(paths[i]);
            File f = new File(paths[i]);
            if (f.isFile() && f.length() > 32 && f.canRead()) {
                return f;
            }
        }
        File dir = new File(sdcard(), "mipsLic");
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) {
                String n = kids[i].getName().toLowerCase();
                if (n.endsWith(".lic") && kids[i].length() > 32) {
                    return kids[i];
                }
            }
        }
        return null;
    }

    private static String[] candidates(String preferred) {
        String sd = sdcard();
        return new String[]{
                preferred,
                sd + "/mipsLic/mipsAi.lic",
                sd + "/mipsLic/mipsAI.lic",
                sd + "/mipsLic/mipsai.lic",
                "/sdcard/mipsLic/mipsAi.lic",
                "/sdcard/mipsLic/mipsAI.lic",
                "/storage/emulated/0/mipsLic/mipsAi.lic",
                "/storage/emulated/0/mipsLic/mipsAI.lic",
                "/mnt/sdcard/mipsLic/mipsAi.lic",
                "/mnt/sdcard/mipsLic/mipsAI.lic",
                sd + "/mipsAi.lic",
                "/sdcard/mipsAi.lic",
                "/system/etc/mipsLic/mipsAi.lic",
                "/vendor/etc/mipsLic/mipsAi.lic"
        };
    }

    private static String sdcard() {
        try {
            File e = Environment.getExternalStorageDirectory();
            if (e != null) {
                return e.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        return "/sdcard";
    }

    private static boolean copy(File src, File dest) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return dest.length() == src.length();
        } catch (Exception e) {
            Log.w(TAG, "copy: " + e.getMessage());
            return false;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
