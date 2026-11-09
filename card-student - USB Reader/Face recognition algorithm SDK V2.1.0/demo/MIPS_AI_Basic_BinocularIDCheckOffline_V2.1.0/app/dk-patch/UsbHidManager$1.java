package com.dk.usbNfc.UsbHidManager;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.lang.reflect.Field;

/** Build-time replacement for the AAR inner class (PendingIntent flags=0 crashes on Android 12+). */
class UsbHidManager$1 implements Runnable {
    final UsbHidManager this$0;

    UsbHidManager$1(UsbHidManager manager) {
        this.this$0 = manager;
    }

    public void run() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(
                this$0.mContext, 0, new Intent("com.android.example.USB_PERMISSION"), flags);
        setField("mPermissionIntent", pi);
        IntentFilter filter = new IntentFilter("com.android.example.USB_PERMISSION");
        filter.addAction("android.hardware.usb.action.USB_ACCESSORY_ATTACHED");
        filter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        filter.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
        BroadcastReceiver rx = (BroadcastReceiver) getField("usbReceiver");
        if (Build.VERSION.SDK_INT >= 33) {
            this$0.mContext.registerReceiver(rx, filter, Context.RECEIVER_EXPORTED);
        } else {
            this$0.mContext.registerReceiver(rx, filter);
        }
        this$0.open();
    }

    private void setField(String name, Object value) {
        try {
            Field f = UsbHidManager.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(this$0, value);
        } catch (Exception ignored) {
        }
    }

    private Object getField(String name) {
        try {
            Field f = UsbHidManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(this$0);
        } catch (Exception e) {
            return null;
        }
    }
}
