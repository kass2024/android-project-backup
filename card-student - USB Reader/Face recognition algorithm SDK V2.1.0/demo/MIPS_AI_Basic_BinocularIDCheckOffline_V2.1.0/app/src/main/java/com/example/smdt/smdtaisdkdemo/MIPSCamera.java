package com.example.smdt.smdtaisdkdemo;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * camera View
 */
public class MIPSCamera {

    private final static String TAG = "CameraView";
    private final static boolean DEBUG = true;
    public static int CameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private final static int SCALE_TYPE_4_3 = 1; 
    private final static int SCALE_TYPE_16_9 = 2; 

    private Camera mCamera; 
    private Matrix matrix = new Matrix(); 
    private PreviewCallback mPreviewCallback; 
    private Size mPreviewSize; 

    private float mPreviewScale; 
    private int mResolution; 
    private int mCameraFacing; 

    public int mPreviewWidth; 
    public int mPreviewHeight; 
    public int mDegrees; 
    public byte[] mBuffer; 
    public List<Size>msupportedPreviewSizes;
    public MIPSCamera() {

    }

    public void openCameraId(int cameraId) throws RuntimeException {
        releaseCamera();
        int n = Camera.getNumberOfCameras();
        if (n <= 0) {
            return;
        }
        if (cameraId < 0 || cameraId >= n) {
            cameraId = 0;
        }
        mCamera = Camera.open(cameraId);
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        mCameraFacing = info.facing;
        initParameters();
    }

    public void openCamera1(int mCameraFacing) throws RuntimeException {
        releaseCamera();
        //mPreviewWidth = width;
        //mPreviewHeight = height;
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == mCameraFacing) {
                mCamera = Camera.open(i); 
                break;
            }
        }
        initParameters();
        //initPreviewSize(width,height);
        //initPreviewBuffer();
    }

    public void openCamera(int mCameraFacing,int width, int height) throws RuntimeException {
        releaseCamera();
        mPreviewWidth = width;
        mPreviewHeight = height;
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == mCameraFacing) {
                mCamera = Camera.open(i); 
                break;
            }
        }
        initParameters();
        initPreviewSize(width,height);
        //initPreviewBuffer();
    }

    public void initParameters() {
        if (mCamera == null) {
            return;
        }
        try {
            Parameters parameters = mCamera.getParameters();
            List<String> supportedFlashModes = parameters.getSupportedFlashModes();
            if (supportedFlashModes != null && supportedFlashModes.contains(Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Parameters.FLASH_MODE_OFF); 
            }
            List<String> supportedFocusModes = parameters.getSupportedFocusModes();
            if (supportedFocusModes != null && supportedFocusModes.contains(Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO); 
            }
            //List<Integer> supportedPreviewFmts = parameters.getSupportedPreviewFormats();
            msupportedPreviewSizes = parameters.getSupportedPreviewSizes();
            parameters.setPreviewFormat(ImageFormat.NV21); 
            parameters.setPictureFormat(ImageFormat.JPEG); 
            mCamera.setParameters(parameters); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


 
    public void initPreviewSize(int width, int height) {
        if (mCamera == null) {
            return;
        }
        Parameters parameters = mCamera.getParameters();
        mPreviewSize = getFitPreviewSize(parameters,width,height); 
        mPreviewWidth = mPreviewSize.width;
        mPreviewHeight = mPreviewSize.height;
        parameters.setPreviewSize(mPreviewWidth, mPreviewHeight); 
        if (DEBUG) {
            Log.d(TAG, "initPreviewSize() mPreviewWidth: " + mPreviewWidth + ", mPreviewHeight: " + mPreviewHeight);
        }
        mCamera.setParameters(parameters);
    }


    public Size getFitPreviewSize(Parameters parameters,int width, int height) {
        List<Size> previewSizes = parameters.getSupportedPreviewSizes(); 
        int minDelta = Integer.MAX_VALUE; 
        int index = 0; 
        for (int i = 0; i < previewSizes.size(); i++) {
            Size previewSize = previewSizes.get(i);
            if (DEBUG) {
                Log.d(TAG, "SupportedPreviewSize, width: " + previewSize.width + ", height: " + previewSize.height);
            }
           
            if (previewSize.width  == width && previewSize.height == height) {
                int delta = Math.abs(mResolution - previewSize.height);
                if (delta == 0) {
                    return previewSize;
                }
                if (minDelta > delta) {
                    minDelta = delta;
                    index = i;
                }
            }
        }
        return previewSizes.get(index); 
    }

    public void initPreviewBuffer() {
        if (mCamera == null) {
            return;
        }
        for(int i=0; i<1; i++) {
            //byte[] buffer;
            mBuffer = new byte[mPreviewWidth * mPreviewHeight * 3 / 2]; 
            if (DEBUG) {
                Log.d(TAG, "initPreviewBuffer() mBuffer.length: " + mBuffer.length);
            }
            mCamera.addCallbackBuffer(mBuffer); 
        }
        mCamera.setPreviewCallbackWithBuffer(mPreviewCallback); 
    }


    public void setCameraDisplayOrientation(int rotation) {

        switch (rotation) {
            case Surface.ROTATION_0: // portrait
                mDegrees = 0;
                break;
            case Surface.ROTATION_90: // landscape
                mDegrees = 90;
                break;
            case Surface.ROTATION_180: // portrait-reverse
                mDegrees = 180;
                break;
            case Surface.ROTATION_270: // landscape-reverse
                mDegrees = 270;
                break;
            default:
                mDegrees = 0; 
                break;
        }
        if(mCamera!= null){
            mCamera.setDisplayOrientation(mDegrees);
            try {
                Camera.Parameters p = mCamera.getParameters();
                p.setRotation(mDegrees);
                mCamera.setParameters(p);
            } catch (Exception ignored) {
            }
        }
    }


    public void releaseCamera() {
        if (null != mCamera) {
            if (DEBUG) {
                Log.v(TAG, "releaseCamera()");
            }
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public void setPreviewCallback(PreviewCallback previewCallback) {
        mPreviewCallback = previewCallback;
    }

    public void addCallbackBuffer(byte[] data) {
        if (mCamera != null) {
            mCamera.addCallbackBuffer(mBuffer);
        }
    }

    public Camera getCamera() {
        return mCamera;
    }

    public List<Size> getSupportPreviewSize() {
        if (mCamera == null) {
            return null;
        }
        return mCamera.getParameters().getSupportedPreviewSizes();
    }

    public Matrix getMatrix() {
        return matrix;
    }

    public Size getPreviewSize() {
        return mPreviewSize;
    }

    public boolean isFrontCamera() {
        return mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    public void startPreview(SurfaceHolder holder) {
        if (mCamera != null) {
            if (DEBUG) {
                Log.d(TAG, "startPreview()");
            }

            if(holder != null) {
                try {
                    mCamera.setPreviewDisplay(holder);// set the surface to be used for live preview
                } catch (Exception ex) {
                    if (null != mCamera) {
                        mCamera.release();
                        mCamera = null;
                    }
                }
            }
            mCamera.startPreview();
        }
    }

    public void stopPreview() {
        if (mCamera != null) {
            if (DEBUG) {
                Log.d(TAG, "stopPreview()");
            }
            mCamera.stopPreview();
        }
    }

    public void setPreviewDisplay(SurfaceHolder holder) {
        if (mCamera != null) {
            if(holder != null) {
                try {
                    mCamera.setPreviewDisplay(holder);// set the surface to be used for live preview
                } catch (Exception ex) {
                    if (null != mCamera) {
                        mCamera.release();
                        mCamera = null;
                    }
                }
            }
        }
    }
}