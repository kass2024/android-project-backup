package com.qonics.somanet.cashflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Full-screen dim with a portrait A4 window (210×297).
 */
public class A4ScanOverlay extends View {

    private final Paint dim = new Paint();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();

    public A4ScanOverlay(Context context) {
        this(context, null);
    }

    public A4ScanOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        dim.setColor(0xAA000000);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(0xFFFFFFFF);
        corner.setStyle(Paint.Style.STROKE);
        corner.setStrokeWidth(dp(4));
        corner.setStrokeCap(Paint.Cap.SQUARE);
        corner.setColor(0xFF5EEAD4);
    }

    public RectF getFrame() {
        return new RectF(frame);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w < 2 || h < 2) return;

        float side = dp(18);
        float maxW = w - side * 2;
        float maxH = h - dp(108) - dp(132);
        float a4 = 297f / 210f;
        float frameW = maxW;
        float frameH = frameW * a4;
        if (frameH > maxH) {
            frameH = maxH;
            frameW = frameH / a4;
        }
        float left = (w - frameW) / 2f;
        float top = dp(96) + Math.max(0, (maxH - frameH) / 2f);
        frame.set(left, top, left + frameW, top + frameH);

        canvas.drawRect(0, 0, w, frame.top, dim);
        canvas.drawRect(0, frame.bottom, w, h, dim);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom, dim);
        canvas.drawRect(frame.right, frame.top, w, frame.bottom, dim);
        canvas.drawRect(frame, stroke);

        float c = dp(28);
        drawCorner(canvas, frame.left, frame.top, c, c);
        drawCorner(canvas, frame.right, frame.top, -c, c);
        drawCorner(canvas, frame.left, frame.bottom, c, -c);
        drawCorner(canvas, frame.right, frame.bottom, -c, -c);
    }

    private void drawCorner(Canvas canvas, float x, float y, float dx, float dy) {
        canvas.drawLine(x, y, x + dx, y, corner);
        canvas.drawLine(x, y, x, y + dy, corner);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
