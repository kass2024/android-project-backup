//package com.hf.hp605pv.scan;
//
//import android.os.Bundle;
//import android.os.IBinder;
//import android.os.RemoteException;
//import android.view.MotionEvent;
//import android.view.View;
//import android.widget.Toast;
//
//import com.hf.hp605pv.BaseActivity;
//import com.hf.hp605pv.R;
//import com.hf.hp605pv.databinding.ActivityScanBinding;
//import com.xzy.pos.sdk.dev.PosScanManager;
//import com.xzy.posscanservice.IScanResultListener;
//
//public class SoftScanActivity extends BaseActivity {
//
//    public static final int CODE_SUCCESS = 0;
//    public static final int CODE_TIMEOUT = 1;
//    public static final int CODE_CANCEL = 9;
//    public static final int CODE_GENERATE_LICENSE_FAIL = 10;
//    ActivityScanBinding scanBinding;
//
//    PosScanManager posScanManager = new PosScanManager();
//
//    @Override
//    public void onBaseCreate(Bundle bundle) {
//        scanBinding = ActivityScanBinding.inflate(getLayoutInflater());
//        setContentView(scanBinding.getRoot());
//
//        try {
//            if(!posScanManager.initScanService()){
//                Toast.makeText(SoftScanActivity.this,getString(R.string.init_failed),Toast.LENGTH_SHORT).show();
//                finish();
//            }
//        } catch (RemoteException e) {
//            Toast.makeText(SoftScanActivity.this,getString(R.string.init_failed),Toast.LENGTH_SHORT).show();
//            finish();
//        }
//        try {
//            posScanManager.setInvertFlag(false);
//        } catch (RemoteException e) {
//        }
//
//        scanBinding.actionbar.backTitleStyle(getString(R.string.scan));
//
//        scanBinding.trigger.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                if(event.getAction() == MotionEvent.ACTION_BUTTON_PRESS){
//                    try {
//                        posScanManager.startScan(50, new IScanResultListener() {
//                            @Override
//                            public IBinder asBinder() {
//                                return null;
//                            }
//
//                            @Override
//                            public void onScanResult(int i, String s) {
//                                String str = "";
//                                switch(i)
//                                {
//                                    case CODE_SUCCESS:
//                                        str = "Scan Result:" + s;
//                                        break;
//                                    case CODE_TIMEOUT:
//                                        str = "Scan time out";
//                                        break;
//                                    case CODE_CANCEL:
//                                        str = "Scan user canceled";
//                                        break;
//                                    case CODE_GENERATE_LICENSE_FAIL:
//                                        str = "Generate license fail";
//                                        break;
//                                }
//                                scanBinding.logView.append(str);
//                            }
//                        });
//                    } catch (RemoteException e) {
//
//                    }
//                }
//                return false;
//            }
//        });
//    }
//}
