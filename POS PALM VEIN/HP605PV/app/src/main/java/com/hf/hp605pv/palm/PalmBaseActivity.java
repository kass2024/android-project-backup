package com.hf.hp605pv.palm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.hf.hp605pv.BaseActivity;
import com.hf.hp605pv.R;
import com.hf.hp605pv.TextToSpeechUtil;
import com.xinran.device.JXPalmAPITool;
import com.xinran.device.JXPalmApiListener;
import com.xinran.device.JXPalmInstance;
import com.xinran.device.JXPalmUSBManager;
import com.xinran.device.JXPalmUSBManagerListener;

import java.io.DataOutputStream;
import java.util.Arrays;

public abstract class PalmBaseActivity extends BaseActivity implements JXPalmUSBManagerListener{

    public abstract SurfaceView getSuraceView();

    public abstract void onErrorMessage(String msg);

    public abstract void onSuccessMessage(String msg);

    public abstract void onDeviceOpen();

    public abstract void onDeviceClose();
    JXPalmUSBManager jxPalmUSBManager;

    private boolean bstart = false;

    private final static int IDENTIFY_THRESOLD = 650;

    private int[] palmRect = new int[8];

    private final static int ENROLL_CNT = 5;

    private String regId = "";

    public void startPalm(){
//        getSDKUSBPermission();

        usbOkAndSetup();
    }

    public void closeDevice() {
        if (bstart) {
            JXPalmAPITool.getPalmInstance().stopCapture();
            bstart = false;
            JXPalmAPITool.getPalmInstance().unInitialize();
            onDeviceClose();
        }
    }

    public void tryGetUSBPermission(){
        jxPalmUSBManager.initUSBPermission();
    }

