package com.xander.visiting;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

public class RfidWedge {
   private final StringBuilder buffer = new StringBuilder();
   private final Handler handler = new Handler(Looper.getMainLooper());
   private final Listener listener;
   private long lastAt;
   private final Runnable idleFlush = this::finishUid;

   public RfidWedge(Listener listener) {
      this.listener = listener;
   }

   public boolean onKey(KeyEvent event) {
      if (event != null && event.getAction() == 0) {
         long now = System.currentTimeMillis();
         if (now - this.lastAt > 400L) {
            this.buffer.setLength(0);
         }

         this.lastAt = now;
         int keyCode = event.getKeyCode();
         if (keyCode != 66 && keyCode != 160 && keyCode != 61) {
            char ch = keyChar(event);
            if (ch != 0) {
               this.buffer.append(ch);
               this.handler.removeCallbacks(this.idleFlush);
               if (this.buffer.length() >= 8) {
                  this.handler.postDelayed(this.idleFlush, 40L);
               }

               return true;
            } else {
               return true;
            }
         } else {
            this.handler.removeCallbacks(this.idleFlush);
            this.finishUid();
            return true;
         }
      } else {
         return true;
      }
   }

   private void finishUid() {
      this.handler.removeCallbacks(this.idleFlush);
      String uid = clean(this.buffer.toString());
      this.buffer.setLength(0);
      if (uid.length() >= 4 && this.listener != null) {
         this.listener.onUid(uid);
      }

   }

   private static char keyChar(KeyEvent event) {
      int unicode = event.getUnicodeChar();
      if (unicode > 0) {
         char ch = (char)unicode;
         if (Character.isLetterOrDigit(ch)) {
            return ch;
         }
      }

      int keyCode = event.getKeyCode();
      if (keyCode >= 7 && keyCode <= 16) {
         return (char)(48 + (keyCode - 7));
      } else if (keyCode >= 144 && keyCode <= 153) {
         return (char)(48 + (keyCode - 144));
      } else {
         return keyCode >= 29 && keyCode <= 54 ? (char)(65 + (keyCode - 29)) : '\u0000';
      }
   }

   public void reset() {
      this.handler.removeCallbacks(this.idleFlush);
      this.buffer.setLength(0);
      this.lastAt = 0L;
   }

   public static String clean(String raw) {
      return raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
   }

   public interface Listener {
      void onUid(String var1);
   }
}
