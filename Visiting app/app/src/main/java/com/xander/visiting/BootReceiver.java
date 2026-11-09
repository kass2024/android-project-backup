package com.xander.visiting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
   public void onReceive(Context context, Intent intent) {
      if (context != null && intent != null) {
         String action = intent.getAction();
         if ("android.intent.action.BOOT_COMPLETED".equals(action) || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action) || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            Kiosk.lockAndOpen(context.getApplicationContext());
         }

      }
   }
}
