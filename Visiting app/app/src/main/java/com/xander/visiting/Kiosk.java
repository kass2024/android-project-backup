package com.xander.visiting;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

public final class Kiosk {
   private static final long TAP_WINDOW_MS = 700L;
   private static final Handler HANDLER = new Handler(Looper.getMainLooper());
   private static long lastTapAt;
   private static int tapCount;

   private Kiosk() {
   }

   public static void bindExitLogo(Activity activity, View logo) {
      if (activity != null && logo != null) {
         logo.setClickable(true);
         logo.setFocusable(true);
         logo.setOnClickListener((v) -> onLogoTap(activity));
      }
   }

   public static void onLogoTap(Activity activity) {
      long now = SystemClock.uptimeMillis();
      if (now - lastTapAt > 700L) {
         tapCount = 1;
      } else {
         ++tapCount;
      }

      lastTapAt = now;
      if (tapCount >= 3) {
         tapCount = 0;
         exitToDesktop(activity);
      }
   }

   public static void exitToDesktop(Activity activity) {
      if (activity != null) {
         Prefs.setKioskUnlocked(activity, true);
         unpin(activity);
         Intent home = new Intent("android.intent.action.MAIN");
         home.addCategory("android.intent.category.HOME");
         home.addFlags(268435456);

         try {
            activity.startActivity(home);
         } catch (Exception var3) {
         }

         activity.finishAffinity();
      }
   }

   public static void lockAndOpen(Context context) {
      if (context != null) {
         Prefs.setKioskUnlocked(context, false);
         Intent intent = new Intent(context, Prefs.hasSchool(context) ? GateActivity.class : SchoolSetupActivity.class);
         intent.addFlags(872415232);
         context.startActivity(intent);
      }
   }

   public static void unpin(Activity activity) {
      if (activity != null) {
         try {
            activity.stopLockTask();
         } catch (Exception var2) {
         }

      }
   }

   public static void holdScreen(Activity activity) {
      if (activity == null || activity.isFinishing()) {
         return;
      }
      Window window = activity.getWindow();
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
      View decor = window.getDecorView();
      decor.setSystemUiVisibility(
         View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
      if (Build.VERSION.SDK_INT >= 30) {
         window.setDecorFitsSystemWindows(false);
         WindowInsetsController controller = window.getInsetsController();
         if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars() | WindowInsets.Type.systemBars() | WindowInsets.Type.tappableElement() | WindowInsets.Type.systemGestures());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
         }
      }
   }

   public static void reclaimIfLocked(Activity activity) {
      if (activity != null && !activity.isFinishing() && Prefs.kioskLocked(activity)) {
         HANDLER.postDelayed(() -> {
            if (!activity.isFinishing() && Prefs.kioskLocked(activity)) {
               Intent intent = new Intent(activity, activity.getClass());
               intent.addFlags(537001984);

               try {
                  activity.startActivity(intent);
               } catch (Exception var3) {
                  lockAndOpen(activity.getApplicationContext());
               }

            }
         }, 280L);
      }
   }
}
