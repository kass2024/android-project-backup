package com.example.smdt.smdtaisdkdemo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.example.smdt.smdtaisdkdemo.xander.DeviceUsb;

public class USBBroadcastReceiver extends BroadcastReceiver{
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())
                || "android.hardware.usb.action.USB_STATE".equals(intent.getAction())) {
            new DeviceUsb(context).requestMissing();
        }
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
            new DeviceUsb(context).requestMissing();
        }
    }
}
