package com.qonics.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.core.content.ContextCompat;

import com.dk.usbNfc.DeviceManager.DeviceManagerCallback;
import com.dk.usbNfc.DeviceManager.UsbNfcDevice;
import com.dk.usbNfc.Exception.DeviceNoResponseException;
import com.dk.usbNfc.Tool.StringTool;

import java.util.HashMap;

/**
 * USB card reader for the payment search screen only.
 * Same devices as the student USB kiosk: DK dongle 1155/22315, other HID readers,
 * and keyboard-wedge readers. Phone NFC is not used here.
 */
public class PaymentUsbReader {
    private static final String TAG = "PaymentUsb";
    public static final String ACTION = "com.qonics.somanet.USB_PAYMENT_CARD";
    public static final int DK_VID = 1155;
    public static final int DK_PID = 22315;

    public interface Listener {
        void onReader(boolean connected);

        void onCard(String uid);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private Activity activity;
    private Listener listener;
    private UsbManager usb;
    private UsbNfcDevice dk;
    private UsbDeviceConnection hidConn;
    private Thread hidThread;
    private volatile boolean running;
    private boolean registered;
    private boolean readerWasConnected;
    private final StringBuilder wedge = new StringBuilder();
    private long wedgeAt;
    private final Runnable wedgeFlush = new Runnable() {
        @Override
        public void run() {
            finishWedge();
        }
    };

