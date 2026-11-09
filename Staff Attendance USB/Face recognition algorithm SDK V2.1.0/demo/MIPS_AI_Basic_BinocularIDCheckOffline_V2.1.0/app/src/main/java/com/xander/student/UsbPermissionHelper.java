package com.xander.student;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

/**
 * DK reader VID 1155 / PID 22315: request USB permission once if missing,
 * never request when {@link UsbManager#hasPermission(UsbDevice)} is already true.
 */
public final class UsbPermissionHelper {
    private static final String TAG = "XanderUsbPerm";
    static final String ACTION = BuildConfig.APPLICATION_ID + ".USB_PERMISSION";
    private static boolean asked;

    private UsbPermissionHelper() {}

    public static void ensure(Context ctx) {
        if (ctx == null) {
            return;
        }
        UsbManager usb = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        UsbDevice dk = DkUsbCardReader.findDk(usb);
        if (dk == null) {
            return;
        }
        if (usb.hasPermission(dk)) {
            Log.i(TAG, "DK already permitted — skip requestPermission");
            return;
        }
        if (asked) {
            return;
        }
        asked = true;
        try {
            PendingIntent pi = PendingIntent.getBroadcast(
                    ctx.getApplicationContext(), 0, new Intent(ACTION), XanderCompat.pendingMutable());
            usb.requestPermission(dk, pi);
            Log.i(TAG, "requestPermission once vid=" + dk.getVendorId() + " pid=" + dk.getProductId());
        } catch (Exception e) {
            asked = false;
            Log.w(TAG, "requestPermission: " + e.getMessage());
        }
    }
}
