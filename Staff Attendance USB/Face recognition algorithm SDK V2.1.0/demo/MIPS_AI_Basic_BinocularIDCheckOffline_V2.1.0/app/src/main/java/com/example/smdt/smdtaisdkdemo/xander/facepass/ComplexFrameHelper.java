package com.example.smdt.smdtaisdkdemo.xander.facepass;

import android.util.Pair;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ComplexFrameHelper {
    private static final ArrayBlockingQueue<Pair<CameraPreviewData, CameraPreviewData>> QUEUE =
            new ArrayBlockingQueue<Pair<CameraPreviewData, CameraPreviewData>>(1);
    private static CameraPreviewData rgb;
    private static CameraPreviewData ir;

    public static void addRgbFrame(CameraPreviewData frame) {
        synchronized (ComplexFrameHelper.class) {
            rgb = frame;
            drain();
        }
    }

    public static void addIRFrame(CameraPreviewData frame) {
        synchronized (ComplexFrameHelper.class) {
            ir = frame;
            drain();
        }
    }

    private static void drain() {
        if (rgb == null || ir == null) {
            return;
        }
        QUEUE.poll();
        QUEUE.offer(new Pair<CameraPreviewData, CameraPreviewData>(copyFrame(rgb), copyFrame(ir)));
        rgb = null;
        ir = null;
    }

    public static Pair<CameraPreviewData, CameraPreviewData> take() throws InterruptedException {
        return QUEUE.take();
    }

    public static Pair<CameraPreviewData, CameraPreviewData> poll(long ms) throws InterruptedException {
        return QUEUE.poll(ms, TimeUnit.MILLISECONDS);
    }

    public static void clear() {
        synchronized (ComplexFrameHelper.class) {
            rgb = null;
            ir = null;
            QUEUE.clear();
        }
    }

    static CameraPreviewData copyFrame(CameraPreviewData src) {
        if (src == null || src.nv21Data == null) {
            return src;
        }
        byte[] nv = new byte[src.nv21Data.length];
        System.arraycopy(src.nv21Data, 0, nv, 0, src.nv21Data.length);
        return new CameraPreviewData(nv, src.width, src.height, src.rotation, src.mirror);
    }
}
