package com.xander.student;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.dk.usbNfc.DeviceManager.DeviceManagerCallback;
import com.dk.usbNfc.DeviceManager.UsbNfcDevice;
import com.dk.usbNfc.Exception.DeviceNoResponseException;
import com.dk.usbNfc.Tool.StringTool;

/**
 * DK USB NFC. {@code startAutoSearchCard} delay is in 100ms units — 20 was a 2s
 * wait after each tap. Keep it at 1 (100ms) so the next student is accepted immediately.
 * Do not beep on the USB channel; it blocks the next search.
 */
public class DkUsbCardReader {
    private static final String TAG = "XanderDkUsb";
    /** 100ms after a card leaves — fastest stable DK interval. */
    private static final byte SEARCH_DELAY = 1;
    /** ISO14443_P4 = 0x76. Original Card USB search. */
    private static final byte CARD_TYPE = 118;
    private static final long SAME_UID_MS = 220L;

    private UsbNfcDevice device;
    private CardReader.Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean started;
    private volatile boolean searching;
    private String lastUid = "";
    private long lastUidAt;

    private final DeviceManagerCallback callback = new DeviceManagerCallback() {
        public void onReceiveConnectionStatus(boolean connected) {
            if (connected) {
                DkUsbCardReader.this.debug("DK USB connected");
                DkUsbCardReader.this.startAutoSearch();
            } else {
                DkUsbCardReader.this.searching = false;
                DkUsbCardReader.this.debug("DK USB disconnected");
            }
        }

        public void onReceiveRfnSearchCard(boolean ok, int cardType, byte[] uidBytes, byte[] ats) {
            super.onReceiveRfnSearchCard(ok, cardType, uidBytes, ats);
            if (!ok || cardType == 0 || uidBytes == null || uidBytes.length == 0) {
                return;
            }
            String hex = StringTool.byteHexToSting(uidBytes);
            final String uid = CardUid.cleanHexOnly(hex);
            if (uid.length() < 4) {
                return;
            }
            long now = System.currentTimeMillis();
            if (uid.equals(lastUid) && now - lastUidAt < SAME_UID_MS) {
                return;
            }
            lastUid = uid;
            lastUidAt = now;
            String storage = CardUid.toStorageHex(hex);
            Log.e("XanderVisitor", "DK rawHex=" + hex + " clean=" + uid + " storage=" + storage
                    + " type=" + cardType + " bytesLen=" + uidBytes.length);
            DkUsbCardReader.this.debug("CARD OK " + storage + "  via dk-usb type=" + cardType);
            final CardReader.Listener l = DkUsbCardReader.this.listener;
            if (l != null) {
                DkUsbCardReader.this.main.post(new Runnable() {
                    public void run() {
                        l.onCard(uid);
                    }
                });
            }
        }
    };

    public void start(Activity activity, CardReader.Listener listener) {
        this.listener = listener;
        if (this.started && this.device != null) {
            this.debug("DK USB already running");
            if (!this.searching) {
                this.startAutoSearch();
            }
            return;
        }
        this.started = true;
        try {
            this.device = new UsbNfcDevice(activity);
            this.device.setCallBack(this.callback);
            this.debug("DK USB ready — same as Card USB (vid=1155 pid=22315)");
            if (this.device.usbHidManager != null && !this.device.usbHidManager.isClose()) {
                this.startAutoSearch();
            }
        } catch (Throwable t) {
            this.debug("DK USB init: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    public void stop() {
        this.started = false;
        this.searching = false;
        this.listener = null;
        if (this.device != null) {
            try {
                this.device.stoptAutoSearchCard();
            } catch (Throwable ignored) {
            }
            try {
                this.device.destroy();
            } catch (Throwable ignored) {
            }
            this.device = null;
        }
    }

    private void startAutoSearch() {
        final UsbNfcDevice d = this.device;
        if (d == null) {
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (d.usbHidManager != null && d.usbHidManager.isClose()) {
                        DkUsbCardReader.this.debug("DK USB not attached yet");
                        return;
                    }
                    try {
                        byte ver = d.getDeviceVersions();
                        DkUsbCardReader.this.debug("DK USB version " + String.format("%02x", ver));
                    } catch (DeviceNoResponseException e) {
                        DkUsbCardReader.this.debug("DK USB version: " + e.getMessage());
                    }
                    boolean on = d.startAutoSearchCard(SEARCH_DELAY, CARD_TYPE);
                    DkUsbCardReader.this.searching = on;
                    DkUsbCardReader.this.debug(on
                            ? "DK auto-search ON delay=" + SEARCH_DELAY + " — tap card at the bottom"
                            : "DK auto-search failed");
                } catch (Throwable t) {
                    DkUsbCardReader.this.debug("DK auto-search: "
                            + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
                }
            }
        }, "dk-usb-search").start();
    }

    private void debug(final String line) {
        Log.i(TAG, line);
        final CardReader.Listener l = this.listener;
        if (l != null) {
            this.main.post(new Runnable() {
                public void run() {
                    l.onDebug(line);
                }
            });
        }
    }
}
