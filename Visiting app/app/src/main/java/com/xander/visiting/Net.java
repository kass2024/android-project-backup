package com.xander.visiting;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build.VERSION;

public class Net {
   public static boolean isOnline(Context ctx) {
      ConnectivityManager cm = (ConnectivityManager)ctx.getSystemService("connectivity");
      if (cm == null) {
         return false;
      } else if (VERSION.SDK_INT >= 23) {
         Network network = cm.getActiveNetwork();
         if (network == null) {
            return false;
         } else {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (caps.hasTransport(1) || caps.hasTransport(0) || caps.hasTransport(3));
         }
      } else {
         NetworkInfo info = cm.getActiveNetworkInfo();
         return info != null && info.isConnected();
      }
   }
}
