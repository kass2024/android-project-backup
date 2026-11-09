package com.qonics.somanet.prints;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Toast;

import com.qonics.Handler.Handler;
import com.qonics.somanet.R;

import java.io.File;

public class PrintDataActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener{

    // Debugging
    private static final String TAG = "Main_Activity";
    private static final boolean DEBUG = true;
    /******************************************************************************************************/
    // Message types sent from the BluetoothService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_CONNECTION_LOST = 6;
    public static final int MESSAGE_UNABLE_CONNECT = 7;
    /*******************************************************************************************************/
    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_CHOSE_BMP = 3;
    private static final int REQUEST_CAMER = 4;

    //QRcode
    private static final int QR_WIDTH = 350;
    private static final int QR_HEIGHT = 350;
    /*******************************************************************************************************/
    private static final String CHINESE = "GBK";

    //Name of the connected device
    private String mConnectedDeviceName = null;
    //Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    //Member object for the services
    private BluetoothServices mService = null;
    //End Bluetooth
    Button print;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print_data);

        print = findViewById(R.id.btnPrint);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        print.setOnClickListener(view ->{
            PrintIndividualPermission();
        });

    }

    private void PrintIndividualPermission() {
        Intent serverIntent = new Intent(getApplicationContext(), DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (DEBUG)
            Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE: {
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras().getString(
                            DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BluetoothDevice object
                    if (BluetoothAdapter.checkBluetoothAddress(address)) {
                        BluetoothDevice device = mBluetoothAdapter
                                .getRemoteDevice(String.valueOf(address));
                        // Attempt to connect to the device
                        mService.connect(device);
                    }
                }
                break;
            }
            case REQUEST_ENABLE_BT: {
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a session
                    KeyListenerInit();
                } else {
                    // User did not enable Bluetooth or an error occured
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getApplicationContext(), "not enable leaving",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the session
        } else {
            if (mService == null)
                KeyListenerInit();//监听
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if (mService != null) {

            if (mService.getState() == BluetoothServices.STATE_NONE) {
                // Start the Bluetooth services
                mService.start();
            }
        }
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if (DEBUG)
            Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if (DEBUG)
            Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth services
        if (mService != null)
            mService.stop();
        if (DEBUG)
            Log.e(TAG, "--- ON DESTROY ---");
    }
    /*
     * 打印图片
     */

    private void Print_BMP(){
        //File file = new File(sharedPreferences.getString("imagePath",""));
        File file = new File("imagePath");
        if(file.exists()){
//            //	byte[] buffer = PrinterCommand.POS_Set_PrtInit();
//            Bitmap bmImg = BitmapFactory.decodeFile(sharedPreferences.getString("imagePath",""));
//            Toast.makeText(this, sharedPreferences.getString("imagePath",""), Toast.LENGTH_SHORT).show();
            //imageViewPicture.setImageBitmap(bmImg);
            Bitmap mBitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.user)).getBitmap();
            int nMode = 0;
            int nPaperWidth = 184;
//        if(width_58mm.isChecked())
//            nPaperWidth = 384;
//        else if (width_80.isChecked())
//            nPaperWidth = 576;
            if(mBitmap != null)
            {
                /**
                 * Parameters:
                 * mBitmap  要打印的图片
                 * nWidth   打印宽度（58和80）
                 * nMode    打印模式
                 * Returns: byte[]
                 */
                byte[] data = PrintPicture.POS_PrintBMP(mBitmap, nPaperWidth, nMode);
                //	SendDataByte(buffer);
                SendDataByte(Command.ESC_Init);
                SendDataByte(Command.LF);
                SendDataByte(data);
                SendDataByte(PrinterCommand.POS_Set_PrtAndFeedPaper(30));
                SendDataByte(PrinterCommand.POS_Set_Cut(1));
                SendDataByte(PrinterCommand.POS_Set_PrtInit());
            }
        }else{
            Toast.makeText(getApplicationContext(), "No logo found", Toast.LENGTH_SHORT).show();
        }

    }


    private void Print_Receipt() {
        //String lang = getString(R.string.strLang);
        String schoolData = "\n"+"GROUPE SCOLAIRE DE APACE"+"\n"+
                "Kigali Rwanda"+"\n"+
                "Nyarugenge District"+" \n"+
                "CODE"+":4353003345";
        String msg = "\n\n"+"Payment Receipt"+"\n----------------\n";
        String data = "Name: "+ "Dally Jones TEST" +"\n"+
                "REGNO: "+ "RE43004443" +" \n"+
                "CLASS: "+ "S6 P"+" \n"+
                "TERM: Third term" +" \n"+
                "Paid Amount: 300000 Frw" +" \n"+
                " \n"+
                "\n"+
                "Printed By : IMANISHIMWE TEST "+" \n"+
                "Phone : 145898456"+" \n"+
                "------------------------------\n"+
                "Powered By: BDS Ltd"+"\n\n\n";
        SendDataByte(PrinterCommand.POS_Print_Text(schoolData, CHINESE, 1, 0, 0, 0));
        SendDataByte(PrinterCommand.POS_Print_Text(msg, CHINESE, 0, 1, 1, 0));
        SendDataByte(PrinterCommand.POS_Print_Text(data, CHINESE, 0, 0, 0, 0));
        SendDataByte(PrinterCommand.POS_Set_Cut(1));
        SendDataByte(PrinterCommand.POS_Set_PrtInit());
        finish();
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if (DEBUG)
                        Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothServices.STATE_CONNECTED:
                            Print_BMP();
                            Print_Receipt();//
                            break;
                        case BluetoothServices.STATE_CONNECTING:
                            Toast.makeText(getApplicationContext(), "Connecting", Toast.LENGTH_LONG).show();
                            break;
                        case BluetoothServices.STATE_LISTEN:
                        case BluetoothServices.STATE_NONE:

                            //Toast.makeText(Permission.this, R.string.title_not_connected, Toast.LENGTH_LONG).show();
                            break;
                    }
                    break;
                case MESSAGE_WRITE:

                    break;
                case MESSAGE_READ:

                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(),
                            "Connected to" + mConnectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(),
                                    msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
                            .show();
                    break;
                case MESSAGE_CONNECTION_LOST:    //蓝牙已断开连接
                    Toast.makeText(getApplicationContext(), "Lost connection",
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_UNABLE_CONNECT:     //无法连接设备
                    Toast.makeText(getApplicationContext(), "Unable to connect",
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void KeyListenerInit() {
        mService = new BluetoothServices(getApplicationContext(), mHandler);
    }
    /*
     *SendDataByte
     */
    private void SendDataByte(byte[] data) {

        if (mService.getState() != BluetoothServices.STATE_CONNECTED) {
            Toast.makeText(getApplicationContext(), "Not Connected", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        mService.write(data);
    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

}