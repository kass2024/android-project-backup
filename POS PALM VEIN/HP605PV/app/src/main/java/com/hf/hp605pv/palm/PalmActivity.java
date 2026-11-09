package com.hf.hp605pv.palm;

import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;

import com.hf.hp605pv.R;
import com.hf.hp605pv.TextToSpeechUtil;
import com.hf.hp605pv.databinding.ActivityPalmBinding;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;
import com.xinran.device.JXPalmAPITool;

import java.util.List;

public class PalmActivity extends PalmBaseActivity {

    ActivityPalmBinding palmBinding;

    @Override
    public void onBaseCreate(Bundle bundle) {
        palmBinding = ActivityPalmBinding.inflate(getLayoutInflater());
        setContentView(palmBinding.getRoot());

        palmBinding.actionbar.backTitleStyle(getString(R.string.palm));
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        startPalm();
    }

    @Override
    public void onPause() {
        super.onPause();
        closeDevice();
    }

    @Override
    public void onCheckPermission(int result, UsbDevice usbDevice) {
        Log.e("openDevice","PalmUSBManagerListener onCheckPermission result " + result);
        if (0 == result) {
            openDevice(usbDevice);
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    palmBinding.logView.error("USB permission failed, ret" + result);
                }
            });
        }
    }

    @Override
    public void onUSBArrived(UsbDevice device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                palmBinding.logView.error("PalmUSBManagerListener usb-arrived, usb" + device.toString());
            }
        });
        closeDevice();
        tryGetUSBPermission();
    }

    @Override
    public void onUSBRemoved(UsbDevice device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                palmBinding.logView.error("PalmUSBManagerListener usb-removed, usb" + device.toString());
            }
        });
        closeDevice();
    }

    @Override
    public SurfaceView getSuraceView() {
        return palmBinding.surfaceView;
    }

    @Override
    public void onErrorMessage(String msg) {
        palmBinding.logView.error(msg);
    }

    @Override
    public void onSuccessMessage(String msg) {
        palmBinding.logView.notification(msg);
    }

    @Override
    public void onDeviceOpen() {
        List<User> userList = getUserDao().loadAll();
        JXPalmAPITool.getPalmInstance().dbClear();
        if(userList.size()!=0) {
            for(User u:userList) {
                JXPalmAPITool.getPalmInstance().dbAdd(u.name, 0, Base64.decode(u.feature, Base64.NO_WRAP));
            }
        }

        palmBinding.enroll.setEnabled(true);
        palmBinding.enroll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new XPopup.Builder(PalmActivity.this).asInputConfirm(getString(R.string.enroll), "", getString(R.string.input_user_name), new OnInputConfirmListener() {
                    @Override
                    public void onConfirm(String text) {
                        TextToSpeechUtil.say(PalmActivity.this,getString(R.string.show_me_your_hand));
                        enroll(text);
                    }
                }).show();
            }
        });

        palmBinding.userList.setEnabled(true);
        palmBinding.userList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PalmActivity.this,UserListActivity.class));
            }
        });
    }

    @Override
    public void onDeviceClose() {

    }
}
