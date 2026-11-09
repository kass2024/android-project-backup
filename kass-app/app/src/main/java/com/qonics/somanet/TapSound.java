package com.qonics.somanet;

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
 * Same Google voice phrases as the USB attendance reader.
 */
public class TapSound {
    private static final String TAG = "TapSound";
    private static final String GOOGLE_TTS = "com.google.android.tts";
    private static final String[] PHRASES = new String[]{
            "Welcome", "Good bye", "Already in", "Not allowed", "Allowed to eat", "Allowed to ride"
    };
    private static TextToSpeech tts;
    private static volatile boolean ttsReady;
    private static MediaPlayer clip;
    private static File cacheDir;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void speak(Context ctx, String sound) {
        if ("eat".equals(sound)) say(ctx, "Allowed to eat");
        else if ("ride".equals(sound)) say(ctx, "Allowed to ride");
        else if ("already".equals(sound)) say(ctx, "Already in");
        else if ("goodbye".equals(sound)) say(ctx, "Good bye");
        else if ("alert".equals(sound)) say(ctx, "Not allowed");
        else if ("welcome".equals(sound)) say(ctx, "Welcome");
    }

    public static void prepare(Context ctx) {
        final Context app = ctx.getApplicationContext();
        cacheDir = new File(app.getCacheDir(), "tts");
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
            cacheDir = app.getCacheDir();
        }
        ensureTts(app);
    }

    private static void say(final Context ctx, final String text) {
        final Context app = ctx.getApplicationContext();
        prepare(app);
        if (playCached(text)) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            speakNow(text);
            return;
        }
        MAIN.postAtFrontOfQueue(() -> {
            if (!playCached(text)) speakNow(text);
        });
    }

    private static void speakNow(String text) {
        if (!ttsReady || tts == null || text == null || text.length() == 0) return;
        try {
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tap-" + text);
        } catch (Throwable t) {
            Log.w(TAG, "speak failed: " + t.getMessage());
        }
    }

    private static void ensureTts(final Context app) {
        if (tts != null) return;
        Runnable start = () -> {
            if (tts != null) return;
            TextToSpeech.OnInitListener ready = status -> {
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    ttsReady = false;
                    return;
                }
                try {
                    tts.setLanguage(Locale.US);
                    tts.setSpeechRate(1.08f);
                    tts.setPitch(1.0f);
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) {}
                        @Override public void onDone(String utteranceId) {}
                        @Override public void onError(String utteranceId) {}
                    });
                    ttsReady = true;
                    tts.playSilentUtterance(1, TextToSpeech.QUEUE_FLUSH, "warm");
                    cachePhrases();
                } catch (Throwable t) {
                    ttsReady = false;
                    Log.w(TAG, "Google TTS setup: " + t.getMessage());
                }
            };
            try {
                tts = new TextToSpeech(app, ready, GOOGLE_TTS);
            } catch (Throwable t) {
                try {
                    tts = new TextToSpeech(app, ready);
                } catch (Throwable ignored) {
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) start.run();
        else MAIN.postAtFrontOfQueue(start);
    }

    private static void cachePhrases() {
        if (tts == null || cacheDir == null) return;
        for (String text : PHRASES) {
            File out = clipFile(text);
            if (out == null || (out.isFile() && out.length() > 400)) continue;
            try {
                tts.synthesizeToFile(text, null, out, "cache-" + text);
            } catch (Throwable t) {
                Log.w(TAG, "cache " + text + ": " + t.getMessage());
            }
        }
    }

    private static File clipFile(String text) {
        if (cacheDir == null || text == null) return null;
        return new File(cacheDir, text.replace(' ', '_') + ".wav");
    }

    private static boolean playCached(String text) {
        File file = clipFile(text);
        if (file == null || !file.isFile() || file.length() < 400) return false;
        try {
            MediaPlayer next = new MediaPlayer();
            next.setAudioStreamType(AudioManager.STREAM_MUSIC);
            next.setDataSource(file.getAbsolutePath());
            next.prepare();
            next.setVolume(1f, 1f);
            MediaPlayer old = clip;
            clip = next;
            if (old != null) {
                try { old.stop(); } catch (Throwable ignored) {}
                try { old.release(); } catch (Throwable ignored) {}
            }
            next.start();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "clip " + text + ": " + t.getMessage());
            return false;
        }
    }
}
