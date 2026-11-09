package com.qonics.app;

import java.io.File;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

public class LogsList {
	
	private static LogsList instance;
	Context context;
	public static LogsList getInstance() {
    	if(null == instance) {
    		instance = new LogsList();
    	}
    	return instance;
    }
	
	public List<LogItem> logsList=new ArrayList<LogItem>();
	private SQLiteDatabase db;
	
	public static boolean IsFileExists(String filename){
		File f=new File(filename);
		if(f.exists()){
			return true;
		}
		return false;
	}
	
	public void Init(Context context){
		this.context = context;
//		if(IsFileExists(Environment.getExternalStorageDirectory() + "/OnePass/logs.db")){
//			db=SQLiteDatabase.openOrCreateDatabase(Environment.getExternalStorageDirectory() + "/OnePass/logs.db",null);
//		}else{
//			db=SQLiteDatabase.openOrCreateDatabase(Environment.getExternalStorageDirectory() + "/OnePass/logs.db",null);
//			String sql="CREATE TABLE TB_LOGS(userid INTEGER,"
//					+ "name CHAR[24],"
//					+ "status1 INTEGER,"
//					+ "status2 INTEGER,"
//					+ "datetime DATETIME);";
//
//			db.execSQL(sql);
//		}
		db = context.openOrCreateDatabase("somanet_attendance_db",context.MODE_PRIVATE,null);
	}
	
	public void Clear(){
		logsList.clear();
		String sql = "delete from TB_LOGS";
		db.execSQL(sql);
	}
	
	public String getStringDate() {
		Date currentTime = new Date(System.currentTimeMillis());
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String dateString = formatter.format(currentTime);
		return dateString;
	}
	
	public void Append(int userid,int type){
		String sql="insert into tb_records(user_id,user_type,timee) "
				+ "values(?,?,?)";
		//String datetime=getStringDate();
		Calendar c = Calendar.getInstance();
		long timestamp = c.getTimeInMillis()/1000L;
		Log.e("DATEEE",c.getTime().toString());

		Object[] args = new Object[]{userid,type,timestamp};
		db.execSQL(sql,args);
	}
	
	public List<LogItem> Query(int qtype,String qname,String qval){
		logsList.clear();
		switch(qtype){
		case 0:{
			Cursor cursor = db.query ("TB_LOGS",null,null,null,null,null,null);
			if(cursor!=null){
				if(cursor.moveToFirst()){
					for(int i=0;i<cursor.getCount();i++){
						LogItem li=new LogItem();
						li.userid=cursor.getInt(0);
						li.name=cursor.getString(1);
						li.status1=cursor.getInt(2);
						li.status2=cursor.getInt(3);
						li.datetime=cursor.getString(4);
						logsList.add(li);
						cursor.moveToNext();
					}
				}
				cursor.close();
			}
			}
			break;
		case 1:
			break;
		case 2:
			break;
		}
		return logsList;
	}
	
	public byte[] LogItemToBytes(LogItem li){
		byte[] lb=new byte[10];
		Date date=null;
		int year,month,day,hour,min,sec;
		
		try {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			date = (Date)format.parse(li.datetime);
			Calendar calendar = Calendar.getInstance(); 
		    calendar.setTime(date); 
		    year = calendar.get(Calendar.YEAR); 
		    month = calendar.get(Calendar.MONTH)+1;
		    day = calendar.get(Calendar.DAY_OF_MONTH);
		    hour = calendar.get(Calendar.HOUR_OF_DAY);
		    min = calendar.get(Calendar.MINUTE);
		    sec = calendar.get(Calendar.SECOND);
		 
		    lb[0]=(byte) (li.userid&0xFF);	
			lb[1]=(byte) ((li.userid>>8)&0xFF);
			lb[2]=(byte) li.status1;
			lb[3]=(byte) li.status2;
			lb[4]=(byte) (year-2000);
			lb[5]=(byte) month;
			lb[6]=(byte) day;
			lb[7]=(byte) hour;
			lb[8]=(byte) min;
			lb[9]=(byte) sec;
			return lb;
		} catch (ParseException e) {
		}		
		return null;
	}
}
