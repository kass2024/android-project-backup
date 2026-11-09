package com.example.smdt.smdtaisdkdemo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.smdt.facesdk.mipsFaceFeature;
import com.smdt.facesdk.mipsFaceFeatureInfrence;
import com.smdt.facesdk.mipsVideoFaceTrack;
import java.util.ArrayList;
import java.util.Arrays;

public class FaceOneToOneActivity extends AppCompatActivity implements View.OnClickListener {
    private Button bt_pic_sel;
    private ImageView iv_photo;
    private Button bt_pic_sel2;
    private ImageView iv_photo2;
    private Button compareBtn;
    private TextView compareresult ;
    private static final int REQUEST_CODE_1 = 0x001;
    private static final int REQUEST_CODE_2 = 0x002;
    private String strFileAdd;

    private mipsVideoFaceTrack mfaceDetect;

    private mipsFaceFeature featurePic1=null;
    private mipsFaceFeature featurePic2=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_one_to_one);



        bt_pic_sel = (Button)findViewById(R.id.pic_sel);
        iv_photo = (ImageView)findViewById(R.id.iv_photo);
        bt_pic_sel2 = (Button)findViewById(R.id.pic_sel2);
        iv_photo2 = (ImageView)findViewById(R.id.iv_photo2);
        bt_pic_sel.setOnClickListener(this);
        bt_pic_sel2.setOnClickListener(this);
        compareBtn = (Button) findViewById(R.id.compareBtn);
        compareBtn.setOnClickListener(this);

        compareresult = (TextView) findViewById(R.id.compareresult);

        mfaceDetect = new mipsVideoFaceTrack();
        int ret=mfaceDetect.mipsInit(getApplicationContext(), 2,0, Environment.getExternalStorageDirectory().getPath() + "/mipsLic/mipsAi.lic", 0);


        if(ret >= 0){
            Toast.makeText(getApplicationContext(), "SDK initialization success", Toast.LENGTH_SHORT).show();
            mfaceDetect.mipsSetPicPitchAngle(18);
        }else {
            mfaceDetect = null;
            if (ret == -7) {
                Toast.makeText(getApplicationContext(), "ensure the license file is correct", Toast.LENGTH_SHORT).show();
            } else if (ret == -1) {
                Toast.makeText(getApplicationContext(), "ensure the hardware and time is correct,SdkType:" + mipsVideoFaceTrack.mipsCheckSdkType(getApplicationContext()), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "SDK initialization faild:" + ret, Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.pic_sel:
                chooseBitmap(REQUEST_CODE_1);
                break;
            case R.id.pic_sel2:
                chooseBitmap(REQUEST_CODE_2);
                break;
            case R.id.compareBtn:
                long time = System.currentTimeMillis();
                float result = startCompare();
                long time1 = System.currentTimeMillis();
                Log.d("whw", String.valueOf(result)+"  time cost：" +(time1-time)  );
                compareresult.setText(String.valueOf(result) +"  time cost：" + String.valueOf((time1-time))+"ms");

                if(result == -1){
                    Toast.makeText(getApplicationContext(), "match failed，feature extract failed for image 1, pls check the image", Toast.LENGTH_SHORT).show();
                }else if(result == -2){
                    Toast.makeText(getApplicationContext(), "match failed，feature extract failed for image 2, pls check the image", Toast.LENGTH_SHORT).show();
                }else if(result == -3){
                    Toast.makeText(getApplicationContext(), "match failed，sdk initialization not success", Toast.LENGTH_SHORT).show();
                }else {
                    Toast.makeText(getApplicationContext(), "match complete，score： " + result, Toast.LENGTH_SHORT).show();
                }

                strFileAdd = null;

                //featurePic1 = null;
                //featurePic2 = null;

                break;




        }
    }


    private float startCompare() {

        if(featurePic1 == null){
            return -1 ;
        }

        if(featurePic2 == null){
            return -2 ;
        }


        if(mfaceDetect != null){
            return mfaceDetect.mipsVerifyID(featurePic1,featurePic2);
        }

        return -3 ;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) {
            return;
        }

        Uri imageUri = data.getData();

        String[] image=new String[5];


        if (requestCode == REQUEST_CODE_1) {
            strFileAdd = getRealFilePath(getBaseContext(),imageUri);
            Bitmap sourceBitmapFace = BitmapFactory.decodeFile(strFileAdd);

            if(mfaceDetect != null){
                image[0] = strFileAdd;
                int ret = mfaceDetect.verifyVipImage(image,1);
                if(ret < 0){
                    Toast.makeText(getApplicationContext(), "Select picture: "+strFileAdd + " not pass the check，ret:" + ret, Toast.LENGTH_SHORT).show();
                    featurePic1 = null;
                    return;
                }
                featurePic1 = mfaceDetect.mipsGetFeature(sourceBitmapFace);

                if(featurePic1 == null){
                    Log.i("yunboa", "mipsGetFeature is null ");
                }
            }

            iv_photo.setImageBitmap(sourceBitmapFace);

        }else if (requestCode == REQUEST_CODE_2) {
            strFileAdd = getRealFilePath(getBaseContext(),imageUri);
            Bitmap sourceBitmapFace = BitmapFactory.decodeFile(strFileAdd);
            iv_photo2.setImageBitmap(sourceBitmapFace);
            if(mfaceDetect != null){

                image[0] = strFileAdd;
                int ret = mfaceDetect.verifyVipImage(image,1);
                if(ret < 0){
                    Toast.makeText(getApplicationContext(), "Select picture: "+strFileAdd + "  not pass the check，ret:" + ret, Toast.LENGTH_SHORT).show();
                    featurePic2 = null;
                    return;
                }
                featurePic2 = mfaceDetect.mipsGetFeature(sourceBitmapFace);
                if(featurePic2 == null){
                    Log.i("yunboa", "mipsGetFeature is null ");
                }
            }

        }

        if(strFileAdd != null)
        {
            Toast.makeText(getApplicationContext(), "Select picture: "+strFileAdd, Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(getApplicationContext(), "not select the picture", Toast.LENGTH_SHORT).show();
        }
    }

    public static String getRealFilePath(final Context context, final Uri uri ) {
        if ( null == uri ) return null;
        final String scheme = uri.getScheme();
        String data = null;
        if ( scheme == null )
            data = uri.getPath();
        else if ( ContentResolver.SCHEME_FILE.equals( scheme ) ) {
            data = uri.getPath();
        } else if ( ContentResolver.SCHEME_CONTENT.equals( scheme ) ) {
            Cursor cursor = context.getContentResolver().query( uri, new String[] { MediaStore.Images.ImageColumns.DATA }, null, null, null );
            if ( null != cursor ) {
                if ( cursor.moveToFirst() ) {
                    int index = cursor.getColumnIndex( MediaStore.Images.ImageColumns.DATA );
                    if ( index > -1 ) {
                        data = cursor.getString( index );
                    }
                }
                cursor.close();
            }
        }
        return data;
    }

    private void chooseBitmap(int requestCode) {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, requestCode);
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mfaceDetect != null){
            mfaceDetect.mipsUninit();
        }

        if(featurePic1 != null){
            featurePic1 = null;
        }
        if(featurePic2 != null){
            featurePic2 = null;
        }

    }
}
