package com.qonics.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.fgtit.fpcore.FPMatch;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;


public class StudentsList {
	
	private static StudentsList instance;
	Context context;
	public static StudentsList getInstance() {
    	if(null == instance) {
    		instance = new StudentsList();
    	}
    	return instance;
    }
	public List<UserItem> usersList=new ArrayList<UserItem>();
		
	private SQLiteDatabase db;
	
	public static boolean IsFileExists(String filename){
		File f=new File(filename);
		if(f.exists()){
			return true;
		}
		return false;
	}
	
	public void LoadAll(Context context){
		this.context = context;
		usersList.clear();
//		if(IsFileExists(Environment.getExternalStorageDirectory() + "/somanet/users.db")){
//			db=SQLiteDatabase.openOrCreateDatabase(Environment.getExternalStorageDirectory() + "/somanet/users.db",null);
//		}else{
//			db=SQLiteDatabase.openOrCreateDatabase(Environment.getExternalStorageDirectory() + "/somanet/somanet_attendance_db.db",null);
//			String sql="CREATE TABLE tb_student(userid INTEGER PRIMARY KEY ASC,"
//					+ "usertype INTEGER,"
//					+ "groupid INTEGER,"
//					+ "name CHAR[24],"
//					+ "expdate BLOB,"
//					+ "enlcon1 BLOB,"
//					+ "enlcon2 BLOB,"
//					+ "enlcon3 BLOB,"
//					+ "fp1 BLOB,"
//					+ "fp2 BLOB,"
//					+ "fp3 BLOB,"
//					+ "enllNO BLOB);";
//
//			db.execSQL(sql);
//		}
		db = context.openOrCreateDatabase("somanet_attendance_db",context.MODE_PRIVATE,null);
		///*
		Cursor cursor = db.query ("tb_student",null,null,null,null,null,null);
		if(cursor!=null){
			if(cursor.moveToFirst()){
				for(int i=0;i<cursor.getCount();i++){
					//cursor.move(i);
					Log.e("STUDENTT",cursor.getString(4)+ " - " + cursor.getString(5));
					UserItem ui=new UserItem();
					ui.userid=cursor.getInt(0);
					ui.usertype= cursor.getString(1);
					ui.name=cursor.getString(4);
					ui.card=cursor.getString(5);
					usersList.add(ui);
					cursor.moveToNext();
				}
			}
			cursor.close();
		}
		///*/
	}
	
	private short getShort(byte b1, byte b2) {
		short temp = 0;
		temp |= (b1 & 0xff);
		temp <<= 8;
		temp |= (b2 & 0xff);
		return temp;
	}
	
	public void ClearStudents(){
		usersList.clear();
		String sql = "delete from tb_student";
		db.execSQL(sql); 
	}


	private boolean bytesEquals(byte[] src,int spos,byte[] dst,int dpos,int size){
		for(int i=0;i<size;i++){
			if(src[spos+i]!=dst[dpos+i])
				return false;
		}
		return true;
	}
	
	public UserItem FindUserItemByCard(String cardsn){
		for(int i=0;i<usersList.size();i++){
			Log.e("CARD",cardsn+" | "+usersList.get(i).card);
			if(cardsn.toLowerCase().equals(usersList.get(i).card.toLowerCase())){
				return usersList.get(i);
			}
		}
		return null;
	}
	
	public UserItem FindUserItemByCardex(byte[] cardsn){
		for(int i=0;i<usersList.size();i++){
				if(bytesEquals(cardsn,0,usersList.get(i).enllNO,0,4)){
					return usersList.get(i);
				}
		}
		return null;
	}
	
	public boolean UserIsExists(int id){
		for(int i=0;i<usersList.size();i++){
			if(usersList.get(i).userid==id)
				return true;
		}
		return false;
	}
}
