package com.xander.pos.reader;

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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Same USB HID path as Card USB (DKCloudID): vendor 1155 / product 22315,
 * plus any HID class-3 reader. UID is taken from interrupt reports.
 */
public class UsbHidCardReader {
    private static final String TAG = "XanderUsbHid";
    public static final String ACTION = "com.xander.pos.USB_HID_CARD";
    /** Card USB demo device_filter.xml */
    public static final int DK_VID = 1155;
    public static final int DK_PID = 22315;

    private final Context ctx;
    private final UsbManager usb;
    private CardReader.Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private Thread readThread;
    private UsbDeviceConnection conn;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String a = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(a)
                    || ACTION.equals(a)) {
                debug("USB event " + a);
                requestAndOpen();
            }
        }
    };

    public UsbHidCardReader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.usb = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
    }

    public void start(CardReader.Listener listener) {
        this.listener = listener;
        running = true;
        if (!registered) {
            IntentFilter f = new IntentFilter(ACTION);
            f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            XanderCompat.register(ctx, receiver, f, true);
            registered = true;
        }
        dumpDevices();
        requestAndOpen();
    }

    public void stop() {
        running = false;
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        closeConn();
        if (registered) {
            try {
                ctx.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            registered = false;
        }
    }

    private void dumpDevices() {
        if (usb == null) {
            debug("UsbManager missing");
            return;
        }
        HashMap<String, UsbDevice> map = usb.getDeviceList();
        if (map == null || map.isEmpty()) {
            debug("USB list empty");
            return;
        }
        Iterator<UsbDevice> it = map.values().iterator();
        while (it.hasNext()) {
            UsbDevice d = it.next();
            debug("usb " + d.getDeviceName()
                    + " vid=" + d.getVendorId()
                    + " pid=" + d.getProductId()
                    + " class=" + d.getDeviceClass()
                    + " perm=" + usb.hasPermission(d)
                    + (isCardReader(d) ? " [READER]" : ""));
        }
    }

    private void requestAndOpen() {
        if (usb == null) {
            return;
        }
        HashMap<String, UsbDevice> map = usb.getDeviceList();
        if (map == null) {
            return;
        }
        UsbDevice reader = null;
        Iterator<UsbDevice> it = map.values().iterator();
        while (it.hasNext()) {
            UsbDevice d = it.next();
            if (!isCardReader(d)) {
                continue;
            }
            if (!usb.hasPermission(d)) {
                PendingIntent pi = PendingIntent.getBroadcast(ctx, 2, new Intent(ACTION), XanderCompat.pendingMutable());
                try {
                    usb.requestPermission(d, pi);
                    debug("ask permission vid=" + d.getVendorId() + " pid=" + d.getProductId());
                } catch (Exception e) {
                    debug("perm: " + e.getMessage());
                }
                continue;
            }
            reader = d;
            break;
        }
        if (reader != null) {
            open(reader);
        }
    }

    private static boolean isCardReader(UsbDevice d) {
        if (d == null) {
            return false;
        }
        // Official DK UsbNfcDevice owns vid 1155 / pid 22315 — do not steal the HID interface.
        if (d.getVendorId() == DK_VID && d.getProductId() == DK_PID) {
            return false;
        }
        boolean keyboardOnly = true;
        boolean hidGeneric = false;
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface inf = d.getInterface(i);
            if (inf == null) {
                continue;
            }
            if (inf.getInterfaceClass() != UsbConstants.USB_CLASS_HID) {
                continue;
            }
            // Protocol 1 = keyboard wedge. Leave it to dispatchKeyEvent so web decimal UIDs stay intact.
            if (inf.getInterfaceProtocol() == 1) {
                continue;
            }
            keyboardOnly = false;
            hidGeneric = true;
        }
        if (hidGeneric) {
            return true;
        }
        if (d.getDeviceClass() == UsbConstants.USB_CLASS_HID && !keyboardOnly) {
            return true;
        }
        return false;
    }

    private synchronized void open(final UsbDevice device) {
        closeConn();
        UsbInterface hid = null;
        UsbEndpoint in = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface inf = device.getInterface(i);
            if (inf == null) {
                continue;
            }
            if (inf.getInterfaceClass() != UsbConstants.USB_CLASS_HID
                    && device.getVendorId() != DK_VID) {
                continue;
            }
            if (inf.getInterfaceProtocol() == 1 && device.getVendorId() != DK_VID) {
                continue;
            }
            for (int e = 0; e < inf.getEndpointCount(); e++) {
                UsbEndpoint ep = inf.getEndpoint(e);
                if (ep.getDirection() == UsbConstants.USB_DIR_IN
                        && (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT
                        || ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                    hid = inf;
                    in = ep;
                    break;
                }
            }
            if (in != null) {
                break;
            }
        }
        if (hid == null || in == null) {
            debug("no HID IN endpoint on vid=" + device.getVendorId());
            return;
        }
        conn = usb.openDevice(device);
        if (conn == null) {
            debug("openDevice failed");
            return;
        }
        if (!conn.claimInterface(hid, true)) {
            debug("claimInterface failed");
            closeConn();
            return;
        }
        debug("HID open vid=" + device.getVendorId() + " pid=" + device.getProductId()
                + " ep=" + in.getAddress() + " max=" + in.getMaxPacketSize());
        final UsbEndpoint epIn = in;
        final UsbInterface claimed = hid;
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buf = new byte[Math.max(64, epIn.getMaxPacketSize())];
                while (running && conn != null) {
                    int n = conn.bulkTransfer(epIn, buf, buf.length, 400);
                    if (n > 0) {
                        handleReport(buf, n);
                    }
                }
                try {
                    if (conn != null) {
                        conn.releaseInterface(claimed);
                    }
                } catch (Exception ignored) {
                }
            }
        }, "usb-hid-card");
        readThread.start();
    }

    private void handleReport(byte[] buf, int n) {
        String hex = toHex(buf, n);
        debug("HID RAW " + hex);
        String ascii = new String(buf, 0, n);
        String uid = ascii.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (uid.length() < 4) {
            uid = uidFromReport(buf, n);
        }
        if (uid.length() < 4) {
            return;
        }
        debug("CARD OK " + CardUid.toStorage(uid) + "  via usb-hid");
        final CardReader.Listener l = listener;
        if (l == null) {
            return;
        }
        final String send = uid;
        main.post(new Runnable() {
            @Override
            public void run() {
                l.onCard(send);
            }
        });
    }

    static String uidFromReport(byte[] buf, int n) {
        String ascii = CardReader.clean(new String(buf, 0, n));
        if (ascii.length() >= 4 && !allZero(ascii)) {
            return ascii;
        }
        int start = 0;
        while (start < n && buf[start] == 0) {
            start++;
        }
        int end = n;
        while (end > start && buf[end - 1] == 0) {
            end--;
        }
        if (end - start < 2) {
            return CardReader.clean(toHex(buf, n));
        }
        return toHex(buf, start, end);
    }

    private static String toHex(byte[] buf, int n) {
        return toHex(buf, 0, n);
    }

    private static String toHex(byte[] buf, int from, int to) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < to; i++) {
            b.append(String.format("%02X", buf[i] & 0xff));
        }
        return b.toString();
    }

    private static boolean allZero(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private void closeConn() {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
            conn = null;
        }
    }

    private void debug(final String line) {
        Log.i(TAG, line);
        final CardReader.Listener l = listener;
        if (l == null) {
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                l.onDebug(line);
            }
        });
    }
}
