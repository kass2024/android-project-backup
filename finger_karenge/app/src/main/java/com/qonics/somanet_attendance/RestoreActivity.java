package com.qonics.somanet_attendance;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class RestoreActivity extends AppCompatActivity {
    Button btnrestore, btnbrowse;
    RelativeLayout rlvaction;
    ProgressBar prb;
    BroadcastReceiver receiver;
    Uri filename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restore);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.e("DEVICE", "USB device connected");
                Toast.makeText(RestoreActivity.this, "USB device connected", Toast.LENGTH_LONG).show();
            }
        };
        populateView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter("android.hardware.usb.action.USB_DEVICE_ATTACHED"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    private void populateView() {
        btnbrowse = findViewById(R.id.btnbrowse);
        btnrestore = findViewById(R.id.btnrestore);
        rlvaction = findViewById(R.id.rlvaction);
        prb = findViewById(R.id.prb);
        prb.setVisibility(View.GONE);
        btnrestore.setEnabled(false);
        btnrestore.setOnClickListener(view -> {
            SweetAlertDialog sa = new SweetAlertDialog(this,SweetAlertDialog.WARNING_TYPE);
            sa.setTitle("");
            try {
                prb.setVisibility(View.VISIBLE);
                btnrestore.setVisibility(View.GONE);
                RestoreDatabase(filename);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(RestoreActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        btnbrowse.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
//            String[] mimeTypes = {"application/bkp","bkp"};
//            intent.putExtra(Intent.EXTRA_MIME_TYPES,mimeTypes);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            try {
                startActivityForResult(Intent.createChooser(intent, "Select backup to restore"), 1);
            }catch (ActivityNotFoundException ex){
                Toast.makeText(this, "Please install a File Manager.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void RestoreDatabase(Uri inFileName) throws IOException, Exception {
        boolean success = true;
        String dirname = "SOMANET_BACKUP";
//        String inFileName = "/data/data/com.qonics.somanet_attendance/databases/somanet_attendance_db";
//        File dbFile = new File(inFileName);
        InputStream fis = getContentResolver().openInputStream(inFileName);
        String outFileName = "/data/data/com.qonics.somanet_attendance/databases/somanet_attendance_db";

        // Open the empty db as the output stream
        OutputStream output = new FileOutputStream(outFileName);

        // Transfer bytes from the inputfile to the outputfile
        byte[] buffer = new byte[1024];
        int length;
        while ((length = fis.read(buffer)) > 0) {
            output.write(buffer, 0, length);
        }

        output.flush();
        output.close();
        fis.close();
        finish();
        Toast.makeText(RestoreActivity.this, "Restore completed", Toast.LENGTH_LONG).show();

    }

    private String getFileName(Uri uri) throws IllegalArgumentException {
        // Obtain a cursor with information regarding this uri
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);

        if (cursor.getCount() <= 0) {
            cursor.close();
            throw new IllegalArgumentException("Can't obtain file name, cursor is empty");
        }

        cursor.moveToFirst();

        String fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));

        cursor.close();

        return fileName;
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==1 && resultCode==RESULT_OK){
            //file chosen
            String ff = getFileName(data.getData());
            if (ff.startsWith("somanetdata-") && ff.endsWith(".db.bkp")) {
                filename = data.getData();
//                Log.e("FILE", filename);
                btnrestore.setEnabled(true);
            }else{
                Toast.makeText(this, "Please select a valid somanet backup file",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
