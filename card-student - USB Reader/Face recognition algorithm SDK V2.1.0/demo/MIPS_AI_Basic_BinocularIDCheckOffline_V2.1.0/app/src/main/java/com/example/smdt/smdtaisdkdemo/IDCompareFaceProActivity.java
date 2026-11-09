package com.example.smdt.smdtaisdkdemo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
//import com.google.android.gms.appindexing.Action;
//import com.google.android.gms.appindexing.AppIndex;
//import com.google.android.gms.common.api.GoogleApiClient;
import com.huashi.otg.sdk.HSInterface;

import java.io.FileInputStream;

import com.smdt.facesdk.mipsFaceDetailedInfo;
import com.smdt.facesdk.mipsFaceFeature;
import com.smdt.facesdk.mipsFaceInfoTrack;
import com.smdt.facesdk.mipsFaceVerifyInfo;
import com.smdt.facesdk.mipsVideoFaceTrack;

public class IDCompareFaceProActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = "VIPCompareActivity";
    private static final int REQUEST_CODE_1 = 0x001;
    //MyConn conn;
    HSInterface HSinterface;
    Intent service;
    String filepath = "";
    private EditText ET_deletevip;
    private TextView tv_id_vip,tv_verify_state,tv_vip_verify;//, tv_info, tv_statu,tv_verifyStatu,tv_IDStatu;
    private ImageView iv_photo;
    private Button bt_pic_sel,bt_vip_verify,bt_abort_verify,bt_opencamera;//,bt_deletevip;//, bt_find, bt_read;
    private MIPSCamera mMipsCameera;
    private byte nv21[];
    private boolean isNV21ready = false;
    int PREVIEW_WIDTH=1280;
    int PREVIEW_HEIGHT=720;
    private String VIP_DB_PATH=null;
    protected int mCameraInit = 0;
    private volatile boolean mIsRqstImage=false; // check if track operation is processing
    protected SurfaceHolder mSurfaceHolderDisplay = null;
    SurfaceView mSurfaceviewCamera = null;
    SurfaceHolder mSurfaceHolderCamera = null;
    private mipsVideoFaceTrack mfaceDetect;
    mipsFaceInfoTrack[] mFaceInfoDetected;
    private int mcntCurFace=0;
    private int VIP_ID=0;
    private Thread thread;
    private Thread threadReadID;
    private volatile boolean killed=false; // check if track operation is processing
    private Bitmap mbmpID;
    int flgReadID=0;
    String strID = "";
    private volatile boolean mIsTracking=false;
    //private Spinner spin_state;
    //private List<String> data_list_state;
    //private ArrayAdapter<String> arr_adapter;
    //private static String[] arr_adapter={"校验","录入"};
    //private ArrayAdapter<CharSequence> adapter = null;
    private String strFileAdd;
    //private int verify_state = 0;
    private int verify_once = 0;
    private int cameraInit = 0;
    private volatile int  vipID=-1;
    mipsFaceFeature featureID=null;
    mipsFaceVerifyInfo verifyInfo=null;
    mipsFaceFeature featureImage=null;
    float msimulateScore = 0;
    private int mtrackLivenessID=-1;
    private FaceCanvasView mOverlayCamera=null;
    int flgFaceChange=0;
    int flgSetFeatureImage=0;
    int flgLiveness=0;
    private String choose_alg;
    int livenessEnable=0;
	private int camera_state=0;
    private Spinner cam_orientation;
    private ArrayAdapter<CharSequence> adapter = null;
    private static String[] arr_adapter={"landscape","portrait ","reverse-landscape","reverse-portrait"};
	
    private int surface_left=0;
    private int surface_right=0;
    private int surface_top=0;
    private int surface_bottom=0;
    private int camera_w=0,camera_h=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        //initData();
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        choose_alg = String.valueOf(bundle.getInt("choose_alg"));
        livenessEnable=Integer.parseInt(choose_alg);
        mMipsCameera = new MIPSCamera();
        mMipsCameera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {

                if (data == null) {
                    return; // 
                }
                //if(verify_once == 1)
                {
                    if (!mIsTracking) {
                        synchronized (nv21) {
                            System.arraycopy(data, 0, nv21, 0, data.length);
                            isNV21ready = true;
                            //Log.i(TAG, "onPreviewFrame: " + data);
                        }
                        synchronized (thread) {
                            thread.notify();
                        }
                    }
                }
                mMipsCameera.addCallbackBuffer(data); // 
            }
        });

    }

    private void initView() {
        setContentView(R.layout.activity_idcompare_face_pro);

        bt_pic_sel = (Button) findViewById(R.id.pic_sel);
        bt_pic_sel.setOnClickListener(this);

        bt_vip_verify  = (Button) findViewById(R.id.bt_vip_verify);
        bt_vip_verify.setOnClickListener(this);

        bt_abort_verify  = (Button) findViewById(R.id.bt_abort_verify);
        bt_abort_verify.setOnClickListener(this);

        bt_opencamera  = (Button) findViewById(R.id.bt_opencamera);
        bt_opencamera.setOnClickListener(this);

        //tv_id_vip = (TextView) findViewById(R.id.id_vip);
        tv_verify_state = (TextView) findViewById(R.id.verify_state);

        ET_deletevip=(EditText)findViewById(R.id.ET_deletevip);

        mOverlayCamera = (FaceCanvasView)findViewById(R.id.canvasview_draw);

        //tv_vip_add_state = (TextView) findViewById(R.id.vip_add_state);
        //tv_vip_verify = (TextView) findViewById(R.id.vip_verify);

        iv_photo = (ImageView) findViewById(R.id.iv_photo);
        iv_photo.setScaleType(ImageView.ScaleType.FIT_CENTER);

        mSurfaceviewCamera = (SurfaceView) findViewById(R.id.vipverifyViewCamera);
        mSurfaceHolderCamera = mSurfaceviewCamera.getHolder();
        cam_orientation = (Spinner) findViewById(R.id.cam_orientation);
        adapter = new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_dropdown_item, arr_adapter);
        cam_orientation.setAdapter(adapter);
        cam_orientation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(camera_state != position) {
                    camera_state = position;
                }
                setCameraState(camera_state);

                TextView tv = (TextView)view;
                tv.setTextColor(Color.BLACK); //
            }
            //
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        cam_orientation.setSelection(0,true);
        cam_orientation.setEnabled(false);
    }

    int getLivenessTrackID(mipsFaceInfoTrack[] info)
    {
        int faceWidthMax=0;
        int idx=-1;
        if(info == null)
        {
            return -1;
        }
        for(int i = 0; i< mipsFaceInfoTrack.MAX_FACE_CNT_ONEfRAME; i++) {
            if (info[i] == null) {
                continue;
            }
            if(info[i].livenessDetecting == 1)
            {
                return info[i].FaceTRrackID;
            }
            if(info[i].faceRect.width() > faceWidthMax)
            {
                faceWidthMax = info[i].faceRect.width();
                idx = i;
            }
        }
        if(idx >= 0)
        {
            return info[idx].FaceTRrackID;
        }

        return -1;
    }

    int getTrackIDIdx(mipsFaceInfoTrack[] info, int trackID)
    {
        if(info == null || trackID<0)
        {
            return -1;
        }
        for(int i = 0; i< mipsFaceInfoTrack.MAX_FACE_CNT_ONEfRAME; i++) {
            if (info[i] == null) {
                continue;
            }
            if(info[i].FaceTRrackID == trackID)
            {
                return i;
            }
        }

        return -1;
    }

    public int openCamera(Context context, int width, int height, SurfaceHolder holder, int rotation) {
        if (width > 0 && height > 0) {
            PREVIEW_WIDTH = width;
            PREVIEW_HEIGHT = height;
        }

        mfaceDetect = new mipsVideoFaceTrack();
        int ret=mfaceDetect.mipsInit(context, 0,0,Environment.getExternalStorageDirectory().getPath() + "/mipsLic/mipsAi.lic", livenessEnable);
        if(ret < 0)
        {
            return -1;
        }
        nv21 = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
		
		if(rotation == 0){
			mfaceDetect.mipsSetTrackLandscape();
		}else if(rotation == 1){
			mfaceDetect.mipsSetTrackPortrait();
		}else if(rotation == 2){
			mfaceDetect.mipsSetTrackReverseLandscape();
		}else if(rotation == 3){
			mfaceDetect.mipsSetTrackReversePortrait();
		}
		
       
		
       // mfaceDetect.mipsSetTrackLandscape();
        mfaceDetect.mipsSetSimilarityThrehold(0.8f);
        mfaceDetect.mipsSetFaceWidthThrehold(90);
        mfaceDetect.mipsSetPicFaceWidthThrehold(30);
        mfaceDetect.mipsEnableIDFeature();
        mfaceDetect.mipsSetPicPitchAngle(18);
        //mfaceDetect.mipsDisableFaceLiveness();
        mfaceDetect.mipsDisableVipFaceVerify();
        final byte[] tmp = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
        VIP_DB_PATH=context.getFilesDir().getAbsolutePath()+File.separator;
        mfaceDetect.initFaceDB(context,VIP_DB_PATH+"mipsVipFaceDB",VIP_DB_PATH+"image",1);
        //mfaceDetect.initFaceDB(context,Environment.getExternalStorageDirectory().getPath()+"/faceVIP/mipsVipFaceDB",Environment.getExternalStorageDirectory().getPath()+"/faceVIP/image",1);
        thread = new Thread() {
            @Override
            public void run() {
                try {

                    while (!killed) {
                        float ret = 0;

                        Bitmap sourceBitmapFace=null;
                        if(verify_once == 0)
                        {
                            featureID=null;
                        }
                        //
                        if (isNV21ready) {
                            mIsTracking = true;
                            synchronized (nv21) {
                                System.arraycopy(nv21, 0, tmp, 0, nv21.length);
                            }

                            if(verify_once == 1) {

                                if(flgSetFeatureImage == 1 ) {

                                    if (featureID == null) {

                                        String[] image=new String[5];
                                        image[0] = strFileAdd;
                                        //
                                        final int isValidPic = mfaceDetect.verifyVipImage(image,1);
                                        if(isValidPic < 0){ //

                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(getApplicationContext(), "select pic: "+strFileAdd + " not pass the check，ret:" + isValidPic, Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            sourceBitmapFace = BitmapFactory.decodeFile(strFileAdd);
                                            verifyInfo = mfaceDetect.verifyVipImage(sourceBitmapFace);
                                            if(verifyInfo != null && verifyInfo.infoList != null){
                                                for(int i=0 ; i< verifyInfo.infoList.size(); i++){
                                                    mipsFaceDetailedInfo info = verifyInfo.infoList.get(i);
                                                    if(info != null) {
                                                        Log.d("yunboa", "yaw: " + info.mYaw + " , roll: " + info.mRoll + " , pitch: " + info.mPitch + " ,width: " + info.mWidth);
                                                    }
                                                }

                                            }
                                            verifyInfo = null;
                                        }else {//

                                            sourceBitmapFace = BitmapFactory.decodeFile(strFileAdd);
                                            featureID = mfaceDetect.mipsGetFeature(sourceBitmapFace);
                                        }
                                    }

                                    //
                                    if (featureImage != null && featureID != null) { //
                                        //featureImage = mfaceDetect.mipsGetFeature(tmp, PREVIEW_WIDTH, PREVIEW_HEIGHT, 0);
                                        ret = mfaceDetect.mipsVerifyID(featureImage, featureID); //
                                        //int ret2 = mfaceDetect.mipsVerifyID(tmp, PREVIEW_WIDTH, PREVIEW_HEIGHT, sourceBitmapFace);
                                        msimulateScore = ret;
                                        if (ret >= 0.6) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    tv_verify_state.setText("face+ID card check pass:" + Float.toString(msimulateScore));
                                                }
                                            });
                                        } else if (ret < 0.6) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    tv_verify_state.setText("face+ID card check failed:" + Float.toString(msimulateScore));
                                                }
                                            });
                                        }
                                    }
                                    else
                                    {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                tv_verify_state.setText("extract face feature failed");
                                            }
                                        });
                                    }

                                    verify_once = 0;
                                    featureImage = null;
                                    featureID = null;
                                    mtrackLivenessID = -1;
                                    flgSetFeatureImage = 0;
                                    flgLiveness = 0;
                                }
                                else  //
                                {
                                    if(mtrackLivenessID < 0) {
                                        mtrackLivenessID = getLivenessTrackID(mFaceInfoDetected);
                                    }
                                }
                            }

                            //
                            flgFaceChange = mfaceDetect.mipsDetectOneFrame(tmp, PREVIEW_WIDTH, PREVIEW_HEIGHT,mtrackLivenessID);
                            if (flgFaceChange == 1) {

                                mcntCurFace = mfaceDetect.mipsGetFaceCnt();
                                //mdbFaceCnt = mfaceTrackLiveness.mipsGetDbFaceCnt();
                                mFaceInfoDetected = mfaceDetect.mipsGetFaceInfoDetected();
                                if (mOverlayCamera != null) {
                                    mOverlayCamera.addFacesLiveness(mFaceInfoDetected, FaceCanvasView.ANALYSIS_STATE);
                                    mOverlayCamera.postInvalidate();
                                }

                                //
                                if(mtrackLivenessID >= 0)
                                {
                                    //
                                    int idx=getTrackIDIdx(mFaceInfoDetected,mtrackLivenessID);
                                    if(idx >= 0) {
                                        mipsFaceInfoTrack info = mFaceInfoDetected[idx];
                                        if (info != null) {
                                            if (((livenessEnable > 0 && info.flgSetLiveness == 1)||(livenessEnable == 0)) && info.flgSetIDPro == 1) {
                                                flgSetFeatureImage = 1; //
                                                flgLiveness = info.flgLiveness; //
                                                featureImage = info.mfaceFeature; //
                                            }
                                        }
                                        else
                                        {
                                            mtrackLivenessID = -1;
                                        }
                                    }
                                    else
                                    {
                                        mtrackLivenessID = -1;
                                    }
                                }
                            }
                            isNV21ready = false;

                        } else {
                            synchronized (this) {
                                mIsTracking = false;
                                thread.wait(); // 
                            }
                        }

                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    //stopThread();
                }
            }
        };
        thread.start();

        if (mCameraInit == 0 && mMipsCameera != null) {
            if (holder != null) {
                mSurfaceHolderDisplay = holder;
                mMipsCameera.openCamera(MIPSCamera.CameraFacing, PREVIEW_WIDTH, PREVIEW_HEIGHT);
                mMipsCameera.initPreviewBuffer();
                mMipsCameera.setCameraDisplayOrientation(rotation);
                mMipsCameera.startPreview(mSurfaceHolderDisplay);
                //openCamera(CameraFacing,mSurfaceHolderDisplay);
            } else {
                mMipsCameera.openCamera(MIPSCamera.CameraFacing, PREVIEW_WIDTH, PREVIEW_HEIGHT);
                mMipsCameera.initPreviewBuffer();
                mMipsCameera.setCameraDisplayOrientation(rotation);
                mMipsCameera.startPreview(null);
                //openCamera(CameraFacing,mSurfaceHolder);
            }
        }
        return 0;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        killed = true;
        if (null != mMipsCameera) {
            mMipsCameera.releaseCamera();
            mMipsCameera = null;
        }
        if(mfaceDetect != null) {
            mfaceDetect.mipsUninit();
        }
        super.onDestroy();
        //HSinterface.unInit();
        //stopService(service);
        //unbindService(conn);
    }
    private void copyFile(String oldPath, String newPath) {
        try {
            int bytesum = 0;
            int byteread = 0;
            File oldfile = new File(oldPath);
            if (oldfile.exists()) { //
                InputStream inStream = new FileInputStream(oldPath); //
                FileOutputStream fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[1444];
                int length;
                while ( (byteread = inStream.read(buffer)) != -1) {
                    bytesum += byteread; // 
                    //System.out.println(bytesum);
                    fs.write(buffer, 0, byteread);
                }
                inStream.close();
            }
        }
        catch (Exception e) {
            System.out.println("copy file error");
            e.printStackTrace();

        }
    }

    public static String getRealFilePath( final Context context, final Uri uri ) {
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
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) {
            return;
        }
        Uri imageUri = data.getData();
        if (requestCode == REQUEST_CODE_1) {
            strFileAdd = getRealFilePath(getBaseContext(),imageUri);//getPhotoPathFromContentUri(getBaseContext(),imageUri);
            Bitmap sourceBitmapFace = BitmapFactory.decodeFile(strFileAdd);
            iv_photo.setImageBitmap(sourceBitmapFace);
        }
    }

    private void chooseBitmap(int requestCode) {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, requestCode);
    }

    //
    public void mipsSetTrackLandscape()
    {
        if(mfaceDetect != null) {
            mfaceDetect.mipsSetTrackLandscape();
            if(mMipsCameera != null){
                mMipsCameera.setCameraDisplayOrientation(0);
            }
        }
    }
    
    public void mipsSetTrackPortrait()
    {
        if(mfaceDetect != null) {
            mfaceDetect.mipsSetTrackPortrait();
            if(mMipsCameera != null) {
                mMipsCameera.setCameraDisplayOrientation(1);
            }
        }
    }

    //
    public void mipsSetTrackReverseLandscape()
    {
        if(mfaceDetect != null) {
            mfaceDetect.mipsSetTrackReverseLandscape();
            if(mMipsCameera != null) {
                mMipsCameera.setCameraDisplayOrientation(2);
            }
        }
    }
    //
    public void mipsSetTrackReversePortrait()
    {
        if(mfaceDetect != null) {
            mfaceDetect.mipsSetTrackReversePortrait();
            if(mMipsCameera != null) {
                mMipsCameera.setCameraDisplayOrientation(3);
            }
        }
    }

    private void setCameraState(int state)
    {
        if(state == 0)
        {

            mipsSetTrackLandscape();
            mOverlayCamera.setOverlayRect(surface_left, surface_right, surface_top, surface_bottom, camera_w,camera_h);
            mOverlayCamera.setCavasLandscape();
        }
        else if(state == 1)
        {
            mipsSetTrackPortrait();
            mOverlayCamera.setOverlayRect(surface_left, surface_right, surface_top, surface_bottom,camera_h, camera_w);
            mOverlayCamera.setCavasPortrait();
        }
        else if(state == 2)
        {

            mipsSetTrackReverseLandscape();
            mOverlayCamera.setOverlayRect(surface_left, surface_right, surface_top, surface_bottom, camera_w,camera_h);
            mOverlayCamera.setCavasReverseLandscape();
        }
        else if(state == 3)
        {
            mipsSetTrackReversePortrait();
            mOverlayCamera.setOverlayRect(surface_left, surface_right, surface_top, surface_bottom,camera_h, camera_w);
            mOverlayCamera.setCavasReversePortrait();
        }
    }
	
	
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_opencamera:
                if(cameraInit == 0) {
                    openCamera(getApplicationContext(), PREVIEW_WIDTH, PREVIEW_HEIGHT, mSurfaceHolderCamera, camera_state);
                    int left = mSurfaceviewCamera.getLeft();
                    int right = mSurfaceviewCamera.getRight();
                    int top = mSurfaceviewCamera.getTop();
                    int bottom = mSurfaceviewCamera.getBottom();

                    surface_left = left;
                    surface_right = right;
                    surface_top = top;
                    surface_bottom = bottom;
                    camera_w = PREVIEW_WIDTH;
                    camera_h = PREVIEW_HEIGHT;
                    mOverlayCamera.setOverlayRect(left, right, top, bottom, PREVIEW_WIDTH, PREVIEW_HEIGHT);
                    cam_orientation.setEnabled(true);
					//setCameraState(left,right,top,bottom,camera_state);
                    cameraInit = 1;
                }
                else
                {
                    mMipsCameera.setPreviewDisplay(mSurfaceHolderCamera);
                }
                break;
            case R.id.bt_vip_verify:
                //
                verify_once = 1;
                break;
            case R.id.bt_abort_verify:
                //
                verify_once = 0;
                break;
            case R.id.pic_sel:
                chooseBitmap(REQUEST_CODE_1);
                if(strFileAdd != null)
                {
                    Toast.makeText(getApplicationContext(), "select pic: "+strFileAdd, Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(getApplicationContext(), "not select pic", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }
}
