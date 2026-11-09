package com.example.smdt.smdtaisdkdemo.xander;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Live face boxes over the RGB preview (3D / IR liveness status on the box).
 */
public class FaceTrackerView extends View {
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Rect[] faces = new Rect[0];
    private int imgW = 1;
    private int imgH = 1;
    private boolean live3d;
    private boolean spoof;

    public FaceTrackerView(Context context) {
        super(context);
        init();
    }

    public FaceTrackerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(5f);
        box.setColor(0xFF22D3EE);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x6622D3EE);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(28f);
        label.setFakeBoldText(true);
    }

    public void setFaces(Rect[] next, int width, int height, boolean live3d, boolean spoof) {
        this.faces = next == null ? new Rect[0] : next;
        this.imgW = width <= 0 ? 1 : width;
        this.imgH = height <= 0 ? 1 : height;
        this.live3d = live3d;
        this.spoof = spoof;
        if (spoof) {
            box.setColor(0xFFEF4444);
            fill.setColor(0x66EF4444);
        } else if (live3d) {
            box.setColor(0xFF4ADE80);
            fill.setColor(0x334ADE80);
        } else {
            box.setColor(0xFF22D3EE);
            fill.setColor(0x3322D3EE);
        }
        postInvalidate();
    }

    public void clear() {
        setFaces(null, 1, 1, live3d, false);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faces.length == 0) {
            return;
        }
        int vw = getWidth();
        int vh = getHeight();
        float scale = Math.max(vw / (float) imgW, vh / (float) imgH);
        float dx = (vw - imgW * scale) / 2f;
        float dy = (vh - imgH * scale) / 2f;
        for (int i = 0; i < faces.length; i++) {
            Rect r = faces[i];
            if (r == null) {
                continue;
            }
            RectF d = new RectF(
                    dx + r.left * scale,
                    dy + r.top * scale,
                    dx + r.right * scale,
                    dy + r.bottom * scale);
            canvas.drawRect(d, box);
            String tag = spoof ? "PHOTO BLOCKED" : (live3d ? "3D LIVE" : "FACE");
            canvas.drawRect(d.left, d.top - 36, d.left + label.measureText(tag) + 16, d.top, fill);
            canvas.drawText(tag, d.left + 8, d.top - 10, label);
        }
    }
}
