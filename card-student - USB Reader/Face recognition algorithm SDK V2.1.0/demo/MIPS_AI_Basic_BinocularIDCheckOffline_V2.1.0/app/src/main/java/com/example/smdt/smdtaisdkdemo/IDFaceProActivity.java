package com.example.smdt.smdtaisdkdemo;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

//import com.example.smdt.smdtaisdkdemo.service.MipsCameraService;
import com.example.smdt.smdtaisdkdemo.service.MipsIDFaceProService;
import com.smdt.facesdk.InitCallback;
import com.smdt.facesdk.mipsFaceDetailedInfo;
import com.smdt.facesdk.mipsFaceFeature;
import com.smdt.facesdk.mipsFaceInfoTrack;
import com.smdt.facesdk.mipsFaceVerifyInfo;



import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class IDFaceProActivity extends AppCompatActivity implements View.OnClickListener, ServiceConnection, InitCallback {
    private static final int REQUEST_CODE_1 = 0x001;
    private static final int REQUEST_CODE_2 = 0x002;
    private static final String TAG = IDFaceProActivity.class.getSimpleName();
    private Intent mIntent;
    private MipsIDFaceProService mipsFaceService;
    private CheckBox cb_vip;
    private CheckBox cb_faceattr;
    private CheckBox cb_refreshattr;
    private CheckBox cb_faceattrYM;
    private CheckBox cb_refreshVIP;
    private CheckBox cb_pic_display;
    private CheckBox cb_realFace;
    private CheckBox cb_saveFacePic;
    private CheckBox cb_istest;
    private int camcnt=0;

    TextView cnt_cur_faceText;
    SurfaceView mSurfaceviewCamera = null;
    SurfaceHolder mSurfaceHolderCamera = null;

    SurfaceView mSurfaceviewCameraIR = null;
    SurfaceHolder mSurfaceHolderCameraIR = null;
    private FaceCanvasView mfaceOverlayIR;
    //,yaw_angleText,pitch_angleText,roll_angleText,trackIDText;
    List<Drawable> drawableList = new ArrayList<>();
    private Button bt_start;
    private Button bt_set;
    private Button bt_get;
    private Button bt_disp;

    private Button bt_getfile;
    private Button bt_addface;
    private Button bt_deletevip;
    private Button bt_export;
    private Button bt_batchVipAdd;
    private Button bt_loopInit;
    //private Button bt_sel_lic;
    //private Button bt_lic;

    private EditText ET_live_mode;


    private EditText ET_camera_w;
    private EditText ET_camera_h;
    private EditText ET_similarity;
    private EditText ET_face_score;
    private EditText ET_track_cnt;
    private EditText ET_roll;
    private EditText ET_pitch;
    private EditText ET_YAW;
    private EditText ET_deletevip;
    private EditText ET_LivenessThreshold;
    private EditText ET_liveness_facewidth;
    private EditText ET_licpath;
    private EditText ET_FaceWidth;
    private EditText ET_PicFaceWidth;
    private EditText ET_vipIDAdd;
    private EditText ET_vipImagePath;
    private EditText ET_facesavepath;
    private EditText ET_idbase;
    private String strFileAdd;
    private String strfacesavepath;
    private int midbase;
    private FaceCanvasView mfaceOverlay;
    private Thread thread1;

    private Button bt_verifyPicSel;
    private Button bt_verify;

    private Spinner cam_orientation;
    private Spinner camera_resolution;
    private static String[] arr_adapter_cam_res={"connect camera"};
    private ArrayAdapter<CharSequence> adapter_cam_res = null;
    private int camera_state_cam_res=0;
    private List<String> data_list_state;
    //private ArrayAdapter<String> arr_adapter;
    private static String[] arr_adapter={"landscape","portrait ","reverse-landscape","reverse-portrait"};
    private ArrayAdapter<CharSequence> adapter = null;
    private int camera_state=0;

    private int surface_width=0;
    private int surface_left=0;
    private int surface_right=0;
    private int surface_top=0;
    private int surface_bottom=0;
    private int camera_w=0,camera_h=0;
    private String vipImagePath=null;
    private Context context;
    int cntVIP=0;
    public List<Camera.Size> mCameraSize;
    private UsbManager usbManager;
    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;
    //private ImageView mcapture_photo;
    //private ImageView mvip_photo;
    FaceImageView faceView[];
    private String choose_alg;

    private boolean isPause = false;

    private int cameraWidthDefault=1280;
    private int cameraHeightDefault=720;

    private boolean isIniting = false;
    private TextView tv_AlgorithmInfo;

    public class FaceImageView {
        public ImageView mcapture_photo;
        public ImageView mvip_photo;
        public int idx;
        public int vipID;
        public int trackID;
        public String name;

        public FaceImageView() {
            mcapture_photo = null;
            mvip_photo = null;
            idx = -1;
            vipID=-1;
            trackID=-1;
            name = null;
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idface_pro);
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        choose_alg = String.valueOf(bundle.getInt("choose_alg"));
        Log.d("whw","Alogrithm select "+choose_alg);
        cb_vip = (CheckBox) findViewById(R.id.CB_VIP);
        cb_faceattr = (CheckBox) findViewById(R.id.CB_faceattr);
        cb_faceattrYM = (CheckBox) findViewById(R.id.CB_faceattrYM);
        cb_refreshattr = (CheckBox) findViewById(R.id.CB_refreshattr);
        cb_refreshVIP = (CheckBox) findViewById(R.id.CB_refreshVIP);
        cb_pic_display = (CheckBox) findViewById(R.id.CB_pic_display);
        cb_realFace = (CheckBox) findViewById(R.id.CB_realFace);
        cb_saveFacePic = (CheckBox) findViewById(R.id.CB_saveFacePic);
        cb_istest = (CheckBox) findViewById(R.id.CB_istest);
        tv_AlgorithmInfo = (TextView) findViewById(R.id.tv_AlgorithmInfo);

        TextView tv_live_mode = (TextView) findViewById(R.id.tv_live_mode);
        TextView tv_FACEATTRYM = (TextView) findViewById(R.id.tv_FACEATTRYM);
        ET_live_mode=(EditText)findViewById(R.id.ET_live_mode);
        if(choose_alg.equals("2")||choose_alg.equals("7")){
            tv_live_mode.setVisibility(View.VISIBLE);
            ET_live_mode.setVisibility(View.VISIBLE);
        }

        if(choose_alg.equals("3")||choose_alg.equals("1")){
            tv_FACEATTRYM.setVisibility(View.VISIBLE);
            cb_faceattrYM.setVisibility(View.VISIBLE);
        }

        cb_pic_display.setChecked(true);

        bt_start = (Button) this.findViewById(R.id.bt_start);
        bt_start.setOnClickListener(this);

        bt_disp = (Button) this.findViewById(R.id.bt_disp);
        bt_disp.setOnClickListener(this);


        bt_set = (Button) this.findViewById(R.id.bt_set);
        bt_set.setOnClickListener(this);


        bt_get = (Button) this.findViewById(R.id.bt_get);
        bt_get.setOnClickListener(this);


        bt_getfile = (Button) this.findViewById(R.id.bt_getfile);
        bt_getfile.setOnClickListener(this);


        bt_addface = (Button) this.findViewById(R.id.bt_addface);
        bt_addface.setOnClickListener(this);


        bt_deletevip = (Button) this.findViewById(R.id.bt_deletevip);
        bt_deletevip.setOnClickListener(this);


        bt_verifyPicSel = (Button) this.findViewById(R.id.bt_verifyPicSel);
        bt_verifyPicSel.setOnClickListener(this);


        bt_verify = (Button) this.findViewById(R.id.bt_verify);
        bt_verify.setOnClickListener(this);


        bt_export = (Button) this.findViewById(R.id.bt_export);
        bt_export.setOnClickListener(this);


        bt_batchVipAdd = (Button) this.findViewById(R.id.bt_batchVipAdd);
        bt_batchVipAdd.setOnClickListener(this);


        bt_disp.setEnabled(false);
        bt_deletevip.setEnabled(false);
        bt_verifyPicSel.setEnabled(false);
        bt_verify.setEnabled(false);
        //bt_export.setEnabled(false);
        bt_batchVipAdd.setEnabled(false);
        bt_addface.setEnabled(false);
        bt_getfile.setEnabled(false);
        bt_get.setEnabled(false);
        bt_set.setEnabled(false);

        ET_camera_w=(EditText)findViewById(R.id.ET_camera_w);
        ET_camera_h=(EditText)findViewById(R.id.ET_camera_h);


        ET_idbase = (EditText)findViewById(R.id.ET_idbase);
        ET_facesavepath = (EditText)findViewById(R.id.ET_facesavepath);
        ET_similarity=(EditText)findViewById(R.id.ET_similarity);
        ET_face_score=(EditText)findViewById(R.id.ET_face_score);
        ET_track_cnt=(EditText)findViewById(R.id.ET_track_cnt);
        ET_liveness_facewidth=(EditText)findViewById(R.id.ET_liveness_facewidth);
        ET_roll=(EditText)findViewById(R.id.ET_roll);
        ET_pitch=(EditText)findViewById(R.id.ET_pitch);
        ET_YAW=(EditText)findViewById(R.id.ET_YAW);
        ET_deletevip=(EditText)findViewById(R.id.ET_deletevip);
        ET_LivenessThreshold = (EditText)findViewById(R.id.ET_LivenessThreshold);
        ET_licpath = (EditText)findViewById(R.id.ET_licpath);
        ET_FaceWidth = (EditText)findViewById(R.id.ET_FaceWidth);
        ET_PicFaceWidth = (EditText)findViewById(R.id.ET_PicFaceWidth);
        ET_vipIDAdd = (EditText)findViewById(R.id.ET_vipIDAdd);
        ET_vipImagePath = (EditText)findViewById(R.id.ET_vipImagePath);

        drawableList = new ArrayList<>();

        mSurfaceviewCamera = (SurfaceView) findViewById(R.id.surfaceViewCamera);
        mSurfaceHolderCamera = mSurfaceviewCamera.getHolder();

        mSurfaceviewCameraIR = (SurfaceView) findViewById(R.id.surfaceViewCameraIR);
        mSurfaceHolderCameraIR = mSurfaceviewCameraIR.getHolder();

        faceView = new FaceImageView[4];
        faceView[0]=new FaceImageView();
        faceView[0].mcapture_photo = (ImageView) findViewById(R.id.capture_photo0);
        faceView[0].mcapture_photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        faceView[0].mvip_photo = (ImageView) findViewById(R.id.vip_photo0);
        faceView[0].mvip_photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        faceView[0].mcapture_photo.setImageDrawable(getResources().getDrawable(R.drawable.gray));
        faceView[0].mvip_photo.setImageDrawable(getResources().getDrawable(R.drawable.gray));

        faceView[1]=new FaceImageView();
        faceView[1].mcapture_photo = (ImageView) findViewById(R.id.capture_photo1);
        faceView[1].mcapture_photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        faceView[1].mvip_photo = (ImageView) findViewById(R.id.vip_photo1);
        faceView[1].mvip_photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        faceView[1].mcapture_photo.setImageDrawable(getResources().getDrawable(R.drawable.green));
        faceView[1].mvip_photo.setImageDrawable(getResources().getDrawable(R.drawable.green));

        faceView[2]=new FaceImageView();
        faceView[2].mcapture_photo = (ImageView) findViewById(R.id.capture_photo2);
        faceView[2].mcapture_photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        faceView[2].mvip_photo = (ImageView) findViewById(R.id.vip_photo2);
        faceView[2].mvip_photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        faceView[2].mcapture_photo.setImageDrawable(getResources().getDrawable(R.drawable.gray));
        faceView[2].mvip_photo.setImageDrawable(getResources().getDrawable(R.drawable.gray));

        faceView[3]=new FaceImageView();
        faceView[3].mcapture_photo = (ImageView) findViewById(R.id.capture_photo3);
        faceView[3].mcapture_photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        faceView[3].mvip_photo = (ImageView) findViewById(R.id.vip_photo3);
        faceView[3].mvip_photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        faceView[3].mcapture_photo.setImageDrawable(getResources().getDrawable(R.drawable.green));
        faceView[3].mvip_photo.setImageDrawable(getResources().getDrawable(R.drawable.green));


        mfaceOverlay = (FaceCanvasView)findViewById(R.id.canvasview_draw);

        mIntent = new Intent(this, MipsIDFaceProService.class);

        cam_orientation = (Spinner) findViewById(R.id.cam_orientation);
        adapter = new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_dropdown_item, arr_adapter);
        cam_orientation.setAdapter(adapter);
        cam_orientation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
               
                if(camera_state != position) {
                    camera_state = position;
                }
                setCameraState(camera_state);

                TextView tv = (TextView)view;
                tv.setTextColor(Color.WHITE); 
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        cam_orientation.setSelection(0,true);

        camera_resolution = (Spinner) findViewById(R.id.camera_resolution);
        camera_resolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(mipsFaceService == null)
                {
                    return;
                }
                if(camera_state_cam_res != position) {
                    camera_state_cam_res = position;
                    if(mCameraSize!=null)
                    {
                        if(mCameraSize.size()>0 && camera_state_cam_res<mCameraSize.size())
                        {
                            ET_camera_w.setText(Integer.toString(mCameraSize.get(camera_state_cam_res).width));
                            ET_camera_h.setText(Integer.toString(mCameraSize.get(camera_state_cam_res).height));
                        }
                    }
                }
                TextView tv = (TextView)view;
                tv.setTextColor(Color.WHITE); //
            }
            //
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        //mfaceDetect = new mipsVideoFaceTrack();

        //mipsVideoFaceTrack.mipsFaceSdkCheck(this,"/sdcard/mipsLic/mipsAi.lic");
        startService(mIntent);
        bindService(mIntent, this, BIND_AUTO_CREATE);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if(intent.hasExtra(UsbManager.EXTRA_PERMISSION_GRANTED)) {
                    boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                }
                switch(action) {
                    case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                        refreshCamera();
                        break;
                    case UsbManager.ACTION_USB_DEVICE_DETACHED:
                        break;
                }
            }
        };

        intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(receiver, intentFilter);

    }

    private void setCameraState(int state)
    {
        if(state == 0)
        {
            if(mipsFaceService != null)
            {
                mipsFaceService.mipsSetTrackLandscape();
            }
            mfaceOverlay.setOverlayRect(surface_left, surface_right, surface_top, surface_bottom, camera_w,camera_h);
            mfaceOverlay.setCavasLandscape();
        }
        else if(state == 1)
        {
            if(mipsFaceService != null)
            {
                mipsFaceService.mipsSetTrackPortrait();
            }

            mfaceOverlay.setOverlayRect(surface_left, surface_right, surface_top, surface_bottom,camera_h, camera_w);
            mfaceOverlay.setCavasPortrait();
        }
        else if(state == 2)
        {
            if(mipsFaceService != null)
            {
                mipsFaceService.mipsSetTrackReverseLandscape();
            }

            mfaceOverlay.setOverlayRect(surface_left, surface_right, surface_top, surface_bottom, camera_w,camera_h);
            mfaceOverlay.setCavasReverseLandscape();
        }
        else if(state == 3)
        {
            if(mipsFaceService != null)
            {
                mipsFaceService.mipsSetTrackReversePortrait();
            }

            mfaceOverlay.setOverlayRect(surface_left, surface_right, surface_top, surface_bottom,camera_h, camera_w);
            mfaceOverlay.setCavasReversePortrait();
        }
    }

    public int checkCameraSize(int w, int h)
    {
        if(mCameraSize == null)
        {
            return -1;
        }
        for(int i=0; i<mCameraSize.size();i++)
        {
            if((mCameraSize.get(i).width == w) && (mCameraSize.get(i).height == h))
            {
                return i;
            }
        }
        return -1;
    }
    public int refreshCamera()
    {
        camcnt=mipsFaceService.openCamera();
        mCameraSize = mipsFaceService.mipsGetCameraSize();
        if(mCameraSize != null && mCameraSize.size() > 0)
        {
            List<String> list = new ArrayList<String>();
            for(int i=0;i<mCameraSize.size();i++)
            {
                list.add(Integer.toString(mCameraSize.get(i).width)+"x"+Integer.toString(mCameraSize.get(i).height));
            }
            int size = list.size();
            String[] array = (String[])list.toArray(new String[size]);
            adapter_cam_res = new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_dropdown_item, array);
            camera_resolution.setAdapter(adapter_cam_res);
            int idx=checkCameraSize(cameraWidthDefault,cameraHeightDefault);
            if(idx >= 0)
            {
                camera_resolution.setSelection(idx,true);
            }
            else
            {
                ET_camera_w.setText(Integer.toString(mCameraSize.get(0).width));
                ET_camera_h.setText(Integer.toString(mCameraSize.get(0).height));
            }
        }
        else {
            adapter_cam_res = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_dropdown_item, arr_adapter_cam_res);
            camera_resolution.setAdapter(adapter_cam_res);
        }
        return 0;
    }

    public int getFreeFaceView()
    {
        int ret=-1;
        for(int j=0; j<4;j++) {
            if(faceView[j].idx == -1)
            {
                ret = j;
                break;
            }
        }
        return ret;
    }

    public void disableFaceView(int id)
    {
        if(id > 4 || id <0)
        {
            return;
        }
        faceView[id].idx=-1;
        faceView[id].trackID = -1;
        faceView[id].vipID = -1;
        if(id%2 != 0) {
            faceView[id].mcapture_photo.setImageDrawable(getResources().getDrawable(R.drawable.green));
            faceView[id].mvip_photo.setImageDrawable(getResources().getDrawable(R.drawable.green));
        }
        else
        {
            faceView[id].mcapture_photo.setImageDrawable(getResources().getDrawable(R.drawable.gray));
            faceView[id].mvip_photo.setImageDrawable(getResources().getDrawable(R.drawable.gray));
        }
    }

    public int findFaceView(int trackID)
    {
        int ret=-1;
        for(int j=0; j<4;j++) {
            if(faceView[j].trackID == trackID)
            {
                ret = j;
                break;
            }
        }
        return ret;
    }

    public void saveFaceBitmap(Bitmap bitmapRGB,Bitmap bitmapIR,int isLiveness)
    {
        if(strfacesavepath == null || cb_saveFacePic.isChecked() == false)
        {
            return;
        }
        if(cb_istest.isChecked() == false) {
            if (bitmapRGB != null) {
                String path = null;//=strfacesavepath+"rgb/";
                if (cb_realFace.isChecked() == true) {
                    path = strfacesavepath + "rgb/" + "real/";
                } else {
                    path = strfacesavepath + "rgb/" + "fake/";
                }
                FileUtil.makeRootDirectory(path);
                FileUtil.writeBitmapToSDCard(bitmapRGB, path, Integer.toString(midbase));
            }
            if (bitmapIR != null) {
                String path = null;//=strfacesavepath+"rgb/";
                if (cb_realFace.isChecked() == true) {
                    path = strfacesavepath + "ir/" + "real/";
                } else {
                    path = strfacesavepath + "ir/" + "fake/";
                }
                FileUtil.makeRootDirectory(path);
                FileUtil.writeBitmapToSDCard(bitmapIR, path, Integer.toString(midbase));
            }
            midbase++;
        }
        else
        {
            if (bitmapRGB != null) {
                String path = null;//=strfacesavepath+"rgb/";
                if (isLiveness == 1) {
                    path = strfacesavepath + "rgb/" + "real/";
                } else {
                    path = strfacesavepath + "rgb/" + "fake/";
                }
                FileUtil.makeRootDirectory(path);
                FileUtil.writeBitmapToSDCard(bitmapRGB, path, Integer.toString(midbase));
            }
            if (bitmapIR != null) {
                String path = null;//=strfacesavepath+"rgb/";
                if (isLiveness == 1) {
                    path = strfacesavepath + "ir/" + "real/";
                } else {
                    path = strfacesavepath + "ir/" + "fake/";
                }
                FileUtil.makeRootDirectory(path);
                FileUtil.writeBitmapToSDCard(bitmapIR, path, Integer.toString(midbase));
            }
            midbase++;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        MipsIDFaceProService.Binder binder = (MipsIDFaceProService.Binder) service;
        mipsFaceService = binder.getService();
        mipsFaceService.registPoseCallback(new MipsIDFaceProService.PoseCallBack() {
            @Override
            public void onPosedetected(final String flag, final int curFaceCnt, final int cntFaceDB, final mipsFaceInfoTrack[] faceInfo)
            {
                // TODO Auto-generated method stub
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if("pose".equals(flag))
                        {
                            int i;
                            for(int j=0; j<4;j++) {
                                if(faceView[j].idx == -1)
                                {
                                    continue;
                                }
                                int idx=faceView[j].idx;
                                if(idx > mipsFaceInfoTrack.MAX_FACE_CNT_ONEfRAME)
                                {
                                    disableFaceView(j);
                                    continue;
                                }
                                if(faceInfo[idx] == null)
                                {
                                    disableFaceView(j);
                                    continue;
                                }
                                if(faceInfo[idx].FaceTRrackID != faceView[j].trackID)
                                {
                                    disableFaceView(j);
                                    continue;
                                }
                            }
                            for(i=0; i< mipsFaceInfoTrack.MAX_FACE_CNT_ONEfRAME; i++)
                            {
                                if(faceInfo[i] ==null)
                                {
                                    continue;
                                }
                                //if((faceInfo[i].mBitmapFaceIR ==null&&mipsFaceService.mipsGetLivenessMode()==0) || faceInfo[i].mfaceFeature == null || faceInfo[i].mfaceFeature.mBitmapFace == null)
                                if(faceInfo[i].mfaceFeature == null || faceInfo[i].mfaceFeature.mBitmapFace == null)
                                {
                                    continue;
                                }
                                if(cb_istest.isChecked() == true && faceInfo[i].flgSetLiveness != 1)
                                {
                                    continue;
                                }
                                if(faceInfo[i].flgSetVIP > 0)
                                {
                                    int id=findFaceView(faceInfo[i].FaceTRrackID);
                                    if(id>=0)
                                    {
                                        continue;
                                    }
                                    id = getFreeFaceView();
                                    if(id < 0)
                                    {
                                        continue;
                                    }
                                    faceView[id].trackID = faceInfo[i].FaceTRrackID;
                                    faceView[id].vipID = faceInfo[i].FaceIdxDB;
                                    faceView[id].idx = i;
                                    faceView[id].name = faceInfo[i].name;
                                    //saveFaceBitmap(faceInfo[i].mBitmapFaceRGB,faceInfo[i].mBitmapFaceIR,faceInfo[i].flgLiveness);
                                    ET_idbase.setText(Integer.toString(midbase));
                                    if(faceInfo[i].mfaceFeature!=null && faceInfo[i].mfaceFeature.mBitmapFace!=null)
                                    {
                                        faceView[id].mcapture_photo.setImageBitmap(faceInfo[i].mfaceFeature.mBitmapFace);
                                    }
                                    if(faceView[id].vipID >= 0) {
                                        Bitmap sourceBitmapFace;
                                   
                                        sourceBitmapFace = BitmapFactory.decodeFile(Environment.getExternalStorageDirectory().getPath() + "/faceVIP/imageVIP" + '/' + (faceView[id].name) + ".jpg");
                                        if (sourceBitmapFace != null) {
                                            faceView[id].mvip_photo.setImageBitmap(sourceBitmapFace);
                                        }
                                    }
                                    //Log.d("yunboa","file to show , " + faceView[id].vipID);
                                }
                            }
                        }
                        System.currentTimeMillis();
                    }
                });
            }
        });
        refreshCamera();
    }
    @Override
    public void onServiceDisconnected(ComponentName name) {

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
    public void onResume() {
        super.onResume();
    }
    private void chooseBitmap(int requestCode) {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, requestCode);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) {
            return;
        }
        Uri imageUri = data.getData();
        if (requestCode == REQUEST_CODE_1) {
            strFileAdd = getRealFilePath(getBaseContext(),imageUri);//getPhotoPathFromContentUri(getBaseContext(),imageUri);
        }
        if(cb_pic_display.isChecked() == true) {
            //mipsFaceService.mipsSetSurface(mSurfaceHolderCamera);
        }
    }

    public  ArrayList<File> refreshFileList(String strPath) {
        ArrayList<File> filelist = new ArrayList<File>();
        File dir = new File(strPath);
        File[] files = dir.listFiles();

        if (files == null)
            return null;
        for (int i = 0; i < files.length; i++) {
            if(files[i].isDirectory() == true) {
                ArrayList<File> tmp=refreshFileList(strPath+files[i].getName()+"/");
                if(tmp != null) {
                    filelist.addAll(tmp);
                }
            }
            else
            {
                if (files[i].getName().toLowerCase().endsWith("jpg") || files[i].getName().toLowerCase().endsWith("JPG")) {
                    filelist.add(files[i]);
                }
            }
        }
        return filelist;
    }

    @Override
    public void onClick(View v) {
        float similarity;
        float liveness_threshold;
        float face_score;
        int track_cnt;
        int faceWidth;
        int faceWidthPic;
        float roll;
        float pitch;
        float YAW;
        int vip,attr,vipRefresh,attrRefresh,livenessfacewidth;
        int live_mode;

        String licPath=null;
        int ret ;
        switch (v.getId()){
            case R.id.bt_start:
                if(!isIniting) {
                    new Thread() {
                        @Override
                        public void run() {
                            super.run();
                            isIniting = true;
                            final int ret;
                            int w = 0, h = 0;
                            String licPath = null;
                            final float similarity;
                            final float liveness_threshold;
                            final float face_score;
                            final int track_cnt;
                            final int faceWidth;
                            final int faceWidthPic;
                            final float roll;
                            final float pitch;
                            final float YAW;
                            final int vip, attr, vipRefresh, attrRefresh, livenessfacewidth;
                            final int live_mode;
                            final int live_type;
                            final int live_cnt;

                            if (ET_licpath.getText().toString() != null) {
                                licPath = ET_licpath.getText().toString();
                            } else {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getApplicationContext(), "pls check the license file path is correct", Toast.LENGTH_SHORT).show();
                                    }
                                });

                                return;
                            }
                            if (mipsFaceService != null) {
                                if (ET_camera_w.getText().toString() != null && ET_camera_h.getText().toString() != null) {
                                    w = Integer.parseInt(ET_camera_w.getText().toString());
                                    h = Integer.parseInt(ET_camera_h.getText().toString());
                                }
                                if (mCameraSize == null) {
                                    camcnt = mipsFaceService.openCamera();
                                    if (camcnt == 0) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getApplicationContext(), "no camera detected", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                        return;
                                    }
                                    mCameraSize = mipsFaceService.mipsGetCameraSize();
                                }
                                //mipsFaceService.algorithm_version = algorithm_version;
                                if (cb_pic_display.isChecked() == true) {
                                    ret = mipsFaceService.startDetect(getApplicationContext(), licPath, w, h, mSurfaceHolderCamera, mSurfaceHolderCameraIR, camera_state, getAssets(), choose_alg);
                                } else {
                                    ret = mipsFaceService.startDetect(getApplicationContext(), licPath, w, h, null, null, camera_state, getAssets(), choose_alg);
                                }
                                if (ret >= 0) {
                                    similarity = mipsFaceService.mipsGetSimilarityThrehold(); // 
                                    face_score = mipsFaceService.mipsGetFaceScoreThrehold();
                                    track_cnt = mipsFaceService.mipsGetMaxFaceTrackCnt();
                                    roll = mipsFaceService.mipsGetRollAngle();
                                    pitch = mipsFaceService.mipsGetPitchAngle();
                                    YAW = mipsFaceService.mipsGetYawAngle();
                                    vip = mipsFaceService.mipsGetVipFaceVerifyState();
                                    attr = mipsFaceService.mipsGetFaceLivenessState();
                                    vipRefresh = mipsFaceService.mipsGetRefreshFaceVIPState();
                                    attrRefresh = mipsFaceService.mipsGetRefreshFaceLivenessState();
                                    liveness_threshold = mipsFaceService.mipsGetLivenessThresholdBinocular();
                                    faceWidth = mipsFaceService.mipsGetFaceWidthThrehold();
                                    faceWidthPic = mipsFaceService.mipsGetPicFaceWidthThrehold();
                                    cntVIP = mipsFaceService.mipsGetDbFaceCnt();
                                    livenessfacewidth = mipsFaceService.mipsGetLivenessFaceWidthThrehold();
                                    live_mode = mipsFaceService.mipsGetLivenessMode();
                                    //live_type = mipsFaceService.mipsGetLivenessType();
                                    //live_cnt = mipsFaceService.mipsGetLivenessDetectCnt();
                                    int left = mSurfaceviewCamera.getLeft();
                                    int right = mSurfaceviewCamera.getRight();
                                    int top = mSurfaceviewCamera.getTop();
                                    int bottom = mSurfaceviewCamera.getBottom();

                                    surface_left = left;
                                    surface_right = right;
                                    surface_top = top;
                                    surface_bottom = bottom;
                                    camera_w = w;
                                    camera_h = h;
                                    mfaceOverlay.setOverlayRect(left, right, top, bottom, w, h);
                                    mipsFaceService.mipsSetOverlay(mfaceOverlay);

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            bt_disp.setEnabled(true);
                                            bt_deletevip.setEnabled(true);
                                            bt_verifyPicSel.setEnabled(true);
                                            bt_verify.setEnabled(true);
                                            //bt_export.setEnabled(true);
                                            bt_batchVipAdd.setEnabled(true);
                                            bt_addface.setEnabled(true);
                                            bt_getfile.setEnabled(true);
                                            bt_get.setEnabled(true);
                                            bt_set.setEnabled(true);
                                            //bt_batchVipdelete.setEnabled(true);

                                            ET_liveness_facewidth.setText(Integer.toString(livenessfacewidth));
                                            ET_similarity.setText(Float.toString(similarity));
                                            ET_face_score.setText(Float.toString(face_score));
                                            ET_track_cnt.setText(Integer.toString(track_cnt));
                                            ET_roll.setText(Float.toString(roll));
                                            ET_pitch.setText(Float.toString(pitch));
                                            ET_YAW.setText(Float.toString(YAW));
                                            ET_LivenessThreshold.setText(Float.toString(liveness_threshold));
                                            ET_FaceWidth.setText(Integer.toString(faceWidth));
                                            ET_PicFaceWidth.setText(Integer.toString(faceWidthPic));
                                            ET_vipIDAdd.setText(Integer.toString(cntVIP));
                                            ET_live_mode.setText(Integer.toString(live_mode));

                                            strfacesavepath = ET_facesavepath.getText().toString();
                                            midbase = Integer.parseInt(ET_idbase.getText().toString());
                                            tv_AlgorithmInfo.setText(mipsFaceService.mipsGetAlgorithmInfo());
                                            if (vip == 1) {
                                                cb_vip.setChecked(true);
                                            } else {
                                                cb_vip.setChecked(false);
                                            }

                                            if (attr > 0) {
                                                cb_faceattr.setChecked(true);
                                                cb_faceattrYM.setChecked(true);
                                            } else {
                                                cb_faceattr.setChecked(false);
                                                cb_faceattrYM.setChecked(false);
                                            }

                                            if (vipRefresh == 1) {
                                                cb_refreshVIP.setChecked(true);
                                            } else {
                                                cb_refreshVIP.setChecked(false);
                                            }

                                            if (attrRefresh > 0) {
                                                cb_refreshattr.setChecked(true);
                                            } else {
                                                cb_refreshattr.setChecked(false);
                                            }

                                        }
                                    });


                                    final String ver = mipsFaceService.mipsGetVersion();
                                    if (camcnt == 1) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getApplicationContext(), "detect <single> camera " + ver, Toast.LENGTH_SHORT).show();
                                            }
                                        });

                                    } else if (camcnt == 2) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getApplicationContext(), "detect<Dual>cameras " + ver, Toast.LENGTH_SHORT).show();
                                            }
                                        });

                                    }
                                    setCameraState(camera_state);
                                } else {
                                    if (ret == -7) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getApplicationContext(), "Check the license file is correct", Toast.LENGTH_SHORT).show();
                                            }
                                        });

                                    } else if (ret == -1) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getApplicationContext(), "check the time of system and AI hardware licensing is correct,mSdktype:" + mipsFaceService.mSdktype, Toast.LENGTH_SHORT).show();
                                            }
                                        });

                                    } else {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getApplicationContext(), "SDK initializing failed:" + ret, Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                }
                            }
                            isIniting = false;
                        }
                    }.start();
                }
                else
                {
                    Toast.makeText(getApplicationContext(), "initializing,pls waiting......" , Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_get:
                similarity = mipsFaceService.mipsGetSimilarityThrehold();
                face_score= mipsFaceService.mipsGetFaceScoreThrehold();
                track_cnt= mipsFaceService.mipsGetMaxFaceTrackCnt();
                roll= mipsFaceService.mipsGetRollAngle();
                pitch= mipsFaceService.mipsGetPitchAngle();
                YAW= mipsFaceService.mipsGetYawAngle();
                vip = mipsFaceService.mipsGetVipFaceVerifyState();
                attr = mipsFaceService.mipsGetFaceLivenessState();
                vipRefresh = mipsFaceService.mipsGetRefreshFaceVIPState();
                attrRefresh = mipsFaceService.mipsGetRefreshFaceLivenessState();
                liveness_threshold = mipsFaceService.mipsGetLivenessThresholdBinocular();
                faceWidth = mipsFaceService.mipsGetFaceWidthThrehold();
                faceWidthPic = mipsFaceService.mipsGetPicFaceWidthThrehold();
                livenessfacewidth = mipsFaceService.mipsGetLivenessFaceWidthThrehold();
                live_mode=mipsFaceService.mipsGetLivenessMode();

                ET_liveness_facewidth.setText(Integer.toString(livenessfacewidth));
                ET_similarity.setText(Float.toString(similarity));
                ET_face_score.setText(Float.toString(face_score));
                ET_track_cnt.setText(Integer.toString(track_cnt));
                ET_roll.setText(Float.toString(roll));
                ET_pitch.setText(Float.toString(pitch));
                ET_YAW.setText(Float.toString(YAW));
                ET_LivenessThreshold.setText(Float.toString(liveness_threshold));
                ET_FaceWidth.setText(Integer.toString(faceWidth));
                ET_PicFaceWidth.setText(Integer.toString(faceWidthPic));
                ET_live_mode.setText(Integer.toString(live_mode));
                if(vip == 1)
                {
                    cb_vip.setChecked(true);
                }
                else
                {
                    cb_vip.setChecked(false);
                }

                if(attr > 0)
                {
                    cb_faceattr.setChecked(true);
                    cb_faceattrYM.setChecked(true);
                }
                else
                {
                    cb_faceattr.setChecked(false);
                    cb_faceattrYM.setChecked(false);
                }

                if(vipRefresh == 1)
                {
                    cb_refreshVIP.setChecked(true);
                }
                else
                {
                    cb_refreshVIP.setChecked(false);
                }

                if(attrRefresh > 0)
                {
                    cb_refreshattr.setChecked(true);
                }
                else
                {
                    cb_refreshattr.setChecked(false);
                }


                break;

            case R.id.bt_set:
                if(ET_similarity.getText().toString().isEmpty() == false)
                {
                    similarity = Float.parseFloat(ET_similarity.getText().toString());
                    mipsFaceService.mipsSetSimilarityThrehold(similarity);
                }

                if(ET_LivenessThreshold.getText().toString().isEmpty() == false)
                {
                    liveness_threshold = Float.parseFloat(ET_LivenessThreshold.getText().toString());
                    mipsFaceService.mipsSetLivenessThreholdBinocular(liveness_threshold);
                }

                if (ET_live_mode.getText().toString().isEmpty() == false) {
                    live_mode = Integer.parseInt(ET_live_mode.getText().toString());
                    mipsFaceService.mipsSetLivenessMode(live_mode);
                }

                if(ET_face_score.getText().toString().isEmpty() == false)
                {
                    face_score = Float.parseFloat(ET_face_score.getText().toString());
                    mipsFaceService.mipsSetFaceScoreThrehold(face_score);
                }

                if(ET_track_cnt.getText().toString().isEmpty() == false)
                {
                    track_cnt = Integer.parseInt(ET_track_cnt.getText().toString());
                    mipsFaceService.mipsSetMaxFaceTrackCnt(track_cnt);
                }
                if(ET_liveness_facewidth.getText().toString().isEmpty() == false)
                {
                    livenessfacewidth = Integer.parseInt(ET_liveness_facewidth.getText().toString());
                    mipsFaceService.mipsSetLivenessFaceWidthThrehold(livenessfacewidth);
                }
                if(ET_roll.getText().toString().isEmpty() == false)  // 
                {
                    roll = Float.parseFloat(ET_roll.getText().toString());
                    mipsFaceService.mipsSetRollAngle(roll);
                }

                if(ET_pitch.getText().toString().isEmpty() == false) //
                {
                    pitch = Float.parseFloat(ET_pitch.getText().toString());
                    mipsFaceService.mipsSetPitchAngle(pitch);
                }

                if(ET_YAW.getText().toString().isEmpty() == false)  //
                {
                    YAW = Float.parseFloat(ET_YAW.getText().toString());
                    mipsFaceService.mipsSetYawAngle(YAW);
                }

                if(ET_FaceWidth.getText().toString().isEmpty() == false)
                {
                    faceWidth = Integer.parseInt(ET_FaceWidth.getText().toString());
                    mipsFaceService.mipsSetFaceWidthThrehold(faceWidth);
                }
                if(ET_PicFaceWidth.getText().toString().isEmpty() == false)
                {
                    faceWidthPic = Integer.parseInt(ET_PicFaceWidth.getText().toString());
                    mipsFaceService.mipsSetPicFaceWidthThrehold(faceWidthPic);
                }
                if(cb_vip.isChecked() == true)
                {
                    mipsFaceService.mipsEnableVipFaceVerify();
                }
                else
                {
                    mipsFaceService.mipsDisableVipFaceVerify();
                }

                if(cb_faceattr.isChecked() == true)
                {
                    mipsFaceService.mipsEnableFaceLiveness(this);
                }
                else
                {
                    mipsFaceService.mipsDisableFaceLiveness();
                }

                if(cb_faceattrYM.isChecked() == true)
                {
                    mipsFaceService.mipsEnableFaceAttr(this);
                }
                else
                {
                    mipsFaceService.mipsDisableFaceAttr();
                }

                if(cb_refreshattr.isChecked() == true)
                {
                    mipsFaceService.mipsEnableRefreshFaceLiveness();
                }
                else
                {
                    mipsFaceService.mipsDisableRefreshFaceLiveness();
                }

                if(cb_refreshVIP.isChecked() == true)
                {
                    mipsFaceService.mipsEnableRefreshFaceVIP();
                }
                else
                {
                    mipsFaceService.mipsDisableRefreshFaceVIP();
                }

                break;
            case R.id.bt_getfile:
                chooseBitmap(REQUEST_CODE_1);
                if(strFileAdd != null)
                {
                    Toast.makeText(getApplicationContext(), "Select pic: "+strFileAdd, Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(getApplicationContext(), "no pic select", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_batchVipAdd:

                if(ET_vipImagePath.getText().toString() != null)
                {
                    vipImagePath = ET_vipImagePath.getText().toString();
                }
                if(mipsFaceService == null)
                {
                    Toast.makeText(getApplicationContext(), "pls click <START>", Toast.LENGTH_SHORT).show();
                }
                else {
                    if(ET_vipIDAdd.getText().toString().isEmpty() == true)
                    {
                        cntVIP = mipsFaceService.mipsGetDbFaceCnt();
                    }
                    else {
                        cntVIP=Integer.parseInt(ET_vipIDAdd.getText().toString());
                    }
                    thread1 = new Thread() {
                        @Override
                        public void run() {
                            int faceCntScan;
                            int ret;

                            ArrayList<File> filelist = refreshFileList(vipImagePath);
                            if(filelist == null || filelist.size()==0)
                            {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getApplicationContext(), "not pic file found", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            if(filelist != null) {
                                for (int i = 0; i < filelist.size(); i++) {
                                    ret = mipsFaceService.mipsAddVipFace(context, filelist.get(i).getAbsolutePath(), cntVIP, true);
                                    if (ret >= 0) {
                                        cntVIP++;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                ET_vipIDAdd.setText(Integer.toString(cntVIP));
                                            }
                                        });
                                    }
                                }
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), "bulk import complete", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    };
                }
                thread1.start();
                break;
            case R.id.bt_disp:
                mipsFaceService.mipsSetSurface(mSurfaceHolderCamera,mSurfaceHolderCameraIR);
                break;
            case R.id.bt_export:
                String info=MipsIDFaceProService.mipsGetDeviceInfo(getApplicationContext());
                String fileContent= FileUtil.readFileFromSDCard2(Environment.getExternalStorageDirectory().getPath()+"/deviceInfo.txt");
                if(fileContent != null) {
                    if (fileContent.contains(info)) {
                        Toast.makeText(getApplicationContext(), "device info exists ", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
                String filePath = Environment.getExternalStorageDirectory().getPath() + "/deviceInfo.txt";
                if(info == null)
                {
                    Toast.makeText(getApplicationContext(), "get device info failed ", Toast.LENGTH_SHORT).show();
                    break;
                }
                try {
                    File deviceFile = new File(filePath);
                    if (!deviceFile.exists())
                        deviceFile.createNewFile();

                    FileOutputStream fs = new FileOutputStream(filePath,true);
                    fs.write(info.getBytes(), 0, info.length());
                    fs.close();
                }
                catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "write file failed : "+filePath, Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
                Toast.makeText(getApplicationContext(), "export success: "+filePath, Toast.LENGTH_SHORT).show();
                break;
            case R.id.bt_addface:

                if(strFileAdd == null)
                {
                    Toast.makeText(getApplicationContext(), "no pic selected", Toast.LENGTH_SHORT).show();
                    break;
                }
                ret=-1;//mipsFaceService.mipsAddVipFace(this,strFileAdd);
                int cnt = 0;

/*                Bitmap bitmap = BitmapFactory.decodeFile(strFileAdd);
                mipsFaceFeature feature = null;
                if(bitmap != null) {
//                    feature = mipsFaceService.mipsGetFeature(bitmap);
                }

                if(feature != null && feature.mfaceFeature != null) {


                    if (ET_vipIDAdd.getText().toString().isEmpty() == true) {
                        cnt = mipsFaceService.mipsGetDbFaceCnt();
//                        ret = mipsFaceService.mipsAddVipFace(this, feature.mfaceFeature, cnt);
                    } else {
                        cnt = Integer.parseInt(ET_vipIDAdd.getText().toString());
//                        ret = mipsFaceService.mipsAddVipFace(this, feature.mfaceFeature, cnt);
                    }
                }*/

                if(ET_vipIDAdd.getText().toString().isEmpty() == true)
                {
                    cnt=mipsFaceService.mipsGetDbFaceCnt();
                    ret=mipsFaceService.mipsAddVipFace(this,strFileAdd,cnt,true);
                }
                else
                {
                    cnt = Integer.parseInt(ET_vipIDAdd.getText().toString());
                    ret=mipsFaceService.mipsAddVipFace(this,strFileAdd,cnt,true);
                }

                if(ret < 0)
                {
                    Toast.makeText(getApplicationContext(), "Add VIP failed ", Toast.LENGTH_SHORT).show();
                }
                else {
                    cnt++;
                    ET_vipIDAdd.setText(Integer.toString(cnt));
                    Toast.makeText(getApplicationContext(), "Add VIP success", Toast.LENGTH_SHORT).show();
                }
                strFileAdd = null;
                break;
            case R.id.bt_deletevip:
                if(ET_deletevip.getText().toString().isEmpty())
                {
                    Toast.makeText(getApplicationContext(), "insert VIP ID", Toast.LENGTH_SHORT).show();
                    break;
                }
                int id = Integer.parseInt(ET_deletevip.getText().toString());

                ret=mipsFaceService.mipsDeleteVipFace(this,id);
                if(ret < 0)
                {
                    Toast.makeText(getApplicationContext(), "delete VIP failed", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(getApplicationContext(), "delete VIP success", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_verifyPicSel:
                chooseBitmap(REQUEST_CODE_1);
                if(strFileAdd != null)
                {
                    Toast.makeText(getApplicationContext(), "select pic: "+strFileAdd, Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(getApplicationContext(), "no pic selected", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_verify:
                if(strFileAdd == null)
                {
                    Toast.makeText(getApplicationContext(), "no pic selected", Toast.LENGTH_SHORT).show();
                    break;
                }
                String[] image=new String[5];
                image[0] = strFileAdd;
                ret=mipsFaceService.mipsVerifyVipImage(image,1);
                if(ret < 0)
                {
                    Toast.makeText(getApplicationContext(), "Check VIP failed :" + ret, Toast.LENGTH_SHORT).show();
                    mipsFaceVerifyInfo verifyInfo = mipsFaceService.verifyVipImage( BitmapFactory.decodeFile(strFileAdd));

                    if(verifyInfo != null){
                        Log.d("yunboa", "faceCnt: " + verifyInfo.faceCnt + " , resultCode: " + verifyInfo.resultCode
                                    + " , threshold --> width:" +verifyInfo.PIC_FACE_WIDTH_THRESHOLD +" , score:" + verifyInfo.PIC_SCORE_THRESHOLD  + ", pitch:" + verifyInfo.PIC_PITCH_THRESHOLD
                                    + " , yaw:" + verifyInfo.PIC_YAW_THRESHOLD + " , roll:" + verifyInfo.PIC_ROLL_THRESHOLD + " , X:" + verifyInfo.PIC_RESOLUTION_X_THRESHOLD + " , Y:" + verifyInfo.PIC_RESOLUTION_Y_THRESHOLD);

                        if(verifyInfo.infoList != null){
                            for(int i=0 ; i< verifyInfo.infoList.size(); i++){
                                mipsFaceDetailedInfo info2 = verifyInfo.infoList.get(i);
                                if(info2 != null) {
                                    Log.d("yunboa", " face info -_> yaw: " + info2.mYaw + " , roll: " + info2.mRoll + " , pitch: " + info2.mPitch + " ,width: " + info2.mWidth + " ,score:" +info2.mScore);
                                }
                            }

                        }

                    }

                }
                else {
                    Toast.makeText(getApplicationContext(), "校验VIP成功", Toast.LENGTH_SHORT).show();
                }
                strFileAdd = null;

                /*float score = mipsFaceService.mipsCompareFeatureInfaceDb(getApplicationContext(),9,10);
                Log.d("yunboa","mipsCompareFeatureInfaceDb result :" + score);*/

                break;
            default:
                break;
        }
    }

    private void initData(int w, int h) {
        float similarity;
        float face_score;
        int track_cnt;
        float roll;
        float pitch;
        float YAW;
        int vip;
        int attr;
        int vipRefresh;
        int attrRefresh;
        float liveness_threshold;
        int faceWidth;
        int faceWidthPic;
        int livenessfacewidth;
        int live_mode;
        bt_disp.setEnabled(true);
        bt_deletevip.setEnabled(true);
        bt_verifyPicSel.setEnabled(true);
        bt_verify.setEnabled(true);
        //bt_export.setEnabled(true);
        bt_batchVipAdd.setEnabled(true);
        bt_addface.setEnabled(true);
        bt_getfile.setEnabled(true);
        bt_get.setEnabled(true);
        bt_set.setEnabled(true);

        int left = mSurfaceviewCamera.getLeft();
        int right = mSurfaceviewCamera.getRight();
        int top = mSurfaceviewCamera.getTop();
        int bottom = mSurfaceviewCamera.getBottom();

        surface_left = left;
        surface_right = right;
        surface_top = top;
        surface_bottom = bottom;
        camera_w = w;
        camera_h = h;

        mfaceOverlay.setOverlayRect(left, right, top, bottom, w, h);
        mipsFaceService.mipsSetOverlay(mfaceOverlay);

        similarity = mipsFaceService.mipsGetSimilarityThrehold(); // 
        face_score= mipsFaceService.mipsGetFaceScoreThrehold();
        track_cnt= mipsFaceService.mipsGetMaxFaceTrackCnt();
        roll= mipsFaceService.mipsGetRollAngle();
        pitch= mipsFaceService.mipsGetPitchAngle();
        YAW= mipsFaceService.mipsGetYawAngle();
        vip = mipsFaceService.mipsGetVipFaceVerifyState();
        attr = mipsFaceService.mipsGetFaceLivenessState();
        vipRefresh = mipsFaceService.mipsGetRefreshFaceVIPState();
        attrRefresh = mipsFaceService.mipsGetRefreshFaceLivenessState();
        liveness_threshold = mipsFaceService.mipsGetLivenessThresholdBinocular();
        faceWidth = mipsFaceService.mipsGetFaceWidthThrehold();
        faceWidthPic = mipsFaceService.mipsGetPicFaceWidthThrehold();
        cntVIP = mipsFaceService.mipsGetDbFaceCnt();
        livenessfacewidth = mipsFaceService.mipsGetLivenessFaceWidthThrehold();
        live_mode=mipsFaceService.mipsGetLivenessMode();

        ET_liveness_facewidth.setText(Integer.toString(livenessfacewidth));
        ET_similarity.setText(Float.toString(similarity));
        ET_face_score.setText(Float.toString(face_score));
        ET_track_cnt.setText(Integer.toString(track_cnt));
        ET_roll.setText(Float.toString(roll));
        ET_pitch.setText(Float.toString(pitch));
        ET_YAW.setText(Float.toString(YAW));
        ET_LivenessThreshold.setText(Float.toString(liveness_threshold));
        ET_FaceWidth.setText(Integer.toString(faceWidth));
        ET_PicFaceWidth.setText(Integer.toString(faceWidthPic));
        ET_vipIDAdd.setText(Integer.toString(cntVIP));
        ET_live_mode.setText(Integer.toString(live_mode));

        strfacesavepath = ET_facesavepath.getText().toString();
        midbase = Integer.parseInt(ET_idbase.getText().toString());

        if(vip == 1)
        {
            cb_vip.setChecked(true);
        }
        else
        {
            cb_vip.setChecked(false);
        }

        if(attr > 0)
        {
            cb_faceattr.setChecked(true);
            cb_faceattrYM.setChecked(true);
        }
        else
        {
            cb_faceattr.setChecked(false);
            cb_faceattrYM.setChecked(false);
        }

        if(vipRefresh == 1)
        {
            cb_refreshVIP.setChecked(true);
        }
        else
        {
            cb_refreshVIP.setChecked(false);
        }

        if(attrRefresh > 0)
        {
            cb_refreshattr.setChecked(true);
        }
        else
        {
            cb_refreshattr.setChecked(false);
        }

        String ver=mipsFaceService.mipsGetVersion();
        if(camcnt == 1)
        {
            Toast.makeText(getApplicationContext(), "detect <single> camera "+ver, Toast.LENGTH_SHORT).show();
        }
        else if(camcnt == 2)
        {
            Toast.makeText(getApplicationContext(), "detect<Dual>cameras"+ver, Toast.LENGTH_SHORT).show();
        }
        setCameraState(camera_state);
    }

    @Override
    public void onDestroy() {
        mipsFaceService.stopservice();
        unbindService(this);
        stopService(mIntent);
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPause = true;

        mipsFaceService.stopCamera();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPostResume() {

        super.onPostResume();
        if(isPause == true && mipsFaceService !=null) {
            isPause = false;
            int w,h;
            mipsFaceService.openCamera();
            if (ET_camera_w.getText().toString() != null && ET_camera_h.getText().toString() != null) {
                w = Integer.parseInt(ET_camera_w.getText().toString());
                h = Integer.parseInt(ET_camera_h.getText().toString());
            }else{
                w = cameraWidthDefault;
                h = cameraHeightDefault;
            }
            mipsFaceService.startCamera(w, h, mSurfaceHolderCamera, mSurfaceHolderCameraIR, camera_state);

        }
    }

    @Override
    public void onSucceed() {
        bt_addface.post(new Runnable() {
            @Override
            public void run() {
                int w = Integer.parseInt(ET_camera_w.getText().toString());
                int h = Integer.parseInt(ET_camera_h.getText().toString());
                initData(w, h);
            }
        });
    }

    @Override
    public void onFailed(int ret) {
        if(ret == -7) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "licensing for MAC?", Toast.LENGTH_SHORT).show();
                }
            });

        }
        else if(ret == -1) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "check the time of system and AI hardware licensing is correct,mSdktype:", Toast.LENGTH_SHORT).show();
                }
            });

        }
        else
        {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "SDK initializing failed:", Toast.LENGTH_SHORT).show();
                }
            });

        }
    }
}
