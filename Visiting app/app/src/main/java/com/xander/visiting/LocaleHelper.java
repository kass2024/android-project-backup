package com.xander.visiting;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build.VERSION;
import java.util.Locale;

public class LocaleHelper {
   public static Context wrap(Context context) {
      return wrap(context, Prefs.lang(context));
   }

   public static Context wrap(Context context, String lang) {
      Locale locale = new Locale(lang != null && !lang.isEmpty() ? lang : "en");
      Locale.setDefault(locale);
      Resources res = context.getResources();
      Configuration cfg = new Configuration(res.getConfiguration());
      cfg.keyboard = 1;
      cfg.hardKeyboardHidden = 2;
      cfg.keyboardHidden = 2;
      if (VERSION.SDK_INT >= 24) {
         cfg.setLocale(locale);
         return context.createConfigurationContext(cfg);
      } else {
         cfg.locale = locale;
         res.updateConfiguration(cfg, res.getDisplayMetrics());
         return context;
      }
   }
}
