package com.xander.student;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dk.usbNfc.DeviceManager.DeviceManagerCallback;
import com.dk.usbNfc.DeviceManager.UsbNfcDevice;
import com.dk.usbNfc.Exception.DeviceNoResponseException;
import com.dk.usbNfc.Tool.StringTool;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DkUsbCardReader {
    private static final String TAG = "XanderDkUsb";
    /** DK delay unit is 100ms. 1 = 100ms so the next student can tap immediately. */
    private static final byte SEARCH_DELAY = 1;
    private static final byte ISO14443_P4 = (byte) 118;
    private UsbNfcDevice device;
    private CardReader.Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService usbIo = Executors.newSingleThreadExecutor();
    private volatile boolean started;
    private volatile boolean searching;
    private volatile boolean versionLogged;

    private final DeviceManagerCallback callback = new DeviceManagerCallback() {
        @Override
        public void onReceiveConnectionStatus(boolean connected) {
            if (connected) {
                debug("DK USB connected");
                startAutoSearch();
            } else {
                searching = false;
                debug("DK USB disconnected");
            }
        }

        @Override
        public void onReceiveRfnSearchCard(boolean ok, int cardType, byte[] uidBytes, byte[] ats) {
            super.onReceiveRfnSearchCard(ok, cardType, uidBytes, ats);
            if (!ok || cardType == 0 || uidBytes == null || uidBytes.length == 0) {
                return;
            }
            String hex = StringTool.byteHexToSting(uidBytes);
            final String uid = CardUid.cleanHexOnly(hex);
            String storage = CardUid.toStorageHex(hex);
            debug("CARD OK " + storage + " via dk-usb type=" + cardType);
            final CardReader.Listener l = listener;
            if (l != null && uid.length() >= 4) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        l.onCard(uid);
                    }
                });
            }
            // Auto-search stops after each UID on this dongle. Re-arm immediately
            // on the USB thread — do not beep here (USB beep blocks the next tap).
            armSearch();
        }
    };

    public void start(Activity activity, CardReader.Listener listener) {
        this.listener = listener;
        if (started && device != null) {
            debug("DK USB already running");
            if (!searching) {
                startAutoSearch();
            }
            return;
        }
        started = true;
        try {
            device = new UsbNfcDevice(activity);
            device.setCallBack(callback);
            debug("DK USB ready — vid=1155 pid=22315");
            if (device.usbHidManager != null && !device.usbHidManager.isClose()) {
                startAutoSearch();
            }
        } catch (Throwable t) {
            debug("DK USB init: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    public void stop() {
        started = false;
        searching = false;
        listener = null;
        if (device != null) {
            try {
                device.stoptAutoSearchCard();
            } catch (Throwable ignored) {
            }
            try {
                device.destroy();
            } catch (Throwable ignored) {
            }
            device = null;
        }
    }

    public static boolean dkDevicePresent(Context ctx) {
        if (ctx == null) {
            return false;
        }
        UsbManager usb = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        return findDk(usb) != null;
    }

    static UsbDevice findDk(UsbManager usb) {
        if (usb == null) {
            return null;
        }
        HashMap<String, UsbDevice> map = usb.getDeviceList();
        if (map == null) {
            return null;
        }
        Iterator<UsbDevice> it = map.values().iterator();
        while (it.hasNext()) {
            UsbDevice d = it.next();
            if (d != null && d.getVendorId() == UsbHidCardReader.DK_VID
                    && d.getProductId() == UsbHidCardReader.DK_PID) {
                return d;
            }
        }
        return null;
    }

    private void startAutoSearch() {
        armSearch();
    }

    private void armSearch() {
        final UsbNfcDevice d = device;
        if (d == null || !started) {
            return;
        }
        usbIo.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (d.usbHidManager != null && d.usbHidManager.isClose()) {
                        debug("DK USB not attached yet");
                        return;
                    }
                    if (!versionLogged) {
                        try {
                            byte ver = d.getDeviceVersions();
                            debug("DK USB version " + String.format("%02x", ver));
                            versionLogged = true;
                        } catch (DeviceNoResponseException e) {
                            debug("DK USB version: " + e.getMessage());
                        }
                    }
                    boolean on = d.startAutoSearchCard(SEARCH_DELAY, ISO14443_P4);
                    searching = on;
                    debug(on ? "DK auto-search ON" : "DK auto-search failed");
                } catch (Throwable t) {
                    searching = false;
                    debug("DK auto-search: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
                }
            }
        });
    }

    private void debug(final String line) {
        Log.i(TAG, line);
        final CardReader.Listener l = listener;
        if (l != null) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    l.onDebug(line);
                }
            });
        }
    }
}
