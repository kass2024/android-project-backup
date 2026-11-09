package com.xander.visiting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Build.VERSION;
import java.util.List;

public class WifiKeeper {
   private static final Handler HANDLER = new Handler(Looper.getMainLooper());
   private static ConnectivityManager.NetworkCallback wifiCb;
   private static BroadcastReceiver receiver;
   private static WifiManager.WifiLock wifiLock;
   private static boolean started;
   private static Context appCtx;
   private static final Runnable KEEP_TICK = WifiKeeper::keepTick;

   private static void keepTick() {
      if (appCtx != null) {
         reconnect(appCtx);
         if (!isWifiOnline(appCtx)) {
            HANDLER.postDelayed(KEEP_TICK, 2500L);
         }

      }
   }

   public static void start(Context context) {
      appCtx = context.getApplicationContext();
      rememberCurrentSsid(appCtx);
      holdWifiLock(appCtx);
      reconnect(appCtx);
      requestWifi(appCtx);
      listen(appCtx);
      started = true;
      HANDLER.removeCallbacks(KEEP_TICK);
      HANDLER.post(KEEP_TICK);
   }

   public static void keep(Context context) {
      appCtx = context.getApplicationContext();
      if (!started) {
         start(context);
      } else {
         reconnect(appCtx);
         if (!isWifiOnline(appCtx)) {
            HANDLER.removeCallbacks(KEEP_TICK);
            HANDLER.post(KEEP_TICK);
         }

      }
   }

   public static void whenOnline(Context context, final long timeoutMs, final Runnable ready, final Runnable timeout) {
      final Context app = context.getApplicationContext();
      keep(app);
      if (!isWifiOnline(app) && !Net.isOnline(app)) {
         final long start = SystemClock.elapsedRealtime();
         Runnable tick = new Runnable() {
            public void run() {
               WifiKeeper.reconnect(app);
               if (!WifiKeeper.isWifiOnline(app) && !Net.isOnline(app)) {
                  if (SystemClock.elapsedRealtime() - start >= timeoutMs) {
                     if (timeout != null) {
                        timeout.run();
                     }

                  } else {
                     WifiKeeper.HANDLER.postDelayed(this, 600L);
                  }
               } else {
                  if (ready != null) {
                     ready.run();
                  }

               }
            }
         };
         HANDLER.postDelayed(tick, 400L);
      } else {
         if (ready != null) {
            ready.run();
         }

      }
   }

   public static boolean isWifiOnline(Context context) {
      Context app = context.getApplicationContext();
      WifiManager wm = wifi(app);
      if (wm != null && wm.isWifiEnabled()) {
         try {
            WifiInfo info = wm.getConnectionInfo();
            if (info != null && info.getNetworkId() != -1) {
               return true;
            }
         } catch (Exception var4) {
         }
      }

      return Net.isOnline(app);
   }

   public static void reconnect(Context context) {
      Context app = context.getApplicationContext();
      WifiManager wm = wifi(app);
      if (wm != null) {
         try {
            if (!wm.isWifiEnabled()) {
               wm.setWifiEnabled(true);
            }
         } catch (Exception var5) {
         }

         rememberCurrentSsid(app);
         enableSavedNetwork(wm, app);

         try {
            if (wm.isWifiEnabled()) {
               wm.reconnect();
               wm.reassociate();
            }
         } catch (Exception var4) {
         }

         bindWifi(app);
      }
   }

   private static void enableSavedNetwork(WifiManager wm, Context ctx) {
      try {
         List<WifiConfiguration> configs = wm.getConfiguredNetworks();
         if (configs == null || configs.isEmpty()) {
            return;
         }

         String want = Prefs.wifiSsid(ctx);
         int fallback = -1;
         int match = -1;

         for(WifiConfiguration config : configs) {
            if (config != null) {
               try {
                  wm.enableNetwork(config.networkId, false);
               } catch (Exception var9) {
               }

               String ssid = stripSsid(config.SSID);
               if (!ssid.isEmpty()) {
                  if (fallback < 0) {
                     fallback = config.networkId;
                  }

                  if (!want.isEmpty() && want.equalsIgnoreCase(ssid)) {
                     match = config.networkId;
                  }
               }
            }
         }

         int id = match >= 0 ? match : fallback;
         if (id >= 0) {
            wm.enableNetwork(id, true);
         }
      } catch (Exception var10) {
      }

   }

