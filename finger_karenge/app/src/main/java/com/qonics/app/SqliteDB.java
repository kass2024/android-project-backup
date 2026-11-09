package com.qonics.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

public class SqliteDB {
	
	private SQLiteDatabase db;
	
	public void open(){
		db=SQLiteDatabase.openOrCreateDatabase(Environment.getExternalStorageDirectory() + "/OnePass/onepass.db",null);
	}
	
	public void createTable(){
		String stu_table="create table usertable(_id integer primary key autoincrement,sname text,snumber text)"; 

		db.execSQL(stu_table); 
		
		String sql="CREATE TABLE tb_staff(userid CHAR[16] PRIMARY KEY ASC,"
				+ "cardsn	CHAR[32],"
				+ "name CHAR[24],"
				+ "password CHAR[16],"
				+ "userstyle INTEGER,"
				+ "identifytype INTEGER,"
				+ "groupid INTEGER,"
				+ "jobtype INTEGER,"
				+ "depttype INTEGER,"
				+ "leveltype INTEGER,"
				+ "usetype INTEGER,"
				+ "enroldate DATE,"
				+ "expdate DATE,"
				+ "remarks VARCHAR(100),"
				+ "finger1 INTEGER,"
				+ "finger2 INTEGER,"
				+ "photo BLOB,"
				+ "template1 BLOB,"
				+ "template2 BLOB);";
		
		db.execSQL(sql);
	}
	
	public void insertData(){
		ContentValues cValue = new ContentValues();
		cValue.put("sname","xiaoming");
		cValue.put("snumber","01005");
		db.insert("stu_table",null,cValue); 
	} 
	
	public void insert(){
		String stu_sql="insert into stu_table(sname,snumber) values('xiaoming','01005')";
		db.execSQL(stu_sql); 
	} 
	
	public void deleteData() {
		String whereClause = "id=?";
		String[] whereArgs = {String.valueOf(2)};
		db.delete("stu_table",whereClause,whereArgs); 
	} 
	
	public void delete() {
		String sql = "delete from stu_table where _id = 6";
		db.execSQL(sql); 
	} 
	
	public void updateData() {
		ContentValues values = new ContentValues();
		values.put("snumber","101003");
		String whereClause = "id=?"; 

		String[] whereArgs={String.valueOf(1)};
		db.update("usertable",values,whereClause,whereArgs); 
	}
	
	public void update(){
		String sql = "update stu_table set snumber = 654321 where id = 1";
		db.execSQL(sql); 
	}
	
	public void drop(){
		String sql ="DROP TABLE stu_table";
		db.execSQL(sql); 
	} 
	
	/*
	getCount()
 	isFirst()
 	isLast()
 	moveToFirst()
 	moveToLast()
 	move(int offset)
 	moveToNext()
 	moveToPrevious()
 	getColumnIndexOrThrow(String  columnName)
 	getInt(int columnIndex)
 	getString(int columnIndex)
	 */
	public void query(SQLiteDatabase db) {
		Cursor cursor = db.query ("usertable",null,null,null,null,null,null); 

		if(cursor.moveToFirst()){
			for(int i=0;i<cursor.getCount();i++){ 
				cursor.move(i);
				int id = cursor.getInt(0);
				String name=cursor.getString(1);
				String password=cursor.getString(2); 
				// System.out.println(id+":"+sname+":"+snumber);
			} 
		} 
	}
}
