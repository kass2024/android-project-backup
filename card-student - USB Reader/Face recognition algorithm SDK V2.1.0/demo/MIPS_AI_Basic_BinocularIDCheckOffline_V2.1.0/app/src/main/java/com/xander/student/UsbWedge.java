package com.xander.student;

import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;

final class UsbWedge {
    interface Listener {
        void onUid(String uid);
    }

    private final StringBuilder buffer = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private long lastAt;
    private final Runnable idleFlush = new Runnable() {
        @Override
        public void run() {
            finishUid();
        }
    };

    UsbWedge(Listener listener) {
        this.listener = listener;
    }

    boolean onKey(KeyEvent event) {
        if (event == null) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            String chars = event.getCharacters();
            if (chars != null && chars.length() > 0) {
                buffer.append(chars);
                finishUid();
            }
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastAt > 500L) {
            buffer.setLength(0);
        }
        lastAt = now;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == KeyEvent.KEYCODE_TAB) {
            handler.removeCallbacks(idleFlush);
            finishUid();
            return true;
        }
        char ch = keyChar(event);
        if (ch != 0) {
            buffer.append(ch);
            handler.removeCallbacks(idleFlush);
            if (buffer.length() >= 4) {
                handler.postDelayed(idleFlush, 40L);
            }
        }
        return true;
    }

    void reset() {
        handler.removeCallbacks(idleFlush);
        buffer.setLength(0);
        lastAt = 0L;
    }

    private void finishUid() {
        handler.removeCallbacks(idleFlush);
        String uid = clean(buffer.toString());
        buffer.setLength(0);
        if (uid.length() >= 4 && listener != null) {
            listener.onUid(uid);
        }
    }

    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    static boolean isPhysicalKeyboard(KeyEvent event) {
        if (event == null || event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            return true;
        }
        if (event.getDeviceId() == -1) {
            return false;
        }
        InputDevice device = event.getDevice();
        if (device == null) {
            return event.getDeviceId() > 0;
        }
        if (device.isVirtual()) {
            return false;
        }
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                || (sources & InputDevice.SOURCE_DPAD) != 0;
    }

    private static char keyChar(KeyEvent event) {
        int uni = event.getUnicodeChar();
        if (uni > 0) {
            char ch = Character.toUpperCase((char) uni);
            if (Character.isLetterOrDigit(ch)) {
                return ch;
            }
        }
        int code = event.getKeyCode();
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            return (char) ('0' + (code - KeyEvent.KEYCODE_0));
        }
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
            return (char) ('0' + (code - KeyEvent.KEYCODE_NUMPAD_0));
        }
        if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) {
            return (char) ('A' + (code - KeyEvent.KEYCODE_A));
        }
        return 0;
    }
}