   private static void requestWifi(final Context ctx) {
      final ConnectivityManager cm = (ConnectivityManager)ctx.getSystemService("connectivity");
      if (cm != null) {
         if (wifiCb != null) {
            try {
               cm.unregisterNetworkCallback(wifiCb);
            } catch (Exception var5) {
            }
         }

         NetworkRequest request = (new NetworkRequest.Builder()).addTransportType(1).build();
         wifiCb = new ConnectivityManager.NetworkCallback() {
            public void onAvailable(Network network) {
               WifiKeeper.rememberCurrentSsid(ctx);

               try {
                  if (VERSION.SDK_INT >= 23) {
                     cm.bindProcessToNetwork(network);
                  }
               } catch (Exception var3) {
               }

            }

            public void onLost(Network network) {
               WifiKeeper.HANDLER.postDelayed(() -> WifiKeeper.reconnect(ctx), 400L);
            }
         };

         try {
            cm.requestNetwork(request, wifiCb);
         } catch (Exception var4) {
         }

         bindWifi(ctx);
      }
   }

   private static void bindWifi(Context ctx) {
      ConnectivityManager cm = (ConnectivityManager)ctx.getSystemService("connectivity");
      if (cm != null && VERSION.SDK_INT >= 23) {
         try {
            Network[] networks = cm.getAllNetworks();
            if (networks == null) {
               return;
            }

            for(Network network : networks) {
               NetworkCapabilities caps = cm.getNetworkCapabilities(network);
               if (caps != null && caps.hasTransport(1)) {
                  cm.bindProcessToNetwork(network);
                  return;
               }
            }
         } catch (Exception var8) {
         }

      }
   }

   private static void holdWifiLock(Context ctx) {
      try {
         WifiManager wm = wifi(ctx);
         if (wm == null) {
            return;
         }

         if (wifiLock == null) {
            wifiLock = wm.createWifiLock(3, "visiting-wifi");
            wifiLock.setReferenceCounted(false);
         }

         if (!wifiLock.isHeld()) {
            wifiLock.acquire();
         }
      } catch (Exception var2) {
      }

   }

   private static void listen(Context ctx) {
      if (receiver == null) {
         receiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
               if (intent != null) {
                  String action = intent.getAction();
                  if ("android.net.wifi.WIFI_STATE_CHANGED".equals(action)) {
                     int state = intent.getIntExtra("wifi_state", 4);
                     if (state == 3) {
                        WifiKeeper.reconnect(context);
                     } else if (state == 1) {
                        WifiKeeper.HANDLER.postDelayed(() -> WifiKeeper.reconnect(context), 400L);
                     }

                  } else {
                     if ("android.net.wifi.STATE_CHANGE".equals(action)) {
                        NetworkInfo info = (NetworkInfo)intent.getParcelableExtra("networkInfo");
                        if (info != null && info.isConnected()) {
                           WifiKeeper.rememberCurrentSsid(context);
                           WifiKeeper.bindWifi(context);
                        } else if (info != null && !info.isConnectedOrConnecting()) {
                           WifiKeeper.HANDLER.postDelayed(() -> WifiKeeper.reconnect(context), 800L);
                        }
                     }

                  }
               }
            }
         };
         IntentFilter filter = new IntentFilter();
         filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
         filter.addAction("android.net.wifi.STATE_CHANGE");

         try {
            if (VERSION.SDK_INT >= 33) {
               ctx.registerReceiver(receiver, filter, 4);
            } else {
               ctx.registerReceiver(receiver, filter);
            }
         } catch (Exception var3) {
         }

      }
   }

   private static void rememberCurrentSsid(Context ctx) {
      try {
         WifiManager wm = wifi(ctx);
         if (wm == null) {
            return;
         }

         WifiInfo info = wm.getConnectionInfo();
         if (info == null) {
            return;
         }

         String ssid = stripSsid(info.getSSID());
         if (ssid.isEmpty() || "<unknown ssid>".equalsIgnoreCase(ssid) || "0x".equals(ssid)) {
            return;
         }

         Prefs.setWifiSsid(ctx, ssid);
      } catch (Exception var4) {
      }

   }

   private static String stripSsid(String ssid) {
      return ssid == null ? "" : ssid.replace("\"", "").trim();
   }

   private static WifiManager wifi(Context ctx) {
      return (WifiManager)ctx.getApplicationContext().getSystemService("wifi");
   }
}