    public static void getSDKUSBPermission() {
        String[] commands = new String[] {"chmod -R 777 /dev/bus/usb" };
        Process process = null;
        DataOutputStream dataOutputStream = null;
        try {

            process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());

            int length = commands.length;
            for (int i = 0; i < length; i++) {
                dataOutputStream.writeBytes(commands[i] + "\n");
            }
            dataOutputStream.writeBytes("exit\n");
            dataOutputStream.flush();
            process.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
                process.destroy();
            } catch (Exception e) {

            }
        }
    }

    private void usbOkAndSetup(){
        jxPalmUSBManager = new JXPalmUSBManager(getApplicationContext(),this);
        jxPalmUSBManager.registerUSBPermissionReceiver();

        tryGetUSBPermission();
    }

    public void openDevice(UsbDevice usbDevice) {
        int retVal = JXPalmAPITool.getPalmInstance().initialize(this,usbDevice);
        if (0 != retVal)
        {
            Log.e("openDevice","start capture failed, ret= " + retVal);
            // TODO: reset
            onErrorMessage("device open failed");
            return;
        }
        Log.e("openDevice", "device open success, ret=" + retVal);
        setColor(3);
        final JXPalmApiListener listener = new JXPalmApiListener() {
            @Override
            public void onCapture(int actionResult, byte[] palmImage) {
                if (0 == actionResult) {
                    final int width = JXPalmAPITool.getPalmInstance().getImageWidth();
                    final int height = JXPalmAPITool.getPalmInstance().getImageHeight();
                    Bitmap bitmapPalm = JXPalmAPITool.renderCroppedGreyScaleBitmap(palmImage, width, height);
                    doDraw(getSuraceView(), bitmapPalm);
                }
                else {
                    //失败不做处理
                }
            }

            @Override
            public void onException() {
                onErrorMessage("device exception");
            }

            @Override
            public void onMatch(int actionResult, byte[] verTemplate, byte[] image) {
                if (0 == actionResult)
                {
                    String[] idRet = new String[1];

                    int retVal = JXPalmAPITool.getPalmInstance().dbIdentify(verTemplate, idRet, false);

                    if (retVal >= IDENTIFY_THRESOLD) {
                        setColor(2);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                getWindow().getDecorView().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        setColor(3);
                                    }
                                },2000);
                                if(TextToSpeechUtil.say(PalmBaseActivity.this,"verify success, hello "+idRet[0])){
                                    onSuccessMessage("verify success, hello "+idRet[0]);
                                }
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(TextToSpeechUtil.say(PalmBaseActivity.this,"verify failed")){
                                    onErrorMessage("verify failed");
                                }
                            }
                        });
                    }
                }
                else
                {
                    // 不做处理
                }
            }

            @Override
            public void onEnroll(int actionResult, int times, byte[] verTemplate, byte[] regTemplate) {
                int retVal = 0;
                if (0 == actionResult)
                {
                    String[] idRet = new String[1];
                    retVal = JXPalmAPITool.getPalmInstance().dbIdentify(verTemplate, idRet, false);
                    if (retVal >= IDENTIFY_THRESOLD) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(TextToSpeechUtil.say(PalmBaseActivity.this,"enroll failed")){
                                    onErrorMessage(idRet[0] + " is already exist!");
                                }
                            }
                        });
                        JXPalmAPITool.getPalmInstance().cancelEnroll();
                        return;
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onSuccessMessage("need " + (ENROLL_CNT - times) + " more times capture");
                            }
                        });
                    }
                }
                else if (1 == actionResult)
                {
                    JXPalmAPITool.getPalmInstance().dbAdd(regId, 0, regTemplate);
                    if (0 == retVal)
                    {
                        String strFeature = Base64.encodeToString(regTemplate, Base64.NO_WRAP);
                        User user = User.create();
                        user.name = regId;
                        user.feature = strFeature;
                        getUserDao().insert(user);
                        int finalRetVal = retVal;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(TextToSpeechUtil.say(PalmBaseActivity.this,"enroll success")){
                                    onSuccessMessage("enroll success， retVal=" + finalRetVal);
                                }
                            }
                        });
                    }
                    else
                    {
                        int finalRetVal1 = retVal;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(TextToSpeechUtil.say(PalmBaseActivity.this,"enroll failed")){
                                    onErrorMessage("enroll failed, ret=" + finalRetVal1);
                                }
                            }
                        });
                    }
                }
                else
                {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(TextToSpeechUtil.say(PalmBaseActivity.this,"enroll failed")){
                                onErrorMessage("enroll failed, ret=" + actionResult);
                            }
                        }
                    });
                }
            }

            @Override
            public void onImageRegistered(int i, int i1, byte[] bytes, int[] ints) {

            }

            @Override
            public void onFeatureInfo(int actionResult, int imageQuality, int templateQuality, int[] rect) {
                if (0 == actionResult)
                {
                    System.arraycopy(rect, 0, palmRect, 0, rect.length);
                }
                else
                {
                    Arrays.fill(palmRect, 0x0);
                }
            }

            @Override
            public void onDistance(float v) {
            }
        };
        JXPalmAPITool.getPalmInstance().setPalmApiListener(listener);
        JXPalmAPITool.getPalmInstance().startCapture();
        bstart = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onDeviceOpen();
            }
        });
    }

    private void drawFaceRectCorner(Canvas canvas, int[] rect) {
        Paint mFacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFacePaint.setStrokeWidth(4);
        mFacePaint.setStyle(Paint.Style.STROKE);
        mFacePaint.setColor(getResources().getColor(R.color.white));

        canvas.drawLine(rect[0], rect[1], rect[2], rect[3], mFacePaint);
        canvas.drawLine(rect[2], rect[3], rect[4], rect[5], mFacePaint);
        canvas.drawLine(rect[4], rect[5], rect[6], rect[7], mFacePaint);
        canvas.drawLine(rect[6], rect[7], rect[0], rect[1], mFacePaint);
    }

    private void doDraw(SurfaceView surfaceView, Bitmap bitmap) {
        SurfaceHolder holder = surfaceView.getHolder();
        if (null == holder) {
            return;
        }
        Canvas canvas = holder.lockCanvas();
        if (null == canvas) {
            return;
        }
        Paint painter = new Paint();
        painter.setStyle(Paint.Style.FILL);
        painter.setAntiAlias(true);
        painter.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, 0, 0, painter);
        drawFaceRectCorner(canvas, palmRect);
        holder.unlockCanvasAndPost(canvas);
    }

    public void setColor(int color){
        byte[] data = new byte[1];
        data[0] = (byte)color;
        int[] size = {1};
        JXPalmInstance.setPalmParameter(2004, data, size);
    }

    public void enroll(String enrollName){
        regId = enrollName;
        JXPalmAPITool.getPalmInstance().startEnroll();
    }


    @Override
    public void onDestroy(){
        super.onDestroy();
        jxPalmUSBManager.unRegisterUSBPermissionReceiver();
    }
}
