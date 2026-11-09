package com.example.smdt.smdtaisdkdemo.xander.facepass;

public class CameraPreviewData {
    public byte[] nv21Data;
    public int width;
    public int height;
    public int rotation;
    public boolean mirror;

    public CameraPreviewData(byte[] nv21Data, int width, int height, int rotation, boolean mirror) {
        this.nv21Data = nv21Data;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.mirror = mirror;
    }
}
