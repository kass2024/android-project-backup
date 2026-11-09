package com.example.smdt.smdtaisdkdemo.xander;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

/**
 * On-screen live log for the kiosk (also goes to logcat).
 */
public class KioskLog {
    public interface Sink {
        void onLine(String line);
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final StringBuilder BUF = new StringBuilder();
    private static Sink sink;

    public static void setSink(Sink next) {
        sink = next;
        if (next != null) {
            final String snap;
            synchronized (BUF) {
                snap = BUF.toString();
            }
            if (snap.length() > 0) {
                next.onLine(snap);
            }
        }
    }

    public static void bind(final TextView tv) {
        if (tv == null) {
            setSink(null);
            return;
        }
        setSink(new Sink() {
            @Override
            public void onLine(final String line) {
                UI.post(new Runnable() {
                    @Override
                    public void run() {
                        tv.setText(line);
                    }
                });
            }
        });
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg == null ? "" : msg);
        append(tag + ": " + msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg == null ? "" : msg);
        append("ERR " + tag + ": " + msg);
    }

    public static String friendlyNet(Throwable t) {
        if (t == null) {
            return "Network error";
        }
        String m = t.getMessage();
        if (m == null) {
            m = t.getClass().getSimpleName();
        }
        if (m.contains("Unable to resolve host") || m.contains("UnknownHost")
                || t instanceof java.net.UnknownHostException) {
            return "DNS failed — using school IP. Face still saves on this kiosk.";
        }
        if (m.length() > 140) {
            return m.substring(0, 140);
        }
        return m;
    }

    private static void append(String line) {
        if (line == null) {
            return;
        }
        if (line.length() > 160) {
            line = line.substring(0, 160);
        }
        String snap;
        synchronized (BUF) {
            BUF.append(line).append('\n');
            while (countLines(BUF) > 8) {
                int n = BUF.indexOf("\n");
                if (n < 0) {
                    break;
                }
                BUF.delete(0, n + 1);
            }
            snap = BUF.toString();
        }
        Sink s = sink;
        if (s != null) {
            s.onLine(snap);
        }
    }

    private static int countLines(StringBuilder b) {
        int n = 0;
        for (int i = 0; i < b.length(); i++) {
            if (b.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }
}
