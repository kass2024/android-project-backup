package com.hf.hp605pv.widget;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.hf.hp605pv.R;

/**
 * @author tx
 * @date 2023/5/31 8:59
 * @target actionbar
 */
public class EasyActionbar extends RelativeLayout {

    //三个容器
    private LinearLayout leftViewContainer, rightViewContainer, centerViewContainer;

    //顶部高度
    int addHeight = 0;

    //容器高度
    int containerHeight = 50;

    public EasyActionbar(Context context) {
        super(context);
        init();
    }

    public EasyActionbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EasyActionbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public EasyActionbar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    //测量顶部高度
    public int getStateBarHeight() {
        int result = DPPX.dip2px(getContext(), 25);
        int resourceId = this.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = this.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void init() {
        //顶部预留高度
        addHeight = getStateBarHeight();

        //初始化左侧容器
        LayoutParams leftContainerLayoutParams = new LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, DPPX.dip2px(getContext(), containerHeight));
        leftContainerLayoutParams.topMargin = addHeight;
        leftContainerLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        leftViewContainer = new LinearLayout(getContext());
        leftViewContainer.setLayoutParams(leftContainerLayoutParams);
        addView(leftViewContainer);

        //初始化右侧容器
        LayoutParams rightContainerLayoutParams = new LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, DPPX.dip2px(getContext(), containerHeight));
        rightContainerLayoutParams.topMargin = addHeight;
        rightContainerLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        rightViewContainer = new LinearLayout(getContext());
        rightViewContainer.setLayoutParams(rightContainerLayoutParams);
        addView(rightViewContainer);

