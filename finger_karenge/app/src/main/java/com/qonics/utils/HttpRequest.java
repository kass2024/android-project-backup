package com.qonics.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.pedant.SweetAlert.SweetAlertDialog;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpRequest extends AsyncTask<Map<String,?>,Integer, JSONObject> {
    String url;
    Callback callback;
    Context context;
    View v=null,v1 =null,v2=null;
    ProgressDialog p = null;
    SweetAlertDialog s = null;
    int response_code = 200;
    String response = "";
    public HttpRequest(Context context) {
        this.context = context;
    }

    public void get(String url, Class<JSONObject> jsonObjectClass, Callback callback){
        this.url = url;
        this.callback = callback;
        execute(new HashMap<String, Object>());
    }
    public void post(String url,Map map,Callback callback){
        this.url = url;
        this.callback = callback;
        execute(map);
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
        OkHttpClient client = new OkHttpClient();
        FormBody.Builder body_content = new FormBody.Builder();
        if (data != null) {
            for (String key : data.keySet()) {
                String value = data.get(key).toString();
                body_content.add(key, value);
            }
        } else {
            body_content.add("temp", "temp");
        }
        /*
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("fname", "someValue")
                .build();
*/
        RequestBody body = body_content.build();
        Request request = null;
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
            //Log.e("ERRRR2",resStr);
            return new JSONObject(resStr);
        } catch (IOException e) {
            //e.printStackTrace();
            response_code = 400;
            if (e.getMessage().toLowerCase().contains("failed to connect")){
                response_code = 101;
                response = "Network error: please verify your network and try again";
            }else {
                response_code = 400;
                response = "Error: " + e.getMessage();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            response_code = 500;
            response = "INTERNAL SERVER ERROR";
        }
        return null;
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
