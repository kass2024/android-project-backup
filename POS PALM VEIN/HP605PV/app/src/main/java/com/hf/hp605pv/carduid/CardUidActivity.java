package com.hf.hp605pv.carduid;

import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Toast;

import com.hf.hp605pv.BaseActivity;
import com.hf.hp605pv.R;
import com.hf.hp605pv.databinding.ActivityCardUidBinding;
import com.hf.hp605pv.insertcard.InsertCardActivity;
import com.pos.sdk.result.ReIccCardInfo;
import com.xzy.pos.sdk.card.PosCardManager;
import com.xzy.poscardservice.IPosCardService;

public class CardUidActivity extends BaseActivity {

    ActivityCardUidBinding cardUidBinding;

    PosCardManager cardManager = new PosCardManager();

    public interface ICardUid {
        void result(String r);

        void failed();
    }

    @Override
    public void onBaseCreate(Bundle bundle) {
        cardUidBinding = ActivityCardUidBinding.inflate(getLayoutInflater());
        setContentView(cardUidBinding.getRoot());

        cardUidBinding.actionbar.backTitleStyle(getString(R.string.card_uid));

        cardUidBinding.read.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cardUidBinding.read.setEnabled(false);
                getCardUid(new ICardUid() {
                    @Override
                    public void result(String r) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                cardUidBinding.logView.notification(r);
                                cardUidBinding.read.setEnabled(true);
                            }
                        });
                    }

                    @Override
                    public void failed() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                cardUidBinding.read.setEnabled(true);
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void onPause(){
        super.onPause();
        stopDetect();
    }

    public static String Bytes2HexString(byte[] b) {
        String ret = "";
        for (int i = 0; i < b.length; ++i) {
            String hex = Integer.toHexString(b[i] & 255);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }

            ret = ret + hex.toUpperCase();
        }
        return ret;
    }


    private void getCardUid(ICardUid iCardUid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 500; i++) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                    }
                    ReIccCardInfo reIccInfo = null;
                    try {
                        reIccInfo = cardManager.Card_picc_detect_uid();
                    }catch (NullPointerException n){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                iCardUid.failed();
                                Toast.makeText(CardUidActivity.this, "device not support", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                        return;
                    }
                    if (reIccInfo.getRet() == 0) {
                        iCardUid.result(Bytes2HexString(reIccInfo.getUid()));
                        cardManager.Card_picc_field_off();
                        cardManager.Card_picc_close();
                        return;
                    }
                }
                iCardUid.failed();
                try {
                    cardManager.Card_picc_field_off();
                    cardManager.Card_picc_close();
                }catch (NullPointerException n){

                }
            }
        }).start();
    }

    public void stopDetect() {
        try {
            cardManager.Card_picc_field_off();
            cardManager.Card_picc_close();
        }catch (NullPointerException n){

        }
    }
}
