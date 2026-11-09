package com.hf.hp605pv;

import android.content.Intent;
import android.os.Bundle;

import com.hf.hp605pv.databinding.ActivityStartBinding;

public class StartActivity extends BaseActivity {

    ActivityStartBinding startBinding;

    @Override
    public void onBaseCreate(Bundle bundle) {
        startBinding = ActivityStartBinding.inflate(getLayoutInflater());
        setContentView(startBinding.getRoot());

        startBinding.vn.setText("version:"+getVersionName());
        startBinding.vc.setText("code:"+getVersionCode());

        TextToSpeechUtil.say(getApplicationContext(),getString(R.string.app_name)+" "+getString(R.string.welcome_to_));

        getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(StartActivity.this, MainActivity.class));
                finish();
            }
        },2000);
    }

}
