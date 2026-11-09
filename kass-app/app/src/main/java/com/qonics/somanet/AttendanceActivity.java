package com.qonics.somanet;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class AttendanceActivity extends AppCompatActivity {
    FloatingActionButton fab;
    Toolbar toolbar;
    ImageView imgerror;
    RelativeLayout rlvloading;
    ProgressBar prb;
    TextView txtstatus,txtcount,txtretry;
    LinearLayout lnlstudents;
    HttpRequest req;
    String server = MainActivity.SERVER;
    ActionBar actionBar;
    ArrayList<Integer> present = new ArrayList<>();
    SharedPreferences preferences;
    int stTotal = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        populateView();
        loadStudent();
    }

    private void populateView() {
        toolbar =  findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        actionBar = getSupportActionBar();
        txtretry = findViewById(R.id.txtretry);
        actionBar.setTitle("Class: "+getIntent().getStringExtra("classTitle"));
        fab = findViewById(R.id.fab);
        txtstatus = findViewById(R.id.txtstatus);
        txtcount = findViewById(R.id.txtcount);
        prb = findViewById(R.id.prb);
        rlvloading = findViewById(R.id.rlvloading);
        imgerror = findViewById(R.id.imgerror);
        lnlstudents = findViewById(R.id.lnlstudents);

        txtretry.setVisibility(View.INVISIBLE);
        txtretry.setOnClickListener(view -> {
            loadStudent();
        });

        fab.hide();
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendAttendance();
            }
        });
    }

    private void sendAttendance() {
        rlvloading.setVisibility(View.VISIBLE);
        lnlstudents.setVisibility(View.GONE);
        prb.setVisibility(View.VISIBLE);
        imgerror.setVisibility(View.INVISIBLE);
        txtstatus.setText(getString(R.string.please_wait));
        changeTitle(getString(R.string.please_wait));
        Map map = new HashMap();
        map.put("teacher",preferences.getInt("soma_id",0));
        map.put("course",getIntent().getIntExtra("courseId",0));
        map.put("class",getIntent().getIntExtra("classId",0));
        JSONArray jar = new JSONArray();
        for (Integer a:present){
            jar.put(a);
        }
        map.put("students",jar.toString());
        req = new HttpRequest(this);
        req.post(server + "save_course_attendance", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    prb.setVisibility(View.INVISIBLE);
                    imgerror.setVisibility(View.VISIBLE);
                    txtstatus.setText(getString(R.string.oops)+status);
                } else {
                    try {
                        if (object.has("error")) {
                            prb.setVisibility(View.INVISIBLE);
                            imgerror.setVisibility(View.VISIBLE);
                            txtstatus.setText(getString(R.string.oops)+object.getString("error"));

                        } else {
                            SweetAlertDialog sa = new SweetAlertDialog(AttendanceActivity.this,SweetAlertDialog.SUCCESS_TYPE);
                            sa.setTitle(getString(R.string.good_job));
                            sa.setContentText(getString(R.string.atte_save_msg))
                                    .setConfirmButton(getString(R.string.ok), sweetAlertDialog -> {
                                        sweetAlertDialog.dismiss();
                                        finish();
                                    })
                                    .setCancelable(false);
                            sa.show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }


    private void loadStudent(){
        lnlstudents.removeAllViews();
        changeTitle(getString(R.string.please_wait));
        imgerror.setVisibility(View.INVISIBLE);
        txtstatus.setText(getString(R.string.please_wait));
        prb.setVisibility(View.VISIBLE);
        txtretry.setVisibility(View.INVISIBLE);
        req = new HttpRequest(this);
        req.get(server + "/get_students/"+getIntent().getIntExtra("classId",0)+"/"+preferences.getInt("soma_academic",0), JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    imgerror.setVisibility(View.VISIBLE);
                    prb.setVisibility(View.INVISIBLE);
                    txtstatus.setText("Oops! "+status);
                    txtretry.setVisibility(View.VISIBLE);
                    changeTitle(getString(R.string.error_occurred));
                } else {
                    try {
                        if (object.has("error")) {
                            imgerror.setVisibility(View.VISIBLE);
                            prb.setVisibility(View.INVISIBLE);
                            txtretry.setVisibility(View.VISIBLE);
                            if (object.getString("error").equals("0")){
                                txtstatus.setText(getString(R.string.atte_err_one));
                                changeTitle(getString(R.string.no_student));
                            }else{
                                txtstatus.setText(getString(R.string.oops)+object.getString("error"));
                                changeTitle(getString(R.string.error_occurred));
                            }

                        } else {
                            rlvloading.setVisibility(View.GONE);
                            //fab.show();
                            JSONArray jar = object.getJSONArray("students");
                            stTotal = jar.length();
                            changeTitle(getString(R.string.students)+" 0/"+stTotal);
                            for (int a=0;a<jar.length();a++){
                                JSONObject data = jar.getJSONObject(a);
                                View v = getLayoutInflater().inflate(R.layout.student_row, null);
                                TextView txtname = v.findViewById(R.id.txtname);
                                TextView txtregno = v.findViewById(R.id.txtregno);
                                ImageView imgabsent = v.findViewById(R.id.imgabsent);
                                ImageView imgpresent = v.findViewById(R.id.imgpresent);
                                ImageView imgdelete = v.findViewById(R.id.imgdelete);
                                if (imgdelete != null) {
                                    imgdelete.setOnClickListener(null);
                                    imgdelete.setClickable(false);
                                    imgdelete.setVisibility(View.GONE);
                                }
                                String name = data.getString("name");
                                String regno = data.getString("regno");
                                int id = data.getInt("id");
                                txtname.setText(name);
                                txtregno.setText(regno);
                                imgabsent.setOnClickListener(view -> {
                                    lnlstudents.removeView(v);
                                    populateList();
                                    Toast.makeText(AttendanceActivity.this,name+" "+getString(R.string.atte_is_absent),Toast.LENGTH_SHORT).show();
                                });

                                imgpresent.setOnClickListener(view -> {
                                    present.add(id);
                                    lnlstudents.removeView(v);
                                    populateList();
                                    Toast.makeText(AttendanceActivity.this,name+" "+getString(R.string.atte_is_present),Toast.LENGTH_SHORT).show();
                                });

                                lnlstudents.addView(v);
                            }

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void populateList(){
        changeTitle("Students "+present.size()+"/"+stTotal);
        if (lnlstudents.getChildCount()==0){
            fab.show();
            TextView txt = new TextView(this);
            txt.setText(getString(R.string.atte_is_fine));
            txt.setTextSize(40);
            txt.setGravity(Gravity.CENTER);
            txt.setPadding(0,20,0,20);
            lnlstudents.addView(txt);
        }
    }
    private void changeTitle(String title){
        txtcount.setText(title);
    }
}
