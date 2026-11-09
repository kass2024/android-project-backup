package com.qonics.app;

public class UserItem {
	
	public int 	userid;
	public String usertype;
	public byte groupid;
	public String   name="";
	public byte[] 	expdate=new byte[3];
	public byte[]   enlcon1=new byte[5];
	public byte[]   enlcon2=new byte[5];
	public byte[]   enlcon3=new byte[5];
	public byte[]	fp1=new byte[512];
	public byte[]	fp2=new byte[512];
	public byte[]	fp3=new byte[512];
	public byte[]	face1=new byte[112];
	public byte[]	face2=new byte[112];
	public byte[]   enllNO=new byte[4];
	public String	photo="";
	public String	phone="";
	public String	card="";
	public String	face_dim1="";
	public String	face_dim2="";
	public int 	gender=0;
}
