package com.xander.visiting;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Build.VERSION;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

public class VoiceAnnouncer {
   private TextToSpeech tts;
   private boolean ready;
   private String pendingText;
   private String pendingLang;
   private int savedVolume = -1;
   private boolean askedForKinyarwanda;

   public void start(Context context) {
      Context app = context.getApplicationContext();
      if (this.tts == null) {
         String engine = googleEngine(app);
         this.tts = engine != null ? new TextToSpeech(app, (status) -> this.onEngineReady(app, engine, status), engine) : new TextToSpeech(app, (status) -> this.onEngineReady(app, (String)null, status));
      }
   }

   private void onEngineReady(Context app, String engine, int status) {
      this.ready = status == 0;
      if (!this.ready && engine != null) {
         try {
            if (this.tts != null) {
               this.tts.shutdown();
            }
         } catch (Exception ignored) {
         }

         this.tts = new TextToSpeech(app, (fallback) -> {
            this.ready = fallback == 0;
            this.speakPending(app);
         });
         return;
      }

      this.speakPending(app);
   }

   private void speakPending(Context app) {
      if (this.ready && this.pendingText != null) {
         this.speakNow(app, this.pendingText, this.pendingLang);
         this.pendingText = null;
      }
   }

   private static String googleEngine(Context ctx) {
      try {
         ctx.getPackageManager().getPackageInfo("com.google.android.tts", 0);
         return "com.google.android.tts";
      } catch (PackageManager.NameNotFoundException var2) {
         return null;
      }
   }

   public void speak(Context context, String text, String lang) {
      if (text != null && !text.trim().isEmpty()) {
         this.pendingText = null;
         if (this.tts != null) {
            try {
               this.tts.stop();
            } catch (Exception var5) {
            }
         }

         if (this.ready && this.tts != null) {
            this.speakNow(context.getApplicationContext(), text, lang);
         } else {
            this.pendingText = text;
            this.pendingLang = lang;
         }
      }
   }

   public void shutdown() {
      if (this.tts != null) {
         this.tts.stop();
         this.tts.shutdown();
         this.tts = null;
      }

      this.ready = false;
   }

   private void speakNow(Context ctx, String text, String lang) {
      this.boostVolume(ctx);
      this.applyLanguage(ctx, lang);
      this.tts.setSpeechRate("en".equals(lang) ? 0.92F : 0.88F);
      this.tts.setPitch(1.05F);
      if (VERSION.SDK_INT >= 21) {
         this.tts.setAudioAttributes((new AudioAttributes.Builder()).setUsage(11).setContentType(1).build());
         this.tts.speak(text, 0, (Bundle)null, "gate_success");
      } else {
         HashMap<String, String> params = new HashMap();
         params.put("volume", "1.0");
         params.put("streamType", String.valueOf(3));
         this.tts.speak(text, 0, params);
      }
   }

   private void boostVolume(Context ctx) {
      try {
         AudioManager am = (AudioManager)ctx.getSystemService("audio");
         if (am == null) {
            return;
         }

         int max = am.getStreamMaxVolume(3);
         if (this.savedVolume < 0) {
            this.savedVolume = am.getStreamVolume(3);
         }

         am.setStreamVolume(3, max, 0);
      } catch (Exception var4) {
      }

   }

   private void applyLanguage(Context ctx, String lang) {
      if (!"rw".equals(lang)) {
         this.tts.setLanguage(Locale.US);
         return;
      }
      Voice voice = this.kinyarwandaVoice();
      if (voice != null) {
         this.tts.setVoice(voice);
         return;
      }
      int result = this.tts.setLanguage(new Locale("rw", "RW"));
      if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
         this.askForKinyarwanda(ctx);
         this.tts.setLanguage(Locale.US);
      }
   }

   private Voice kinyarwandaVoice() {
      if (VERSION.SDK_INT < 21 || this.tts == null) {
         return null;
      }
      try {
         Set<Voice> voices = this.tts.getVoices();
         if (voices == null) {
            return null;
         }
         Voice best = null;
         for (Voice voice : voices) {
            Locale locale = voice.getLocale();
            if (locale == null || !"rw".equalsIgnoreCase(locale.getLanguage())) {
               continue;
            }
            Set<String> features = voice.getFeatures();
            if (features != null && features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
               continue;
            }
            if (best == null || this.betterVoice(voice, best)) {
               best = voice;
            }
         }
         return best;
      } catch (Exception ignored) {
         return null;
      }
   }

   private boolean betterVoice(Voice candidate, Voice current) {
      if (candidate.getQuality() != current.getQuality()) {
         return candidate.getQuality() > current.getQuality();
      }
      return !candidate.isNetworkConnectionRequired() && current.isNetworkConnectionRequired();
   }

   private void askForKinyarwanda(Context ctx) {
      if (this.askedForKinyarwanda) {
         return;
      }
      this.askedForKinyarwanda = true;
      try {
         Intent install = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
         install.setPackage("com.google.android.tts");
         install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
         ctx.startActivity(install);
      } catch (Exception ignored) {
      }
   }

}
