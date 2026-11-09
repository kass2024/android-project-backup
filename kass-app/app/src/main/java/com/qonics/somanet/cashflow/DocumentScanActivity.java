package com.qonics.somanet.cashflow;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-screen A4 scanner. The camera stays inside the app so the phone camera app cannot cancel the shot.
 */
@SuppressWarnings("deprecation")
public class DocumentScanActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    public static String activeBridgeToken = "";

    private static final int REQ_CAMERA = 4102;
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_DOCUMENT_PATH = "document_path";
    public static final String MODE_STANDALONE = "standalone";
    public static final String MODE_BRIDGE = "bridge";

    private SharedPreferences preferences;
    private TextView txtStatus;
    private ImageView imgPreview;
    private CheckBox chkClean;
    private RadioButton rbJpeg;
    private Button btnUse;
    private SurfaceView preview;
    private SurfaceHolder holder;
    private A4ScanOverlay overlay;
    private View review;
    private View shutterWrap;

    private String mode = MODE_STANDALONE;
    private String bridgeToken = "";
    private Bitmap capturedBitmap;
    private Camera camera;
    private int cameraId = 0;
    private int pictureRotation;
    private boolean surfaceReady;
    private boolean opening;
    private boolean capturing;
    private boolean shotTaken;
    private boolean torchOn;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_document_scan);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        txtStatus = findViewById(R.id.txt_scan_status);
        imgPreview = findViewById(R.id.img_scan_preview);
        chkClean = findViewById(R.id.chk_clean_scan);
        rbJpeg = findViewById(R.id.rb_format_jpeg);
        btnUse = findViewById(R.id.btn_use_document);
        preview = findViewById(R.id.scan_preview);
        overlay = findViewById(R.id.scan_overlay);
        review = findViewById(R.id.scan_review);
        shutterWrap = findViewById(R.id.scan_shutter_wrap);
        holder = preview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        resolveModeAndToken(getIntent());

        findViewById(R.id.btn_capture).setOnClickListener(v -> capturePage());
        findViewById(R.id.btn_scan_close).setOnClickListener(v -> finish());
        findViewById(R.id.btn_scan_flash).setOnClickListener(v -> toggleTorch());
        findViewById(R.id.btn_scan_retake).setOnClickListener(v -> retake());
        btnUse.setOnClickListener(v -> useDocument());
        chkClean.setOnCheckedChangeListener((b, checked) -> refreshPreview());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (review.getVisibility() != View.VISIBLE) {
            ensureCamera();
        }
    }

    @Override
    protected void onPause() {
        releaseCamera();
        super.onPause();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (review.getVisibility() != View.VISIBLE) {
            ensureCamera();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceReady = true;
        if (camera != null && review.getVisibility() != View.VISIBLE) {
            try {
                camera.stopPreview();
            } catch (Exception ignored) {
            }
            startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CAMERA) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ensureCamera();
        } else {
            txtStatus.setText(R.string.cf_scan_allow);
        }
    }

    private void ensureCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            txtStatus.setText(R.string.cf_scan_allow);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        if (!surfaceReady || camera != null || review.getVisibility() == View.VISIBLE) return;
        openCamera();
    }

    private void openCamera() {
        if (camera != null || opening) return;
        opening = true;
        try {
            cameraId = backCameraId();
            camera = Camera.open(cameraId);
            pictureRotation = displayRotation(cameraId);
            applyPictureSettings();
            startPreview();
            txtStatus.setText(MODE_BRIDGE.equals(mode) ? R.string.cf_scan_web_waiting : R.string.cf_scan_hint);
        } catch (Exception e) {
            releaseCamera();
            txtStatus.setText(R.string.cf_scan_retry);
        } finally {
            opening = false;
        }
    }

    private void startPreview() {
        if (camera == null || !surfaceReady) return;
        try {
            camera.setPreviewDisplay(holder);
            camera.setDisplayOrientation(pictureRotation);
            camera.startPreview();
            try {
                camera.cancelAutoFocus();
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            txtStatus.setText(R.string.cf_scan_retry);
        }
    }

    private void applyPictureSettings() {
        if (camera == null) return;
        try {
            Camera.Parameters params = camera.getParameters();
            float viewAspect = previewAspect();
            Camera.Size previewSize = bestSize(params.getSupportedPreviewSizes(), 1280, viewAspect);
            Camera.Size pictureSize = bestSize(params.getSupportedPictureSizes(), 1920, viewAspect);
            if (previewSize != null) params.setPreviewSize(previewSize.width, previewSize.height);
            if (pictureSize != null) params.setPictureSize(pictureSize.width, pictureSize.height);
            List<String> focus = params.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            params.setJpegQuality(90);
            camera.setParameters(params);
        } catch (Exception ignored) {
        }
    }

    private void capturePage() {
        if (camera == null) {
            ensureCamera();
            return;
        }
        if (capturing) return;
        capturing = true;
        shotTaken = false;
        String focus = null;
        try {
            focus = camera.getParameters().getFocusMode();
        } catch (Exception ignored) {
        }
        if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(focus)) {
            takeShot();
            return;
        }
        try {
            camera.autoFocus((success, cam) -> takeShot());
        } catch (Exception e) {
            takeShot();
            return;
        }
        handler.postDelayed(this::takeShot, 280);
    }

    private void takeShot() {
        synchronized (this) {
            if (shotTaken || camera == null) return;
            shotTaken = true;
        }
        try {
            camera.takePicture(null, null, (data, cam) -> {
                Bitmap page = cropToA4(decodeUpright(data));
                runOnUiThread(() -> showPage(page));
            });
        } catch (Exception e) {
            capturing = false;
            shotTaken = false;
            txtStatus.setText(R.string.cf_scan_retry);
        }
    }

    private void showPage(Bitmap page) {
        capturing = false;
        if (page == null) {
            txtStatus.setText(R.string.cf_scan_error);
            retake();
            return;
        }
        if (capturedBitmap != null && capturedBitmap != page && !capturedBitmap.isRecycled()) {
            capturedBitmap.recycle();
        }
        capturedBitmap = page;
        review.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.GONE);
        shutterWrap.setVisibility(View.GONE);
        txtStatus.setVisibility(View.GONE);
        refreshPreview();
        releaseCamera();
    }

    private void retake() {
        review.setVisibility(View.GONE);
        overlay.setVisibility(View.VISIBLE);
        shutterWrap.setVisibility(View.VISIBLE);
        txtStatus.setVisibility(View.VISIBLE);
        imgPreview.setImageDrawable(null);
        if (capturedBitmap != null && !capturedBitmap.isRecycled()) {
            capturedBitmap.recycle();
            capturedBitmap = null;
        }
        capturing = false;
        shotTaken = false;
        ensureCamera();
    }

    private void toggleTorch() {
        if (camera == null) return;
        try {
            Camera.Parameters params = camera.getParameters();
            List<String> modes = params.getSupportedFlashModes();
            if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) return;
            torchOn = !torchOn;
            params.setFlashMode(torchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(params);
        } catch (Exception ignored) {
        }
    }

    private Bitmap decodeUpright(byte[] data) {
        if (data == null || data.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
        while (longEdge / sample > 2200) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, sample);
        Bitmap raw = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (raw == null) return null;
        if (pictureRotation == 0) return raw;
        Matrix matrix = new Matrix();
        matrix.postRotate(pictureRotation);
        Bitmap turned = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), matrix, true);
        if (turned != raw) raw.recycle();
        return turned;
    }

    /** Crop the on-screen A4 window out of the upright photo. */
    private Bitmap cropToA4(Bitmap upright) {
        if (upright == null) return null;
        RectF frame = overlay.getFrame();
        int viewW = overlay.getWidth();
        int viewH = overlay.getHeight();
        if (frame.width() < 20 || frame.height() < 20 || viewW < 2 || viewH < 2) return upright;
        int left = Math.round(frame.left / viewW * upright.getWidth());
        int top = Math.round(frame.top / viewH * upright.getHeight());
        int right = Math.round(frame.right / viewW * upright.getWidth());
        int bottom = Math.round(frame.bottom / viewH * upright.getHeight());
        left = Math.max(0, Math.min(left, upright.getWidth() - 1));
        top = Math.max(0, Math.min(top, upright.getHeight() - 1));
        right = Math.max(left + 1, Math.min(right, upright.getWidth()));
        bottom = Math.max(top + 1, Math.min(bottom, upright.getHeight()));
        Bitmap page = Bitmap.createBitmap(upright, left, top, right - left, bottom - top);
        if (page != upright) upright.recycle();
        return page;
    }

    private void releaseCamera() {
        if (camera == null) return;
        try {
            camera.stopPreview();
        } catch (Exception ignored) {
        }
        try {
            camera.release();
        } catch (Exception ignored) {
        }
        camera = null;
        torchOn = false;
    }

    private int backCameraId() {
        int count = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return 0;
    }

    private int displayRotation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        int degrees = 0;
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                break;
        }
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - ((info.orientation + degrees) % 360)) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private float previewAspect() {
        int w = preview.getWidth();
        int h = preview.getHeight();
        if (w < 2 || h < 2) return 297f / 210f;
        return h / (float) w;
    }

    /** Prefer a size that fills the portrait screen, near the target long edge. */
    private static Camera.Size bestSize(List<Camera.Size> sizes, int targetLong, float viewAspect) {
        if (sizes == null || sizes.isEmpty()) return null;
        Camera.Size best = sizes.get(0);
        float bestScore = Float.MAX_VALUE;
        for (Camera.Size size : sizes) {
            int edge = Math.max(size.width, size.height);
            float rotated = size.width / (float) Math.max(1, size.height);
            float score = Math.abs(rotated - viewAspect) * 1000f + Math.abs(edge - targetLong);
            if (edge > targetLong + 900) score += 5000f;
            if (score < bestScore) {
                bestScore = score;
                best = size;
            }
        }
        return best;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resolveModeAndToken(intent);
    }

    private void resolveModeAndToken(Intent intent) {
        mode = MODE_STANDALONE;
        bridgeToken = "";
        if (intent == null) {
            txtStatus.setText(R.string.cf_scan_hint);
            return;
        }
        String m = intent.getStringExtra(EXTRA_MODE);
        if (m != null && !m.isEmpty()) mode = m;

        String token = intent.getStringExtra(EXTRA_TOKEN);
        Uri data = intent.getData();
        if ((token == null || token.isEmpty()) && data != null) {
            token = data.getQueryParameter("token");
            if (token == null || token.isEmpty()) {
                String host = data.getHost();
                if ("amscan".equalsIgnoreCase(host) && data.getLastPathSegment() != null
                        && !"amscan".equalsIgnoreCase(data.getLastPathSegment())) {
                    token = data.getLastPathSegment();
                }
            }
            if (token != null && !token.isEmpty()) {
                mode = MODE_BRIDGE;
            }
        }
        if (token != null && !token.isEmpty()) {
            bridgeToken = token;
            activeBridgeToken = token;
            mode = MODE_BRIDGE;
            txtStatus.setText(R.string.cf_scan_web_waiting);
        } else {
            txtStatus.setText(R.string.cf_scan_hint);
        }
    }

    private void refreshPreview() {
        if (capturedBitmap == null) return;
        if (chkClean.isChecked()) {
            Bitmap cleaned = ScanDocumentHelper.cleanScan(capturedBitmap);
            imgPreview.setImageBitmap(cleaned != null ? cleaned : capturedBitmap);
        } else {
            imgPreview.setImageBitmap(capturedBitmap);
        }
    }

    private void useDocument() {
        if (capturedBitmap == null) {
            Toast.makeText(this, R.string.cf_scan_error, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean asPdf = !rbJpeg.isChecked();
        boolean clean = chkClean.isChecked();
        File outDir = getExternalFilesDir("Documents");
        if (outDir == null) outDir = getCacheDir();
        File out;
        try {
            String base = "cf_doc_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            out = ScanDocumentHelper.writeDocument(outDir, base, capturedBitmap, clean, asPdf);
        } catch (IOException e) {
            Toast.makeText(this, R.string.cf_scan_error, Toast.LENGTH_LONG).show();
            return;
        }

        if (MODE_BRIDGE.equals(mode) && bridgeToken != null && !bridgeToken.isEmpty()) {
            uploadToWebBridge(out);
            return;
        }

        final String path = out.getAbsolutePath();
        if (getCallingActivity() != null) {
            Intent result = new Intent();
            result.putExtra(EXTRA_DOCUMENT_PATH, path);
            setResult(RESULT_OK, result);
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setMessage(R.string.cf_scan_done_options)
                .setPositiveButton(R.string.cf_attach_to_request, (d, w) -> {
                    Intent i = new Intent(this, CashFlowRequestActivity.class);
                    i.putExtra(EXTRA_DOCUMENT_PATH, path);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton(R.string.cf_scan_done, (d, w) -> finish())
                .show();
    }

    private void uploadToWebBridge(File file) {
        int staffId = preferences.getInt("soma_id", 0);
        if (staffId <= 0) {
            Toast.makeText(this, R.string.cf_scan_error, Toast.LENGTH_LONG).show();
            return;
        }
        byte[] bytes;
        try {
            bytes = ScanDocumentHelper.fileToBytes(file);
        } catch (IOException e) {
            Toast.makeText(this, R.string.cf_scan_error, Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("staff_id", String.valueOf(staffId));
        map.put("token", bridgeToken);
        map.put("filename", file.getName());
        map.put("image_base64", ScanDocumentHelper.toBase64(bytes));

        new HttpRequest(this).post(MainActivity.SERVER + "cash_flow_scan_upload", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object != null && object.optBoolean("success", false)) {
                    Toast.makeText(DocumentScanActivity.this, R.string.cf_scan_sent_web, Toast.LENGTH_LONG).show();
                    activeBridgeToken = "";
                    finish();
                } else {
                    String err = object != null ? object.optString("error", status) : status;
                    Toast.makeText(DocumentScanActivity.this,
                            err != null && !err.isEmpty() ? err : getString(R.string.cf_scan_error),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseCamera();
        if (MODE_BRIDGE.equals(mode)) {
            activeBridgeToken = "";
        }
        if (capturedBitmap != null && !capturedBitmap.isRecycled()) {
            capturedBitmap.recycle();
            capturedBitmap = null;
        }
        super.onDestroy();
    }
}