    private final BroadcastReceiver usbEvents = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!running || intent == null) return;
            refresh();
        }
    };

    private final DeviceManagerCallback dkCallback = new DeviceManagerCallback() {
        @Override
        public void onReceiveConnectionStatus(boolean connected) {
            if (!running) return;
            postReader(readerAttached() || connected);
            if (connected) armDkSearch();
        }

        @Override
        public void onReceiveRfnSearchCard(boolean ok, int cardType, byte[] uidBytes, byte[] ats) {
            super.onReceiveRfnSearchCard(ok, cardType, uidBytes, ats);
            if (!running || !ok || cardType == 0 || uidBytes == null || uidBytes.length == 0) return;
            String hex = StringTool.byteHexToSting(uidBytes);
            deliver(hex);
            armDkSearch();
        }
    };

    public void start(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.usb = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        running = true;
        if (!registered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            ContextCompat.registerReceiver(activity, usbEvents, filter, ContextCompat.RECEIVER_EXPORTED);
            registered = true;
        }
        refresh();
        startDk();
    }

    public void stop() {
        running = false;
        main.removeCallbacks(wedgeFlush);
        if (hidThread != null) {
            hidThread.interrupt();
            hidThread = null;
        }
        closeHid();
        if (dk != null) {
            try {
                dk.stoptAutoSearchCard();
            } catch (Throwable ignored) {
            }
            try {
                dk.destroy();
            } catch (Throwable ignored) {
            }
            dk = null;
        }
        if (registered && activity != null) {
            try {
                activity.unregisterReceiver(usbEvents);
            } catch (Exception ignored) {
            }
            registered = false;
        }
        listener = null;
        activity = null;
    }

    public boolean onKey(KeyEvent event) {
        if (!running || event == null || !isPhysicalKeyboard(event)) return false;
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            String chars = event.getCharacters();
            if (chars != null) wedge.append(chars);
            finishWedge();
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        long now = System.currentTimeMillis();
        if (now - wedgeAt > 500L) wedge.setLength(0);
        wedgeAt = now;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_TAB) {
            main.removeCallbacks(wedgeFlush);
            finishWedge();
            return true;
        }
        char ch = keyChar(event);
        if (ch != 0) {
            wedge.append(ch);
            main.removeCallbacks(wedgeFlush);
            if (wedge.length() >= 4) main.postDelayed(wedgeFlush, 40L);
        }
        return true;
    }

    public static boolean isPhysicalKeyboard(KeyEvent event) {
        if (event == null || event.getKeyCode() == KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) return true;
        if (event.getDeviceId() <= 0) return false;
        InputDevice device = event.getDevice();
        if (device == null) return true;
        if (device.isVirtual()) return false;
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD;
    }

    private void refresh() {
        boolean connected = readerAttached();
        if (connected) openHid();
        else closeHid();
        postReader(connected);
    }

    private boolean readerAttached() {
        if (usb == null) return false;
        HashMap<String, UsbDevice> map = usb.getDeviceList();
        if (map == null) return false;
        for (UsbDevice device : map.values()) {
            if (isDk(device) || isHidReader(device) || isWedge(device)) return true;
        }
        return false;
    }

    private void startDk() {
        if (activity == null || !running) return;
        if (!dkPresent()) return;
        try {
            if (dk == null) {
                dk = new UsbNfcDevice(activity);
                dk.setCallBack(dkCallback);
            }
            if (dk.usbHidManager != null && !dk.usbHidManager.isClose()) {
                postReader(true);
                armDkSearch();
            }
        } catch (Throwable t) {
            Log.i(TAG, "DK init " + t.getMessage());
        }
    }

    private boolean dkPresent() {
        if (usb == null) return false;
        HashMap<String, UsbDevice> map = usb.getDeviceList();
        if (map == null) return false;
        for (UsbDevice device : map.values()) {
            if (isDk(device)) return true;
        }
        return false;
    }

    private void armDkSearch() {
        final UsbNfcDevice device = dk;
        if (device == null || !running) return;
        new Thread(() -> {
            try {
                if (device.usbHidManager != null && device.usbHidManager.isClose()) return;
                try {
                    device.getDeviceVersions();
                } catch (DeviceNoResponseException ignored) {
                }
                device.startAutoSearchCard((byte) 1, (byte) 118);
            } catch (Throwable t) {
                Log.i(TAG, "DK search " + t.getMessage());
            }
        }, "payment-dk-search").start();
    }

    private void openHid() {
        if (usb == null || hidConn != null) return;
        UsbDevice reader = null;
        HashMap<String, UsbDevice> map = usb.getDeviceList();
        if (map == null) return;
        for (UsbDevice device : map.values()) {
            if (!isHidReader(device)) continue;
            if (!usb.hasPermission(device)) {
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                PendingIntent pi = PendingIntent.getBroadcast(activity, 7, new Intent(ACTION), flags);
                try {
                    usb.requestPermission(device, pi);
                } catch (Exception e) {
                    Log.i(TAG, "usb permission " + e.getMessage());
                }
                continue;
            }
            reader = device;
            break;
        }
        if (reader == null) return;
        UsbInterface hid = null;
        UsbEndpoint in = null;
        for (int i = 0; i < reader.getInterfaceCount(); i++) {
            UsbInterface inf = reader.getInterface(i);
            if (inf == null || inf.getInterfaceClass() != UsbConstants.USB_CLASS_HID) continue;
            if (inf.getInterfaceProtocol() == 1) continue;
            for (int e = 0; e < inf.getEndpointCount(); e++) {
                UsbEndpoint ep = inf.getEndpoint(e);
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    hid = inf;
                    in = ep;
                    break;
                }
            }
            if (in != null) break;
        }
        if (hid == null || in == null) return;
        hidConn = usb.openDevice(reader);
        if (hidConn == null || !hidConn.claimInterface(hid, true)) {
            closeHid();
            return;
        }
        final UsbEndpoint epIn = in;
        final UsbInterface claimed = hid;
        hidThread = new Thread(() -> {
            byte[] buf = new byte[Math.max(64, epIn.getMaxPacketSize())];
            while (running && hidConn != null) {
                int n = hidConn.bulkTransfer(epIn, buf, buf.length, 400);
                if (n > 0) deliver(uidFromReport(buf, n));
            }
            try {
                if (hidConn != null) hidConn.releaseInterface(claimed);
            } catch (Exception ignored) {
            }
        }, "payment-usb-hid");
        hidThread.start();
        postReader(true);
    }

    private void closeHid() {
        if (hidThread != null) {
            hidThread.interrupt();
            hidThread = null;
        }
        if (hidConn != null) {
            try {
                hidConn.close();
            } catch (Exception ignored) {
            }
            hidConn = null;
        }
    }

    private void finishWedge() {
        main.removeCallbacks(wedgeFlush);
        String uid = wedge.toString();
        wedge.setLength(0);
        deliver(uid);
    }

    private void deliver(String raw) {
        if (!running || raw == null) return;
        String uid = raw.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (uid.length() < 4) return;
        final Listener current = listener;
        if (current == null) return;
        main.post(() -> {
            if (running && listener != null) listener.onCard(uid);
        });
    }

    private void postReader(boolean connected) {
        if (connected == readerWasConnected) return;
        readerWasConnected = connected;
        final Listener current = listener;
        if (current == null) return;
        main.post(() -> {
            if (listener != null) listener.onReader(connected);
        });
    }

    private static boolean isDk(UsbDevice device) {
        return device != null && device.getVendorId() == DK_VID && device.getProductId() == DK_PID;
    }

    private static boolean isHidReader(UsbDevice device) {
        if (device == null || isDk(device)) return false;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface inf = device.getInterface(i);
            if (inf == null || inf.getInterfaceClass() != UsbConstants.USB_CLASS_HID) continue;
            if (inf.getInterfaceProtocol() != 1) return true;
        }
        return device.getDeviceClass() == UsbConstants.USB_CLASS_HID;
    }

    private static boolean isWedge(UsbDevice device) {
        if (device == null || isDk(device)) return false;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface inf = device.getInterface(i);
            if (inf != null && inf.getInterfaceClass() == UsbConstants.USB_CLASS_HID && inf.getInterfaceProtocol() == 1) {
                return true;
            }
        }
        return false;
    }

    private static String uidFromReport(byte[] buf, int n) {
        String ascii = new String(buf, 0, n).replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (ascii.length() >= 4) return ascii;
        int start = 0;
        while (start < n && buf[start] == 0) start++;
        int end = n;
        while (end > start && buf[end - 1] == 0) end--;
        if (end - start < 2) return "";
        StringBuilder hex = new StringBuilder();
        for (int i = start; i < end; i++) hex.append(String.format("%02X", buf[i] & 0xff));
        return hex.toString();
    }

    private static char keyChar(KeyEvent event) {
        int uni = event.getUnicodeChar();
        if (uni > 0) {
            char ch = Character.toUpperCase((char) uni);
            if (Character.isLetterOrDigit(ch)) return ch;
        }
        int code = event.getKeyCode();
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return (char) ('0' + (code - KeyEvent.KEYCODE_0));
        if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) return (char) ('A' + (code - KeyEvent.KEYCODE_A));
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
            return (char) ('0' + (code - KeyEvent.KEYCODE_NUMPAD_0));
        }
        return 0;
    }
}
