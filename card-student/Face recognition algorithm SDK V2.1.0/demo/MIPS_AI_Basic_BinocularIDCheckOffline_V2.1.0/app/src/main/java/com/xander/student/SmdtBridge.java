package com.xander.student;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Talks to the smdtserver binder. This tablet has that service, but the
 * SmdtManagerNew class is not on the boot classpath, so the calls go out
 * as raw transactions with the codes from the SMDT AIDL.
 */
public final class SmdtBridge {
    private static final String TAG = "XanderSmdt";
    private static final String DESCRIPTOR = "android.app.smdt.ISmdtManagerNew";
    private static final int SYS_SET_SYSTEM_BOOT_APP = 93;
    private static final int SYS_SET_VOLUME = 102;
    private static final int SYS_SET_OUT_VOLUME = 104;
    private static final int SYS_SET_AUDIO_OUTPUT = 108;
    private static final int SYS_GET_AUDIO_OUTPUT = 109;
    private static final int SYS_SET_VOLUME_MUTE = 216;
    private static final int SYS_DO_SU_CMD = 222;
    private static final int DEV_SET_LED_LIGHTED = 253;
    /** GPIO3 pin 19, active-low. Electrical low turns the speaker amp on. */
    private static final String AMP_ON = "devmem 0xFE760004 4 0x00080000";
    private static final String AMP_OFF = "devmem 0xFE760004 4 0x00080008";

    /** 0 auto, 1 HDMI, 2 speaker. */
    private static final int OUTPUT_SPEAKER = 2;

    private SmdtBridge() {
    }

    public static IBinder binder() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method get = sm.getMethod("getService", String.class);
            IBinder binder = (IBinder) get.invoke(null, "smdtserver");
            if (binder == null) {
                Log.w(TAG, "smdtserver binder is null");
                return null;
            }
            return binder;
        } catch (Throwable t) {
            Log.w(TAG, "binder: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return null;
        }
    }

    public static int transact(IBinder binder, int code, int... args) {
        if (binder == null) {
            Log.w(TAG, "code " + code + " skipped, no binder");
            return -1;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (args != null) {
                for (int arg : args) {
                    data.writeInt(arg);
                }
            }
            binder.transact(code, data, reply, 0);
            reply.readException();
            int result = reply.readInt();
            Log.i(TAG, "code " + code + " -> " + result);
            return result;
        } catch (Throwable t) {
            Log.w(TAG, "code " + code + " failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return -1;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public static void routeSpeaker() {
        IBinder binder = binder();
        int before = transact(binder, SYS_GET_AUDIO_OUTPUT);
        int mute = transact(binder, SYS_SET_VOLUME_MUTE, 0);
        int set = transact(binder, SYS_SET_AUDIO_OUTPUT, OUTPUT_SPEAKER);
        int after = transact(binder, SYS_GET_AUDIO_OUTPUT);
        int volume = transact(binder, SYS_SET_VOLUME, 15);
        int out = transact(binder, SYS_SET_OUT_VOLUME, 1, 15);
        setProp("persist.audio.output.mode", "2");
        Log.i(TAG, "speaker route before=" + before + " mute=" + mute
                + " set=" + set + " now=" + after + " vol=" + volume + " out=" + out);
    }

    private static void setProp(String key, String value) {
        try {
            Class<?> props = Class.forName("android.os.SystemProperties");
            Method set = props.getMethod("set", String.class, String.class);
            set.invoke(null, key, value);
            Method get = props.getMethod("get", String.class, String.class);
            Object now = get.invoke(null, key, "");
            Log.i(TAG, key + "=" + now);
        } catch (Throwable t) {
            Log.w(TAG, "prop " + key + " " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    public static void setBootApp(String packageName) {
        IBinder binder = binder();
        if (binder == null || packageName == null) {
            Log.w(TAG, "boot app skipped");
            return;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(packageName);
            binder.transact(SYS_SET_SYSTEM_BOOT_APP, data, reply, 0);
            reply.readException();
            int code = reply.readInt();
            Log.i(TAG, "boot app " + packageName + " -> " + code);
        } catch (Throwable t) {
            Log.w(TAG, "boot app failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /**
     * The speaker amp enable is active-low and the audio path never drives it,
     * so the horn stays silent even when the codec volume is full.
     * Turn it on only while a phrase is playing, then release it.
     */
    public static void setLed(String name, boolean on) {
        IBinder binder = binder();
        if (binder == null || name == null) {
            return;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(name);
            data.writeInt(on ? 1 : 0);
            binder.transact(DEV_SET_LED_LIGHTED, data, reply, 0);
            reply.readException();
            Log.i(TAG, "led " + name + "=" + (on ? 1 : 0) + " -> " + reply.readInt());
        } catch (Throwable t) {
            Log.w(TAG, "led " + name + " failed: " + t.getMessage());
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public static void speakerAmp(boolean on) {
        IBinder binder = binder();
        if (binder == null) {
            return;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(on ? AMP_ON : AMP_OFF);
            binder.transact(SYS_DO_SU_CMD, data, reply, 0);
            reply.readException();
            Log.i(TAG, "speaker amp " + (on ? "on" : "off"));
        } catch (Throwable t) {
            Log.w(TAG, "speaker amp failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /**
     * RK809 speaker path. SPK_HP drops the codec volume to about -48 dB,
     * which is silent outdoors. SPK sets the speaker DAC near full level.
     */
    public static void forceLoudspeaker() {
        try {
            Class<?> audio = Class.forName("android.media.AudioSystem");
            Method disconnect = audio.getMethod("setDeviceConnectionState",
                    int.class, int.class, String.class, String.class);
            disconnect.invoke(null, 0x8, 0, "", "");
            disconnect.invoke(null, 0x4, 0, "", "");
            Method setForce = audio.getMethod("setForceUse", int.class, int.class);
            setForce.invoke(null, 1, 1);
        } catch (Throwable t) {
            Log.w(TAG, "force speaker " + t.getMessage());
        }
        Log.i(TAG, "loudspeaker " + mixerPath("SPK"));
    }

    /** Keep the codec on the loud speaker path. The headphone path is about -48 dB. */
    public static void speakerPath() {
        mixerPath("SPK");
    }

    private static String mixerPath(String path) {
        return runMixer(path);
    }

    private static String runMixer(String path) {
        try {
            ProcessBuilder builder = path == null
                    ? new ProcessBuilder("tinymix")
                    : new ProcessBuilder("tinymix", "Playback Path", path);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            java.io.InputStream in = process.getInputStream();
            byte[] buf = new byte[1024];
            int total = 0;
            int n;
            while (total < buf.length && (n = in.read(buf, total, buf.length - total)) > 0) {
                total += n;
            }
            process.waitFor();
            String out = total > 0 ? new String(buf, 0, total) : "";
            int mark = out.indexOf("Playback Path");
            if (mark >= 0) {
                int end = out.indexOf('\n', mark);
                out = end > mark ? out.substring(mark, end).trim() : out.substring(mark).trim();
            } else {
                out = out.trim();
            }
            return "exit=" + process.exitValue() + " " + out;
        } catch (Throwable t) {
            return "failed " + t.getClass().getSimpleName() + " " + t.getMessage();
        }
    }
}
