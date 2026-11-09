package com.hf.hp605pv;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.hf.hp605pv.palm.UserDao;

public abstract class BaseActivity extends AppCompatActivity {

    public abstract void onBaseCreate(Bundle bundle);

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        onBaseCreate(bundle);
    }

    public UserDao getUserDao(){
        return ((App)getApplication()).getUserDao();
    }

    public String getVersionName() {
        PackageInfo packageInfo = getPackageInfo();
        return packageInfo == null ? "" : packageInfo.versionName;
    }

    public int getVersionCode() {
        PackageInfo packageInfo = getPackageInfo();
        return packageInfo.versionCode;
    }

    private PackageInfo getPackageInfo() {
        try {
            PackageManager e = getPackageManager();
            return e.getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException var1) {
            var1.printStackTrace();
            return null;
        }
    }
}
