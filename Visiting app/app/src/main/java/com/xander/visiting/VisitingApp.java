package com.xander.visiting;

import android.app.Application;
import android.content.Context;

public class VisitingApp extends Application {
   protected void attachBaseContext(Context base) {
      super.attachBaseContext(LocaleHelper.wrap(base));
   }

   public void onCreate() {
      super.onCreate();
      LocaleHelper.wrap(this);
      WifiKeeper.start(this);
   }
}
