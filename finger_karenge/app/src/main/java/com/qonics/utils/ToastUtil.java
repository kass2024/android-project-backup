package com.qonics.utils;

import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

public class ToastUtil {

	public static void showToast(Context context,String msg) {
         Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
	}
	
	
	public static void showToast(Context context,int resId) {
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
	}

	public static void showToastTop(Context context,String msg) {
		Toast toast=Toast.makeText(context, msg, Toast.LENGTH_SHORT/*2000 */);
		toast.setGravity(Gravity.TOP|Gravity.CENTER, 0, 120);  
		toast.show();
	}

	public static void showToastDef(Context context,String msg,int pos,int tm) {
		Toast toast=Toast.makeText(context, msg, tm);
		toast.setGravity(Gravity.TOP|Gravity.CENTER, 0, pos);  
		toast.show();
	}
	
	public static void showToastEx(Context context,String msg,int tm) {
		Toast toast=Toast.makeText(context, msg, tm);
		toast.setGravity(Gravity.CENTER, 0, 0);  
		toast.show();
	}
}
