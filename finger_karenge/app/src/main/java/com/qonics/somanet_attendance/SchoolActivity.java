package com.qonics.somanet_attendance;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class SchoolActivity extends Activity {
    Button btncontinue,btnclose;
    ImageView imglogo;
    LinearLayout lnlcontent;
    EditText content;
    TextView txttitle,txtschool,txtsyncprogress,txtsync;
    ProgressBar progressBar;
    RelativeLayout rlvsync;
    Spinner spattendancetype;
    HttpRequest req;
    SharedPreferences preferences;
    String server;
    String school_id = "";
    String school_name = "";
    BroadcastReceiver receiver;
    BroadcastReceiver syncReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_school);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        //server = preferences.getString(SettingsActivity.KEY_WEB_ADDR, "") + "/api/";
        server = MainActivity.SERVER;
//        ActivityCompat.requestPermissions(this,new String[]{
//                Manifest.permission.CAMERA,Manifest.permission.READ_PHONE_STATE,Manifest.permission.READ_EXTERNAL_STORAGE
//                ,Manifest.permission.WRITE_EXTERNAL_STORAGE},11);
        populateView();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 11: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED) {

                    populateView();
                } else {
                    Toast.makeText(SchoolActivity.this, "Permission denied, Application will not work", Toast.LENGTH_SHORT).show();
                    finish();
                }
                return;
            }
        }


    }

    private void populateView() {
        spattendancetype = findViewById(R.id.spattendancetype);
        txttitle = findViewById(R.id.txttitle);
        txtschool = findViewById(R.id.txtschool);
        txtsyncprogress = findViewById(R.id.txtsyncprogress);
        txtsync = findViewById(R.id.txtsync);
        imglogo = findViewById(R.id.imglogo);
        btncontinue = findViewById(R.id.btncontinue);
        btnclose = findViewById(R.id.btnclose);
        btnclose.setVisibility(View.INVISIBLE);
        lnlcontent = findViewById(R.id.lnlcontent);
        rlvsync = findViewById(R.id.rlvsync);
        progressBar = findViewById(R.id.progressBar);
        content = findViewById(R.id.content);

        spattendancetype.setAdapter(new ArrayAdapter(this,R.layout.spinner_item,new String[]{"FINGERPRINT & CARD","FACE", "CARD"}));

        rlvsync.setVisibility(View.GONE);
        btnclose.setOnClickListener(view -> {
            startActivity(new Intent(SchoolActivity.this,MainActivity.class));
            finish();
        });
        txtschool.setText("");
        progressBar.setVisibility(View.INVISIBLE);
        lnlcontent.setVisibility(View.GONE);
        txttitle.setVisibility(View.VISIBLE);
        imglogo.setVisibility(View.VISIBLE);
        btncontinue.setVisibility(View.GONE);
        btncontinue.setOnClickListener(view -> {
            if (content.getText().toString().trim().length() <2){
                content.setError("Content is too short");
                content.requestFocus();
            }else {
                if (school_id.length()==0){
                    //no school set
                    check_school(content.getText().toString().trim());
                }else{
                    //school set, verify password
                    verify_password(school_id,content.getText().toString().trim());
                }
            }
        });
        final Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        anim.setInterpolator((new AccelerateDecelerateInterpolator()));
        anim.setFillAfter(true);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                //startActivity(new Intent(SchoolActivity.this,MainActivity.class));
                lnlcontent.setVisibility(View.VISIBLE);
                btncontinue.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        final Handler handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what==1){
                    //start animation
                    txttitle.setVisibility(View.GONE);
                    imglogo.setAnimation(anim);
                }
                return false;
            }
        });
        final Message msg = new Message();
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                msg.what =1;
                handler.sendMessage(msg);
            }
        },3000);

    }

    @Override
    protected void onResume() {
        super.onResume();
        receiver = new SyncedReceiver();
        syncReceiver = new SyncReceiver();
        registerReceiver(receiver,new IntentFilter("sync.qonics.com.somanet.success"));
        registerReceiver(syncReceiver,new IntentFilter("sync.qonics.com.somanet"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        unregisterReceiver(syncReceiver);
    }

    private void check_school(String data){
        req = new HttpRequest(this);
        req.progress(progressBar).get(server + "check_school/" + data, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null){
                    Toast.makeText(SchoolActivity.this,"Error: "+status,Toast.LENGTH_LONG).show();
                }else{
                    try {
                        if (object.has("error")){
                            if (object.getString("error").equals("0")){
                                content.setError("No school found");
                                //Snackbar.make(btncontinue,"No school found",Snackbar.LENGTH_LONG).show();
                            }else{
                                Toast.makeText(SchoolActivity.this,"Error occurred, please try again later",Toast.LENGTH_LONG).show();
                                Log.e("SCHOOL Error",object.getString("error"));
                            }
                        }else{
                            txtschool.setText(object.getString("name"));
                            content.setError(null);
                            school_id = object.getString("id");
                            school_name = object.getString("name");
                            content.setHint("Enter school secret code");
                            content.setText("");
                            content.setInputType(InputType.TYPE_CLASS_TEXT |
                                    InputType.TYPE_TEXT_VARIATION_PASSWORD);
                            btncontinue.setText("FINISH");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
    private void verify_password(final String school_id, String password){
        Map map = new HashMap();
        map.put("school",school_id);
        map.put("password",password);
        req = new HttpRequest(this);
        req.progress(progressBar).post(server + "verify_school", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null){
                    Toast.makeText(SchoolActivity.this,"Error: "+status,Toast.LENGTH_LONG).show();
                }else{
                    try {
                        if (object.has("error")){
                            if (object.getString("error").equals("0")){
                                //Snackbar.make(btncontinue,"No school found",Snackbar.LENGTH_LONG).show();
                                content.setError("Password not correct");
                            }else{
                                Toast.makeText(SchoolActivity.this,"Error occurred, please try again later",Toast.LENGTH_LONG).show();
                                Log.e("SCHOOL Error",object.getString("error"));
                            }
                        }else if (object.has("success")){
                            preferences.edit().putBoolean("FIRST_RUN",false).putInt("attendance_type",spattendancetype.getSelectedItemPosition()).putString(MainActivity.SK_ID,school_id).putString(MainActivity.SK_NAME,school_name).apply();
                            sendBroadcast(new Intent("sync.qonics.com.somanet"));//send force sync receiver
//                            animatedCircleLoadingView.startIndeterminate();
                            rlvsync.setVisibility(View.VISIBLE);
                        }else{
                            Toast.makeText(SchoolActivity.this,"System error, please try again later",Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private class SyncedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getIntExtra("status",0)==1){
                txtsyncprogress.setText(intent.getStringExtra("msg"));
                txtsync.setText("SOMANET ATTENDANCE SYNC ERROR");
//                animatedCircleLoadingView.stopFailure();
            }
            if (intent.getIntExtra("status",0)==2){
                txtsync.setText("SOMANET ATTENDANCE SYNCED");
                txtsyncprogress.setText("Sync finished successfully");
//                animatedCircleLoadingView.stopOk();
                btnclose.setVisibility(View.VISIBLE);
                startActivity(new Intent(SchoolActivity.this,MainActivity.class));
                finish();
            }
            if (intent.getIntExtra("status",0)==3){
                txtsync.setText("SOMANET ATTENDANCE SYNCED");
                txtsyncprogress.setText("Sync finished with error");
//                animatedCircleLoadingView.stopOk();
                btnclose.setVisibility(View.VISIBLE);
                startActivity(new Intent(SchoolActivity.this,MainActivity.class));
                finish();
            }
        }
    }
}
