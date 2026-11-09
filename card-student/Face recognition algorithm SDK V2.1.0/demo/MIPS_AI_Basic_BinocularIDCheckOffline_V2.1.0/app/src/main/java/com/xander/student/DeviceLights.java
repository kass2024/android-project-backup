package com.xander.student;

import android.app.smdt.SmdtManagerNew;
import android.content.Context;
import android.hardware.Camera;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Idle: lamps off. Registered card: green. Unknown card: red.
 */
public class DeviceLights {
    private static final String TAG = "DeviceLights";
    public static final int LED_CONTROL = 5;
    private static final Object LOCK = new Object();
    private static SmdtManagerNew api;
    private static volatile boolean chromeHidden;
    private static volatile boolean camerasFixed;
    /** Class is not on this tablet. Stop retrying it on every clock tick. */
    private static volatile boolean smdtMissing;
    private static volatile String color = "";
    private static volatile int ledGen;
    private static final ExecutorService LEDS = Executors.newSingleThreadExecutor();

    public static void on(Context ctx) {
        off(ctx);
        hideSystemChrome(ctx);
    }

    public static void forceOn(Context ctx) {
        color = "";
        off(ctx);
    }

    public static void idle(Context ctx) {
        final Context app = ctx.getApplicationContext();
        final int gen = ++ledGen;
        LEDS.execute(new Runnable() {
            @Override
            public void run() {
                if (gen != ledGen) {
                    return;
                }
                off(app);
            }
        });
    }

    public static boolean holding() {
        return "green".equals(color) || "red".equals(color);
    }

    public static void pass(Context ctx) {
        paint(ctx, false, true, false);
    }

    public static void fail(Context ctx) {
        paint(ctx, false, false, true);
    }

    /** Lamp changes must not block the next card. */
    private static void paint(final Context ctx, final boolean white, final boolean green, final boolean red) {
        final Context app = ctx.getApplicationContext();
        final int gen = ++ledGen;
        LEDS.execute(new Runnable() {
            @Override
            public void run() {
                if (gen != ledGen) {
                    return;
                }
                setRgb(app, white, green, red);
            }
        });
    }

    public static void off(Context ctx) {
        synchronized (LOCK) {
            SmdtBridge.setLed("LED_GREEN", false);
            SmdtBridge.setLed("LED_RED", false);
            SmdtManagerNew n = neu(ctx);
            if (n != null) {
                killWhite(n, true);
                try {
                    n.dev_setLedLighted("LED_GREEN", false);
                    n.dev_setLedLighted("LED_RED", false);
                } catch (Throwable t) {
                    Log.w(TAG, "off: " + t.getMessage());
                }
            }
            color = "off";
        }
    }

    /** Camera firmware often turns the white bar back on. Do not kill green/red. */
    public static void keepWhiteOff(Context ctx) {
        synchronized (LOCK) {
            SmdtManagerNew n = neu(ctx);
            if (n == null) {
                return;
            }
            boolean hold = "green".equals(color) || "red".equals(color);
            if (!hold) {
                try {
                    n.sys_setControl(LED_CONTROL, false);
                } catch (Throwable ignored) {
                }
            }
            try {
                n.dev_setLedLighted("LED_WHITE", false);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void fixCameras(Context ctx, int rgbId, int irId, int degrees) {
        synchronized (LOCK) {
            if (camerasFixed) {
                return;
            }
            SmdtManagerNew n = neu(ctx);
            if (n == null) {
                return;
            }
            rotateImage(n, rgbId, degrees);
            camerasFixed = true;
        }
    }

    public static void hideSystemChrome(Context ctx) {
        synchronized (LOCK) {
            if (chromeHidden) {
                return;
            }
            SmdtManagerNew n = neu(ctx);
            if (n == null) {
                return;
            }
            try {
                n.disp_setNavigationBar(false);
                n.disp_setStatusBar(false);
                n.disp_setGestureBar(false);
                n.disp_setSystemUIMode(false);
                chromeHidden = true;
            } catch (Throwable t) {
                Log.w(TAG, "chrome: " + t.getMessage());
            }
        }
    }

    public static void applyTorch(Camera camera, boolean on) {
        if (camera == null) {
            return;
        }
        try {
            Camera.Parameters p = camera.getParameters();
            java.util.List<String> modes = p.getSupportedFlashModes();
            if (modes == null) {
                return;
            }
            String want = on ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF;
            if (on && !modes.contains(want) && modes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                want = Camera.Parameters.FLASH_MODE_ON;
            }
            if (modes.contains(want)) {
                p.setFlashMode(want);
                camera.setParameters(p);
            }
        } catch (Throwable t) {
            Log.w(TAG, "torch: " + t.getMessage());
        }
    }

    private static void setRgb(Context ctx, boolean white, boolean green, boolean red) {
        String next = white ? "white" : (green ? "green" : "red");
        synchronized (LOCK) {
            SmdtManagerNew n = neu(ctx);
            if (n == null) {
                SmdtBridge.setLed("LED_GREEN", green);
                SmdtBridge.setLed("LED_RED", red);
                color = next;
                Log.i(TAG, "led=" + next);
                return;
            }
            killWhite(n, false);
            try {
                SmdtBridge.setLed("LED_GREEN", green);
                SmdtBridge.setLed("LED_RED", red);
                n.dev_setLedLighted("LED_GREEN", green);
                n.dev_setLedLighted("LED_RED", red);
                if (white) {
                    n.dev_setLedLighted("LED_WHITE", true);
                }
                color = next;
                Log.i(TAG, "led=" + next);
            } catch (Throwable t) {
                Log.w(TAG, "setRgb: " + t.getMessage());
            }
        }
    }

    private static void killWhite(SmdtManagerNew n, boolean allOff) {
        try {
            n.dev_setLedLighted("LED_WHITE", false);
        } catch (Throwable ignored) {
        }
        if (allOff) {
            try {
                n.sys_setControl(LED_CONTROL, false);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void rotateImage(SmdtManagerNew n, int cameraId, int degrees) {
        try {
            int r = n.dev_setCameraImageRotation(cameraId, degrees);
            Log.i(TAG, "camera " + cameraId + " imageRot=" + degrees + " ret=" + r);
        } catch (Throwable t) {
            Log.w(TAG, "camera " + cameraId + ": " + t.getMessage());
        }
    }

    private static SmdtManagerNew neu(Context ctx) {
        if (api != null || smdtMissing) {
            return api;
        }
        try {
            api = SmdtManagerNew.getInstance(ctx.getApplicationContext());
            Log.i(TAG, "SmdtManagerNew " + (api == null ? "null" : api.info_getApiVersion()));
        } catch (Throwable t) {
            smdtMissing = true;
            Log.w(TAG, "SmdtManagerNew: " + t.getMessage());
        }
        if (api == null) {
            smdtMissing = true;
        }
        return api;
    }
}
