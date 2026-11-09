package com.example.smdt.smdtaisdkdemo.xander;

import android.app.smdt.SmdtManager;
import android.app.smdt.SmdtManagerNew;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import com.example.smdt.smdtaisdkdemo.R;

/**
 * Route audio to the kiosk speaker, max volume, then play bundled WAV files.
 */
public class KioskSound {
    private static final String TAG = "XanderSound";
    private static volatile long lastTickAt;

    public static void ok(Context ctx) {
        play(ctx, R.raw.xander_ok);
    }

    public static void fail(Context ctx) {
        play(ctx, R.raw.xander_fail);
    }

    public static void tick(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastTickAt < 1800) {
            return;
        }
        lastTickAt = now;
        play(ctx, R.raw.xander_tick);
    }

    public static void prepare(Context ctx) {
        blastVolume(ctx.getApplicationContext());
    }

    private static void play(final Context ctx, final int resId) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                blastVolume(app);
                if (!playWav(app, resId, AudioManager.STREAM_MUSIC)) {
                    if (!playWav(app, resId, AudioManager.STREAM_ALARM)) {
                        playWav(app, resId, AudioManager.STREAM_RING);
                    }
                }
            }
        }, "xander-beep").start();
    }

    private static void blastVolume(Context ctx) {
        try {
            SmdtManagerNew n = SmdtManagerNew.getInstance(ctx);
            if (n != null) {
                n.sys_setVolumeMute(false);
                try {
                    n.sys_setAudioOutput(0);
                } catch (Throwable ignored) {
                }
                int max = n.sys_getVolumeMaxMin("max");
                if (max <= 0) {
                    max = 15;
                }
                n.sys_setVolume(max);
                for (int ch = 0; ch <= 8; ch++) {
                    try {
                        n.sys_setOutVolume(ch, max);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "smdt new: " + t.getMessage());
        }
        try {
            SmdtManager m = SmdtManager.create(ctx);
            if (m != null) {
                m.smdtSetVolume(ctx, 15);
            }
        } catch (Throwable t) {
            Log.w(TAG, "smdt legacy: " + t.getMessage());
        }
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return;
            }
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(true);
            am.setMicrophoneMute(false);
            int[] streams = new int[]{
                    AudioManager.STREAM_MUSIC,
                    AudioManager.STREAM_ALARM,
                    AudioManager.STREAM_RING,
                    AudioManager.STREAM_SYSTEM,
                    AudioManager.STREAM_NOTIFICATION
            };
            for (int i = 0; i < streams.length; i++) {
                try {
                    am.setStreamMute(streams[i], false);
                    int max = am.getStreamMaxVolume(streams[i]);
                    if (max > 0) {
                        am.setStreamVolume(streams[i], max, 0);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "audio: " + t.getMessage());
        }
    }

    private static boolean playWav(Context ctx, int resId, int stream) {
        MediaPlayer mp = null;
        AssetFileDescriptor afd = null;
        try {
            afd = ctx.getResources().openRawResourceFd(resId);
            if (afd == null) {
                return false;
            }
            mp = new MediaPlayer();
            mp.setAudioStreamType(stream);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            afd = null;
            mp.setVolume(1f, 1f);
            mp.prepare();
            mp.start();
            int wait = mp.getDuration();
            if (wait < 80) {
                wait = 250;
            }
            Thread.sleep(wait + 40);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "wav stream=" + stream + " " + t.getMessage());
            return false;
        } finally {
            if (afd != null) {
                try {
                    afd.close();
                } catch (Throwable ignored) {
                }
            }
            if (mp != null) {
                try {
                    mp.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
