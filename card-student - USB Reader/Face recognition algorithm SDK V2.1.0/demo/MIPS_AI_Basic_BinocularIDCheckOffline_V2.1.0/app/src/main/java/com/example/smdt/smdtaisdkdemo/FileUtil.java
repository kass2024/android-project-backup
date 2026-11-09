package com.example.smdt.smdtaisdkdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Random;

public class FileUtil {
	final static String TAG = "FileUtil";
	//
	public static boolean fileIsExists(String strFile)
	{
		try
		{
			File f=new File(strFile);
			if(!f.exists())
			{
				return false;
			}

		}
		catch (Exception e)
		{
			return false;
		}

		return true;
	}

	public static int copyFilefrAsset(Context context, String modelName) {
		try {

			if(fileIsExists(context.getFilesDir().getAbsolutePath()+File.separator+modelName) == true)
			{
				return 0;
			}

			String[] names = context.getAssets().list("");
			int i;
			for(i=0;i<names.length;i++){
				if(names[i].equals(modelName.trim())){
					break;
				}
			}
			if(i>=names.length){
				return -1;
			}

			File modelFile = new File(context.getFilesDir(), modelName); 

			InputStream is = context.getAssets().open(modelName);

			OutputStream os = new FileOutputStream(modelFile);
			byte[] buffer = new byte[1024];
			int length = is.read(buffer);
			while (length > 0) {
				os.write(buffer, 0, length);
				length = is.read(buffer);
			}
			os.flush();
			os.close();
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return 1;
	}

	
	public static int copyFilefrSD(Context context, String modelName) {
		try {

			if(fileIsExists(context.getFilesDir().getAbsolutePath()+File.separator+modelName) == true)
			{
				return 0;
			}
			File modelFile = new File(context.getFilesDir(), modelName);

			//InputStream is = context.getAssets().open(modelName);
			final File file_smdt=new File("/sdcard/");
			String file_path=file_smdt.getAbsolutePath();
			File file = new File(file_path, modelName);
			//File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+File.separator+SMDT_FILE_PATH, modelName);
			if (!file.exists()) {
				return -1;
			}
			FileInputStream is = new FileInputStream(file);

			OutputStream os = new FileOutputStream(modelFile);
			byte[] buffer = new byte[1024];
			int length = is.read(buffer);
			while (length > 0) {
				os.write(buffer, 0, length);
				length = is.read(buffer);
			}
			os.flush();
			os.close();
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 1;
	}

	public static String readFileFromSDCard(Context context, File path,
			String filename) {
		StringBuffer sb = null;
		try {
			File file = new File(path, filename);
			if (!file.exists()) {
				return null;
			}
			BufferedReader br = new BufferedReader(new FileReader(file));
			String readline = "";
			sb = new StringBuffer();
			while ((readline = br.readLine()) != null) {
				System.out.println("readline:" + readline);
				sb.append(readline);
			}
			br.close();
			Log.i(TAG, "license:" + sb.toString());
			return sb.toString();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static void writeFileToSDCard(Context context, String filename,byte[] data) {
		File f = new File(Environment.getExternalStorageDirectory(), filename);
		if (f.exists()) {
			f.delete();
		}
		try {
			FileOutputStream out = new FileOutputStream(f);
			out.write(data);
			out.flush();
			out.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void makeRootDirectory(String filePath) {
		File file = null;
		String path = filePath.substring(0,filePath.lastIndexOf("/"));
		String filename = filePath.substring(filePath.lastIndexOf("/") + 1);
		try {
			file = new File(path);
			if (!file.exists()) {
				file.mkdirs();
			}
		} catch (Exception e) {
			Log.i("error:", e+"");
		}
	}

	public static void writeBitmapToSDCard(Bitmap bm,String path,String s) {
		Log.i(TAG, "writeBitmapToSDCard:" + s+".png");
		File f = new File(path, s+".png");
		if (f.exists()) {
			f.delete();
		}
		try {
			FileOutputStream out = new FileOutputStream(f);
			bm.compress(Bitmap.CompressFormat.PNG, 90, out);
			out.flush();
			out.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void writeFileToSDCard(Context context, String filename,String data) {
		File f = new File(Environment.getExternalStorageDirectory(), filename);
		if (f.exists()) {
			f.delete();
		}
		try {
			FileOutputStream out = new FileOutputStream(f);
			out.write(data.getBytes());
			out.flush();
			out.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static Bitmap yuvToBitmap(byte[]nv21 , int width, int height, int yuvType) {
		YuvImage yuvImage = new YuvImage(nv21, yuvType, width, height, null);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, baos);
		byte[] cur = baos.toByteArray();
		return BitmapFactory.decodeByteArray(cur, 0, cur.length);
	}


	public static String readFileFromSDCard2(String filename) {
		String result = null;
		try {
			File file = new File(filename);
			if (!file.exists()) {
				return null;
			}
			FileInputStream inputStream = new FileInputStream(file);
			byte[] b = new byte[inputStream.available()];
			inputStream.read(b);
			result = new String(b);
			Log.i(TAG, "readFileFromSDCard2");
			Log.i(TAG, result);
		} catch (Exception e) {
		}
		return result;
	}

	public static String  getStringContent(String filename) {
		try {
			StringBuilder buf = new StringBuilder();
			File file = new File(filename);
			if (!file.exists()) {
				return null;
			}
			FileInputStream json = new FileInputStream(file);
			//InputStream json = getAssets().open("SENSEID_FACEPRO_7D4BC732-8533-4E68-9E61-B9DE73B7C166.lic");
			BufferedReader in = new BufferedReader(new InputStreamReader(json, "UTF-8"));
			String str;

			while ((str = in.readLine()) != null) {
				buf.append(str);
				buf.append("\n"); // MUST add \n or license will be invalid.
			}

			in.close();
			return buf.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
	public static byte[] readFileFromSDCard3(String filename) {
		byte[] b=null;
		try {
			File file = new File(filename);
			if (!file.exists()) {
				return null;
			}
			FileInputStream inputStream = new FileInputStream(file);
			b = new byte[inputStream.available()];
			inputStream.read(b);
			//result = new String(b);
			Log.i(TAG, "readFileFromSDCard2");
			//Log.i(TAG, result);
		} catch (Exception e) {
		}
		return b;
	}

	public static String readFileFromSDCard2(File path,String filename) {
		String result = null;
		try {
			File file = new File(path, filename);
			if (!file.exists()) {
				return null;
			}
			FileInputStream inputStream = new FileInputStream(file);
			byte[] b = new byte[inputStream.available()];
			inputStream.read(b);
			result = new String(b);
			Log.i(TAG, "readFileFromSDCard2");
			Log.i(TAG, result);
		} catch (Exception e) {
		}
		return result;
	}

	public static byte[] readFileFromSDCardByte(File path,String filename) {
		String result = null;
		byte[] b=null;
		try {
			File file = new File(path, filename);
			if (!file.exists()) {
				return null;
			}
			FileInputStream inputStream = new FileInputStream(file);
			b = new byte[inputStream.available()];
			inputStream.read(b);
			Log.i(TAG, "readFileFromSDCard2");
			Log.i(TAG, result);
		} catch (Exception e) {
		}
		return b;
	}

	public static String readFileFromAssets(Context context, String fileName) {
		String res = "";
		try {
			InputStream in = context.getResources().getAssets().open(fileName);
			int length = in.available();
			byte[] buffer = new byte[length];

			in.read(buffer);
			in.close();
			res = new String(buffer,"UTF-8");

		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}
}
