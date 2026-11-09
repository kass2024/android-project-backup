package com.xander.visiting;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ImageLoader {
   private static final OkHttpClient CLIENT = new OkHttpClient();
   private static final Handler MAIN = new Handler(Looper.getMainLooper());
   private static final ConcurrentHashMap<String, Bitmap> CACHE = new ConcurrentHashMap();

   public static void load(final ImageView view, final String url, int fallback) {
      if (view == null) {
         return;
      }
      view.setTag(url);
      if (url == null || url.trim().isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
         view.setImageResource(fallback);
         return;
      }
      Bitmap cached = (Bitmap)CACHE.get(url);
      if (cached != null && !cached.isRecycled()) {
         view.setImageBitmap(cached);
         return;
      }
      view.setImageResource(fallback);
      Request req;
      try {
         req = (new Request.Builder()).url(url).build();
      } catch (Exception badUrl) {
         return;
      }
      CLIENT.newCall(req).enqueue(new Callback() {
         public void onFailure(Call call, IOException e) {
         }

         public void onResponse(Call call, Response response) {
            try {
               if (!response.isSuccessful() || response.body() == null) {
                  return;
               }
               byte[] bytes = response.body().bytes();
               Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
               if (bmp == null) {
                  return;
               }
               ImageLoader.CACHE.put(url, bmp);
               ImageLoader.MAIN.post(() -> {
                  Object tag = view.getTag();
                  if (tag != null && tag.equals(url)) {
                     view.setImageBitmap(bmp);
                  }
               });
            } catch (Exception ignored) {
            }
         }
      });
   }
}
