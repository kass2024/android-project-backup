package com.qonics.somanet;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

public final class UiMotion {
    private UiMotion() {}

    public static void rise(View view, int delayMs) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(28f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(380)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public static void pop(View view) {
        if (view == null) return;
        view.setScaleX(0.92f);
        view.setScaleY(0.92f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
    }
}
