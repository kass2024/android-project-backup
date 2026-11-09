package com.hf.hp605pv.swipecard;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.hf.hp605pv.R;
import com.hf.hp605pv.databinding.ActivitySwipeCardBinding;
import com.pos.sdk.result.ReData;

import com.hf.hp605pv.BaseActivity;
import com.xzy.pos.emvkernel.define.InputPBOCInitData;
import com.xzy.pos.emvkernel.define.InputPBOCOnlineData;
import com.xzy.pos.emvkernel.define.OutputCardInfoData;
import com.xzy.pos.emvkernel.define.OutputMagCardInfo;
import com.xzy.pos.emvkernel.define.OutputQPBOCResult;
import com.xzy.pos.emvkernel.paypass.callback.PayPassTags;
import com.xzy.pos.emvkernel.pboc.CardReader;
import com.xzy.pos.emvkernel.pboc.OnPBOCListener;
import com.xzy.pos.emvkernel.pboc.PBOCManager;
import com.xzy.pos.emvkernel.pboc.TransType;
import com.xzy.pos.emvkernel.utils.ByteFuncUtil;
import com.xzy.pos.sdk.security.AlgorithmType;
import com.xzy.pos.sdk.security.KeyType;
import com.xzy.pos.sdk.security.PinPadManager;
import com.xzy.pos.sdk.security.PosSecurityManager;

import java.util.List;

public class SwipeCardActivity extends BaseActivity implements OnPBOCListener {

    private String defProtectKey = "11111111111111111111111111111111";
    //    private String mainKey_cipher = "950973182317F80B950973182317F80B";
    private String mainKey_plaintext = "22222222222222222222222222222222";
//    private String mainKey_kcv = "00962B60AA556E65";

    //    private String defMacKey = "083F38779D5F384C083F38779D5F384C";
    private String defMacKey_plaintext = "44444444444444444444444444444444";
    //    private String defMacKeyKcv = "E2F2434039AA34AC";
//    private String defPinKey = "DF801B5B86D24F71DF801B5B86D24F71";
//    private String defPinKeyKcv = "ADC67D8473BF2F06";
    private String defPinKey_plaintext = "33333333333333333333333333333333";
    //    private String defTDKey = "4856617624D761524856617624D76152";
//    private String defTDKeyKcv = "0CD7DC4903A8B88B";
//    private String defTDKey_plaintext = "55555555555555555555555555555555";
    private byte tmkindex = 1;
    private byte tpkindex = 1;
    private byte takindex = 1;
    private byte tdkindex = 1;

    int carType = 0;

    ActivitySwipeCardBinding swipeCardBinding;

    PosSecurityManager securityManager;

    PBOCManager pbocManager;

