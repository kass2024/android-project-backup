package com.xander.pos.reader;

import android.app.smdt.SmdtManager;
import android.app.smdt.SmdtManagerNew;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;

/**
 * RA08 card tap is Wiegand at /dev/wiegand (no Android NFC service).
 */
public class CardReader {
    private static final String TAG = "XanderCard";
    private static final String DEV = "/dev/wiegand";

    public interface Listener {
        void onCard(String uid);

        void onDebug(String line);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private SmdtManagerNew api;
    private SmdtManager legacy;
    private SmdtManagerNew.WiegandCallback callback;
    private Listener listener;
    private volatile boolean running;
    private Thread poll;
    private Thread devThread;
    private String lastRaw = "";
    private long lastAt;
    private int taps;
    private String lastShown = "none";

    public void start(Context ctx, Listener listener) {
        this.listener = listener;
        if (running) {
            debug("CARD DEBUG already listening  taps=" + taps + "  last=" + lastShown);
            return;
        }
        running = true;
        debug("CARD DEBUG starting…");
        try {
            api = SmdtManagerNew.getInstance(ctx.getApplicationContext());
            if (api != null) {
                try {
                    api.sys_setKeyReport(true);
                    debug("HID key-report ON");
                } catch (Throwable t) {
                    debug("HID key-report: " + t.getMessage());
                }
                callback = new SmdtManagerNew.WiegandCallback() {
                    @Override
                    public void onReadData(String data) {
                        ingest("cb", data);
                    }
                };
                api.custom_readWiegandData(callback);
                debug("Wiegand callback armed");
            } else {
                debug("SmdtManagerNew missing");
            }
        } catch (Throwable t) {
            debug("callback: " + t.getMessage());
        }
        try {
            legacy = SmdtManager.create(ctx.getApplicationContext());
            debug("legacy Wiegand poll ready");
        } catch (Throwable t) {
            debug("legacy: " + t.getMessage());
        }
        poll = new Thread(new Runnable() {
            @Override
            public void run() {
                long beat = 0;
                while (running) {
                    try {
                        if (legacy != null) {
                            String d = legacy.smdtReadWiegandData();
                            if (d != null && d.trim().length() > 0) {
                                ingest("poll", d);
                            }
                        }
                    } catch (Throwable t) {
                        debug("poll err " + t.getMessage());
                    }
                    long now = System.currentTimeMillis();
                    if (now - beat > 3000) {
                        beat = now;
                        debug("listening  taps=" + taps + "  last=" + lastShown);
                    }
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "wiegand-poll");
        poll.start();
        devThread = new Thread(new Runnable() {
            @Override
            public void run() {
                readDevice();
            }
        }, "wiegand-dev");
        devThread.start();
    }

    public void stop() {
        running = false;
        if (poll != null) {
            poll.interrupt();
            poll = null;
        }
        if (devThread != null) {
            devThread.interrupt();
            devThread = null;
        }
        if (api != null) {
            try {
                api.custom_releaseWiegandRead();
            } catch (Throwable ignored) {
            }
        }
        callback = null;
        listener = null;
    }

    public void ingestHid(String raw) {
        ingest("hid", raw);
    }

    private void readDevice() {
        File f = new File(DEV);
        if (!f.exists()) {
            debug("no " + DEV);
            return;
        }
        debug(DEV + " open (mode " + (f.canRead() ? "r" : "-") + (f.canWrite() ? "w" : "-") + ")");
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] buf = new byte[128];
            while (running) {
                int n = in.read(buf);
                if (n <= 0) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        return;
                    }
                    continue;
                }
                ingest("dev", decodeBytes(buf, n));
            }
        } catch (Throwable t) {
            debug("dev read: " + t.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void ingest(String src, String raw) {
        if (raw == null) {
            return;
        }
        String shown = raw.trim();
        if (shown.length() == 0) {
            return;
        }
        debug("RAW [" + src + "] " + shown);
        final String uid = CardUid.cleanRaw(shown);
        if (uid.length() < 4 || isAllZero(uid)) {
            debug("ignored [" + src + "] clean=" + uid);
            return;
        }
        long now = System.currentTimeMillis();
        if (uid.equals(lastRaw) && now - lastAt < 150) {
            return;
        }
        lastRaw = uid;
        lastAt = now;
        taps++;
        lastShown = uid;
        Log.i(TAG, "card " + CardUid.toStorage(uid) + " via " + src);
        debug("CARD OK " + CardUid.toStorage(uid) + "  via " + src + "  #" + taps);
        final Listener l = listener;
        if (l == null) {
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                l.onCard(uid);
            }
        });
    }

    private void debug(final String line) {
        Log.i(TAG, line);
        final Listener l = listener;
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

    public static String clean(String raw) {
        return CardUid.cleanRaw(raw);
    }

    private static String decodeBytes(byte[] buf, int n) {
        boolean ascii = true;
        for (int i = 0; i < n; i++) {
            int v = buf[i] & 0xff;
            if (v < 9 || v > 126) {
                ascii = false;
                break;
            }
        }
        if (ascii) {
            return new String(buf, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < n; i++) {
            hex.append(String.format("%02X", buf[i] & 0xff));
        }
        return hex.toString();
    }

    private static boolean isAllZero(String uid) {
        for (int i = 0; i < uid.length(); i++) {
            if (uid.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
