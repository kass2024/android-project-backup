package com.qonics.somanet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.core.content.FileProvider;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Custom Camera activity for student photo capture.
 */
public class CameraExActivity extends Activity
        implements SurfaceHolder.Callback, View.OnClickListener, AutoFocusCallback {

    private SurfaceView mySurfaceView;
    private SurfaceHolder holder;
    private Camera myCamera;
    private byte[] imgdata = null;
    private boolean af;
    private boolean iscapture = false;
    private boolean isflashlight = false;
    private Button capture_top_hint;

    private int studentId = 0;
    private File photoFile;
    private Uri photoUri;
    /** "student" (default) → crop/upload; "document" → return JPEG path for Cash Flow Smart Scan */
    private String captureMode = "student";
    private int displayOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_captureex);

        mySurfaceView = findViewById(R.id.capture_preview_view);
        holder = mySurfaceView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        ImageView btnCancel = findViewById(R.id.imgclose);
        btnCancel.setOnClickListener(this);

        ImageView imageFlash = findViewById(R.id.imgflash);
        imageFlash.setOnClickListener(this);

        capture_top_hint = findViewById(R.id.vcapture);
        capture_top_hint.setOnClickListener(this);

        // Get student id
        studentId = getIntent().getIntExtra("id", 0);
        captureMode = getIntent().getStringExtra("mode");
        if (captureMode == null || captureMode.trim().isEmpty()) {
            captureMode = "student";
        }

        View faceGuide = findViewById(R.id.faceGuide);
        if (faceGuide != null && "document".equals(captureMode)) {
            faceGuide.setVisibility(View.GONE);
        }
        android.widget.TextView tip = findViewById(R.id.txtinfo);
        if (tip != null && "document".equals(captureMode)) {
            tip.setText(R.string.cf_scan_hint);
        } else if (tip != null) {
            tip.setText(R.string.visa_photo_hint);
        }

        // Prepare a safe file path in app’s Pictures folder
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir != null && !dir.exists()) dir.mkdirs();
        String name = "document".equals(captureMode)
                ? ("doc_scan_" + System.currentTimeMillis() + ".jpg")
                : "temp_photo.jpg";
        photoFile = new File(dir, name);
        photoUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                photoFile
        );
    }

    private static final int REQ_PHOTO = 100;

    private final PictureCallback jpeg = new PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            imgdata = data;
            savephoto();

            // Cash Flow / Smart Scan: return file path (same capture engine as student menu)
            if ("document".equals(captureMode)) {
                Intent result = new Intent();
                result.putExtra("photo_path", photoFile.getAbsolutePath());
                result.putExtra("photo_uri", photoUri);
                setResult(RESULT_OK, result);
                finish();
                return;
            }

            // Crop / upload, then forward result to StudentActivity
            Intent intent = new Intent(CameraExActivity.this, PhotoActivity.class);
            intent.putExtra("student_id", studentId);
            intent.putExtra("photo_uri", photoUri);
            startActivityForResult(intent, REQ_PHOTO);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PHOTO) {
            setResult(resultCode, data);
            finish();
        }
    }

    private void savephoto() {
        if (imgdata == null) return;

        try {
            Matrix matrix = new Matrix();
            Bitmap bm = BitmapFactory.decodeByteArray(imgdata, 0, imgdata.length);

            Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(0, info);

            int degrees = 0;
            switch (display.getRotation()) {
                case Surface.ROTATION_0:   degrees = 0; break;
                case Surface.ROTATION_90:  degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }

            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;
            } else {
                result = (info.orientation - degrees + 360) % 360;
            }
            matrix.preRotate(result);

            Bitmap nbm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);

            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(photoFile));
            nbm.compress(Bitmap.CompressFormat.JPEG, 80, bos);
            bos.flush();
            bos.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        // autofocus done, picture taken in click event
    }

    @Override
    public void onClick(View v) {
        int id = v.getId(); // ✅ assign to variable (avoids import issue)
        if (id == R.id.vcapture) {
            myCamera.autoFocus(this);
            if (!iscapture) {
                iscapture = true;
                myCamera.takePicture(null, null, jpeg);
            }
        } else if (id == R.id.imgclose) {
            finish();
        } else if (id == R.id.imgflash) {
            if (myCamera != null) {
                Camera.Parameters parameter = myCamera.getParameters();
                if (isflashlight) {
                    parameter.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    isflashlight = false;
                } else {
                    parameter.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    isflashlight = true;
                }
                myCamera.setParameters(parameter);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!iscapture && myCamera != null) {
            if (event.getY() > 160.0 && event.getY() < 800.0) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    myCamera.autoFocus(this);
                    af = true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP && af) {
                    iscapture = true;
                    myCamera.takePicture(null, null, jpeg);
                    af = false;
                }
            }
        }
        return true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (myCamera == null) return;
        View parent = (View) mySurfaceView.getParent();
        int parentW = parent != null ? parent.getWidth() : width;
        int parentH = parent != null ? parent.getHeight() : height;
        try {
            Camera.Parameters params = myCamera.getParameters();
            Camera.Size preview = chooseSize(params.getSupportedPreviewSizes(), parentW, parentH);
            if (preview != null) {
                params.setPreviewSize(preview.width, preview.height);
                Camera.Size picture = chooseSize(params.getSupportedPictureSizes(), parentW, parentH);
                if (picture != null) {
                    params.setPictureSize(picture.width, picture.height);
                }
                params.setPictureFormat(PixelFormat.JPEG);
                myCamera.setParameters(params);
                boolean swap = displayOrientation == 90 || displayOrientation == 270;
                int dispW = swap ? preview.height : preview.width;
                int dispH = swap ? preview.width : preview.height;
                if (parentW > 2 && parentH > 2 && dispW > 0 && dispH > 0) {
                    float scale = Math.min(parentW / (float) dispW, parentH / (float) dispH);
                    int targetW = Math.max(2, Math.round(dispW * scale));
                    int targetH = Math.max(2, Math.round(dispH * scale));
                    if (Math.abs(mySurfaceView.getWidth() - targetW) > 8
                            || Math.abs(mySurfaceView.getHeight() - targetH) > 8) {
                        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(targetW, targetH);
                        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
                        mySurfaceView.setLayoutParams(lp);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            myCamera.stopPreview();
        } catch (Exception ignored) {
        }
        try {
            myCamera.setPreviewDisplay(holder);
            myCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Pick a camera size whose on-screen aspect matches the view, so the picture is not stretched. */
    private Camera.Size chooseSize(List<Camera.Size> sizes, int viewW, int viewH) {
        if (sizes == null || sizes.isEmpty() || viewW < 2 || viewH < 2) {
            return sizes == null || sizes.isEmpty() ? null : sizes.get(0);
        }
        boolean swap = displayOrientation == 90 || displayOrientation == 270;
        double target = (double) viewW / (double) viewH;
        Camera.Size best = sizes.get(0);
        double bestScore = Double.MAX_VALUE;
        for (Camera.Size size : sizes) {
            double dispW = swap ? size.height : size.width;
            double dispH = swap ? size.width : size.height;
            double aspect = dispH == 0 ? 1 : dispW / dispH;
            double score = Math.abs(aspect - target) * 1000.0
                    - Math.min(size.width * (double) size.height, 12000000d) / 1000000d;
            if (score < bestScore) {
                bestScore = score;
                best = size;
            }
        }
        return best;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (myCamera == null) {
            myCamera = Camera.open();
            Camera.Parameters params = myCamera.getParameters();
            try {
                params.setPictureFormat(PixelFormat.JPEG);
                myCamera.setParameters(params);

                Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(0, info);

                int degrees = 0;
                switch (display.getRotation()) {
                    case Surface.ROTATION_0:   degrees = 0; break;
                    case Surface.ROTATION_90:  degrees = 90; break;
                    case Surface.ROTATION_180: degrees = 180; break;
                    case Surface.ROTATION_270: degrees = 270; break;
                }

                int result;
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    result = (info.orientation + degrees) % 360;
                    result = (360 - result) % 360;
                } else {
                    result = (info.orientation - degrees + 360) % 360;
                }
                displayOrientation = result;
                myCamera.setDisplayOrientation(result);

                myCamera.setPreviewDisplay(holder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (myCamera != null) {
            myCamera.stopPreview();
            myCamera.release();
            myCamera = null;
        }
    }
}
