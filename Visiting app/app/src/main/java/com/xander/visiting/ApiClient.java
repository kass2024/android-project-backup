package com.xander.visiting;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

public class ApiClient {
   private static final OkHttpClient CLIENT;
   private static final Handler MAIN;

   public static void post(Context ctx, String path, Map<String, String> fields, final JsonCallback cb) {
      FormBody.Builder body = new FormBody.Builder();
      if (fields != null) {
         for(Map.Entry<String, String> e : fields.entrySet()) {
            body.add((String)e.getKey(), e.getValue() == null ? "" : (String)e.getValue());
         }
      }

      Request req = (new Request.Builder()).url(Prefs.serverUrl(ctx) + path).post(body.build()).build();
      CLIENT.newCall(req).enqueue(new Callback() {
         public void onFailure(Call call, IOException e) {
            ApiClient.MAIN.post(() -> cb.onResult((JSONObject)null, e.getMessage()));
         }

         public void onResponse(Call call, Response response) throws IOException {
            String raw = response.body() != null ? response.body().string() : "";

            try {
               JSONObject json = new JSONObject(raw);
               ApiClient.MAIN.post(() -> cb.onResult(json, (String)null));
            } catch (Exception ex) {
               ApiClient.MAIN.post(() -> cb.onResult((JSONObject)null, raw.isEmpty() ? ex.getMessage() : raw));
            }

         }
      });
   }

   static {
      CLIENT = (new OkHttpClient.Builder()).connectTimeout(12L, TimeUnit.SECONDS).readTimeout(25L, TimeUnit.SECONDS).writeTimeout(25L, TimeUnit.SECONDS).build();
      MAIN = new Handler(Looper.getMainLooper());
   }

   public interface JsonCallback {
      void onResult(JSONObject var1, String var2);
   }
}
