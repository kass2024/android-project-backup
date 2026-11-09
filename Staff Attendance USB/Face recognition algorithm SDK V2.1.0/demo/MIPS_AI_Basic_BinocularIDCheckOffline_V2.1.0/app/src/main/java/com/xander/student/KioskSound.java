package com.xander.student;

import android.app.smdt.SmdtManager;
import android.app.smdt.SmdtManagerNew;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Google TTS (offline English voice when the engine is installed).
 * WAV beeps remain a fallback if TTS is not ready.
 */
public class KioskSound {
    private static final String TAG = "XanderSound";
    private static final String GOOGLE_TTS = "com.google.android.tts";
    private static TextToSpeech tts;
    private static volatile boolean ttsReady;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void welcome(Context ctx) {
        say(ctx, "Welcome", R.raw.xander_ok);
    }

    public static void welcome(Context ctx, String ignored) {
        welcome(ctx);
    }

    public static void goodbye(Context ctx) {
        say(ctx, "Goodbye", R.raw.xander_out);
    }

    public static void goodbye(Context ctx, String ignored) {
        goodbye(ctx);
    }

    public static void alreadyIn(Context ctx) {
        say(ctx, "Already in", R.raw.xander_ok);
    }

    public static void invalid(Context ctx) {
        say(ctx, "Invalid", R.raw.xander_fail);
    }

    public static void alert(Context ctx) {
        invalid(ctx);
    }

    public static void fail(Context ctx) {
        invalid(ctx);
    }

    public static void unknown(Context ctx) {
        invalid(ctx);
    }

    public static void ok(Context ctx) {
        welcome(ctx);
    }

    public static void prepare(Context ctx) {
        Context app = ctx.getApplicationContext();
        blastVolume(app);
        ensureTts(app);
    }

    private static volatile String pendingPhrase;

    public static void sayNow(Context ctx, String phrase) {
        if (phrase == null) {
            return;
        }
        final String line = phrase.trim();
        if (line.length() == 0) {
            return;
        }
        final Context app = ctx.getApplicationContext();
        ensureTts(app);
        pendingPhrase = line;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (speakNow(line)) {
                pendingPhrase = null;
            }
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (speakNow(line)) {
                    pendingPhrase = null;
                }
            }
        });
    }

    private static void say(final Context ctx, final String phrase, final int fallbackWav) {
        sayNow(ctx, phrase);
    }

    private static boolean speakNow(String phrase) {
        if (!ttsReady || tts == null || phrase == null || phrase.length() == 0) {
            return false;
        }
        try {
            tts.stop();
            int result;
            if (Build.VERSION.SDK_INT >= 21) {
                Bundle b = new Bundle();
                b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
                result = tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, b, "xander-" + phrase);
            } else {
                HashMap<String, String> p = new HashMap<String, String>();
                p.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1");
                result = tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, p);
            }
            return result == TextToSpeech.SUCCESS;
        } catch (Throwable t) {
            Log.w(TAG, "speak: " + t.getMessage());
            return false;
        }
    }

    private static void ensureTts(final Context app) {
        if (tts != null) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (KioskSound.tts != null) {
                    return;
                }
                TextToSpeech.OnInitListener ready = new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if (status != TextToSpeech.SUCCESS || KioskSound.tts == null) {
                            Log.w(TAG, "TTS init failed status=" + status);
                            KioskSound.ttsReady = false;
                            return;
                        }
                        try {
                            int lang = KioskSound.tts.setLanguage(Locale.US);
                            if (lang == TextToSpeech.LANG_MISSING_DATA
                                    || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                                KioskSound.tts.setLanguage(Locale.ENGLISH);
                            }
                            KioskSound.tts.setSpeechRate(1.05f);
                            KioskSound.tts.setPitch(1.0f);
                            pickOfflineVoice();
                            KioskSound.ttsReady = true;
                            if (Build.VERSION.SDK_INT >= 21) {
                                try {
                                    KioskSound.tts.playSilentUtterance(40, TextToSpeech.QUEUE_FLUSH, "warm");
                                } catch (Throwable ignored) {
                                }
                            }
                            String waiting = pendingPhrase;
                            if (waiting != null && waiting.length() > 0) {
                                speakNow(waiting);
                                pendingPhrase = null;
                            }
                            Log.i(TAG, "Google TTS ready (offline voice)");
                        } catch (Throwable t) {
                            KioskSound.ttsReady = false;
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
        });
    }

    private static void pickOfflineVoice() {
        if (Build.VERSION.SDK_INT < 21 || tts == null) {
            return;
        }
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) {
                return;
            }
            Voice best = null;
            for (Voice v : voices) {
                if (v == null || v.getLocale() == null) {
                    continue;
                }
                String lang = v.getLocale().getLanguage();
                if (!"en".equalsIgnoreCase(lang)) {
                    continue;
                }
                boolean network = false;
                try {
                    network = v.isNetworkConnectionRequired();
                } catch (Throwable ignored) {
                }
                if (network) {
                    continue;
                }
                if (best == null || v.getQuality() > best.getQuality()) {
                    best = v;
                }
            }
            if (best != null) {
                tts.setVoice(best);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void play(final Context ctx, final int resId) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                KioskSound.blastVolume(app);
                if (!KioskSound.playWav(app, resId, AudioManager.STREAM_MUSIC)
                        && !KioskSound.playWav(app, resId, AudioManager.STREAM_ALARM)) {
                    KioskSound.playWav(app, resId, AudioManager.STREAM_RING);
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
        } catch (Throwable ignored) {
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
