package com.hf.hp605pv.scan;

import android.os.Bundle;
import android.os.RemoteException;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.hf.hp605pv.BaseActivity;
import com.hf.hp605pv.R;
import com.hf.hp605pv.databinding.ActivityScanBinding;
import com.hf.hp605pv.insertcard.InsertCardActivity;
import com.xzy.pos.sdk.dev.PosHardScanManager;
import com.xzy.poshardscanservice.IScanResultListener;

public class HardScanActivity extends BaseActivity {
    public static final int CODE_SUCCESS = 0;
    public static final int CODE_TIMEOUT = 1;
    public static final int CODE_CANCEL = 9;
    public static final int CODE_GENERATE_LICENSE_FAIL = 10;
    ActivityScanBinding activityScanBinding;
    @Override
    public void onBaseCreate(Bundle bundle) {
        activityScanBinding = ActivityScanBinding.inflate(getLayoutInflater());
        setContentView(activityScanBinding.getRoot());

        activityScanBinding.actionbar.backTitleStyle(getString(R.string.scan));

        try {
            PosHardScanManager.setBeepFlag(true);
        } catch (RemoteException e) {
            Toast.makeText(HardScanActivity.this, "device not support", Toast.LENGTH_SHORT).show();
            finish();
            return;
        } catch (NullPointerException e){
            Toast.makeText(HardScanActivity.this, "device not support", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            PosHardScanManager.setInvertFlag(true);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        activityScanBinding.trigger.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    try {
                        PosHardScanManager.startScan(new IScanResultListener.Stub() {
                            @Override
                            public void onScanResult(int i, String s){
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        activityScanBinding.logView.notification(s);
                                    }
                                });
                            }
                        });
                    } catch (RemoteException e) {

                    }
                }else if(event.getAction() == MotionEvent.ACTION_UP){
                    try {
                        PosHardScanManager.endScan();
                    } catch (RemoteException e) {

                    }
                }
                return false;
            }
        });
    }
}
