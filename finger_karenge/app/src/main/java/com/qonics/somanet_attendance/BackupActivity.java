package com.qonics.somanet_attendance;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.EnvironmentSDCardCheck;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class BackupActivity extends AppCompatActivity {
    Button btnbackup;
    Spinner spaction;
    RelativeLayout rlvaction;
    ProgressBar prb;
    BroadcastReceiver receiver;
    List<String> devices = new ArrayList();
    ArrayAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.e("DEVICE","USB device connected");
                Toast.makeText(BackupActivity.this,"USB device connected",Toast.LENGTH_LONG).show();
                getDetail();
            }
        };
        populateView();
//        checkSdCardPermission();
        getDetail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver,new IntentFilter("android.hardware.usb.action.USB_DEVICE_ATTACHED"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    private void populateView() {
        btnbackup = findViewById(R.id.btnbackup);
        spaction = findViewById(R.id.spaction);
        rlvaction = findViewById(R.id.rlvaction);
        prb = findViewById(R.id.prb);
        adapter = new ArrayAdapter(this,R.layout.support_simple_spinner_dropdown_item,devices);
        prb.setVisibility(View.GONE);
        spaction.setAdapter(adapter);
        btnbackup.setOnClickListener(view -> {
            try {
                prb.setVisibility(View.VISIBLE);
                btnbackup.setVisibility(View.GONE);
                BackupDatabase();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(BackupActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();
            }
        });
    }
    public void getDetail() {
        devices.clear();
        UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        String actionString = "com.qonics.somanet_attendance.action.USB_PERMISSION";

        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new
                Intent(actionString), 0);
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            mUsbManager.requestPermission(device, mPermissionIntent);
            String Model = device.getDeviceName();

            int DeviceID = device.getDeviceId();
            int Vendor = device.getVendorId();
            int Product = device.getProductId();
            int Class = device.getDeviceClass();
            int Subclass = device.getDeviceSubclass();
            devices.add("USB:"+Model+" "+device.getManufacturerName());
//            Log.e("DEVICE","MODEL: "+Model+device.getManufacturerName());
        }
        adapter.notifyDataSetChanged();
    }
    private boolean checkSdCardPermission() {
        boolean flag = false;
        try {
            EnvironmentSDCardCheck.Device[] devices = EnvironmentSDCardCheck.getStorage(BackupActivity.this);
            for (EnvironmentSDCardCheck.Device d : devices) {
                Log.e("DEVICE:",d.getUserLabel()+" | "+d.getType());
                if (d.getType().equals(EnvironmentSDCardCheck.TYPE_SD) || d.getType().contains(EnvironmentSDCardCheck.TYPE_UNKNOWN) || d.getType().contains(EnvironmentSDCardCheck.TYPE_USB)) {
                    flag = d.isAvailable();
                    if (flag)
                        break;
                }
            }
        } catch (Exception e) {

        }
        return flag;
    }


    public void BackupDatabase() throws IOException,Exception
    {
        boolean success =true;
        String dirname="SOMANET_BACKUP";
        File file = null;
        file = new File(Environment.getExternalStorageDirectory() +"/"+dirname);

        if (file.exists())
        {
            success =true;
        }
        else
        {
            success = file.mkdir();
        }

        if (success)
        {
            String inFileName = "/data/data/com.qonics.somanet_attendance/databases/somanet_attendance_db";
            File dbFile = new File(inFileName);
            FileInputStream fis = new FileInputStream(dbFile);
            Calendar c = Calendar.getInstance();
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

            String outFileName = Environment.getExternalStorageDirectory()+"/"+dirname+"/somanetdata-"+df.format(c.getTime())+".db.bkp";

            // Open the empty db as the output stream
            OutputStream output = new FileOutputStream(outFileName);

            // Transfer bytes from the inputfile to the outputfile
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer))>0) {
                output.write(buffer, 0, length);
            }

            output.flush();
            output.close();
            fis.close();
            finish();
            Toast.makeText(BackupActivity.this,"Backup completed",Toast.LENGTH_LONG).show();
        }else {
            prb.setVisibility(View.GONE);
            btnbackup.setVisibility(View.VISIBLE);
            throw new Exception("Failed to create directory");
        }
    }


}
