package com.hf.hp605pv;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import com.hf.hp605pv.carduid.CardUidActivity;
import com.hf.hp605pv.databinding.ActivityMainBinding;
import com.hf.hp605pv.insertcard.InsertCardActivity;
import com.hf.hp605pv.palm.PalmActivity;
import com.hf.hp605pv.scan.HardScanActivity;
import com.hf.hp605pv.swipecard.SwipeCardActivity;

public class MainActivity extends BaseActivity {
    ActivityMainBinding mainBinding;
    @Override
    public void onBaseCreate(Bundle bundle) {
        mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mainBinding.getRoot());

        mainBinding.actionbar.backTitleStyle(getString(R.string.app_name));

        mainBinding.vn.setText("version:"+getVersionName());
        mainBinding.vc.setText("code:"+getVersionCode());

        mainBinding.model.setText(Build.DISPLAY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                mainBinding.serial.setText("SN:"+Build.getSerial());
            }catch (SecurityException e){
                mainBinding.serial.setText("SN:UNKNOWN");
            }
        }

        mainBinding.palm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PalmActivity.class));
            }
        });

        mainBinding.scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HardScanActivity.class));
            }
        });

        mainBinding.carduid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CardUidActivity.class));
            }
        });

        mainBinding.swipeCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SwipeCardActivity.class));
            }
        });

        mainBinding.insertCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, InsertCardActivity.class));
            }
        });
    }
}
