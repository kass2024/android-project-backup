package com.qonics.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.qonics.somanet.R;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;

import cn.pedant.SweetAlert.SweetAlertDialog;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpRequest extends AsyncTask<Map<String,?>,Integer, JSONObject> {
    /** Same host as the web app. If the phone DNS cannot resolve it, use the live server address. */
    private static final String SCHOOL_HOST = "schoolmis.xanderglobalacademy.com";
    private static final Dns SCHOOL_DNS = new Dns() {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            try {
                List<InetAddress> found = Dns.SYSTEM.lookup(hostname);
                if (found != null && !found.isEmpty()) {
                    return found;
                }
            } catch (UnknownHostException ignored) {
            }
            if (SCHOOL_HOST.equalsIgnoreCase(hostname)) {
                return Collections.singletonList(InetAddress.getByAddress(
                        hostname, new byte[]{66, 29, (byte) 135, 120}));
            }
            throw new UnknownHostException(hostname);
        }
    };
    String url;
    Callback callback;
    Context context;
    View v=null,v1 =null,v2=null;
    ProgressDialog p = null;
    SweetAlertDialog s = null;
    int response_code = 200;
    String response = "";
    private int connectTimeoutSec = 15;
    private int readTimeoutSec = 20;
    public HttpRequest(Context context) {
        this.context = context;
    }
    public HttpRequest timeouts(int connectSec, int readSec) {
        if (connectSec > 0) connectTimeoutSec = connectSec;
        if (readSec > 0) readTimeoutSec = readSec;
        return this;
    }

    public void get(String url, Class<JSONObject> jsonObjectClass, Callback callback){
        this.url = url;
        this.callback = callback;
        start(new HashMap<String, Object>());
    }
    public void post(String url,Map map,Callback callback){
        this.url = url;
        this.callback = callback;
        start(map);
    }

    private void start(Map map) {
        try {
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, map);
        } catch (IllegalStateException e) {
            Log.e("HttpRequest", "request was not started", e);
            if (callback != null) {
                callback.HttpCallback(null, 0, e.getMessage());
            }
        }
    }
    public HttpRequest progress(View v){
        this.v = v;
        v.setVisibility(View.VISIBLE);
        return this;
    }
    public HttpRequest progress(ProgressDialog p){
        this.p = p;
        p.show();
        return this;
    }
    public HttpRequest progress(SweetAlertDialog s){
        this.s = s;
        s.show();
        return this;
    }
    /** Live network load (cache used when available; falls back to placeholder). */
    public void image(ImageView imageView, String imageUrl) {
        try {
            Picasso.get()
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(imageView);
        } catch (Exception e) {
            Log.v("Picasso", "Could not fetch image: " + e.getMessage());
            imageView.setImageResource(R.drawable.placeholder_image);
        }
    }
    public HttpRequest hide(View v){
        this.v1 = v;
        v.setVisibility(View.GONE);
        return this;
    }
    public HttpRequest disable(View v){
        this.v2 = v;
        v.setEnabled(false);
        return this;
    }
    @Override
    protected JSONObject doInBackground(Map<String, ?>... maps) {
        MediaType JSON
                = MediaType.get("application/json; charset=utf-8");
        Map<String, ?> data = maps[0];
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
                .writeTimeout(readTimeoutSec, TimeUnit.SECONDS)
                .dns(SCHOOL_DNS)
                .build();
        FormBody.Builder body_content = new FormBody.Builder();
        try{
            if (data != null) {
                for (String key : data.keySet()) {
                    //Log.e("DATA_ERRROR: ", key);
                    String value = data.get(key).toString();
                    body_content.add(key, value);
                }
            } else {
                body_content.add("temp", "temp");
            }
        } catch (NullPointerException npe){
            Log.e("Error", npe.getMessage());
            return null;
        }
        /*
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("fname", "someValue")
                .build();
*/
        RequestBody body = body_content.build();
        Request request = null;
        url = url.contains("?")?url+"&lang="+context.getString(R.string.strLang):url+"?lang="+context.getString(R.string.strLang);
        Log.e("URLL",url);
        if (data.isEmpty()){
            //get
            request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
        }else{
            //post
            request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
        }

        try (Response response = client.newCall(request).execute()) {
            String resStr = response.body().string();
            Log.e("ERRRR2", resStr.length() > 400 ? resStr.substring(0, 400) : resStr);
            return parseObject(resStr);
        } catch (IOException e) {
            //e.printStackTrace();
            response_code = 400;
            if (e.getMessage().toLowerCase().contains("failed to connect")){
                response_code = 101;
                response = context.getString(R.string.network_error);
            }else {
                response_code = 400;
                response = context.getString(R.string.lbl_error) + e.getMessage();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            response_code = 500;
            response = context.getString(R.string.internal_server_error);
        }
        return null;
    }

    /** Accept a JSON object, including one that follows a short PHP notice. */
    private JSONObject parseObject(String raw) throws JSONException {
        String body = raw == null ? "" : raw.trim();
        if (body.startsWith("\uFEFF")) {
            body = body.substring(1).trim();
        }
        try {
            return new JSONObject(body);
        } catch (JSONException first) {
            if (body.startsWith("<")) {
                throw first;
            }
            int open = body.indexOf('{');
            int close = body.lastIndexOf('}');
            if (open >= 0 && close > open) {
                return new JSONObject(body.substring(open, close + 1));
            }
            throw first;
        }
    }


    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(JSONObject object) {
        super.onPostExecute(object);
        if (v != null)
            v.setVisibility(View.GONE);
        if (v1 != null)
            v1.setVisibility(View.VISIBLE);
        if (v2 != null)
            v2.setEnabled(true);
        if (p != null)
            p.dismiss();
        if (s != null)
            s.dismiss();
        callback.HttpCallback(object,response_code,response);
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        callback.HttpCallback(null,502,"BAD GATEWAY");
    }

}
