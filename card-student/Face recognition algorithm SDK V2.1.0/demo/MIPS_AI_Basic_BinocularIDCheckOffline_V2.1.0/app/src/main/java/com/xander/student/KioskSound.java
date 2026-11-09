package com.xander.student;

import android.app.smdt.SmdtManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

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

    public static void alreadyIn(Context ctx) {
        say(ctx, "Already in", R.raw.xander_ok);
    }

    public static void lunch(Context ctx) {
        say(ctx, "Lunch", R.raw.xander_ok);
    }

    public static void backFromLunch(Context ctx) {
        say(ctx, "Welcome back", R.raw.xander_ok);
    }

    public static void goodbye(Context ctx) {
        say(ctx, "Good bye", R.raw.xander_out);
    }

    public static void goodbye(Context ctx, String ignored) {
        goodbye(ctx);
    }

    public static void alert(Context ctx) {
        play(ctx, R.raw.xander_fail);
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                SmdtBridge.setBootApp(app.getPackageName());
                blastVolume(app);
            }
        }, "xander-vol").start();
        ensureTts(app);
    }

    private static boolean speakIfReady(Context app, String text) {
        if (!ttsReady || tts == null || text == null || text.length() == 0) {
            return false;
        }
        try {
            armSpeaker();
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
            // Same as Google TTS Options: Amplify speech volume = Aggressive.
            params.putString("com.google.android.tts:amplify_speech_volume", "2");
            String utter = "xander-" + System.currentTimeMillis();
            int queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utter);
            Log.i(TAG, "speak \"" + text + "\" queued=" + queued + " ready=" + ttsReady);
            return queued == TextToSpeech.SUCCESS;
        } catch (Throwable t) {
            Log.w(TAG, "TTS speak failed: " + t.getMessage());
            return false;
        }
    }

    private static void say(final Context ctx, final String text, final int fallbackWav) {
        final Context app = ctx.getApplicationContext();
        ensureTts(app);
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (speakIfReady(app, text)) {
                    return;
                }
                MAIN.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!speakIfReady(app, text)) {
                            Log.w(TAG, "Google voice not ready, retrying \"" + text + "\"");
                            speakIfReady(app, text);
                        }
                    }
                }, 450);
            }
        });
    }

    private static volatile long speakerUntil;
    private static final Object SPEAKER_LOCK = new Object();
    private static boolean speakerHolding;

    /**
     * Open the horn for the whole spoken word. Switching the codec path on every
     * tick cuts the phrase down to a click, so the path is set only at the start.
     */
    private static void armSpeaker() {
        speakerUntil = System.currentTimeMillis() + 5000;
        synchronized (SPEAKER_LOCK) {
            if (speakerHolding) {
                return;
            }
            speakerHolding = true;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                int ticks = 0;
                try {
                    while (System.currentTimeMillis() < speakerUntil && ticks < 40) {
                        SmdtBridge.speakerAmp(true);
                        if (ticks < 3) {
                            SmdtBridge.speakerPath();
                        }
                        ticks++;
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                } finally {
                    long end = speakerUntil;
                    if (System.currentTimeMillis() >= end) {
                        SmdtBridge.speakerAmp(false);
                    }
                    boolean again = false;
                    synchronized (SPEAKER_LOCK) {
                        speakerHolding = false;
                        again = System.currentTimeMillis() < speakerUntil;
                    }
                    if (again || speakerUntil != end) {
                        armSpeaker();
                    }
                }
            }
        }, "xander-spk").start();
    }

    /** Called when Google finishes the word, so the horn stays up for the whole phrase. */
    private static void notePhrasePlaying() {
        speakerUntil = System.currentTimeMillis() + 5000;
    }

    private static void notePhraseDone() {
        speakerUntil = System.currentTimeMillis() + 250;
    }

    private static void applyVoice(TextToSpeech engine) {
        int lang = engine.setLanguage(Locale.US);
        String name = "";
        try {
            if (engine.getVoice() != null) {
                name = engine.getVoice().getName();
            }
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "Google voice language=" + lang + " voice=" + name);
        engine.setSpeechRate(1.0f);
        engine.setPitch(1.0f);
        engine.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                notePhrasePlaying();
                Log.i(TAG, "Google voice started " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                notePhraseDone();
                Log.i(TAG, "Google voice finished " + utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                Log.w(TAG, "Google voice error " + utteranceId);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                Log.w(TAG, "Google voice error " + utteranceId + " code=" + errorCode);
            }
        });
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
                try {
                    KioskSound.tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            Log.i(TAG, "Google TTS init status=" + status);
                            if (status == TextToSpeech.SUCCESS && KioskSound.tts != null) {
                                try {
                                    KioskSound.applyVoice(KioskSound.tts);
                                    KioskSound.ttsReady = true;
                                    Log.i(TAG, "Google TTS ready");
                                    KioskSound.speakIfReady(app, "Welcome");
                                } catch (Throwable t) {
                                    KioskSound.ttsReady = false;
                                    Log.w(TAG, "Google TTS voice setup failed: " + t.getMessage());
                                }
                            } else {
                                Log.w(TAG, "Google TTS init failed status=" + status);
                                KioskSound.ttsReady = false;
                            }
                        }
                    }, GOOGLE_TTS);
                } catch (Throwable t) {
                    try {
                        KioskSound.tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
                            @Override
                            public void onInit(int status) {
                                KioskSound.ttsReady = status == TextToSpeech.SUCCESS;
                                if (KioskSound.ttsReady && KioskSound.tts != null) {
                                    KioskSound.applyVoice(KioskSound.tts);
                                }
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    private static MediaPlayer player;
    private static final Object PLAY_LOCK = new Object();

    private static void play(final Context ctx, final int resId) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                KioskSound.armSpeaker();
                synchronized (PLAY_LOCK) {
                    KioskSound.releasePlayerLocked();
                    AssetFileDescriptor afd = null;
                    try {
                        afd = app.getResources().openRawResourceFd(resId);
                        if (afd == null) {
                            return;
                        }
                        MediaPlayer mp = new MediaPlayer();
                        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        afd.close();
                        afd = null;
                        mp.setVolume(1f, 1f);
                        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer m) {
                                synchronized (PLAY_LOCK) {
                                    if (KioskSound.player == m) {
                                        KioskSound.releasePlayerLocked();
                                    }
                                }
                            }
                        });
                        mp.prepare();
                        mp.start();
                        KioskSound.player = mp;
                    } catch (Throwable ignored) {
                        KioskSound.releasePlayerLocked();
                    } finally {
                        if (afd != null) {
                            try {
                                afd.close();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
        }, "xander-beep").start();
    }

    private static void releasePlayerLocked() {
        if (player == null) {
            return;
        }
        try {
            player.setOnCompletionListener(null);
            if (player.isPlaying()) {
                player.stop();
            }
        } catch (Throwable ignored) {
        }
        try {
            player.release();
        } catch (Throwable ignored) {
        }
        player = null;
    }

    private static void blastVolume(Context ctx) {
        try {
            SmdtBridge.forceLoudspeaker();
        } catch (Throwable t) {
            Log.w(TAG, "speaker route failed: " + t.getMessage());
        }
        try {
            SmdtManager m = SmdtManager.create(ctx);
            if (m != null) {
                boolean set = m.smdtSetVolume(ctx, 15);
                Log.i(TAG, "speaker volume set=" + set + " now=" + m.smdtGetVolume(ctx));
            } else {
                Log.w(TAG, "legacy volume manager missing");
            }
        } catch (Throwable t) {
            Log.w(TAG, "legacy volume failed: " + t.getMessage());
        }
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return;
            }
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(true);
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
                    Log.i(TAG, "stream " + streams[i] + " vol="
                            + am.getStreamVolume(streams[i]) + "/" + max);
                } catch (Throwable t) {
                    Log.w(TAG, "stream " + streams[i] + " " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "audio manager failed: " + t.getMessage());
        }
    }
}
