package com.xander.student;

import android.app.smdt.SmdtManager;
import android.app.smdt.SmdtManagerNew;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.io.File;
import java.util.Locale;

/**
 * Google voice starts on the tap. Phrases are spoken live, and a short
 * clip recorded by Google TTS is played when it is already on disk so
 * the speaker does not wait for synthesis.
 */
public class KioskSound {
    private static final String TAG = "XanderSound";
    private static final String GOOGLE_TTS = "com.google.android.tts";
    private static final String[] PHRASES = new String[]{
            "Welcome", "Good bye", "Already in", "Not allowed", "Allowed to eat", "Allowed to ride"
    };
    private static TextToSpeech tts;
    private static volatile boolean ttsReady;
    private static volatile boolean volumeReady;
    private static MediaPlayer clip;
    private static File cacheDir;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void welcome(Context ctx) {
        say(ctx, "Welcome");
    }

    public static void welcome(Context ctx, String ignored) {
        welcome(ctx);
    }

    public static void goodbye(Context ctx) {
        say(ctx, "Good bye");
    }

    public static void goodbye(Context ctx, String ignored) {
        goodbye(ctx);
    }

    public static void alreadyIn(Context ctx) {
        say(ctx, "Already in");
    }

    public static void lunch(Context ctx) {
        say(ctx, "Lunch");
    }

    public static void backFromLunch(Context ctx) {
        say(ctx, "Welcome back");
    }

    /** Cafeteria pass. Spoken by Google TTS, same path as the other phrases. */
    public static void allowedToEat(Context ctx) {
        say(ctx, "Allowed to eat");
    }

    /** Transport pass for a day scholar who has paid this term's Transport fee. */
    public static void allowedToRide(Context ctx) {
        say(ctx, "Allowed to ride");
    }

    public static void alert(Context ctx) {
        say(ctx, "Not allowed");
    }

    public static void fail(Context ctx) {
        alert(ctx);
    }

    public static void unknown(Context ctx) {
        alert(ctx);
    }

    public static void ok(Context ctx) {
        welcome(ctx);
    }

    public static void prepare(Context ctx) {
        final Context app = ctx.getApplicationContext();
        cacheDir = new File(app.getCacheDir(), "tts");
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
            cacheDir = app.getCacheDir();
        }
        ensureTts(app);
        if (!volumeReady) {
            volumeReady = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    blastVolume(app);
                }
            }, "xander-vol").start();
        }
    }

    /** Speak now. Never waits on volume or a delayed retry. */
    private static void say(final Context ctx, final String text) {
        final Context app = ctx.getApplicationContext();
        prepare(app);
        if (playCached(app, text)) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            speakNow(text);
            return;
        }
        MAIN.postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                if (!playCached(app, text)) {
                    speakNow(text);
                }
            }
        });
    }

    private static void speakNow(String text) {
        if (!ttsReady || tts == null || text == null || text.length() == 0) {
            return;
        }
        try {
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tap-" + text);
        } catch (Throwable t) {
            Log.w(TAG, "speak failed: " + t.getMessage());
        }
    }

    private static void ensureTts(final Context app) {
        if (tts != null) {
            return;
        }
        Runnable start = new Runnable() {
            @Override
            public void run() {
                if (KioskSound.tts != null) {
                    return;
                }
                TextToSpeech.OnInitListener ready = new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if (status != TextToSpeech.SUCCESS || KioskSound.tts == null) {
                            Log.w(TAG, "Google TTS init failed status=" + status);
                            KioskSound.ttsReady = false;
                            return;
                        }
                        try {
                            KioskSound.tts.setLanguage(Locale.US);
                            KioskSound.tts.setSpeechRate(1.08f);
                            KioskSound.tts.setPitch(1.0f);
                            KioskSound.tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                                @Override
                                public void onStart(String utteranceId) {
                                }

                                @Override
                                public void onDone(String utteranceId) {
                                }

                                @Override
                                public void onError(String utteranceId) {
                                }
                            });
                            KioskSound.ttsReady = true;
                            KioskSound.tts.playSilentUtterance(1, TextToSpeech.QUEUE_FLUSH, "warm");
                            cachePhrases();
                        } catch (Throwable t) {
                            KioskSound.ttsReady = false;
                            Log.w(TAG, "Google TTS setup: " + t.getMessage());
                        }
                    }
                };
                try {
                    KioskSound.tts = new TextToSpeech(app, ready, GOOGLE_TTS);
                } catch (Throwable t) {
                    try {
                        KioskSound.tts = new TextToSpeech(app, ready);
                    } catch (Throwable ignored) {
                    }
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            start.run();
        } else {
            MAIN.postAtFrontOfQueue(start);
        }
    }

    private static void cachePhrases() {
        if (tts == null || cacheDir == null) {
            return;
        }
        for (int i = 0; i < PHRASES.length; i++) {
            String text = PHRASES[i];
            File out = clipFile(text);
            if (out == null || (out.isFile() && out.length() > 400)) {
                continue;
            }
            try {
                tts.synthesizeToFile(text, null, out, "cache-" + text);
            } catch (Throwable t) {
                Log.w(TAG, "cache " + text + ": " + t.getMessage());
            }
        }
    }

    private static File clipFile(String text) {
        if (cacheDir == null || text == null) {
            return null;
        }
        return new File(cacheDir, text.replace(' ', '_') + ".wav");
    }

    private static boolean playCached(Context ctx, String text) {
        File file = clipFile(text);
        if (file == null || !file.isFile() || file.length() < 400) {
            return false;
        }
        try {
            MediaPlayer next = new MediaPlayer();
            next.setAudioStreamType(AudioManager.STREAM_MUSIC);
            next.setDataSource(file.getAbsolutePath());
            next.prepare();
            next.setVolume(1f, 1f);
            MediaPlayer old = clip;
            clip = next;
            if (old != null) {
                try {
                    old.stop();
                } catch (Throwable ignored) {
                }
                try {
                    old.release();
                } catch (Throwable ignored) {
                }
            }
            next.start();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "clip " + text + ": " + t.getMessage());
            return false;
        }
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
            }
        } catch (Throwable ignored) {
        }
        try {
            SmdtManager m = SmdtManager.create(ctx);
            if (m != null) {
                m.smdtSetVolume(ctx, 15);
            }
        } catch (Throwable ignored) {
        }
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return;
            }
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(true);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max > 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
            }
        } catch (Throwable ignored) {
        }
    }
}
