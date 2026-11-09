package com.qonics.somanet_attendance;

import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.qonics.app.StudentsList;
import com.qonics.app.UsersList;
import com.qonics.utils.Callback;
import com.qonics.utils.DatabaseHandler;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import static android.content.ContentValues.TAG;

public class SyncReceiver extends BroadcastReceiver {
    HttpRequest req;
    String server = "";
    SharedPreferences preferences;
    SQLiteDatabase db;
    ProgressDialog progressDialog;
    private static final int PROGRESS = 33;
    NotificationCompat.Builder mBuilder;
    NotificationManager mNotifyManager;
    int success = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.

        db = context.openOrCreateDatabase("somanet_attendance_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(context).onCreate(db);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        //server = "http://" + preferences.getString(SettingsActivity.KEY_WEB_ADDR, "") + "/smartschool/api/";
        //server = preferences.getString(SettingsActivity.KEY_WEB_ADDR, "") + "/api/";
        server = MainActivity.SERVER;
        progressDialog = new ProgressDialog(context);
        progressDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        progressDialog.setMessage("Syncing....");
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
        progressDialog.setMax(100);

        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();

            if (networkInfo != null &&
                    networkInfo.isConnected()) {
                // Wifi is connected
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ssid = wifiInfo.getSSID();

                Log.e(TAG, " -- Wifi connected --- " + " SSID " + ssid);
                if (!preferences.getBoolean("FIRST_RUN",true))
                checkServer(context);

            }
        } else if (intent.getAction().equalsIgnoreCase(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                Log.e(TAG, " ----- Wifi  Disconnected ----- ");
            }

        } else if (intent.getAction().equals("sync.qonics.com.somanet")) {
            Log.e(TAG, "WIFI receiver sync");
            if (!preferences.getBoolean("FIRST_RUN",true))
            checkServer(context);
        }
    }

    private void checkServer(final Context context) {
        req = new HttpRequest(context);
        req.get(server + "check_server", JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    //Toast.makeText(context,"Error: "+status.getMessage(),Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error:" + status);
                    //notify school sync did not complete
                    Intent intent = new Intent("sync.qonics.com.somanet.success");
                    intent.putExtra("status",1);
                    intent.putExtra("msg",status);
                    context.sendBroadcast(intent);//send force sync receiver
                } else {
                    //server reached try sync
                    if (object.has("success"))
                        syncStudent(context);
                }
            }
        });
    }

    private void syncStudent(final Context context){
        Log.e(TAG, "Syncing students");
        showNotification(context);
        progressDialog.setProgress(PROGRESS * 1);
        mBuilder.setProgress(100, PROGRESS * 1, false);
        mBuilder.setContentText("Sync Students");
        mNotifyManager.notify(1112, mBuilder.build());
        req = new HttpRequest(context);
        Log.e("EEEEE",server + "sync/student/" + preferences.getString(MainActivity.SK_ID, "") + "?updateVersion=" + preferences.getInt(MainActivity.UPDATE_VER_ST, 0));
        req.get(server + "sync/student/" + preferences.getString(MainActivity.SK_ID, "") + "?updateVersion=" + preferences.getInt(MainActivity.UPDATE_VER_ST, 0), JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                syncStaff(context);
                if(object == null){
//                    Toast.makeText(context,"Error: "+status,Toast.LENGTH_LONG).show();
                    Log.e(TAG,"Error:"+status);
                }else{
                    try {
                        success = success+1;
                        if (object.has("error")){
                            if(object.getString("error").equals("0")){
                                Log.e(TAG,"No new student data");
                            }else{
                                Log.e(TAG,"Fatal error: "+object.getString("error"));
                            }
                        }else{
                            //new data found
                            JSONArray jarr = object.getJSONArray("students");
                            int version = 0;
                            for (int a = 0; jarr.length()>a;a++){
                                JSONObject data = jarr.getJSONObject(a);
                                Cursor cursor = db.rawQuery("select id from tb_student where id ='"+data.getInt("id")+"'",null);
                                if(cursor.moveToNext()){
                                    //i.e update
                                    String name = escapeString(data.getString("name"));
                                    String dept = escapeString(data.getString("dept_title"));
                                    db.execSQL("update tb_student set " +
                                            "name='" + name + "'," +
                                            "card='"+data.getString("card")+"',"+
                                            "classe='"+data.getString("class")+"',"+
                                            "dept='"+dept+"',"+
                                            "studying_mode='"+data.getString("studying_mode")+"',"+
                                            "photo='"+data.getString("photo")+"'"+
                                            " where id='"+data.getString("id")+"'");
                                    Log.e("Student", data.getString("name")+" Updated!!!");
                                }else{
                                    //i.e new
                                    try {
                                        String name = escapeString(data.getString("name"));
                                        String sql = "insert into tb_student (id,regno,name,card,classe,dept,studying_mode,photo) values (?,?,?,?,?,?,?,?)";
                                        Object[] args = new Object[]{data.getInt("id"),data.getString("regno"),name,
                                                data.getString("card"),data.getString("class"),data.getString("dept_title").trim(),data.getString("studying_mode"),data.getString("photo")};
                                        db.execSQL(sql,args);

                                        Log.e("Student", data.getString("name"));
                                    }catch (Exception ex){
                                        ex.printStackTrace();
                                    }

                                }
                                if(version < data.getInt("updateVersion"))
                                    version = data.getInt("updateVersion");
                                StudentsList.getInstance().LoadAll(context);
                            }
                            preferences.edit().putInt(MainActivity.UPDATE_VER_ST, version).apply();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void syncStaff(final Context context){
        Log.e(TAG, "Syncing staff");
        progressDialog.setProgress(PROGRESS * 2);
        mBuilder.setProgress(100, PROGRESS * 2, false);
        mBuilder.setContentText("Sync staff");
        mNotifyManager.notify(1112,mBuilder.build());
        req = new HttpRequest(context);
        Log.e("EEEEE",server + "sync/staff/" + preferences.getString(MainActivity.SK_ID, "") + "?updateVersion=" + preferences.getInt(MainActivity.UPDATE_VER_STAFF, 0));
        req.get(server + "sync/staff/" + preferences.getString(MainActivity.SK_ID, "") + "?updateVersion=" + preferences.getInt(MainActivity.UPDATE_VER_STAFF, 0), JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                uploadRecords(context);
                if(object == null){
                    //Toast.makeText(context,"Error: "+status.getMessage(),Toast.LENGTH_LONG).show();
                    Log.e(TAG,"Error:"+status);
                }else{
                    try {
                        success = success+1;
                        if (object.has("error")){
                            if(object.getString("error").equals("0")){
                                Log.e(TAG,"No new staff data");
                            }else{
                                Toast.makeText(context,"Fatal error: "+object.getString("error"),Toast.LENGTH_LONG).show();
                            }
                        }else{
                            //new data found
                            JSONArray jarr = object.getJSONArray("staffs");
                            int version = 0;
                            for (int a = 0; jarr.length()>a;a++){
                                JSONObject data = jarr.getJSONObject(a);
                                Cursor cursor = db.rawQuery("select id from tb_staff where id ='"+data.getInt("id")+"'",null);
                                if(cursor.moveToNext()){
                                    //i.e update
                                    String name = escapeString(data.getString("name"));
                                    db.execSQL("update tb_staff set " +
                                            "name='" + name + "'," +
                                            "phone='"+data.getString("phone")+"',"+
                                            "email='"+data.getString("email")+"',"+
                                            "dept='',"+
                                            "post_title='"+data.getString("post_title")+"',"+
                                            "photo='"+data.getString("photo")+"'"+
                                            " where id='"+data.getString("id")+"'");
                                    Log.e("Staff", data.getString("name")+" Updated!!!");
                                }else {
                                    //i.e new
                                    String name = escapeString(data.getString("name"));
                                    db.execSQL("insert into tb_staff (id,name,phone,email,dept,photo,post_title) values ("+
                                            "'"+data.getInt("id")+"',"+
                                            "'"+name+"',"+
                                            "'"+data.getString("phone")+"',"+
                                            "'"+data.getString("email")+"',"+
                                            "'',"+
                                            "'"+data.getString("photo")+"',"+
                                            "'"+data.getString("post_title")+"')");
                                    UsersList.getInstance().LoadAll(context);
                                    Log.e("Staff",data.getString("name"));
                                }

                                if(version <data.getInt("updateVersion"))
                                    version = data.getInt("updateVersion");
                            }
                            preferences.edit().putInt(MainActivity.UPDATE_VER_STAFF,version).apply();

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void uploadRecords(final Context context) {
        Log.e(TAG, "Uploading attendance records");
        progressDialog.setProgress(PROGRESS * 3);
        mBuilder.setProgress(100, 50, false);
        mBuilder.setContentText("Uploading records");
        mNotifyManager.notify(1112, mBuilder.build());
        req = new HttpRequest(context);
        Cursor c = db.rawQuery("select * from tb_records limit 10", null);
        JSONArray jsonArray = new JSONArray();
        int id = 0;
        boolean recordFound = false;
        while (c.moveToNext()) {
            recordFound = true;
            Log.e("RECORDS", c.getInt(1) + " " + c.getString(2));
            Map mmp = new HashMap();
            mmp.put("id", c.getInt(0));
            mmp.put("user_id", c.getString(1));
            mmp.put("user_type", c.getString(2));
            mmp.put("timee", c.getString(3));
            JSONObject data = new JSONObject(mmp);
            jsonArray.put(data);
            id = c.getInt(0);
        }
        Map map = new HashMap();
        map.put("records", jsonArray.toString());
        map.put("school", preferences.getString(MainActivity.SK_ID,""));
        Log.e("records", jsonArray.toString());
        Log.e("LAST ID", id + "");
        if (recordFound) {
            req.post(server + "upload_data/records", map, new Callback() {
                @Override
                public void HttpCallback(JSONObject object, int status_code, String status) {
                    progressDialog.dismiss();
                    mNotifyManager.cancelAll();
                    if (object == null) {
                        //Toast.makeText(context,"Error: "+status.getMessage(),Toast.LENGTH_LONG).show();
                        Log.e("records", "Error:" + status);
                    } else {
                        try {
                            if (object.has("error")) {
                                Log.e("REC ERROR", object.getString("error") + "");
                            } else if (object.has("success")){
                                Log.e("LAST SAVED ID", object.getInt("last_id") + "");
                                db.execSQL("delete from tb_records where id <=" + object.getInt("last_id"));//delete uploaded records
                                Log.e("records", object.getString("success") + " Success");
                                uploadRecords(context);//upload remains records till it is empty
                            }else{
                                Log.e("REC ERROR", object.getString("msg") + "");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } else {
            success = success + 1;
            mBuilder.setProgress(0, 0, false);
            mBuilder.setContentText("Sync complete");
            mNotifyManager.notify(1112, mBuilder.build());

            //notify school sync complete without error
            Intent intent = new Intent("sync.qonics.com.somanet.success");
            intent.putExtra("status",2);
            context.sendBroadcast(intent);//send force sync receiver
            //syncStudent(context);
            //progressDialog.dismiss();
            //mNotifyManager.cancelAll();
        }

    }


    private void showNotification(Context context) {
        mNotifyManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(context);
        mBuilder.setContentTitle("Somanet sync")
                .setContentText("Upload records")
                .setSmallIcon(R.drawable.ic_launcher);
        mBuilder.setProgress(100, PROGRESS, false);
        mBuilder.setAutoCancel(true);
        mNotifyManager.notify(1112, mBuilder.build());

    }

    private String escapeString(String s) {
        if (s.contains("'")) {
            s = s.replaceAll("'", " ");
        }
        return s;
    }
}