        //初始化中部容器
        LayoutParams centerContainerLayoutParams = new LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, DPPX.dip2px(getContext(), containerHeight));
        centerContainerLayoutParams.topMargin = addHeight;
        centerContainerLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        centerViewContainer = new LinearLayout(getContext());
        centerViewContainer.setGravity(Gravity.CENTER);
        centerViewContainer.setLayoutParams(centerContainerLayoutParams);
        addView(centerViewContainer);
    }

    private void statusBarTextBlack() {
        ((Activity)getContext()).getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void statusBarTextWhite() {
        ((Activity)getContext()).getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    //构建img
    private ImageView buildImageView(int res) {
        ImageView imageView = new ImageView(getContext());
        imageView.setLayoutParams(new LayoutParams(DPPX.dip2px(getContext(), containerHeight), DPPX.dip2px(getContext(), containerHeight)));
        imageView.setImageResource(res);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setPadding(DPPX.dip2px(getContext(),10),
                DPPX.dip2px(getContext(),10),
                DPPX.dip2px(getContext(),10),
                DPPX.dip2px(getContext(),10));
        return imageView;
    }

    private ImageView buildCustomImageView(int res) {
        ImageView imageView = new ImageView(getContext());
        LayoutParams lp =new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, DPPX.dip2px(getContext(), containerHeight));
        lp.addRule(CENTER_VERTICAL);
        imageView.setPadding(DPPX.dip2px(getContext(),12),
                DPPX.dip2px(getContext(),12),
                DPPX.dip2px(getContext(),12),
                DPPX.dip2px(getContext(),12));
        imageView.setAdjustViewBounds(true);
        imageView.setLayoutParams(lp);
        imageView.setImageResource(res);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        return imageView;
    }

    //构建text
    private TextView buildTextView(String str) {
        return buildTextView(str, 16);
    }

    //构建text
    private TextView buildEventTextView(String str) {
        return buildEventTextView(str, 14);
    }

    //构建text
    private TextView buildTextView(String str,int size) {
        TextView textView = new TextView(getContext());
        textView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, DPPX.dip2px(getContext(), containerHeight)));
        textView.setPadding(DPPX.dip2px(getContext(), 3), 0, DPPX.dip2px(getContext(), 3), 0);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(COMPLEX_UNIT_DIP, size);
        textView.setText(str);
        int color = isLight()? R.color.black:R.color.white;
        textView.setTextColor(getResources().getColor(color));
        return textView;
    }

    //构建text
    private TextView buildEventTextView(String str,int size) {
        TextView textView = new TextView(getContext());
        textView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, DPPX.dip2px(getContext(), containerHeight)));
        textView.setPadding(DPPX.dip2px(getContext(), 3), 0, DPPX.dip2px(getContext(), 3), 0);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(COMPLEX_UNIT_DIP, size);
        textView.setText(str);
        int color = isLight()?R.color.black:R.color.white;
        textView.setTextColor(getResources().getColor(color));
        return textView;
    }

    public LinearLayout getLeftViewContainer(){
        return leftViewContainer;
    }

    public LinearLayout getRightViewContainer(){
        return rightViewContainer;
    }

    public LinearLayout getCenterViewContainer(){
        return centerViewContainer;
    }

    //背景颜色深浅判断
    private boolean isLight(){
        Drawable bg = getBackground();
        if (bg instanceof ColorDrawable) {
            ColorDrawable cd = (ColorDrawable) bg;
            int color = cd.getColor();
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            if (r > 125 && g > 125 && b > 125) {
                // 浅色
                return true;
            } else if (r < 105 && g < 105 && b < 105) {
                // 深色
                return false;
            } else if (max(r, g, b) - min(r, g, b) > 50) {
                // 如果最大和最小分量值差大于50,也属于深色
                return false;
            } else {
                // 中等色彩
                return true;
            }
        }
        return true;
    }

    //rgb最大值
    private static int max(int a, int b, int c) {
        int max = a;
        if (b > max) max = b;
        if (c > max) max = c;
        return max;
    }

    //rgb最小值
    private static int min(int a, int b, int c) {
        int min = a;
        if (b < min) min = b;
        if (c < min) min = c;
        return min;
    }


    //设置标题样式
    ////返回-标题-更多图标按钮
    public void backTitleMoreStyle(String title,OnClickListener click) {
        backTitleStyle(title);
        int res = isLight()? R.drawable.more_black:R.drawable.more_white;
        ImageView moreView = buildImageView(res);
        moreView.setOnClickListener(click);
        rightViewContainer.addView(moreView);
    }
    //标题-文字按钮
    public void titleTextStyle(String title,String eventText,OnClickListener click) {
        titleStyle(title);
        TextView rightText = buildEventTextView(eventText);
        rightText.setOnClickListener(click);
        rightViewContainer.addView(rightText);
    }
    //标题-文字按钮
    public void textTitleTextStyle(String left,OnClickListener leftClick,String title,String right,OnClickListener rightClick) {
        titleStyle(title);
        TextView leftText = buildTextView(left);
        leftText.setOnClickListener(leftClick);
        leftViewContainer.addView(leftText);

        TextView rightText = buildTextView(right);
        rightText.setOnClickListener(rightClick);
        rightViewContainer.addView(rightText);
    }

    //标题-文字按钮
    public void titleTextsStyle(String title,String[] eventTexts,OnClickListener[] clicks) {
        titleStyle(title);
        if(eventTexts.length!=clicks.length){
            throw new RuntimeException("eventTexts.length!=clicks.length");
        }
        for(int i=0;i<eventTexts.length;i++) {
            TextView rightText = buildEventTextView(eventTexts[i]);
            rightText.setOnClickListener(clicks[i]);
            rightViewContainer.addView(rightText);
        }
    }
    //返回-标题-文字按钮
    public void backTitleTextStyle(String title,String eventText,OnClickListener click) {
        backTitleStyle(title);
        TextView rightText = buildEventTextView(eventText);
        rightText.setOnClickListener(click);
        rightViewContainer.addView(rightText);
    }
    //返回-标题
    public void backTitleStyle(String title) {
        titleStyle(title);
        int res = isLight()?R.drawable.back_black:R.drawable.ui_back;
        ImageView backView = buildImageView(res);
        backView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ((Activity)getContext()).finish();
            }
        });
        leftViewContainer.addView(backView);
    }

    public interface IFinish{
        boolean canBeFinish();
        void rejectFinish();
    }
    //条件返回-标题
    public void stateBackTitleStyle(String title,IFinish iFinish) {
        titleStyle(title);
        int res = isLight()?R.drawable.back_black:R.drawable.ui_back;
        ImageView backView = buildImageView(res);
        backView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(iFinish.canBeFinish()) {
                    ((Activity) getContext()).finish();
                }else {
                    iFinish.rejectFinish();
                }
            }
        });
        leftViewContainer.addView(backView);
    }

    //返回-标题
    public void customImgRightButtonStyle(int custom,int[] imgList,OnClickListener[] clickers) {
        clearContainer();
        if(isLight()){
            statusBarTextBlack();
        }else {
            statusBarTextWhite();
        }
        ImageView customView = buildCustomImageView(custom);
        leftViewContainer.addView(customView);

        if(imgList.length!=clickers.length){
            throw new RuntimeException("imgList.length!=clickers.length");
        }
        for(int i=0;i<imgList.length;i++) {
            ImageView backView = buildImageView(imgList[i]);
            backView.setOnClickListener(clickers[i]);
            rightViewContainer.addView(backView);
        }
    }

    //纯标题
    public void titleStyle(String title) {
        clearContainer();
        if(isLight()){
            statusBarTextBlack();
        }else {
            statusBarTextWhite();
        }
        centerViewContainer.addView(buildTextView(title));
    }

    private void clearContainer(){
        leftViewContainer.removeAllViews();
        rightViewContainer.removeAllViews();
        centerViewContainer.removeAllViews();
    }
}
