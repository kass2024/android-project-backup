package com.example.smdt.smdtaisdkdemo.xander.facepass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;

import com.example.smdt.smdtaisdkdemo.xander.XanderPrefs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ArrayBlockingQueue;

import mcv.facepass.FacePassException;
import mcv.facepass.FacePassHandler;
import mcv.facepass.auth.FacePassAuthCode;
import mcv.facepass.types.FacePassAddFaceResult;
import mcv.facepass.types.FacePassConfig;
import mcv.facepass.types.FacePassDetectionResult;
import mcv.facepass.types.FacePassImage;
import mcv.facepass.types.FacePassImageRotation;
import mcv.facepass.types.FacePassImageType;
import mcv.facepass.types.FacePassLivenessResult;
import mcv.facepass.types.FacePassModel;
import mcv.facepass.types.FacePassPose;
import mcv.facepass.types.FacePassRecognitionResult;
import mcv.facepass.types.FacePassRecognitionState;
import mcv.facepass.types.FacePassTrackOptions;

/**
 * FacePass (same engine HeyStar uses) instead of MIPS, so the kiosk is not
 * blocked by a missing mipsAi.lic. Auth order: board chip, then Face++ keys
 * from the FacePass SDK, then any .cert on the device.
 */
public class FacePassEngine {
    private static final String TAG = "XanderFacePass";
    private static final String GROUP = "xander";
    private static final String AUTH_IP = "https://api-cn.faceplusplus.com";
    private static final String API_KEY = "KyYYa4n6d-N0J6pAnB-uBM8BXBAsYQXo";
    private static final String API_SECRET = "5scLZqrawhqxrnTTEGGeieub0cai20c4";

    public interface Listener {
        void onStatus(String msg);

        void onReady();

        void onRecognized(int staffId);

        void onUnknown();

        void onSpoof();

        void onLiveness(boolean live, boolean spoof, float score, boolean dual3d);

        void onFaces(android.graphics.Rect[] boxes, int imgW, int imgH, boolean live3d);

        void onPreviewFace(Bitmap bmp);
    }

    private final Context app;
    private final XanderPrefs prefs;
    private Listener listener;
    private FacePassHandler handler;
    private SimpleCamera rgbCam;
    private SimpleCamera irCam;
    private boolean dual;
    private boolean ready;
    private volatile boolean running;
    private Thread feedThread;
    private Thread recThread;
    private final ArrayBlockingQueue<CameraPreviewData> rgbQueue = new ArrayBlockingQueue<CameraPreviewData>(1);
    private final ArrayBlockingQueue<RecJob> recQueue = new ArrayBlockingQueue<RecJob>(1);
    private int imageRotation = FacePassImageRotation.DEG270;
    private volatile byte[] lastNv21;
    private volatile int lastW;
    private volatile int lastH;
    private volatile boolean pauseFeed;
    private volatile boolean searchEnabled = true;
    private volatile FacePassImage lastDetImage;
    private volatile boolean notifiedReady;
    private volatile boolean camerasConfigured;
    private volatile boolean rgbOnlyFeed;
    private int emptyDual;
    private long lastRecLog;

    private static class RecJob {
        byte[] message;
        FacePassTrackOptions[] opts;
    }

    public FacePassEngine(Context ctx, XanderPrefs prefs) {
        this.app = ctx.getApplicationContext();
        this.prefs = prefs;
    }

