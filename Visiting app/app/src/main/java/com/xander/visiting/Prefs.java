package com.xander.visiting;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
   public static final String DEFAULT_SERVER = "https://schoolmis.xanderglobalacademy.com/api/";

   private static SharedPreferences p(Context c) {
      return c.getSharedPreferences("visiting_app", 0);
   }

   public static String serverUrl(Context c) {
      String url = p(c).getString("server", "https://schoolmis.xanderglobalacademy.com/api/");
      if (url == null || url.trim().isEmpty()) {
         url = "https://schoolmis.xanderglobalacademy.com/api/";
      }

      if (!url.endsWith("/")) {
         url = url + "/";
      }

      return url;
   }

   public static void setServerUrl(Context c, String url) {
      if (url == null) {
         url = "https://schoolmis.xanderglobalacademy.com/api/";
      }

      url = url.trim();
      if (url.isEmpty()) {
         url = "https://schoolmis.xanderglobalacademy.com/api/";
      }

      if (!url.endsWith("/")) {
         url = url + "/";
      }

      p(c).edit().putString("server", url).apply();
   }

   public static boolean hasSchool(Context c) {
      return schoolId(c) > 0 && !acronym(c).isEmpty();
   }

   public static int schoolId(Context c) {
      return p(c).getInt("school_id", 0);
   }

   public static String schoolName(Context c) {
      return p(c).getString("school_name", "");
   }

   public static String acronym(Context c) {
      return p(c).getString("acronym", "");
   }

   public static void saveSchool(Context c, int id, String name, String acronym) {
      saveSchool(c, id, name, acronym, schoolLogo(c));
   }

   public static void saveSchool(Context c, int id, String name, String acronym, String logo) {
      p(c).edit().putInt("school_id", id).putString("school_name", name == null ? "" : name).putString("acronym", acronym == null ? "" : acronym.toUpperCase()).putString("school_logo", logo == null ? "" : logo).apply();
   }

   public static String schoolLogo(Context c) {
      return p(c).getString("school_logo", "");
   }

   public static String photoUrl(Context c, String photo) {
      if (photo == null) {
         return "";
      }
      photo = photo.trim();
      if (photo.isEmpty()) {
         return "";
      }
      if (photo.startsWith("http://") || photo.startsWith("https://")) {
         return photo;
      }
      String base = serverUrl(c);
      if (base.endsWith("/api/")) {
         base = base.substring(0, base.length() - 4);
      }
      if (!base.endsWith("/")) {
         base = base + "/";
      }
      photo = photo.replaceFirst("^/+", "");
      if (photo.startsWith("assets/")) {
         return base + photo;
      }
      return base + "assets/images/profile/" + photo;
   }

   public static String logoUrl(Context c) {
      String logo = schoolLogo(c);
      if (logo != null && !logo.trim().isEmpty()) {
         if (!logo.startsWith("http://") && !logo.startsWith("https://")) {
            String base = serverUrl(c);
            if (base.endsWith("/api/")) {
               base = base.substring(0, base.length() - 4);
            }

            return base + "assets/images/logo/" + logo.replaceFirst("^/+", "");
         } else {
            return logo;
         }
      } else {
         return "";
      }
   }

   public static void clearSchool(Context c) {
      p(c).edit().remove("school_id").remove("school_name").remove("acronym").remove("school_logo").apply();
   }

   public static String appMode(Context c) {
      return p(c).getString("app_mode", "visitor");
   }

   public static void setAppMode(Context c, String mode) {
      p(c).edit().putString("app_mode", "staff".equals(mode) ? "staff" : "visitor").apply();
   }

   public static String lang(Context c) {
      return p(c).getString("lang", "en");
   }

   public static void setLang(Context c, String lang) {
      p(c).edit().putString("lang", lang == null ? "en" : lang).apply();
   }

   public static String nextLang(Context c) {
      return "rw".equals(lang(c)) ? "en" : "rw";
   }

   public static String wifiSsid(Context c) {
      String ssid = p(c).getString("wifi_ssid", "");
      return ssid == null ? "" : ssid;
   }

   public static boolean kioskLocked(Context c) {
      return !p(c).getBoolean("kiosk_unlocked", false);
   }

   public static void setKioskUnlocked(Context c, boolean unlocked) {
      p(c).edit().putBoolean("kiosk_unlocked", unlocked).apply();
   }

   public static void setWifiSsid(Context c, String ssid) {
      if (ssid != null) {
         ssid = ssid.replace("\"", "").trim();
         if (!ssid.isEmpty() && !"<unknown ssid>".equalsIgnoreCase(ssid)) {
            p(c).edit().putString("wifi_ssid", ssid).apply();
         }
      }
   }
}
