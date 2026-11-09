package com.xander.student;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Brief overlay of the uploaded View Staff photo. Does not take focus, so
 * HeyStar can stay on the live camera.
 */
public final class PhotoOverlay {
    private static View current;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable HIDE = new Runnable() {
        @Override
        public void run() {
            hideNow();
        }
    };

    private PhotoOverlay() {
    }

    public static void show(Context context, String filePath, String label) {
        if (context == null || filePath == null || filePath.length() < 4) {
            return;
        }
        final Context app = context.getApplicationContext();
        final String path = filePath;
        final String text = label == null ? "" : label;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                showNow(app, path, text);
            }
        });
    }

    private static void showNow(Context app, String filePath, String label) {
        hideNow();
        Bitmap bmp = BitmapFactory.decodeFile(filePath);
        if (bmp == null) {
            return;
        }
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            bmp.recycle();
            return;
        }
        float dp = app.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(app);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackgroundColor(Color.parseColor("#F7FFFFFF"));
        int pad = Math.round(18 * dp);
        box.setPadding(pad, pad, pad, pad);

        ImageView img = new ImageView(app);
        img.setImageBitmap(bmp);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int size = Math.round(240 * dp);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(size, size);
        img.setLayoutParams(ip);
        box.addView(img);

        TextView tv = new TextView(app);
        tv.setText(label.length() == 0 ? "CLOCK IN" : label);
        tv.setTextColor(Color.parseColor("#0F766E"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, Math.round(12 * dp), 0, 0);
        box.addView(tv);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.gravity = Gravity.CENTER;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (Build.VERSION.SDK_INT >= 26) {
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            lp.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        try {
            wm.addView(box, lp);
            current = box;
        } catch (Throwable ignored) {
            bmp.recycle();
            return;
        }
        MAIN.removeCallbacks(HIDE);
        MAIN.postDelayed(HIDE, 2400);
    }

    private static void hideNow() {
        if (current == null) {
            return;
        }
        try {
            Context app = current.getContext();
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                wm.removeView(current);
            }
        } catch (Throwable ignored) {
        }
        current = null;
    }
}