    public boolean isReady() {
        return ready && handler != null;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public android.hardware.Camera rgbHardware() {
        return rgbCam == null ? null : rgbCam.getCamera();
    }

    public synchronized String initSdk() {
        try {
            FacePassHandler.initSDK(app, "");
            Log.i(TAG, "FacePass " + FacePassHandler.getVersion());
        } catch (Throwable t) {
            return "FacePass init failed: " + t.getMessage();
        }
        if (waitAvailable(4000)) {
            return buildHandler("board chip");
        }
        String online = tryFacepplus();
        if (waitAvailable(6000)) {
            return buildHandler("Face++ " + online);
        }
        String cert = tryLocalCerts();
        if (waitAvailable(4000)) {
            return buildHandler("cert " + cert);
        }
        return "Face license not active on this board. Card tap still works. " + online + " " + cert;
    }

    private String tryFacepplus() {
        try {
            int prep = FacePassHandler.prepare_facepplus(app, "");
            if (prep != FacePassAuthCode.FP_AUTH_OK && prep != 0) {
                return "prepare=" + prep;
            }
            if (FacePassHandler.checkAuth_facepplus()) {
                return "cached";
            }
            int ret = FacePassHandler.auth_facepplus(AUTH_IP, API_KEY, API_SECRET, true);
            return "auth=" + ret;
        } catch (Throwable t) {
            return t.getMessage() == null ? "face++ error" : t.getMessage();
        }
    }

    private String tryLocalCerts() {
        String[] paths = new String[]{
                "/sdcard/Download/FacePass-SDK-RK3568-SMALL-20240401.cert",
                "/sdcard/Download/CBG_Panel_Face_Reco_MiniCapacity---30-Trial-one-stage.cert",
                Environment.getExternalStorageDirectory() + "/Download/FacePass-SDK-RK3568-SMALL-20240401.cert",
                Environment.getExternalStorageDirectory() + "/Download/CBG_Panel_Face_Reco_MiniCapacity---30-Trial-one-stage.cert"
        };
        for (int i = 0; i < paths.length; i++) {
            String cert = readFile(paths[i]);
            if (cert.length() < 40) {
                continue;
            }
            try {
                int a = FacePassHandler.auth_algomall(cert);
                if (a == FacePassAuthCode.FP_AUTH_OK || FacePassHandler.isAvailable()) {
                    return "algomall";
                }
                int m = FacePassHandler.auth_mcvsafe(cert, "");
                if (m == FacePassAuthCode.FP_AUTH_OK || FacePassHandler.isAvailable()) {
                    return "mcvsafe";
                }
            } catch (Throwable ignored) {
            }
        }
        return "no cert";
    }

    private boolean waitAvailable(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            try {
                if (FacePassHandler.isAvailable() || FacePassHandler.isAuthorized()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return false;
            }
        }
        try {
            return FacePassHandler.isAvailable() || FacePassHandler.isAuthorized();
        } catch (Throwable t) {
            return false;
        }
    }

    private String buildHandler(String how) {
        try {
            FacePassConfig config = new FacePassConfig();
            config.poseBlurModel = FacePassModel.initModel(app.getAssets(), "attr.pose_blur.arm.190630.bin");
            config.livenessModel = FacePassModel.initModel(app.getAssets(), "liveness.CPU.rgb.G.bin");
            config.rgbIrLivenessModel = FacePassModel.initModel(app.getAssets(), "liveness.CPU.rgbir.I.bin");
            config.rgbIrGaLivenessModel = FacePassModel.initModel(app.getAssets(), "liveness.CPU.rgbir.ga_case.A.bin");
            config.searchModel = FacePassModel.initModel(app.getAssets(), "feat2.arm.K.v1.0_1core.bin");
            config.detectModel = FacePassModel.initModel(app.getAssets(), "detector.arm.G.bin");
            config.detectRectModel = FacePassModel.initModel(app.getAssets(), "detector_rect.arm.G.bin");
            config.landmarkModel = FacePassModel.initModel(app.getAssets(), "pf.lmk.arm.E.bin");
            config.rcAttributeModel = FacePassModel.initModel(app.getAssets(), "attr.RC.arm.G.bin");
            config.occlusionFilterModel = FacePassModel.initModel(app.getAssets(), "attr.occlusion.arm.20201209.bin");
            config.rcAttributeAndOcclusionMode = 1;
            config.searchThreshold = 55f;
            config.livenessThreshold = 40f;
            config.livenessGaThreshold = 55f;
            config.livenessEnabled = true;
            config.rgbIrLivenessEnabled = true;
            config.rgbIrGaLivenessEnabled = false;
            config.poseThreshold = new FacePassPose(40f, 40f, 40f);
            config.blurThreshold = 0.95f;
            config.lowBrightnessThreshold = 10f;
            config.highBrightnessThreshold = 240f;
            config.brightnessSTDThreshold = 90f;
            config.faceMinThreshold = 40;
            config.retryCount = 10;
            config.smileEnabled = false;
            config.maxFaceEnabled = false;
            File root = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            config.fileRootPath = root == null ? app.getFilesDir().getAbsolutePath() : root.getAbsolutePath();

            FacePassHandler h = new FacePassHandler();
            int ret = h.initHandle(config);
            if (ret != 0) {
                return "FacePass handler failed (" + ret + ")";
            }
            try {
                h.setIRConfig(1.0, 0.0, 1.0, 0.0, 0.3);
            } catch (Throwable ignored) {
            }
            handler = h;
            applyAddFaceConfig();
            ensureGroup();
            int rebound = rebindStoredTokens();
            ready = true;
            Log.i(TAG, "ready via " + how + " group=" + groupSize() + " rebound=" + rebound);
            com.example.smdt.smdtaisdkdemo.xander.KioskLog.i(TAG, "ready via " + how
                    + " faces in group=" + groupSize());
            return "ok:" + how;
        } catch (Throwable t) {
            handler = null;
            return "FacePass handler: " + t.getMessage();
        }
    }

    /**
     * Official FacePass add-face thresholds from TestFacePass 3.14.5
     * (pose / blur / brightness / min face size). Recognition config stays
     * separate via {@link FacePassHandler#getConfig()}.
     */
    private void applyAddFaceConfig() {
        if (handler == null) {
            return;
        }
        try {
            FacePassConfig add = handler.getAddFaceConfig();
            add.poseThreshold = new FacePassPose(35f, 35f, 35f);
            add.blurThreshold = 0.7f;
            add.lowBrightnessThreshold = 70f;
            add.highBrightnessThreshold = 220f;
            add.brightnessSTDThresholdLow = 14.14f;
            add.brightnessSTDThreshold = 63.25f;
            add.faceMinThreshold = 100;
            add.rcAttributeAndOcclusionMode = 2;
            handler.setAddFaceConfig(add);
        } catch (Throwable t) {
            Log.w(TAG, "addFaceConfig: " + t.getMessage());
        }
    }

    public void setSearchEnabled(boolean on) {
        searchEnabled = on;
        if (on) {
            pauseFeed = false;
        }
    }

    public boolean camerasRunning() {
        return running && rgbCam != null;
    }

    public boolean needsCameraRestart() {
        return running && rgbCam != null && handler != null && !camerasConfigured;
    }

    public void resumeMatching() {
        pauseFeed = false;
        searchEnabled = true;
    }

    public void rebindInBackground() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                rebindStoredTokens();
            }
        }, "fp-rebind").start();
    }

    public void setRecognizeEnabled(boolean on) {
        searchEnabled = on;
    }

    public void removeStaffFace(int staffId) {
        unbindPrevious(staffId);
    }

    private void ensureGroup() throws FacePassException {
        String[] groups = handler.getLocalGroups();
        boolean found = false;
        if (groups != null) {
            for (int i = 0; i < groups.length; i++) {
                if (GROUP.equals(groups[i])) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            handler.createLocalGroup(GROUP);
        }
    }

    public void startCameras(SurfaceHolder rgbHolder, SurfaceHolder irHolder, int rgbId, int irId, int previewRotation) {
        stopCameras();
        running = true;
        pauseFeed = false;
        rgbOnlyFeed = false;
        emptyDual = 0;
        camerasConfigured = handler != null;
        dual = false;
        imageRotation = rotationFor(prefs.cameraOrientation());
        rgbCam = new SimpleCamera();
        boolean rgbOk = rgbCam.open(rgbId, rgbHolder, 1280, 720, previewRotation, false,
                new SimpleCamera.Listener() {
                    @Override
                    public void onFrame(CameraPreviewData data) {
                        lastNv21 = data.nv21Data;
                        lastW = data.width;
                        lastH = data.height;
                        if (dual) {
                            ComplexFrameHelper.addRgbFrame(data);
                        } else {
                            rgbQueue.poll();
                            rgbQueue.offer(data);
                        }
                    }
                });
        if (!rgbOk) {
            status("Could not open RGB camera " + rgbId);
            return;
        }
        if (irHolder != null && irId >= 0 && irId != rgbId) {
            irCam = new SimpleCamera();
            dual = irCam.open(irId, irHolder, 1280, 720, previewRotation, false,
                    new SimpleCamera.Listener() {
                        @Override
                        public void onFrame(CameraPreviewData data) {
                            ComplexFrameHelper.addIRFrame(data);
                        }
                    });
            if (!dual) {
                irCam = null;
                status("3D/IR camera " + irId + " not open — RGB liveness only");
            }
        }
        Log.i(TAG, "cam rgb=" + rgbId + " ir=" + irId + " dual=" + dual
                + " previewRot=" + previewRotation + " algoRot=" + imageRotation);
        com.example.smdt.smdtaisdkdemo.xander.KioskLog.i(TAG, "cameras rgb=" + rgbId + " ir=" + irId
                + " 3D=" + dual);
        if (handler != null) {
            try {
                FacePassConfig cfg = handler.getConfig();
                if (dual) {
                    cfg.livenessEnabled = false;
                    cfg.rgbIrLivenessEnabled = true;
                    cfg.rgbIrGaLivenessEnabled = false;
                } else {
                    cfg.livenessEnabled = true;
                    cfg.rgbIrLivenessEnabled = false;
                    cfg.rgbIrGaLivenessEnabled = false;
                }
                cfg.searchThreshold = 55f;
                cfg.livenessThreshold = 40f;
                cfg.livenessGaThreshold = 55f;
                cfg.rgbIrLivenessGpuEnabled = false;
                cfg.rgbIrGaLivenessGpuEnabled = false;
                handler.setConfig(cfg);
                camerasConfigured = true;
            } catch (Throwable ignored) {
            }
        }
        startThreads();
        if (listener != null && ready && !notifiedReady) {
            notifiedReady = true;
            listener.onReady();
        }
    }

    private void applyRgbOnlyLiveness() {
        if (handler == null) {
            return;
        }
        try {
            FacePassConfig cfg = handler.getConfig();
            cfg.livenessEnabled = true;
            cfg.rgbIrLivenessEnabled = false;
            cfg.rgbIrGaLivenessEnabled = false;
            cfg.searchThreshold = 55f;
            cfg.livenessThreshold = 40f;
            handler.setConfig(cfg);
        } catch (Throwable ignored) {
        }
    }

    private void startThreads() {
        feedThread = new Thread(new Runnable() {
            @Override
            public void run() {
                feedLoop();
            }
        }, "fp-feed");
        recThread = new Thread(new Runnable() {
            @Override
            public void run() {
                recLoop();
            }
        }, "fp-rec");
        feedThread.start();
        recThread.start();
    }

    private void feedLoop() {
        while (running) {
            if (handler == null || !ready) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    return;
                }
                continue;
            }
            if (pauseFeed) {
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    return;
                }
                continue;
            }
            try {
                FacePassDetectionResult det;
                if (dual && !rgbOnlyFeed) {
                    Pair<CameraPreviewData, CameraPreviewData> pair = ComplexFrameHelper.poll(180);
                    if (pair == null) {
                        continue;
                    }
                    FacePassImage rgb = new FacePassImage(pair.first.nv21Data, pair.first.width, pair.first.height,
                            imageRotation, FacePassImageType.NV21);
                    FacePassImage ir = new FacePassImage(pair.second.nv21Data, pair.second.width, pair.second.height,
                            imageRotation, FacePassImageType.NV21);
                    det = handler.feedFrameRGBIR(rgb, ir);
                    boolean noFaces = det == null || det.faceList == null || det.faceList.length == 0;
                    boolean noMsg = det == null || det.message == null || det.message.length == 0;
                    if (noFaces) {
                        emptyDual++;
                        if (noMsg) {
                            det = handler.feedFrame(rgb);
                        }
                        if (emptyDual > 20) {
                            rgbOnlyFeed = true;
                            Log.w(TAG, "RGBIR empty — falling back to RGB detect");
                            applyRgbOnlyLiveness();
                        }
                    } else {
                        emptyDual = 0;
                    }
                } else if (dual) {
                    Pair<CameraPreviewData, CameraPreviewData> pair = ComplexFrameHelper.poll(180);
                    if (pair == null) {
                        continue;
                    }
                    FacePassImage rgb = new FacePassImage(pair.first.nv21Data, pair.first.width, pair.first.height,
                            imageRotation, FacePassImageType.NV21);
                    det = handler.feedFrame(rgb);
                } else {
                    CameraPreviewData frame = rgbQueue.take();
                    CameraPreviewData copy = ComplexFrameHelper.copyFrame(frame);
                    FacePassImage rgb = new FacePassImage(copy.nv21Data, copy.width, copy.height,
                            imageRotation, FacePassImageType.NV21);
                    det = handler.feedFrame(rgb);
                }
                if (det == null) {
                    continue;
                }
                publishFaces(det);
                keepDetImage(det);
                if (!searchEnabled && det.faceList != null && det.faceList.length > 0 && listener != null) {
                    Bitmap snap = snapshot();
                    if (snap != null) {
                        listener.onPreviewFace(snap);
                    }
                }
                if (det.message != null && det.message.length > 0) {
                    RecJob job = new RecJob();
                    job.message = det.message;
                    job.opts = searchEnabled ? null : trackOpts(det);
                    recQueue.poll();
                    recQueue.offer(job);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                Log.w(TAG, "feed: " + t.getMessage());
            }
        }
    }

    private void recLoop() {
        while (running) {
            try {
                RecJob job = recQueue.take();
                if (handler == null) {
                    continue;
                }
                // Enroll only: livenessClassify. Official FacePass demo does NOT
                // call it before recognize — the same message is one-shot.
                if (!searchEnabled) {
                    classifyEnrollLiveness(job);
                    continue;
                }
                // Official FacePass API: 1-D recognize(group, message). The 2-D
                // topN+trackOpts form was returning empty tokens on this board.
                FacePassRecognitionResult[] arr = handler.recognize(GROUP, job.message);
                int outcome = applyRecognition(arr);
                if (outcome == 0 && listener != null) {
                    listener.onUnknown();
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                Log.w(TAG, "rec: " + t.getMessage());
            }
        }
    }

    /**
     * @return 1 match, 0 unknown face, -1 still tracking / no verdict yet
     */
    private int applyRecognition(FacePassRecognitionResult[] arr) {
        if (arr == null || arr.length == 0) {
            return -1;
        }
        boolean unknown = false;
        boolean hit = false;
        for (int i = 0; i < arr.length; i++) {
            FacePassRecognitionResult one = arr[i];
            if (one == null) {
                continue;
            }
            float search = one.detail == null ? 0f : one.detail.searchScore;
            float live = one.detail == null ? 0f : one.detail.livenessScore;
            boolean hasToken = one.faceToken != null && one.faceToken.length > 0;
            int state = one.recognitionState;
            long now = System.currentTimeMillis();
            if (now - lastRecLog > 1500) {
                lastRecLog = now;
                Log.i(TAG, "rec state=" + state + " search=" + search + " live=" + live
                        + " token=" + (hasToken ? one.faceToken.length : 0)
                        + " liveErr=" + one.livenessErrorCode
                        + " searchErr=" + one.searchErrorCode
                        + " group=" + groupSize());
            }
            if (state == FacePassRecognitionState.RECOGNITION_RETRY
                    || state == FacePassRecognitionState.RECOGNITION_TRACK_MISSING) {
                continue;
            }
            if (!hasToken && isSpoof(one) && search < 55f) {
                if (listener != null) {
                    listener.onSpoof();
                }
                continue;
            }
            boolean pass = hasToken && (state == FacePassRecognitionState.RECOGNITION_PASS
                    || search >= 52f);
            if (!pass) {
                unknown = true;
                continue;
            }
            int staffId = prefs.staffIdForToken(tokenKey(one.faceToken));
            if (staffId <= 0) {
                staffId = prefs.staffIdForTokenBytes(one.faceToken);
            }
            Log.i(TAG, "recognize pass search=" + search + " live=" + live
                    + " staff=" + staffId + " tokenLen="
                    + (one.faceToken == null ? 0 : one.faceToken.length));
            if (staffId > 0) {
                hit = true;
                if (listener != null) {
                    listener.onRecognized(staffId);
                }
            } else {
                Log.w(TAG, "FacePass matched a token that is not mapped to staff");
                unknown = true;
            }
        }
        if (hit) {
            return 1;
        }
        return unknown ? 0 : -1;
    }

    private void classifyEnrollLiveness(RecJob job) {
        boolean live = false;
        boolean spoof = false;
        float liveScore = 0f;
        FacePassLivenessResult[] lr = null;
        try {
            lr = handler.livenessClassify(job.message, job.opts);
            if (lr != null) {
                for (int i = 0; i < lr.length; i++) {
                    FacePassLivenessResult one = lr[i];
                    if (one == null) {
                        continue;
                    }
                    liveScore = Math.max(liveScore, one.livenessScore);
                    if (one.livenessErrorCode != 0) {
                        spoof = true;
                        continue;
                    }
                    float th = one.livenessThreshold > 0 ? one.livenessThreshold : 70f;
                    if (one.livenessScore > 0 && one.livenessScore < th) {
                        spoof = true;
                    } else if (one.livenessScore >= th) {
                        live = true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (listener != null) {
            listener.onLiveness(live, spoof && !live, liveScore, dual);
            if (spoof && !live) {
                listener.onSpoof();
            }
        }
    }

    public Bitmap snapshot() {
        byte[] nv = lastNv21;
        if (nv == null || lastW <= 0) {
            return null;
        }
        try {
            YuvImage yuv = new YuvImage(nv, ImageFormat.NV21, lastW, lastH, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, lastW, lastH), 80, out);
            byte[] jpg = out.toByteArray();
            return BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
        } catch (Throwable t) {
            return null;
        }
    }

    public String enrollStaff(int staffId, Bitmap bmp) {
        if (handler == null || bmp == null) {
            return "Face engine not ready";
        }
        pauseFeed = true;
        try {
            FacePassAddFaceResult result = handler.addFace(bmp);
            if (!okAdd(result)) {
                result = handler.addFace(bmp, imageRotation);
            }
            return finishEnroll(staffId, result);
        } catch (Throwable t) {
            return t.getMessage() == null ? "Enroll failed" : t.getMessage();
        } finally {
            pauseFeed = false;
        }
    }

    /**
     * Encode one live frame (detected FacePass image first, then NV21) and
     * bind the token to this staff. Call three times for a robust template.
     */
    public String enrollShot(int staffId, boolean resetPrevious) {
        if (handler == null) {
            return "Face engine not ready";
        }
        pauseFeed = true;
        try {
            if (resetPrevious) {
                unbindPrevious(staffId);
            }
            FacePassAddFaceResult result = null;
            FacePassImage det = lastDetImage;
            if (det != null) {
                try {
                    result = handler.addFace(det);
                } catch (Throwable ignored) {
                }
            }
            byte[] nv = lastNv21;
            int w = lastW;
            int h = lastH;
            if (!okAdd(result) && nv != null && w > 0 && h > 0) {
                byte[] copy = new byte[nv.length];
                System.arraycopy(nv, 0, copy, 0, nv.length);
                FacePassImage image = new FacePassImage(copy, w, h, imageRotation, FacePassImageType.NV21);
                result = handler.addFace(image);
                if (!okAdd(result)) {
                    Bitmap bmp = snapshotFrom(copy, w, h);
                    if (bmp != null) {
                        result = handler.addFace(bmp, imageRotation);
                        if (!okAdd(result)) {
                            result = handler.addFace(upright(bmp));
                        }
                    }
                }
                if (!okAdd(result) && result != null && result.result != 1) {
                    relaxAddFaceMinSize(70);
                    result = handler.addFace(image);
                    applyAddFaceConfig();
                }
            }
            return bindShot(staffId, result);
        } catch (Throwable t) {
            return t.getMessage() == null ? "Enroll failed" : t.getMessage();
        } finally {
            pauseFeed = false;
        }
    }

    public String enrollStaffLive(int staffId) {
        return enrollShot(staffId, true);
    }

    public Bitmap uprightSnapshot() {
        return upright(snapshot());
    }

    private String finishEnroll(int staffId, FacePassAddFaceResult result) {
        if (result == null) {
            return "No face result";
        }
        if (result.result == 1) {
            return "No face in the frame. Fill the green circle and look at the camera.";
        }
        if (result.result != 0) {
            return "Face quality too low. Hold still, move closer, and use even light.";
        }
        byte[] token = result.faceToken;
        if (token == null || token.length == 0) {
            return "FacePass did not return a face token";
        }
        unbindPrevious(staffId);
        try {
            if (!bindToken(token)) {
                return "Could not save face to the kiosk group";
            }
        } catch (Throwable t) {
            return "Could not save face to the kiosk group";
        }
        saveTokenMapping(staffId, token);
        Log.i(TAG, "enrolled staff " + staffId + " blur=" + result.blur + " group=" + groupSize());
        return null;
    }

    private String bindShot(int staffId, FacePassAddFaceResult result) {
        if (result == null) {
            return "No face result";
        }
        if (result.result == 1) {
            return "No face in the frame. Fill the green circle and look at the camera.";
        }
        if (result.result != 0) {
            return "Face quality too low. Hold still, move closer, and use even light.";
        }
        byte[] token = result.faceToken;
        if (token == null || token.length == 0) {
            return "FacePass did not return a face token";
        }
        try {
            if (!bindToken(token)) {
                return "Could not save face to the kiosk group";
            }
        } catch (Throwable t) {
            return "Could not save face to the kiosk group";
        }
        saveTokenMapping(staffId, token);
        Log.i(TAG, "enroll shot staff " + staffId + " blur=" + result.blur + " group=" + groupSize());
        com.example.smdt.smdtaisdkdemo.xander.KioskLog.i(TAG, "saved token for staff " + staffId
                + " blur=" + result.blur + " group=" + groupSize());
        return null;
    }

    private void keepDetImage(FacePassDetectionResult det) {
        if (det == null || det.images == null || det.images.length == 0 || det.images[0] == null) {
            return;
        }
        FacePassImage src = det.images[0];
        if (src.image == null || src.image.length == 0 || src.width <= 0) {
            return;
        }
        try {
            byte[] copy = new byte[src.image.length];
            System.arraycopy(src.image, 0, copy, 0, src.image.length);
            lastDetImage = new FacePassImage(copy, src.width, src.height,
                    src.facePassImageRotation, src.facePassImageType);
        } catch (Throwable ignored) {
            lastDetImage = src;
        }
    }

    private static FacePassTrackOptions[] trackOpts(FacePassDetectionResult det) {
        if (det == null || det.images == null || det.images.length == 0) {
            return null;
        }
        FacePassTrackOptions[] opts = new FacePassTrackOptions[det.images.length];
        for (int i = 0; i < det.images.length; i++) {
            if (det.images[i] == null) {
                continue;
            }
            opts[i] = new FacePassTrackOptions(det.images[i].trackId, 55f, 58f, 68f, -1f);
        }
        return opts;
    }

    private void unbindPrevious(int staffId) {
        String old = prefs.tokenForStaff(staffId);
        if (handler != null && old != null && old.length() > 0) {
            String[] parts = old.split("\n");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] == null || parts[i].length() == 0) {
                    continue;
                }
                try {
                    byte[] tok = decodeStoredToken(parts[i]);
                    if (tok != null && tok.length > 0) {
                        handler.unBindGroup(GROUP, tok);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        prefs.clearFaceTokens(staffId);
        prefs.setStaffFaceEnrolled(staffId, false);
    }

    private void saveTokenMapping(int staffId, byte[] token) {
        prefs.addFaceToken(staffId, tokenKey(token));
    }

    private boolean bindToken(byte[] token) {
        if (handler == null || token == null || token.length == 0) {
            return false;
        }
        try {
            if (handler.bindGroup(GROUP, token)) {
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "bindGroup: " + t.getMessage());
        }
        if (tokenInGroup(token)) {
            return true;
        }
        try {
            return handler.bindGroup(GROUP, token);
        } catch (Throwable t) {
            Log.w(TAG, "bindGroup retry: " + t.getMessage());
            return tokenInGroup(token);
        }
    }

    private boolean tokenInGroup(byte[] token) {
        String want = tokenKey(token);
        String wantRaw = new String(token);
        try {
            byte[][] existing = handler.getLocalGroupInfo(GROUP);
            if (existing == null) {
                return false;
            }
            for (int i = 0; i < existing.length; i++) {
                if (existing[i] == null || existing[i].length == 0) {
                    continue;
                }
                if (want.equals(tokenKey(existing[i])) || wantRaw.equals(new String(existing[i]))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private int groupSize() {
        try {
            byte[][] existing = handler.getLocalGroupInfo(GROUP);
            return existing == null ? 0 : existing.length;
        } catch (Throwable t) {
            return -1;
        }
    }

    private int rebindStoredTokens() {
        int n = 0;
        java.util.Map<Integer, String> all = prefs.allFaceTokens();
        for (java.util.Map.Entry<Integer, String> e : all.entrySet()) {
            String[] parts = e.getValue().split("\n");
            for (int i = 0; i < parts.length; i++) {
                byte[] tok = decodeStoredToken(parts[i]);
                if (tok == null || tok.length == 0) {
                    continue;
                }
                if (bindToken(tok)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static byte[] decodeStoredToken(String part) {
        if (part == null || part.length() == 0) {
            return null;
        }
        if (looksBase64(part)) {
            try {
                byte[] b64 = android.util.Base64.decode(part, android.util.Base64.NO_WRAP);
                if (b64 != null && b64.length > 0) {
                    return b64;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            return part.getBytes("ISO-8859-1");
        } catch (Exception e) {
            return part.getBytes();
        }
    }

    private static boolean looksBase64(String s) {
        int n = s.length();
        if (n < 8 || (n % 4) != 0) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private void relaxAddFaceMinSize(int min) {
        try {
            FacePassConfig add = handler.getAddFaceConfig();
            add.faceMinThreshold = min;
            add.blurThreshold = 0.8f;
            handler.setAddFaceConfig(add);
        } catch (Throwable ignored) {
        }
    }

    private static boolean okAdd(FacePassAddFaceResult result) {
        return result != null && result.result == 0 && result.faceToken != null && result.faceToken.length > 0;
    }

    private Bitmap snapshotFrom(byte[] nv, int w, int h) {
        try {
            YuvImage yuv = new YuvImage(nv, ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, w, h), 90, out);
            byte[] jpg = out.toByteArray();
            return BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap upright(Bitmap bmp) {
        if (bmp == null) {
            return null;
        }
        float deg = 0f;
        if (imageRotation == FacePassImageRotation.DEG90) {
            deg = 90f;
        } else if (imageRotation == FacePassImageRotation.DEG180) {
            deg = 180f;
        } else if (imageRotation == FacePassImageRotation.DEG270) {
            deg = 270f;
        }
        if (deg == 0f) {
            return bmp;
        }
        Matrix m = new Matrix();
        m.postRotate(deg);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
    }

    public void stopCameras() {
        running = false;
        ComplexFrameHelper.clear();
        rgbQueue.clear();
        recQueue.clear();
        camerasConfigured = false;
        if (feedThread != null) {
            feedThread.interrupt();
            feedThread = null;
        }
        if (recThread != null) {
            recThread.interrupt();
            recThread = null;
        }
        if (rgbCam != null) {
            rgbCam.close();
            rgbCam = null;
        }
        if (irCam != null) {
            irCam.close();
            irCam = null;
        }
    }

    public void release() {
        stopCameras();
        ready = false;
        if (handler != null) {
            try {
                handler.release();
            } catch (Throwable ignored) {
            }
            handler = null;
        }
    }

    private void status(String msg) {
        Log.i(TAG, msg);
        if (listener != null) {
            listener.onStatus(msg);
        }
    }

    private void publishFaces(FacePassDetectionResult det) {
        if (listener == null) {
            return;
        }
        int count = det.faceList == null ? 0 : det.faceList.length;
        android.graphics.Rect[] boxes = new android.graphics.Rect[count];
        for (int i = 0; i < count; i++) {
            if (det.faceList[i] == null || det.faceList[i].rect == null) {
                continue;
            }
            mcv.facepass.types.FacePassRect r = det.faceList[i].rect;
            boxes[i] = new android.graphics.Rect(r.left, r.top, r.right, r.bottom);
        }
        int w = lastW;
        int h = lastH;
        if (imageRotation == FacePassImageRotation.DEG90 || imageRotation == FacePassImageRotation.DEG270) {
            w = lastH;
            h = lastW;
        }
        listener.onFaces(boxes, w <= 0 ? 720 : w, h <= 0 ? 1280 : h, dual);
    }

    private static boolean isSpoof(FacePassRecognitionResult one) {
        if (one.detail == null || one.detail.livenessScore <= 0) {
            return false;
        }
        if (one.detail.livenessThreshold > 0 && one.detail.livenessScore < one.detail.livenessThreshold) {
            return true;
        }
        return one.livenessErrorCode != 0;
    }

    public boolean isDual() {
        return dual;
    }

    private static int rotationFor(int orient) {
        if (orient == 0) {
            return FacePassImageRotation.DEG0;
        }
        if (orient == 1) {
            return FacePassImageRotation.DEG90;
        }
        if (orient == 2) {
            return FacePassImageRotation.DEG180;
        }
        return FacePassImageRotation.DEG270;
    }

    private static String tokenKey(byte[] token) {
        if (token == null || token.length == 0) {
            return "";
        }
        return android.util.Base64.encodeToString(token, android.util.Base64.NO_WRAP);
    }

    private static String readFile(String path) {
        File f = new File(path);
        if (!f.isFile() || f.length() < 40 || f.length() > 200000) {
            return "";
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = in.read(buf);
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n);
        } catch (Exception e) {
            return "";
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
