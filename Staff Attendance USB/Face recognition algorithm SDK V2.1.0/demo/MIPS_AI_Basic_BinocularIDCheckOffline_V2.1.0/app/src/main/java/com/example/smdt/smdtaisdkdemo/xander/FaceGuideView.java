package com.example.smdt.smdtaisdkdemo.xander;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Large rotating face oval over the live camera. Color = detection quality.
 */
public class FaceGuideView extends View {
    public static final int IDLE = 0;
    public static final int FACE = 1;
    public static final int FULL = 2;
    public static final int BAD = 3;

    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path mask = new Path();
    private int state = IDLE;
    private boolean enroll;
    private int got;
    private int need = 3;
    private float spin;
    private boolean spinning;
    private String label = "Look into the circle";

    private final Runnable spinTick = new Runnable() {
        @Override
        public void run() {
            if (!spinning) {
                return;
            }
            spin += state == FULL ? 14f : 8f;
            if (spin >= 360f) {
                spin -= 360f;
            }
            invalidate();
            postOnAnimation(this);
        }
    };

    public FaceGuideView(Context context) {
        super(context);
        init();
    }

    public FaceGuideView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceGuideView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(false);
        setFocusable(false);
        dim.setColor(0xB3020C1A);
        dim.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(10f);
        ring.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(34f);
        spinPaint.setStyle(Paint.Style.STROKE);
        spinPaint.setStrokeWidth(14f);
        spinPaint.setStrokeCap(Paint.Cap.ROUND);
        caption.setColor(0xFFFFFFFF);
        caption.setTextAlign(Paint.Align.CENTER);
        caption.setFakeBoldText(true);
        caption.setTextSize(32f);
        caption.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        dot.setStyle(Paint.Style.STROKE);
        dot.setStrokeWidth(4f);
        dotFill.setStyle(Paint.Style.FILL);
        applyState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        spinning = true;
        postOnAnimation(spinTick);
    }

    @Override
    protected void onDetachedFromWindow() {
        spinning = false;
        removeCallbacks(spinTick);
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return false;
    }

    public void setEnroll(boolean enroll) {
        this.enroll = enroll;
        applyState();
        postInvalidate();
    }

    public void setProgress(int got, int need) {
        this.got = got;
        this.need = need <= 0 ? 3 : need;
        postInvalidate();
    }

    public void setState(int next) {
        if (next == state) {
            return;
        }
        state = next;
        applyState();
        postInvalidate();
    }

    public int getState() {
        return state;
    }

    public void setLabel(String text) {
        if (text == null) {
            return;
        }
        label = text;
        postInvalidate();
    }

    private void applyState() {
        if (state == FULL) {
            ring.setColor(0x6622C55E);
            glow.setColor(0x4422C55E);
            spinPaint.setColor(0xFF4ADE80);
            label = enroll ? "Hold still" : "Hold still";
        } else if (state == FACE) {
            ring.setColor(0x66F59E0B);
            glow.setColor(0x44F59E0B);
            spinPaint.setColor(0xFFFBBF24);
            label = enroll ? "Look at the camera" : "Move closer";
        } else if (state == BAD) {
            ring.setColor(0x66EF4444);
            glow.setColor(0x44EF4444);
            spinPaint.setColor(0xFFF87171);
            label = enroll ? "Photo blocked — use a live face" : "Use a live face";
        } else {
            ring.setColor(0x6638BDF8);
            glow.setColor(0x4438BDF8);
            spinPaint.setColor(0xFF38BDF8);
            label = enroll ? "Center your face in the circle" : "Look into the circle";
        }
    }

    public RectF ovalRect() {
        return oval;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float size = Math.min(w, h) * 0.94f;
        float ow = size;
        float oh = size * 1.22f;
        if (oh > h * 0.74f) {
            oh = h * 0.74f;
            ow = oh / 1.22f;
        }
        if (ow > w * 0.94f) {
            ow = w * 0.94f;
            oh = ow * 1.22f;
        }
        float left = (w - ow) / 2f;
        float top = h * 0.11f;
        oval.set(left, top, left + ow, top + oh);
        float rx = ow / 2f;
        float ry = oh / 2f;

        mask.reset();
        mask.setFillType(Path.FillType.EVEN_ODD);
        mask.addRect(0, 0, w, h, Path.Direction.CW);
        mask.addRoundRect(oval, rx, ry, Path.Direction.CW);
        canvas.drawPath(mask, dim);
        canvas.drawRoundRect(oval, rx, ry, glow);
        canvas.drawRoundRect(oval, rx, ry, ring);
        canvas.drawArc(oval, spin, 86f, false, spinPaint);
        canvas.drawArc(oval, spin + 180f, 64f, false, spinPaint);

        float cx = oval.centerX();
        float cy = Math.min(oval.bottom + 44f, h - 96f);
        canvas.drawText(label, cx, cy, caption);

        if (enroll && need > 0) {
            float dy = Math.min(cy + 34f, h - 62f);
            float gap = 40f;
            float start = cx - ((need - 1) * gap) / 2f;
            for (int i = 0; i < need; i++) {
                float x = start + i * gap;
                boolean on = i < got;
                dot.setColor(on ? 0xFF22C55E : 0x88FFFFFF);
                dotFill.setColor(on ? 0xFF22C55E : 0x33FFFFFF);
                canvas.drawCircle(x, dy, 11f, dotFill);
                canvas.drawCircle(x, dy, 11f, dot);
            }
        }
    }
}
