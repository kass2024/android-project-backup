package com.xander.visiting;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CardUid {
   private CardUid() {
   }

   public static String clean(String raw) {
      return raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
   }

   public static String hex(String raw) {
      String uid = clean(raw);
      if (uid.isEmpty()) {
         return "";
      } else {
         if (uid.matches("\\d+")) {
            try {
               uid = (new BigInteger(uid)).toString(16).toUpperCase();
               if (uid.length() % 2 == 1) {
                  uid = "0" + uid;
               }

               while(uid.length() < 8) {
                  uid = "0" + uid;
               }
            } catch (Exception var3) {
               return "";
            }
         }

         uid = uid.replaceAll("[^A-F0-9]", "");
         return uid.length() >= 4 ? uid : "";
      }
   }

   public static String reverseBytes(String hex) {
      String uid = hex == null ? "" : hex.replaceAll("[^A-F0-9]", "").toUpperCase();
      if (!uid.isEmpty() && uid.length() % 2 == 0) {
         StringBuilder out = new StringBuilder(uid.length());

         for(int i = uid.length() - 2; i >= 0; i -= 2) {
            out.append(uid, i, i + 2);
         }

         return out.toString();
      } else {
         return "";
      }
   }

   public static String[] variants(String raw) {
      Set<String> out = new LinkedHashSet();
      String cleaned = clean(raw);
      if (cleaned.length() >= 4) {
         out.add(cleaned);
      }

      String hex = hex(raw);
      if (hex.length() >= 4) {
         out.add(hex);
         String reversed = reverseBytes(hex);
         if (reversed.length() >= 4) {
            out.add(reversed);
         }
      }

      return (String[])out.toArray(new String[0]);
   }

   public static boolean same(String a, String b) {
      String[] left = variants(a);
      String[] right = variants(b);
      if (left.length != 0 && right.length != 0) {
         for(String x : left) {
            for(String y : right) {
               if (x.equals(y) || x.endsWith(y) || y.endsWith(x)) {
                  return true;
               }

               int n = Math.min(8, Math.min(x.length(), y.length()));
               if (n >= 4 && x.substring(x.length() - n).equals(y.substring(y.length() - n))) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
