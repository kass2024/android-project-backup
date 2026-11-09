package com.hf.hp605pv;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class TextToSpeechUtil {

    private static TextToSpeech tts;

    public static boolean say(Context context,String content){

        if(tts ==null) {
            tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = tts.setLanguage(Locale.getDefault());
                        tts.setPitch(1.0f);
                        tts.setSpeechRate(1.0f);
                        say(tts, content);
                    }
                }
            });
            return true;
        }else {
            return say(tts,content);
        }
    }

    private static String lastSay = "";
    private static long lastSayTimes = 0;
    private static boolean say(TextToSpeech tts,String content){
        if(lastSay.equals(content)&&System.currentTimeMillis()-lastSayTimes<3000){
            return false;
        }
        lastSay = content;
        lastSayTimes= System.currentTimeMillis();
        tts.speak(content,TextToSpeech.QUEUE_FLUSH, null, content);
        return true;
    }
}
