package com.example.smdt.smdtaisdkdemo;


import android.graphics.Color;
import android.hardware.Camera;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.content.Intent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.smdt.facesdk.mipsVideoFaceTrack;


public class MainActivity extends AppCompatActivity  implements View.OnClickListener{
    private Button bt_face_detect;
    private Button bt_idverify_test;
    private Button bt_vipverify_test;
    private Button bt_idcompare_test;
    private Button bt_liveness_test;
    private Button bt_idfacepro_test;
    private Button bt_idcompare_facepro_test;
    private Button bt_vsfacefeature ;
    private Spinner choose_algorithm;
    private static String[] choose_algorithm_arr_adapter={"face+ID card","face+ID card(single liveness)","face+card(dual liveness)","common(single,dual liveness)","common"};
    private ArrayAdapter<CharSequence> choose_algorithm_adapter = null;
    private int choose_algorithm_state =0 ;
    private Spinner cam_facing;
    private static String[] arr_adapter={"rear camera","front camera"};
    private ArrayAdapter<CharSequence> adapter = null;
    private int camera_state= Camera.CameraInfo.CAMERA_FACING_BACK;
    TextView tv_infosdk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        bt_idfacepro_test = (Button) this.findViewById(R.id.bt_idfacepro_test);
        bt_idfacepro_test.setOnClickListener(this);

        bt_idcompare_facepro_test = (Button) this.findViewById(R.id.bt_idcompare_facepro_test);
        bt_idcompare_facepro_test.setOnClickListener(this);

        cam_facing = (Spinner) findViewById(R.id.cam_facing);
        adapter = new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_dropdown_item, arr_adapter);
        cam_facing.setAdapter(adapter);
        cam_facing.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                if(camera_state != position) {
                    camera_state = position;
                }
                if(camera_state == 0)
                {
                    MIPSCamera.CameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
                }
                else if(camera_state == 1)
                {
                    MIPSCamera.CameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
                }
                TextView tv = (TextView)view;
                if(tv != null ){
                    tv.setTextColor(Color.BLUE);
                }
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        cam_facing.setSelection(0,true);

        choose_algorithm = (Spinner) findViewById(R.id.choose_algorithm_spin);
        choose_algorithm_adapter = new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_dropdown_item, choose_algorithm_arr_adapter);
        choose_algorithm.setAdapter(choose_algorithm_adapter);
        choose_algorithm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(choose_algorithm_state != position) {
                    choose_algorithm_state = position;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
		
		
        bt_vsfacefeature = (Button) findViewById(R.id.bt_vsfacefeature);
        bt_vsfacefeature.setOnClickListener(this);

        tv_infosdk = (TextView)findViewById(R.id.tv_infosdk);
        tv_infosdk.setText(mipsVideoFaceTrack.mipsGetVersion()+"(face+ID card identification)demo");
    }

    @Override
    public void onClick(View v) {
        Intent intent;
        Bundle bundle;
        switch (v.getId()){
            case R.id.bt_idfacepro_test:
                intent = new Intent(MainActivity.this,IDFaceProActivity.class);
                Log.d("whw"," "+choose_algorithm.getSelectedItemPosition());
                bundle = new Bundle();
                bundle.putInt("choose_alg",choose_algorithm.getSelectedItemPosition());
                intent.putExtras(bundle);
                startActivity(intent);
                break;
            case R.id.bt_idcompare_facepro_test:
                intent = new Intent(MainActivity.this,IDCompareFaceProActivity.class);
                bundle = new Bundle();
                bundle.putInt("choose_alg",choose_algorithm.getSelectedItemPosition());
                intent.putExtras(bundle);
                startActivity(intent);
                break;
            case R.id.bt_vsfacefeature:
                intent = new Intent(MainActivity.this,FaceOneToOneActivity.class);
                startActivity(intent);
            default:
                break;
        }
    }
}
