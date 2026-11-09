package com.xander.student;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ImageView;

/** Square photo frame. The old circle clip plus oval background drew two circles. */
public class CircleImageView extends ImageView {
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CircleImageView(Context context) {
        super(context);
        init();
    }

    public CircleImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircleImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setScaleType(ScaleType.CENTER_CROP);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(8f);
        frame.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int side = Math.min(getMeasuredWidth(), getMeasuredHeight());
        if (side > 0) {
            setMeasuredDimension(side, side);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = frame.getStrokeWidth() / 2f;
        canvas.drawRect(inset, inset, getWidth() - inset, getHeight() - inset, frame);
    }
}
