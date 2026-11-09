package com.example.smdt.smdtaisdkdemo.xander;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Request USB permission for UVC RGB/IR cameras before Camera.open().
 */
public class DeviceUsb {
    public static final String ACTION = "com.example.smdt.smdtaisdkdemo.USB_PERMISSION";
    private static final String TAG = "DeviceUsb";

    public interface Listener {
        void onUsbReady();
    }

    private final Context ctx;
    private final UsbManager usb;
    private Listener listener;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION.equals(intent.getAction())) {
                return;
            }
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            Log.i(TAG, "usb permission " + granted + " " + (device == null ? "" : device.getDeviceName()));
            if (allGranted() && listener != null) {
                listener.onUsbReady();
            } else {
                requestMissing();
            }
        }
    };

    public DeviceUsb(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.usb = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
    }

    public void start(Listener listener) {
        this.listener = listener;
        if (!registered) {
            ctx.registerReceiver(receiver, new IntentFilter(ACTION));
            registered = true;
        }
        if (allGranted()) {
            if (listener != null) {
                listener.onUsbReady();
            }
        } else {
            requestMissing();
        }
    }

    public void stop() {
        if (registered) {
            try {
                ctx.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            registered = false;
        }
    }

    public boolean allGranted() {
        if (usb == null) {
            return true;
        }
        HashMap<String, UsbDevice> map = usb.getDeviceList();
        if (map == null || map.isEmpty()) {
            return true;
        }
        Iterator<UsbDevice> it = map.values().iterator();
        while (it.hasNext()) {
            UsbDevice d = it.next();
            if (d != null && !usb.hasPermission(d)) {
                return false;
            }
        }
        return true;
    }

    public void requestMissing() {
        if (usb == null) {
            return;
        }
        HashMap<String, UsbDevice> map = usb.getDeviceList();
        if (map == null) {
            return;
        }
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, new Intent(ACTION), 0);
        Iterator<UsbDevice> it = map.values().iterator();
        while (it.hasNext()) {
            UsbDevice d = it.next();
            if (d != null && !usb.hasPermission(d)) {
                Log.i(TAG, "requestPermission " + d.getDeviceName()
                        + " vid=" + d.getVendorId() + " pid=" + d.getProductId()
                        + " class=" + d.getDeviceClass());
                try {
                    usb.requestPermission(d, pi);
                } catch (Exception e) {
                    Log.w(TAG, e.getMessage());
                }
            }
        }
    }
}
