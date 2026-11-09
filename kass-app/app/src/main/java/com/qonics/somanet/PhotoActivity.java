package com.qonics.somanet;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Auto-crops a captured student photo (face-aware + white ID background) and uploads it.
 * No manual crop UI — processing and save happen automatically.
 */
public class PhotoActivity extends AppCompatActivity {

    private ProgressBar prb;
    private Button btnretry;
    private ImageView preview;
    private TextView txtstatus;
    private Uri photoUri;
    private int studentId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean uploading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_photo);

        prb = findViewById(R.id.prb);
        btnretry = findViewById(R.id.btnsave);
        preview = findViewById(R.id.imgpreview);
        txtstatus = findViewById(R.id.txtstatus);

        studentId = getIntent().getIntExtra("student_id", 0);
        photoUri = getIntent().getParcelableExtra("photo_uri");

        btnretry.setVisibility(View.GONE);
        btnretry.setOnClickListener(v -> processAndUpload());

        if (photoUri == null || studentId < 1) {
            Toast.makeText(this, "No photo received", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        processAndUpload();
    }

    private void processAndUpload() {
        if (uploading) {
            return;
        }
        uploading = true;
        btnretry.setVisibility(View.GONE);
        prb.setVisibility(View.VISIBLE);
        txtstatus.setText(R.string.photo_processing);

        executor.execute(() -> {
            final Bitmap idPhoto = StudentPhotoHelper.decodeAndMakeIdPhoto(PhotoActivity.this, photoUri);
            mainHandler.post(() -> {
                if (isFinishing()) {
                    return;
                }
                if (idPhoto == null) {
                    uploading = false;
                    prb.setVisibility(View.GONE);
                    txtstatus.setText(R.string.photo_process_failed);
                    btnretry.setVisibility(View.VISIBLE);
                    btnretry.setText(R.string.retry);
                    return;
                }
                preview.setImageBitmap(idPhoto);
                txtstatus.setText(R.string.photo_uploading);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                idPhoto.compress(Bitmap.CompressFormat.JPEG, 92, baos);
                String encoded = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                uploadImage(encoded);
            });
        });
    }

    private void uploadImage(String photo) {
        HttpRequest req = new HttpRequest(this);
        Map<String, Object> map = new HashMap<>();
        map.put("photo", photo);
        map.put("student", studentId);

        req.progress(prb).post(MainActivity.SERVER + "save_student_photo", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                uploading = false;
                if (object == null) {
                    txtstatus.setText(status != null ? status : getString(R.string.server_error));
                    btnretry.setVisibility(View.VISIBLE);
                    btnretry.setText(R.string.retry);
                    Toast.makeText(PhotoActivity.this, status, Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    if (object.has("error")) {
                        txtstatus.setText(object.getString("error"));
                        btnretry.setVisibility(View.VISIBLE);
                        btnretry.setText(R.string.retry);
                        Toast.makeText(PhotoActivity.this,
                                "Error: " + object.getString("error"),
                                Toast.LENGTH_LONG).show();
                    } else if (object.has("success")) {
                        String filename = object.getString("success");
                        StudentPhotoHelper.updateLocalDb(PhotoActivity.this, studentId, filename);
                        StudentPhotoHelper.invalidate(filename);

                        Toast.makeText(PhotoActivity.this,
                                getString(R.string.photo_saved),
                                Toast.LENGTH_LONG).show();

                        Intent intent = new Intent();
                        intent.putExtra("photo", filename);
                        intent.putExtra("student_id", studentId);
                        setResult(RESULT_OK, intent);
                        finish();
                    } else {
                        txtstatus.setText(R.string.server_error);
                        btnretry.setVisibility(View.VISIBLE);
                        btnretry.setText(R.string.retry);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    btnretry.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