    @Override
    public void onBaseCreate(Bundle bundle) {
        swipeCardBinding = ActivitySwipeCardBinding.inflate(getLayoutInflater());
        setContentView(swipeCardBinding.getRoot());

        swipeCardBinding.actionbar.backTitleStyle(getString(R.string.swipe_card));

        securityManager = new PosSecurityManager();
        try {
            pbocManager = new PBOCManager(this);
        } catch (UnsatisfiedLinkError error){
            Toast.makeText(SwipeCardActivity.this,"device not support",Toast.LENGTH_SHORT).show();
            finish();
            return;
        } catch (NoClassDefFoundError error){
            Toast.makeText(SwipeCardActivity.this,"device not support",Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadProtectKey();
        loadMainKey();
        loadMacKey();
        loadPinKey();
        loadTDKey();

//        swipeCardBinding.tdea.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
////                Bundle bundle = new Bundle();
////                bundle.putByte("algorithmType", (byte) 0);
////                bundle.putSerializable("transactionType", TransType.TT_PURCHASE);
////                getSupportFragmentManager()
////                        .beginTransaction()
////                        .add(R.id.container, InputAmountFragment.newInstance(bundle))
////                        .commit();
//                start(TransType.TT_PURCHASE);
//            }
//        });
//
//        swipeCardBinding.aes.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
////                Bundle bundle = new Bundle();
////                bundle.putByte("algorithmType", (byte) 1);
////                bundle.putSerializable("transactionType", TransType.TT_PURCHASE);
////                getSupportFragmentManager()
////                        .beginTransaction()
////                        .add(R.id.container, InputAmountFragment.newInstance(bundle))
////                        .commit();
//                start(TransType.TT_PURCHASE);
//            }
//        });

        swipeCardBinding.start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Bundle bundle = new Bundle();
//                bundle.putByte("algorithmType", (byte) 2);
//                bundle.putSerializable("transactionType", TransType.TT_PURCHASE);
//                getSupportFragmentManager()
//                        .beginTransaction()
//                        .add(R.id.container, InputAmountFragment.newInstance(bundle))
//                        .commit();
                swipeCardBinding.start.setEnabled(false);
                start(TransType.TT_PURCHASE);
            }
        });
    }

    public void start(TransType transType){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // pbocManager.DelAid();//todo ,test by use default aid, if load ,don't call it
                    // pbocManager.updateAID()
                    pbocManager.DelAid();
                    pbocManager.DelCapk();
                    //  pbocManager.enableContinueRead();
        /*            for (String rid:
                            VisaCDETParam.ca_data) {
                        pbocManager.updateRID(0, rid);
                    }
                    for (String aid:PayPassParam.aid_data){
                        pbocManager.loadCustomKernelContactlessAID(KernelType.master_kernel, aid);
                    }*/

                    for (String aid: AmexParam.aid_data
                    ) {
                        pbocManager.loadContactlessAID(0,aid);
                        pbocManager.loadContactAID(0,aid);
                    }

                    int amount = 100;

                    runOnUiThread(() -> Toast.makeText(SwipeCardActivity.this, "transType:"+transType.name(),Toast.LENGTH_LONG).show());
     /*               PosSystemManager systemManager = new PosSystemManager();
                    systemManager.setSystemStatusBarType(
                            PosSystemManager.DISABLE_NONE);*/


                    if(transType == TransType.TT_PURCHASE_WITH_CASHBACK){
                        InputPBOCInitData initData = new InputPBOCInitData();
                        initData.getIntent().putExtra(InputPBOCInitData.USE_DEVICE_FLAG, InputPBOCInitData.USE_MAG_CARD | InputPBOCInitData.USE_RF_CARD | InputPBOCInitData.USE_IC_CARD);
                        initData.setAmount(amount);
                        initData.setOtherAmount(1); // cashBack Amount
                        initData.setForceOnlinePin(true);
                        initData.getIntent().putExtra(InputPBOCInitData.TIMEOUT, 130);
                        pbocManager.startTransfer(transType, initData.getIntent(), SwipeCardActivity.this);

                    }else{
                        InputPBOCInitData initData = new InputPBOCInitData();
                        initData.getIntent().putExtra(InputPBOCInitData.USE_DEVICE_FLAG, InputPBOCInitData.USE_MAG_CARD | InputPBOCInitData.USE_RF_CARD | InputPBOCInitData.USE_IC_CARD);
                        initData.setAmount(0);
                        initData.setCanAmountZero(true);
                        initData.getIntent().putExtra(InputPBOCInitData.TIMEOUT, 130);
                        pbocManager.startTransfer(transType, initData.getIntent(), SwipeCardActivity.this);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            PBOCManager.stopTransfer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadProtectKey() {
        int ret = 0;
        try {
            ret = PinPadManager.tdesLoadProtectKey(ByteFuncUtil.HexToByteArr(defProtectKey));
        } catch (RemoteException e) {

        }
        if (ret == 1) {
            Log.e("loadKey", "load protect Key Success!");
        } else {
            Log.e("loadKey", "error");
        }
    }

    public void loadMainKey() {
        try {
            //Demo main key

            int iRet = PinPadManager.tdesLoadMainKey(tmkindex, ByteFuncUtil.HexToByteArr(mainKey_plaintext), new byte[0], (byte) 0);
            if (iRet == 1) {
                Log.e("loadKey", "load Main Key Success!");

            } else {
                Log.e("loadKey", "error");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadMacKey() {
        try {
            int iRet = PinPadManager.tdesLoadWorkKey(tmkindex, KeyType.TAK_TYPE, AlgorithmType.DES,
                    takindex, ByteFuncUtil.HexToByteArr(defMacKey_plaintext), new byte[0]);
            ReData reData = PinPadManager.tdesGetKcv(KeyType.TAK_TYPE, takindex);
            if (iRet == 1) {
                Log.e("loadKey", "load mac Key Success!");
            } else {
                Log.e("loadKey", "error");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadPinKey() {
        try {
            int iRet = PinPadManager.tdesLoadWorkKey(tmkindex, KeyType.TPK_TYPE, AlgorithmType.DES,
                    tpkindex, ByteFuncUtil.HexToByteArr(defPinKey_plaintext), new byte[0]);

            ReData reData = PinPadManager.tdesGetKcv(KeyType.TPK_TYPE, tpkindex);
            if (iRet == 1) {
                Log.e("loadKey", "load pin Key Success!");
            } else {
                Log.e("loadKey", "load pin Key error");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadTDKey() {
        try {
            int iRet = PinPadManager.tdesLoadWorkKey(tmkindex, KeyType.TDK_TYPE, AlgorithmType.DES,
                    tdkindex, ByteFuncUtil.HexToByteArr(defPinKey_plaintext), new byte[0]);
            ReData reData = PinPadManager.tdesGetKcv(KeyType.TDK_TYPE, tdkindex);
            if (iRet == 1) {
                Log.e("loadKey", "load td Key Success!");
            } else {
                Log.e("loadKey", "load td Key error");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("loadKey", "loadTDKey error !");
        }
    }

    @Override
    public void onError(int type, byte[] data) throws Exception {


    }

    @Override
    public void onFindingCard(int card, Intent var2) throws Exception {
        Log.e("DetectCardFragment", "onFindingCard");
        carType = card;
        if (card == CardReader.SWIPE) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    OutputMagCardInfo cardInfo = new OutputMagCardInfo(var2);
                    if (TextUtils.isEmpty(cardInfo.getPAN())) {
                        swipeCardBinding.logView.error(getString(R.string.failed) + ":" + card);
                    } else {
                        swipeCardBinding.logView.notification(getString(R.string.success) + "\n" +
                               "getPAN-" + cardInfo.getPAN()+"\n"+
                                "getCardHoldName-" + cardInfo.getCardHoldName()+"\n"+
                                "getMaskedPAN-" + cardInfo.getMaskedPAN()+"\n"+
                                "getExpiredDate-" + cardInfo.getExpiredDate()+"\n"+
                                "getServiceCode-" + cardInfo.getServiceCode()+"\n"+
                                "getTrack2HexString-" + cardInfo.getTrack2HexString()+"\n"+
                                "getTrack3HexString-" + cardInfo.getTrack3HexString());
//                            showConfirmMarCardNoDialog(getString(R.string.PleaseConfirmYourCardNumber), cardInfo);
                    }

                    swipeCardBinding.start.setEnabled(true);
                }
            });


        }
        setCardFindView(card);
    }

    public void setCardFindView(int carType) {

    }

    @Override
    public void onContactlessCardType(int kernelType) throws Exception {
    }

    @Override
    public void onStartPBOC() throws Exception {

    }

    @Override
    public void onSelectApplication(List<String> applicationList) throws Exception {

    }

    @Override
    public void onConfirmCertInfo(String var1, String var2) throws Exception {
        Log.e("DetectCardFragment", "onConfirmCertInfo");
        pbocManager.confirmCerInfo();
    }

    @Override
    public void onConfirmCardInfo(Intent var1) throws Exception {
//        LogUtil.e("DetectCardFragment", "onConfirmCardInfo");
        OutputCardInfoData cardInfoData = new OutputCardInfoData(var1);
        String mCardNumber = cardInfoData.getPAN();
        if (carType == CardReader.INSERT) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    swipeCardBinding.logView.notification(getString(R.string.success) + ":" + mCardNumber);
                }
            });
        } else if (carType == CardReader.PICC) {
            pbocManager.confirmCardInfo(true);
        }
    }

    @Override
    public void onRequestInputPIN(boolean isOnlinePin, int retryTimes) throws Exception {
        Log.e("DetectCardFragment", "onRequestInputPIN");
        if (carType == CardReader.PICC) {
            swipeCardBinding.logView.notification(getString(R.string.success) + ":" + "to do onRequestInputPIN");
        }
    }

    @Override
    public void onAARequestOnlineProcess(Intent var1) throws Exception {
        OutputQPBOCResult out = new OutputQPBOCResult(var1);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                swipeCardBinding.logView.append("PBOC 55 field:" + out.get55Field());
                // LogUtil.e(getClass(), "PBOC AA result:" + out.getAAResult());
                swipeCardBinding.logView.append("out.getPAN():" + out.getPAN());
                swipeCardBinding.logView.append("out.getMaskedPan():" + out.getMaskedPan());
                swipeCardBinding.logView.append("out.getECBalance():" + out.getECBalance());
                // swipeCardBinding.logView.append( "PBOC Card seq number:" + out.getCardSeqNum());
                swipeCardBinding.logView.append("PBOC IC data:" + out.getICData());
                //  swipeCardBinding.logView.append( "PBOC reversal data:" + out.getReversalData());
                swipeCardBinding.logView.append("out.getTCData():" + out.getTCData());
                swipeCardBinding.logView.append("out.getDate():" + out.getDate());
                swipeCardBinding.logView.append("out.getExpiredDate():" + out.getExpiredDate());
                swipeCardBinding.logView.append("out.getCardSN():" + out.getCardSN());
            }
        });
        InputPBOCOnlineData onlineData = new InputPBOCOnlineData();

        onlineData.setResponseCode("00"); //bit39   example : 00
        onlineData.setAuthCode("AAEED480EEA0F997");  //arpc data ten bytes
        onlineData.setICData("71105056A2B083934000FFFE000000016300");
        pbocManager.inputOnlineProcessResult(onlineData.getIntent());
    }

    @Override
    public void onTransactionResult(int var1, Intent var2) throws Exception {
        OutputQPBOCResult out = new OutputQPBOCResult(var2);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                swipeCardBinding.logView.append("PBOC 55 field:" + out.get55Field());
                // swipeCardBinding.logView.append( "PBOC AA result:" + out.getAAResult());
                swipeCardBinding.logView.append("out.getPAN():" + out.getPAN());
                swipeCardBinding.logView.append("out.getMaskedPan():" + out.getMaskedPan());
                swipeCardBinding.logView.append("out.getECBalance():" + out.getECBalance());
                // swipeCardBinding.logView.append( "PBOC Card seq number:" + out.getCardSeqNum());
                swipeCardBinding.logView.append("PBOC IC data:" + out.getICData());
                //  swipeCardBinding.logView.append( "PBOC reversal data:" + out.getReversalData());
                swipeCardBinding.logView.append("out.getTCData():" + out.getTCData());
                swipeCardBinding.logView.append("out.getDate():" + out.getDate());
                swipeCardBinding.logView.append("out.getExpiredDate():" + out.getExpiredDate());
                swipeCardBinding.logView.append("out.getCardSN():" + out.getCardSN());
                try {
                    byte[] tagValue = pbocManager.getEmvTlvData(new byte[]{(byte) 0x9f, 0x6E});
                    if (tagValue != null) {
                        swipeCardBinding.logView.append("9f6e:tagValue:" + ByteFuncUtil.ByteArrToHex(tagValue));
                    }
                    tagValue = pbocManager.getEmvTlvData(new byte[]{(byte) 0x9f, 0x1A});
                    if (tagValue != null) {
                        swipeCardBinding.logView.append("9f1A:tagValue:" + ByteFuncUtil.ByteArrToHex(tagValue));
                    }
                    tagValue = pbocManager.getEmvTlvData(PayPassTags.TAG_CVM);
                    if (tagValue != null) {
                        swipeCardBinding.logView.append("tagValue:" + ByteFuncUtil.ByteArrToHex(tagValue));
                    }
                    if (var1 != 0) {
                        swipeCardBinding.logView.error(getString(R.string.failed) + ":" + var1);
                    } else {
                        swipeCardBinding.logView.error(getString(R.string.success) + ":" + var1);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                swipeCardBinding.start.setEnabled(true);
            }
        });
        PBOCManager.stopTransfer();
    }

    @Override
    public void onRequestAmount() throws Exception {

    }

    @Override
    public void onReadECBalance(Intent var1) throws Exception {

    }

    @Override
    public void onReadCardOfflineRecord(Intent var1) throws Exception {

    }

    @Override
    public void onRequestSinature() throws Exception {

    }

    @Override
    public void onFindSelectAid(String var1) throws Exception {

    }

    @Override
    public void onRupayContactlessSecondTapCard() throws Exception {

    }
}
