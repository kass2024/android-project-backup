package com.example.smdt.smdtaisdkdemo.xander.facepass;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;

import java.util.List;

public class SimpleCamera {
    private static final String TAG = "XanderCam";
    private Camera camera;
    private int previewW;
    private int previewH;
    private Listener listener;
    private boolean front;

    public interface Listener {
        void onFrame(CameraPreviewData data);
    }

    public Camera getCamera() {
        return camera;
    }

    public int width() {
        return previewW;
    }

    public int height() {
        return previewH;
    }

    public boolean open(int cameraId, SurfaceHolder holder, int wantW, int wantH, int displayRotation, boolean front, Listener listener) {
        close();
        this.listener = listener;
        this.front = front;
        try {
            camera = Camera.open(cameraId);
        } catch (Exception e) {
            Log.w(TAG, "open " + cameraId + ": " + e.getMessage());
            return false;
        }
        try {
            Camera.Parameters p = camera.getParameters();
            Camera.Size size = pickSize(p.getSupportedPreviewSizes(), wantW, wantH);
            previewW = size.width;
            previewH = size.height;
            p.setPreviewSize(previewW, previewH);
            p.setPreviewFormat(ImageFormat.NV21);
            List<String> focus = p.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            camera.setParameters(p);
            camera.setDisplayOrientation(displayRotation);
            if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
                camera.setPreviewDisplay(holder);
            }
            PixelFormat info = new PixelFormat();
            PixelFormat.getPixelFormatInfo(ImageFormat.NV21, info);
            int frameBytes = previewW * previewH * info.bitsPerPixel / 8;
            for (int i = 0; i < 3; i++) {
                camera.addCallbackBuffer(new byte[frameBytes]);
            }
            camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera cam) {
                    if (SimpleCamera.this.listener != null && data != null) {
                        byte[] copy = new byte[data.length];
                        System.arraycopy(data, 0, copy, 0, data.length);
                        SimpleCamera.this.listener.onFrame(
                                new CameraPreviewData(copy, previewW, previewH, 0, SimpleCamera.this.front));
                    }
                    if (cam != null) {
                        cam.addCallbackBuffer(data);
                    }
                }
            });
            camera.startPreview();
            Log.i(TAG, "cam " + cameraId + " " + previewW + "x" + previewH + " rot=" + displayRotation);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "config " + cameraId + ": " + e.getMessage());
            close();
            return false;
        }
    }

    public void close() {
        if (camera == null) {
            return;
        }
        try {
            camera.setPreviewCallbackWithBuffer(null);
            camera.stopPreview();
        } catch (Exception ignored) {
        }
        try {
            camera.release();
        } catch (Exception ignored) {
        }
        camera = null;
    }

    private static Camera.Size pickSize(List<Camera.Size> sizes, int wantW, int wantH) {
        Camera.Size best = sizes.get(0);
        for (int i = 0; i < sizes.size(); i++) {
            Camera.Size s = sizes.get(i);
            if (s.width == wantW && s.height == wantH) {
                return s;
            }
            if (s.width * s.height > best.width * best.height) {
                best = s;
            }
        }
        return best;
    }
}
