package com.xander.pos.palm

import android.graphics.Bitmap
import android.hardware.usb.UsbManager
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.hfims.android.lib.palm.IPalmSdk
import com.hfims.android.lib.palm.PalmConfig
import com.hfims.android.lib.palm.PalmSdk
import com.hfims.android.lib.palm.model.PalmCamera
import com.hfims.android.lib.palm.model.PalmTrack
import com.hfims.android.lib.palm.ui.HCameraConfig
import com.hfims.android.lib.palm.ui.HTextureView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
/**
 * Embedded VP530 palm scanner on Smart Pos X60 (Sonix + RGB UVC inside the till).
 * Same HFims PalmSdk / HTextureView path as the installed Vp530Demo — not JXPalm OTG.
 *
 * Vp530Demo AppSettings / HTextureView defaults name Sonix as the RGB USB camera and the
 * companion sensor as IR. On this X60 the companion is Usb Webcam 1287/1409 (not 7532/259).
 */
class PalmEngine(
    private val activity: ComponentActivity,
    private val store: PalmVeinStore,
    private val events: (PalmEvent) -> Unit,
) {
    private val sdk: IPalmSdk by lazy { PalmSdk.getInstance() }
    private var resumed = false
    private var task: PalmTask = PalmTask.Stop
    private var initialized = false
    private var initStarted = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val opening = AtomicBoolean(false)
    private val frameLock = Any()
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame

    private var host: FrameLayout? = null
    private var previewRoot: FrameLayout? = null
    private var parked: FrameLayout? = null
    /** UVC must stay closed until palm init finishes. Opening it during authenByIC SIGSEGVs in uvc_stop_streaming. */
    private var pendingHost: FrameLayout? = null
    private var layoutWait: android.view.View.OnLayoutChangeListener? = null
    /** Collect started before the vein camera produced a frame. Restart once it does. */
    private var enrollAwaitingIr = false
    private var irView: HTextureView? = null
    private var rgbView: HTextureView? = null
    private var latestIr: PalmCamera? = null
    private var latestRgb: PalmCamera? = null
    private var lastIrAt = 0L
    /** Ignore the leftover frame for a moment, then read the live palm. */
    @Volatile private var scanAfter = 0L
    private var lastRgbAt = 0L
    private var dualExpected = false
    private var enrolling = false
    private var enrollTaken = 0
    private val enrollSamples = ArrayList<String>(3)
    private var rgbFrameCount = 0
    private var irFrameCount = 0
    private var pushCount = 0
    private var loggedFrameSize = false
    private var lastPalmNullAt = 0L
    private var lastHoldStillAt = 0L
    private var lastDistanceLogAt = 0L
    private var activeCollectUid: String? = null
    private var activeCollectHand = PalmHand.Left
    /** Right-hand capture stays closed until the left palm leaves the glass. */
    private var acceptAfterLift = false
    private var lifted = true
    private var liftSawPalm = false
    private var liftToken = 0
    private var lastLiftPromptAt = 0L
    /** Payment loads one card. Recording reloads every saved palm before the next capture. */
    private var galleryDirty = true
    private val galleryLock = Any()
    private val collectGen = AtomicInteger()
    /** Drops a restart that was queued inside a collect callback the SDK then stops. */
    private val collectRestart = AtomicInteger()
    private val enrollPump = AtomicInteger()
    private val payPump = AtomicInteger()
    private val grabBusy = AtomicBoolean(false)
    private var lastGrabAt = 0L
    private val previewBusy = AtomicBoolean(false)
    private val previewExecutor = Executors.newSingleThreadExecutor()
    /** Same as Vp530Demo CameraViewModel — drop frames if the SDK is busy. */
    private val feedExecutor = Executors.newSingleThreadExecutor()
    private val claimExecutor = Executors.newSingleThreadExecutor()
    private val feeding = AtomicBoolean(false)
    private val feedPaused = AtomicBoolean(false)
    /** YUVUtils.rotateYUV180 writes one static buffer. Feed and extract must not share it. */
    private val yuvLock = Any()
    private val claimLock = Any()
    private var claimToken = 0

    private val collectCb = object : IPalmSdk.CollectCallback {
        override fun onDistance(distance: Float) {
            if (task !is PalmTask.Enroll) return
            val now = System.currentTimeMillis()
            if (now - lastDistanceLogAt > 800) {
                lastDistanceLogAt = now
                Log.i(TAG, "collect onDistance=$distance")
            }
            if (distance > 0f) {
                when {
                    distance < 3f -> post(PalmEvent.Status("A little farther — about a hand’s width.", true))
                    distance > 12f -> post(PalmEvent.Status("Closer — about 7–10 cm over the glass.", true))
                    else -> Unit
                }
            }
        }

        override fun onFailure(code: Int, sub: Int, a: String?, b: String?, map: MutableMap<String, Any>?) {
            Log.e(TAG, "collect onFailure code=$code sub=$sub aLen=${a?.length} b=$b hand=${handFromMap(map)} map=$map")
            if (paying) {
                onPayCapture(code, a, b)
                return
            }
            if (task is PalmTask.Enroll && code == 1) {
                val uid = activeCollectUid
                val matchedId = b?.trim().orEmpty()
                val matchedCard = cardUidForSdkId(matchedId)
                val ours = uid != null && (
                    matchedCard?.equals(uid, true) == true ||
                        (0 until ENROLL_COUNT).any { sdkSlotId(uid, it) == matchedId }
                    )
                if (ours) {
                    val live = cleanBase64(a.orEmpty()).ifBlank { enrollSamples.firstOrNull().orEmpty() }
                    takeEnrollSample(live, matchedThisCard = true)
                    return
                }
            }
            if (task is PalmTask.Enroll && code != 1) {
                val sdkCode = (map?.get("errorCode") as? Number)?.toInt()
                Log.w(TAG, "enroll strict extract gave up code=$code sdk=$sdkCode sample=${enrollSamples.size + 1}")
                return
            }
            if (deferUntilLifted()) return
            val existingFeat = a?.takeIf { it.length > 32 && code == 1 }
            val cardUid = activeCollectUid ?: (task as? PalmTask.Enroll)?.uid?.uppercase()
            val hand = activeCollectHand
            val matchedId = b?.trim().orEmpty()
            val matchedCard = cardUidForSdkId(matchedId)
            val sameCard = !cardUid.isNullOrBlank() && (
                matchedId.equals(cardUid, true) ||
                    matchedCard?.equals(cardUid, true) == true
                )
            val matchedHand = if (cardUid.isNullOrBlank()) null else handOfSdkId(matchedId, cardUid)
            val uid = cardUid
            if (code == 1 && existingFeat != null && uid != null && sameCard && matchedHand == hand) {
                enrolling = false
                activeCollectUid = null
                registerFeature(uid, existingFeat, hand)
                say("Palm saved")
                post(PalmEvent.Enrolled(uid, existingFeat))
                return
            }
            if (code == 1 && existingFeat != null) {
                enrolling = false
                activeCollectUid = null
                val owner = matchedCard?.let { store.find(it)?.name?.trim() }.orEmpty().ifBlank { "another student" }
                say("Already recorded")
                post(PalmEvent.EnrollFailed("This palm is already recorded for $owner. It cannot be saved on another student."))
                return
            }
            enrolling = false
            activeCollectUid = null
            say("Try again")
            val reason = when (code) {
                3 -> "The vein is not clear enough. Hold the whole palm flat, about 8 cm, then tap Retry."
                1 -> "This palm is already on the till. Try the other hand, or another student."
                else -> b?.takeIf { it.length in 1..80 }
                    ?: a?.takeIf { it.length in 1..80 }
                    ?: "Palm not recorded. Hold still and try again."
            }
            post(PalmEvent.EnrollFailed(reason))
        }

        override fun onImage(bitmap: Bitmap?, tracks: MutableList<PalmTrack>?, map: MutableMap<String, Any>?) = Unit

        override fun onPalmIn(tracks: MutableList<PalmTrack>?, map: MutableMap<String, Any>?) {
            Log.i(TAG, "collect onPalmIn tracks=${tracks?.size ?: 0}")
            if (paying) {
                val now = System.currentTimeMillis()
                if (now - lastHoldStillAt > 800) {
                    lastHoldStillAt = now
                    post(PalmEvent.Status("Matching the recorded palm…", true))
                }
                return
            }
            if (task is PalmTask.Enroll) {
                val now = System.currentTimeMillis()
                if (now - lastHoldStillAt > 800) {
                    lastHoldStillAt = now
                    post(PalmEvent.Status("Infrared sample ${enrollSamples.size + 1} of $ENROLL_COUNT. Either palm.", true))
                }
                grabEnrollFeature()
                return
            }
        }

        override fun onPalmNull(code: Int, map: MutableMap<String, Any>?) {
            if (acceptAfterLift && !lifted && task is PalmTask.Enroll) {
                finishLift()
                return
            }
            val now = System.currentTimeMillis()
            if (now - lastPalmNullAt > 1500) {
                lastPalmNullAt = now
                Log.i(TAG, "collect onPalmNull code=$code")
                if (task is PalmTask.Enroll) {
                    post(PalmEvent.Status("Place the palm flat again, 7–10 cm over the glass.", true))
                }
            }
        }

        override fun onSuccess(id: String?, feature: String?, extra: String?, map: MutableMap<String, Any>?) {
            // TaskStartCollect calls onSuccess(feature, feature, palmId, map) — not (id, feature, …).
            Log.i(TAG, "collect onSuccess idLen=${id?.length} featLen=${feature?.length} extra=$extra hand=${handFromMap(map)}")
            if (paying) {
                val feat = sequenceOf(feature, id)
                    .mapNotNull { it?.trim()?.takeIf { s -> s.length > 32 } }
                    .firstOrNull()
                    .orEmpty()
                onPayFeature(feat)
                return
            }
            if (deferUntilLifted()) return
            if (task is PalmTask.Enroll) {
                val feat = sequenceOf(feature, id)
                    .mapNotNull { it?.trim()?.takeIf { s -> s.length > 32 } }
                    .firstOrNull()
                    .orEmpty()
                val encoded = if (feat.matches(BASE64)) feat else Base64.encodeToString(feat.toByteArray(), Base64.NO_WRAP)
                takeEnrollSample(encoded, matchedThisCard = false)
                return
            }
            enrolling = false
            val cardUid = (activeCollectUid ?: (task as? PalmTask.Enroll)?.uid).orEmpty().uppercase()
            activeCollectUid = null
            val feat = sequenceOf(feature, id)
                .mapNotNull { it?.trim()?.takeIf { s -> s.length > 32 } }
                .firstOrNull()
                .orEmpty()
            if (cardUid.isBlank() || feat.isBlank()) {
                Log.e(TAG, "collect onSuccess missing cardUid or feature")
                post(PalmEvent.EnrollFailed("Palm feature was empty. Try again."))
                return
            }
            val encoded = if (feat.matches(BASE64)) feat else Base64.encodeToString(feat.toByteArray(), Base64.NO_WRAP)
            claimUniquePalm(cardUid, encoded, encoded)
        }
    }

    private var verifyPalmSeen = false
    private var verifyAccepted = false
    private var verifyStoredFeature: String? = null
    private var verifyCardUid: String? = null
    private val lastVerifyRejectAt = AtomicLong(0L)
    private val payBusy = AtomicBoolean(false)
    private var paying = false
    private var lookupSaidFor = ""
    private var lookupMatchedAt = 0L
    private var payUid = ""
    private var payLeft = ""
    private var payRight = ""
    private var paySamples: List<String> = emptyList()
    private var savedEnrollQuality: Int? = null
    private var savedLiveness: Boolean? = null
    private var savedPointResponse: Float? = null
    private var lastExtractLogAt = 0L
    private var lastEnrollSampleAt = 0L

    private val recognizeCb = object : IPalmSdk.RecognizeCallback {
        override fun onDistance(distance: Float) = Unit

        override fun onFailure(code: Int, sub: Int, map: MutableMap<String, Any>?) {
            Log.d(TAG, "recognize onFailure code=$code sub=$sub")
            if (acceptAfterLift && !lifted) return
            // Only reject after a palm was actually seen — do not spam on empty frames.
            if (task is PalmTask.Verify && verifyPalmSeen && !verifyAccepted) {
                verifyPalmSeen = false
                throttleReject()
                // SDK may stop after failed identify budget — keep payment listening.
                activity.window.decorView.postDelayed({
                    if (resumed && !verifyAccepted && task is PalmTask.Verify) {
                        try {
                            sdk.startRecognize(HashMap<String, Any>())
                            Log.i(TAG, "restartRecognize after failure")
                        } catch (error: Throwable) {
                            Log.w(TAG, "restartRecognize", error)
                        }
                    }
                }, 300)
            }
        }

        override fun onImage(bitmap: Bitmap?, tracks: MutableList<PalmTrack>?, map: MutableMap<String, Any>?) = Unit

        override fun onPalmIn(tracks: MutableList<PalmTrack>?, map: MutableMap<String, Any>?) {
            Log.i(TAG, "recognize onPalmIn tracks=${tracks?.size ?: 0}")
            if (acceptAfterLift && !lifted && task is PalmTask.Enroll) {
                liftSawPalm = true
                val now = System.currentTimeMillis()
                if (now - lastLiftPromptAt > 2000) {
                    lastLiftPromptAt = now
                    post(PalmEvent.Status("Lift the left palm off the glass, then place the right palm.", true))
                }
                return
            }
            if (task is PalmTask.Verify) {
                verifyPalmSeen = true
                post(PalmEvent.Status("Palm detected. Checking…", true))
            } else {
                post(PalmEvent.Status("Palm seen. Tap the student card to record.", true))
            }
        }

        override fun onPalmNull(code: Int, map: MutableMap<String, Any>?) {
            if (acceptAfterLift && !lifted && task is PalmTask.Enroll) {
                finishLift()
                return
            }
            val now = System.currentTimeMillis()
            if (now - lastPalmNullAt > 1500) {
                lastPalmNullAt = now
                Log.i(TAG, "recognize onPalmNull code=$code")
            }
        }

        override fun onSuccess(id: String?, score: Int, map: MutableMap<String, Any>?) {
            if (acceptAfterLift && !lifted && task is PalmTask.Enroll) {
                // The palm still on the glass matched someone. Keep watching until it lifts.
                activity.window.decorView.postDelayed({
                    if (acceptAfterLift && !lifted && task is PalmTask.Enroll && resumed) {
                        try {
                            sdk.startRecognize(HashMap<String, Any>())
                        } catch (error: Throwable) {
                            Log.w(TAG, "lift recognize", error)
                        }
                    }
                }, 200)
                return
            }
            if (verifyAccepted) return
            verifyPalmSeen = false
            val sdkId = id.orEmpty().trim()
            val expected = (task as? PalmTask.Verify)?.uid?.uppercase().orEmpty()
            // Gallery holds only the tapped card, so a passing score is that recorded palm.
            val cardUid = (cardUidForSdkId(sdkId) ?: verifyCardUid).orEmpty().uppercase()
            // Same bar as the HP605 sample: IDENTIFY_THRESOLD 650, accept only at or above it.
            val ok = task is PalmTask.Verify &&
                expected.isNotBlank() &&
                sameCard(cardUid, expected) &&
                score >= MATCH_SCORE
            Log.i(TAG, "recognize onSuccess sdkId=$sdkId card=$cardUid score=$score ok=$ok expected=$expected")
            if (ok) {
                acceptVerify(cardUid, score)
            } else if (task is PalmTask.Verify && score > 0) {
                throttleReject()
            }
        }
    }

    init {
        tts = TextToSpeech(activity) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }
    }

    /**
     * Attach dual embedded UVC previews (Sonix primary + Webcam companion), matching Vp530Demo.
     *
     * HTextureView.init() already parents itself into an internal LinearLayout (`getFrame()`).
     * The host must add that frame — never the TextureView itself — or addView throws
     * "The specified child already has a parent".
     */
    fun attachHost(container: FrameLayout) {
        if (!initialized) {
            pendingHost = container
            ensureInit()
            return
        }
        // The first Compose pass attaches this view before it has a size. Opening UVC
        // then leaves the vein camera black until the screen is built again.
        if (container.width <= 0 || container.height <= 0) {
            pendingHost = container
            if (layoutWait == null) {
                val listener = android.view.View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    if (view.width > 0 && view.height > 0 && pendingHost === view) {
                        view.removeOnLayoutChangeListener(layoutWait)
                        layoutWait = null
                        attachHost(view as FrameLayout)
                    }
                }
                layoutWait = listener
                container.addOnLayoutChangeListener(listener)
            }
            return
        }
        pendingHost = null
        val root = previewRoot
        if (rgbView != null && root != null) {
            val moved = root.parent !== container
            if (moved) {
                (root.parent as? ViewGroup)?.removeView(root)
                root.alpha = 1f
                root.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                container.addView(root)
            }
            host = container
            if (resumed) resumeCameras()
            applyScanSurface()
            if (task is PalmTask.Enroll || task is PalmTask.Verify || task is PalmTask.Lookup) {
                turnVeinLightOn(force = true)
            }
            // Moving the view destroys the surface. HTextureView then closes the vein
            // camera and does not open it again, so payment and Verify stay black.
            if (scanPreview()) scheduleScanRevive(container, force = moved)
            else if (moved && task is PalmTask.Enroll) scheduleRecordRevive(container)
            return
        }
        try {
            host = container
            container.removeAllViews()
            val holder = FrameLayout(activity)
            previewRoot = holder
            val cams = resolveCameras()
            // Color thumbnail uses the X60 mount. The vein camera matches Vp530Demo:
            // SettingVar.previewRotation 0, no mirror, full-frame IR.
            val rgbConfig = HCameraConfig.Builder()
                .setCameraWidth(PREVIEW_W)
                .setCameraHeight(PREVIEW_H)
                .setRealWidth(PREVIEW_W)
                .setRealHeight(PREVIEW_H)
                .setPreviewRotation(PREVIEW_ROTATION)
                .setTrackMirrorV(IMAGE_MIRROR_V)
                .setTrackMirrorH(IMAGE_MIRROR_H)
                .build()
            val irConfig = HCameraConfig.Builder()
                .setCameraWidth(PREVIEW_W)
                .setCameraHeight(PREVIEW_H)
                .setRealWidth(PREVIEW_W)
                .setRealHeight(PREVIEW_H)
                .setPreviewRotation(PREVIEW_ROTATION)
                .setTrackMirrorV(IMAGE_MIRROR_V)
                .setTrackMirrorH(IMAGE_MIRROR_H)
                .build()

            dualExpected = cams.dual
            veinLightOn = false
            // Sonix palm module only. Xinran LED openLed/onConnect SIGSEGV on this X60 — skip LED.
            // HTextureView.beforePreview already calls UVCCamera.openImage() for the vein channel.
            val rgb = HTextureView(activity).apply {
                setRgbCamera(true)
                setRgbUSBCameraParams(cams.rgbVid, cams.rgbPid)
                setCameraConfig(rgbConfig)
                try {
                    setTargetFps(RGB_FPS)
                } catch (error: Throwable) {
                    Log.w(TAG, "rgb fps", error)
                }
                setScaleType(HTextureView.ScaleType.FIT_CENTER)
                setPortrait(true)
                setListener(HTextureView.OnCameraDataEnableListener { data, _ ->
                    noteFrameSize(data)
                    // UVC reuses the buffer — copy before the next frame overwrites it.
                    latestRgb = palmFrame(data.copyOf())
                    lastRgbAt = System.currentTimeMillis()
                    rgbFrameCount++
                    pushFrames()
                })
            }
            val ir: HTextureView? = if (cams.dual) {
                HTextureView(activity).apply {
                    setRgbCamera(false)
                    setIrUSBCameraParams(cams.irVid, cams.irPid)
                    setCameraConfig(irConfig)
                    try {
                        setTargetFps(IR_FPS)
                    } catch (error: Throwable) {
                        Log.w(TAG, "ir fps", error)
                    }
                    setScaleType(HTextureView.ScaleType.FIT_CENTER)
                    setPortrait(true)
                    setListener(HTextureView.OnCameraDataEnableListener { data, _ ->
                        noteFrameSize(data)
                        val copy = data.copyOf()
                        latestIr = irFrame(copy)
                        lastIrAt = System.currentTimeMillis()
                        irFrameCount++
                        if (irFrameCount % 20 == 0) turnVeinLightOn(force = true, lamp = this@apply)
                        // Matcher first. The preview JPEG must not hold up the vein frame.
                        pushFrames()
                        schedulePreview(copy)
                        if (enrollAwaitingIr) {
                            enrollAwaitingIr = false
                            activity.runOnUiThread {
                                if (resumed && task is PalmTask.Enroll) {
                                    enrolling = false
                                    applyTask()
                                }
                            }
                        }
                    })
                    veinLamp = this
                }
            } else {
                null
            }
            val rgbFrame = rgb.frame
                ?: error("HTextureView RGB getFrame() was null")
            val fill = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            if (ir != null) {
                // Vein image fills the preview. The color camera stays off-screen at its real
                // size so the module streams, without painting over the infrared picture.
                val irFrame = ir.frame ?: error("HTextureView IR getFrame() was null")
                holder.setBackgroundColor(android.graphics.Color.BLACK)
                irFrame.layoutParams = FrameLayout.LayoutParams(fill)
                // The screen draws one upright infrared still. This surface stays black on
                // first open, and leaving it visible stacks a second, mirrored palm on top.
                irFrame.alpha = 0f
                holder.addView(irFrame)
                rgbFrame.alpha = 0f
                rgbFrame.layoutParams = FrameLayout.LayoutParams(PREVIEW_W, PREVIEW_H)
                val hidden = parkSlot()
                hidden.removeAllViews()
                hidden.addView(rgbFrame)
            } else {
                rgbFrame.layoutParams = FrameLayout.LayoutParams(fill)
                holder.addView(rgbFrame)
            }
            holder.layoutParams = FrameLayout.LayoutParams(fill)
            container.addView(holder)
            rgbView = rgb
            irView = ir
            Log.i(TAG, "cameras rgb=${cams.rgbVid}/${cams.rgbPid} ir=${cams.irVid}/${cams.irPid} dual=${cams.dual}")
            if (resumed) {
                resumeCameras()
                if (scanTask()) {
                    applyScanSurface()
                    scheduleScanRevive(container)
                } else {
                    ensureCamerasLive()
                }
                // Payment preview often attaches after Verify already started — restart once cameras live.
                if (initialized && (task is PalmTask.Verify || task is PalmTask.Lookup || task is PalmTask.Watch)) {
                    activity.window.decorView.postDelayed({
                        if (resumed && host === container && (task is PalmTask.Verify || task is PalmTask.Lookup || task is PalmTask.Watch)) {
                            applyTask()
                        }
                    }, 350)
                }
            }
            ensureInit()
        } catch (error: Throwable) {
            Log.e(TAG, "attachHost", error)
            host = null
            previewRoot = null
            rgbView = null
            irView = null
            post(PalmEvent.Status("Palm camera did not open: ${error.message ?: error.javaClass.simpleName}", false))
        }
    }

    private var reopenAttempts = 0
    /** Payment and Verify only. Recording never increments this. */
    private var scanRevives = 0
    private var scanRevivePosted = false
    private var veinLightOn = false
    private var lastVeinLightAt = 0L
    private var veinLamp: com.hfims.android.lib.palm.ui.HTextureView? = null

    /**
     * Keep the vein lamp on for recording, payment, and verify.
     * Uses the camera that is actually streaming. Not the Xinran LED.
     */
    private fun turnVeinLightOn(force: Boolean = false, lamp: com.hfims.android.lib.palm.ui.HTextureView? = null) {
        val now = System.currentTimeMillis()
        if (force && now - lastVeinLightAt < 500) return
        if (!force && veinLightOn && now - lastVeinLightAt < 1500) return
        val view = lamp ?: veinLamp ?: irView
        if (view == null) {
            Log.w(TAG, "vein light skipped")
            return
        }
        try {
            val manager = com.hfims.android.lib.palm.ui.HTextureView::class.java
                .getMethod("access" + "$" + "900", com.hfims.android.lib.palm.ui.HTextureView::class.java)
                .invoke(null, view) ?: return
            val camera = manager.javaClass.getMethod("getUVCCamera").invoke(manager) ?: return
            try {
                camera.javaClass.getMethod("openImage").invoke(camera)
            } catch (error: Throwable) {
                Log.w(TAG, "vein image", error)
            }
            camera.javaClass.getMethod("openLight").invoke(camera)
            veinLightOn = true
            lastVeinLightAt = now
            Log.i(TAG, "vein light on task=$task")
        } catch (error: Throwable) {
            Log.w(TAG, "vein light", error)
        }
    }

    /** Payment confirmation and the Verify menu. Recording is not this. */
    private fun scanTask(): Boolean = task is PalmTask.Verify || task is PalmTask.Lookup

    private fun scanPreview(): Boolean = scanTask()

    /** Keep the vein surface hidden. The upright still is the picture on every screen. */
    private fun applyScanSurface() {
        irView?.frame?.alpha = 0f
    }

    /**
     * Payment and Verify only. If the vein camera produced no new frame, open it again.
     * A forced check is used after the view moves, because the previous frame time still looks fresh
     * while closeCamera is already running. Never called while a recording capture is in progress.
     */
    private fun scheduleScanRevive(container: FrameLayout, force: Boolean = false) {
        if (!scanTask() || enrolling) return
        if (!force && irLive()) {
            turnVeinLightOn(force = true)
            applyScanSurface()
            return
        }
        if (scanRevivePosted) return
        scanRevivePosted = true
        val seen = irFrameCount
        container.postDelayed({
            scanRevivePosted = false
            if (!resumed || enrolling || !scanTask()) return@postDelayed
            if (!container.isAttachedToWindow) return@postDelayed
            if (irFrameCount > seen) {
                scanRevives = 0
                turnVeinLightOn(force = true)
                applyScanSurface()
                return@postDelayed
            }
            val state = irView?.cameraState?.name.orEmpty()
            if (state == "OPENING") {
                container.postDelayed({
                    if (!resumed || enrolling || !scanTask()) return@postDelayed
                    if (irFrameCount > seen) {
                        turnVeinLightOn(force = true)
                        applyScanSurface()
                        return@postDelayed
                    }
                    if (scanRevives >= 2) return@postDelayed
                    scanRevives++
                    Log.w(TAG, "scan camera still opening — reopening ir=$irFrameCount")
                    rebuildScanCameras(container)
                }, 1500)
                return@postDelayed
            }
            if (scanRevives >= 2) return@postDelayed
            scanRevives++
            Log.w(TAG, "scan camera silent — reopening ir=$irFrameCount state=$state")
            rebuildScanCameras(container)
        }, 1600)
    }

    /**
     * Recording screen got the camera back after another screen had moved it.
     * If frames are still arriving, do nothing. If the move closed the camera, open it
     * again so the existing capture can see the palm.
     */
    private fun scheduleRecordRevive(container: FrameLayout) {
        val seen = irFrameCount
        container.postDelayed({
            if (!resumed || task !is PalmTask.Enroll) return@postDelayed
            if (!container.isAttachedToWindow) return@postDelayed
            if (irFrameCount > seen) return@postDelayed
            Log.w(TAG, "record camera closed by a screen change — opening again")
            reopenCameras(container)
        }, 1600)
    }

    /** New vein views for payment or Verify. Does not run during palm recording. */
    private fun rebuildScanCameras(container: FrameLayout) {
        if (enrolling || !scanTask()) return
        reopenCameras(container)
    }

    private fun reopenCameras(container: FrameLayout) {
        try {
            rgbView?.onPause()
            irView?.onPause()
        } catch (_: Throwable) {
        }
        rgbView = null
        irView = null
        veinLamp = null
        veinLightOn = false
        lastVeinLightAt = 0L
        previewRoot?.let { root ->
            (root.parent as? ViewGroup)?.removeView(root)
        }
        previewRoot = null
        container.removeAllViews()
        container.postDelayed({
            if (!resumed || task is PalmTask.Stop) return@postDelayed
            if (!container.isAttachedToWindow) return@postDelayed
            attachHost(container)
        }, 400)
    }

    /**
     * One retry if the vein camera never produces a frame.
     * Opening it again while frames are already arriving crashes authenByIC on this till.
     */
    private fun ensureCamerasLive() {
        val irBefore = irFrameCount
        activity.window.decorView.postDelayed({
            if (!resumed || rgbView == null || host == null) return@postDelayed
            if (irFrameCount > irBefore || (!dualExpected && rgbFrameCount > 0)) {
                reopenAttempts = 0
                return@postDelayed
            }
            if (reopenAttempts >= 1) return@postDelayed
            reopenAttempts++
            Log.w(TAG, "cameras silent — reopening")
            val container = host ?: return@postDelayed
            try {
                rgbView?.onPause()
                irView?.onPause()
            } catch (_: Throwable) {
            }
            rgbView = null
            irView = null
            veinLightOn = false
            lastVeinLightAt = 0L
            previewRoot?.let { root ->
                (root.parent as? ViewGroup)?.removeView(root)
            }
            previewRoot = null
            container.removeAllViews()
            attachHost(container)
        }, 2500)
    }

    private var lastPreviewAt = 0L

    /** JPEG off the camera thread so the vein matcher gets the next infrared frame immediately. */
    private fun schedulePreview(nv21: ByteArray) {
        val now = System.currentTimeMillis()
        if (now - lastPreviewAt < 80) return
        if (!previewBusy.compareAndSet(false, true)) return
        lastPreviewAt = now
        previewExecutor.execute {
            try {
                showIrPreview(nv21)
            } finally {
                previewBusy.set(false)
            }
        }
    }

    /** Draw the vein frame the matcher is using. The camera surface itself stays black on first open. */
    private fun showIrPreview(nv21: ByteArray) {
        try {
            val yuv = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, PREVIEW_W, PREVIEW_H, null)
            val jpeg = java.io.ByteArrayOutputStream()
            if (!yuv.compressToJpeg(android.graphics.Rect(0, 0, PREVIEW_W, PREVIEW_H), 60, jpeg)) return
            val bytes = jpeg.toByteArray()
            val raw = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            // Upright on this till. No extra mirror — that drew the palm backwards.
            val matrix = android.graphics.Matrix().apply {
                postRotate(PREVIEW_ROTATION.toFloat())
            }
            val turned = android.graphics.Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (turned !== raw) raw.recycle()
            publishFrame(turned)
            if (!turned.isRecycled) turned.recycle()
        } catch (error: Throwable) {
            Log.w(TAG, "ir preview", error)
        }
    }

    fun detachHost(container: FrameLayout) {
        if (pendingHost === container) pendingHost = null
        if (host !== container) return
        parkPreview()
    }

    /** Keep the UVC surface alive between record and payment so the next capture starts immediately. */
    private fun parkPreview() {
        val root = previewRoot ?: return
        val slot = parkSlot()
        if (root.parent !== slot) {
            (root.parent as? ViewGroup)?.removeView(root)
            root.alpha = 0f
            root.layoutParams = FrameLayout.LayoutParams(PREVIEW_W, PREVIEW_H)
            slot.addView(root)
        }
        host = null
    }

    private fun parkSlot(): FrameLayout {
        parked?.let { return it }
        val decor = activity.window.decorView as ViewGroup
        val slot = FrameLayout(activity)
        slot.layoutParams = FrameLayout.LayoutParams(PREVIEW_W, PREVIEW_H)
        slot.translationX = -6000f
        decor.addView(slot)
        parked = slot
        return slot
    }

    fun onHostResume() {
        resumed = true
        ensureInit()
        if (initialized && rgbView != null) resumeCameras()
        if (initialized && task !is PalmTask.Stop) applyTask()
    }

    fun onHostPause() {
        resumed = false
        stopWork()
        pauseCameras()
    }

    fun ensureRunning() {
        if (!resumed || task is PalmTask.Stop) return
        ensureInit()
        resumeCameras()
        // Never re-applyTask here — a second startCollect aborts an active enroll.
    }

    fun setTask(next: PalmTask) {
        val sameEnroll = next is PalmTask.Enroll &&
            task is PalmTask.Enroll &&
            (next as PalmTask.Enroll).uid.equals((task as PalmTask.Enroll).uid, true) &&
            (next as PalmTask.Enroll).hand == (task as PalmTask.Enroll).hand
        val waitingLift = sameEnroll && acceptAfterLift && !lifted
        val restartEnroll = sameEnroll && !enrolling && !waitingLift
        val changed = task != next && !(sameEnroll && (enrolling || waitingLift))
        task = next
        if (next is PalmTask.Verify || next is PalmTask.Lookup) {
            scanRevives = 0
        }
        applyScanSurface()
        if (!resumed) return
        if (next is PalmTask.Stop) {
            stopWork()
            return
        }
        ensureInit()
        if (initialized && (changed || restartEnroll)) applyTask()
        val watching = host
        if (watching != null && (next is PalmTask.Verify || next is PalmTask.Lookup)) {
            scheduleScanRevive(watching)
        }
    }

    /**
     * Store changed. Payment still matches one card. The next recording reloads every palm
     * so the scanner can reject a vein that already belongs to someone.
     */
    fun reloadDatabase() {
        if (!initialized) {
            ensureInit()
            activity.window.decorView.postDelayed({ reloadDatabase() }, 800)
            return
        }
        when (val current = task) {
            is PalmTask.Verify -> loadCardFeature(current.uid)
            else -> clearSdkGallery()
        }
    }

    /** Load only this card's template into the SDK (1:1 match, O(1) vs full gallery). */
    private fun loadCardFeature(cardUid: String): Boolean = synchronized(galleryLock) {
        val rec = store.find(cardUid) ?: run {
            Log.w(TAG, "loadCardFeature: no local template for $cardUid")
            verifyStoredFeature = null
            verifyCardUid = null
            return false
        }
        verifyStoredFeature = rec.feature
        verifyCardUid = rec.uid.uppercase()
        val (left, right) = unpackPalms(rec.feature)
        val samples = palmSamples(rec.feature).ifEmpty { listOf(left, right).filter { it.length > 32 } }
        try {
            val lib = sdk.palmLib ?: return false
            clearSdkGallery()
            var loaded = false
            samples.forEachIndexed { slot, feature ->
                if (feature.length <= 32) return@forEachIndexed
                val id = sdkSlotId(rec.uid, slot)
                val result = lib.addFeature(id, feature)
                Log.i(
                    TAG,
                    "loadCardFeature card=${rec.uid} slot=$slot sdkId=$id ok=${result.first} msg=${result.second}",
                )
                if (result.first == true) loaded = true
            }
            Log.i(TAG, "loadCardFeature count=${lib.featureCount} loaded=$loaded")
            return loaded
        } catch (error: Throwable) {
            Log.e(TAG, "loadCardFeature", error)
            return false
        }
    }

    private fun clearSdkGallery() {
        synchronized(galleryLock) {
        galleryDirty = true
        try {
            val lib = sdk.palmLib ?: return
            try {
                lib.deleteAll()
            } catch (_: Throwable) {
                store.all().forEach { rec ->
                    for (slot in 0 until ENROLL_COUNT) {
                        try {
                            lib.deleteFeature(sdkSlotId(rec.uid, slot))
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            Log.i(TAG, "clearSdkGallery count=${lib.featureCount}")
        } catch (error: Throwable) {
            Log.w(TAG, "clearSdkGallery", error)
        }
        }
    }

    fun destroy() {
        stopWork()
        pauseCameras()
        try {
            sdk.removeCollectCallback(collectCb)
            sdk.removeRecognizeCallback(recognizeCb)
        } catch (_: Throwable) {
        }
        clearFrame()
        tts?.shutdown()
        tts = null
        host = null
        irView = null
        rgbView = null
    }

    private fun ensureInit() {
        if (initialized || initStarted) return
        if (!opening.compareAndSet(false, true)) return
        initStarted = true
        post(PalmEvent.Status("Starting the palm scanner…", false))
        try {
            val cams = resolveCameras()
            val appDir = File(activity.filesDir, "palm_vp530").apply { mkdirs() }.absolutePath
            val dbDir = File(activity.filesDir, "palm_vp530_db").apply { mkdirs() }.absolutePath
            val builder = PalmConfig.Builder()
                .appDir(appDir)
                .dataDbDir(dbDir)
                .sdCardDbDir(dbDir)
                .width(PREVIEW_W)
                .height(PREVIEW_H)
                .previewRealWidth(PREVIEW_W)
                .previewRealHeight(PREVIEW_H)
                .rotation(PREVIEW_ROTATION)
                .imageMirrorH(IMAGE_MIRROR_H)
                .imageMirrorV(IMAGE_MIRROR_V)
                // getCollectTimes() is the max *failed* enroll samples before onFailure(3), not UI steps.
                .collectTimes(COLLECT_RETRY_LIMIT)
                .recognizeThreshold(MATCH_SCORE)
                // SDK aborts recognize after this many failed identify attempts — must be >1 for payment.
                .recognizeTimes(RECOGNIZE_RETRY_LIMIT)
                .imageCallback4Collect(true)
                .imageCallback4Recognize(true)
                .enableLog(true)
                .timeout(60_000L)
                .irVidPid(cams.irVid, cams.irPid)
                .rgbVidPid(cams.rgbVid, cams.rgbPid)
                // Vp530Demo leaves dualMode off and feeds only the IR frame.
                .dualMode(false)
            val config = builder.build()
            // Same 4-arg init path as Vp530Demo InitViewModel (off the UI thread).
            initExecutor.execute {
                try {
                    sdk.init(activity.applicationContext, config, HashMap<String, Any>(), IPalmSdk.InitCallback { ok, msg, detail ->
                        opening.set(false)
                        if (ok) {
                            initialized = true
                            try {
                                sdk.addCollectCallback(collectCb)
                                sdk.addRecognizeCallback(recognizeCb)
                            } catch (error: Throwable) {
                                Log.w(TAG, "callbacks", error)
                            }
                            logUsb()
                            try {
                                val api = com.veinauthen.tool.JXPalmTool.getPalmInstance()
                                api.setEnrollInterval(0L)
                                com.veinauthen.palm.JXPalmConfig.setIrDetectFirst(true)
                                com.veinauthen.palm.JXPalmConfig.setForceCaptureEnable(true)
                                loosenNativeGates()
                            } catch (error: Throwable) {
                                Log.w(TAG, "enroll speed", error)
                            }
                            post(PalmEvent.Status("Palm scanner ready. Place the palm over the sensor.", true))
                            activity.window.decorView.postDelayed({ auditStoredPalms() }, 2500)
                            activity.runOnUiThread {
                                val waiting = pendingHost
                                if (resumed && waiting != null && rgbView == null) {
                                    attachHost(waiting)
                                }
                                if (resumed && task !is PalmTask.Stop) applyTask()
                            }
                        } else {
                            initStarted = false
                            val text = listOfNotNull(msg, detail).filter { it.isNotBlank() }.joinToString(" — ")
                                .ifBlank { "Palm scanner did not start." }
                            Log.e(TAG, "init failed: $text")
                            post(PalmEvent.Status(text, false))
                            activity.window.decorView.postDelayed({
                                if (resumed && task !is PalmTask.Stop && !initialized) {
                                    initStarted = false
                                    ensureInit()
                                }
                            }, 2500)
                        }
                    })
                } catch (error: Throwable) {
                    opening.set(false)
                    initStarted = false
                    Log.e(TAG, "init", error)
                    post(PalmEvent.Status("Palm scanner did not start: ${error.message}", false))
                }
            }
        } catch (error: Throwable) {
            opening.set(false)
            initStarted = false
            Log.e(TAG, "init", error)
            post(PalmEvent.Status("Palm scanner did not start: ${error.message}", false))
        }
    }

    private fun applyTask() {
        if (!initialized || !resumed) return
        val token = collectGen.incrementAndGet()
        stopWork()
        when (val current = task) {
            is PalmTask.Enroll -> {
                val uid = current.uid.uppercase()
                val hand = current.hand
                claimExecutor.execute {
                    if (token != collectGen.get()) return@execute
                    feedPaused.set(true)
                    try {
                        if (token == collectGen.get()) {
                            // One hand only while the palm is held. The full library is checked once, after capture.
                            clearSdkGallery()
                            if (hand == PalmHand.Right) {
                                val left = store.find(uid)?.let { unpackPalms(it.feature).first }.orEmpty()
                                if (left.length > 32) registerFeature(uid, left, PalmHand.Left, quiet = true)
                            }
                        }
                    } catch (error: Throwable) {
                        Log.e(TAG, "enroll gallery", error)
                    } finally {
                        feedPaused.set(false)
                    }
                    activity.runOnUiThread {
                        if (token != collectGen.get() || !resumed) return@runOnUiThread
                        val now = task as? PalmTask.Enroll ?: return@runOnUiThread
                        if (!now.uid.equals(uid, true) || now.hand != hand) return@runOnUiThread
                        beginEnrollSession(uid, hand)
                    }
                }
            }
            is PalmTask.Lookup -> {
                verifyPalmSeen = false
                verifyAccepted = false
                lookupSaidFor = ""
                lookupMatchedAt = 0L
                val token = collectGen.get()
                activity.runOnUiThread {
                    if (token != collectGen.get() || !resumed || task !is PalmTask.Lookup) return@runOnUiThread
                    if (store.all().isEmpty()) {
                        post(PalmEvent.Status("No palm is registered on this till.", false))
                        return@runOnUiThread
                    }
                        loosenPayCapture()
                        paying = true
                        enrolling = false
                        turnVeinLightOn()
                        beginLiveWait()
                        post(PalmEvent.Status("Place a registered palm.", true))
                    val pump = payPump.incrementAndGet()
                    pumpLookup(token, pump)
                }
            }
            is PalmTask.Verify -> {
                verifyPalmSeen = false
                verifyAccepted = false
                val uid = current.uid
                val token = collectGen.get()
                claimExecutor.execute {
                    if (token != collectGen.get()) return@execute
                    val rec = store.find(uid)
                    val samples = rec?.feature?.let { palmSamples(it) }.orEmpty().map { cleanBase64(it) }.filter { it.length > 32 }
                    if (rec == null || samples.isEmpty()) {
                        Log.w(TAG, "pay: no local base64 template for $uid palms=${store.all().size}")
                        activity.runOnUiThread {
                            if (token != collectGen.get()) return@runOnUiThread
                            post(PalmEvent.Status("No palm on this till for this card. Record it here first.", false))
                        }
                        return@execute
                    }
                    payUid = rec.uid.uppercase()
                    paySamples = samples
                    payLeft = samples.first()
                    payRight = samples.getOrElse(1) { "" }
                    Log.i(TAG, "pay templates card=$payUid samples=${samples.size}")
                    activity.runOnUiThread {
                        if (token != collectGen.get() || !resumed || task !is PalmTask.Verify) return@runOnUiThread
                        loosenPayCapture()
                        paying = true
                        enrolling = false
                        turnVeinLightOn()
                        beginLiveWait()
                        activeCollectUid = payUid
                        post(PalmEvent.Status("Place the palm to confirm.", true))
                        val pump = payPump.incrementAndGet()
                        pumpPay(token, pump)
                    }
                }
            }
            PalmTask.Watch -> {
                verifyPalmSeen = false
                verifyAccepted = false
                verifyStoredFeature = null
                verifyCardUid = null
                clearSdkGallery()
                post(PalmEvent.Status("Palm scanner ready.", true))
                try {
                    sdk.startRecognize(HashMap<String, Any>())
                } catch (error: Throwable) {
                    Log.w(TAG, "watch recognize", error)
                }
            }
            PalmTask.Stop -> Unit
        }
    }

    private fun stopWork() {
        acceptAfterLift = false
        lifted = true
        liftToken++
        collectRestart.incrementAndGet()
        enrollPump.incrementAndGet()
        payPump.incrementAndGet()
        paying = false
        enrollSamples.clear()
        lookupSaidFor = ""
        lookupMatchedAt = 0L
        restorePayCapture()
        try {
            if (enrolling) sdk.stopCollect()
        } catch (_: Throwable) {
        }
        enrolling = false
        activeCollectUid = null
        try {
            sdk.stopRecognize()
        } catch (_: Throwable) {
        }
    }

    /**
     * Recording keeps every saved palm in the scanner. Its own match (the same one used at
     * payment) then refuses a vein that already belongs to a student. Payment still loads
     * only the tapped card, so the till match stays a single comparison.
     */
    private fun ensureGallery() {
        synchronized(galleryLock) {
        if (!galleryDirty) return
        if (sdk.palmLib == null) {
            Log.w(TAG, "ensureGallery: palm library is not ready")
            return
        }
        val started = System.currentTimeMillis()
        val records = store.all()
        clearSdkGallery()
        var added = 0
        for (rec in records) {
            palmSamples(rec.feature).forEachIndexed { slot, feature ->
                if (feature.length > 32 && registerSlot(rec.uid, feature, slot)) added++
            }
        }
        galleryDirty = false
        Log.i(TAG, "ensureGallery palms=${records.size} templates=$added in ${System.currentTimeMillis() - started}ms")
        }
    }

    private fun beginEnrollSession(uid: String, hand: PalmHand) {
        activeCollectUid = uid
        activeCollectHand = hand
        enrollSamples.clear()
        acceptAfterLift = false
        lifted = true
        try {
            sdk.palmLib?.deleteFeature(sdkSlotId(uid, 0))
        } catch (_: Throwable) {
        }
        say("Place either palm")
        loosenEnrollCapture()
        wakeVeinCamera()
        enrolling = true
        lastEnrollSampleAt = 0L
        post(PalmEvent.EnrollStep(0, ENROLL_COUNT))
        post(PalmEvent.Status("Place either palm. 3 infrared samples.", true))
        val token = collectGen.get()
        val pump = enrollPump.incrementAndGet()
        pumpEnroll(uid, token, pump)
    }

    /** Same infrared read as payment, scored against every palm registered on this till. */
    private fun pumpLookup(token: Int, pump: Int) {
        if (pump != payPump.get() || token != collectGen.get() || !paying || !resumed) return
        if (task !is PalmTask.Lookup) return
        grabLookupFeature(token)
        activity.window.decorView.postDelayed({ pumpLookup(token, pump) }, 60)
    }

    private fun grabLookupFeature(token: Int) {
        if (!paying || token != collectGen.get() || task !is PalmTask.Lookup) return
        val now = System.currentTimeMillis()
        if (now - lastGrabAt < 40) return
        if (!grabBusy.compareAndSet(false, true)) return
        lastGrabAt = now
        val frame = latestIr
        val data = frame?.data?.copyOf()
        if (frame == null || data == null) {
            grabBusy.set(false)
            return
        }
        claimExecutor.execute {
            try {
                if (!paying || token != collectGen.get() || task !is PalmTask.Lookup) return@execute
                val hit = extractLiveVein(frame, data)
                if (hit.feature.isEmpty()) {
                    val nowLog = System.currentTimeMillis()
                    if (nowLog - lastExtractLogAt > 700) {
                        lastExtractLogAt = nowLog
                        Log.i(TAG, "lookup extract code=${hit.code} bright=${hit.bright}")
                    }
                    activity.runOnUiThread {
                        if (!paying || token != collectGen.get() || task !is PalmTask.Lookup) return@runOnUiThread
                        noteLookupLifted()
                    }
                    return@execute
                }
                if (System.currentTimeMillis() < scanAfter) return@execute
                val search = try {
                    sdk.palmSearch
                } catch (error: Throwable) {
                    Log.w(TAG, "lookup search", error)
                    null
                }
                var best = 0
                var bestUid = ""
                val started = System.currentTimeMillis()
                val book = store.all()
                if (search != null) {
                    lookup@ for (rec in book) {
                        val uid = rec.uid.uppercase()
                        for (stored in palmSamples(rec.feature)) {
                            val clean = cleanBase64(stored)
                            if (clean.length <= 32) continue
                            val score = try {
                                search.verify(hit.feature, clean)
                            } catch (error: Throwable) {
                                Log.w(TAG, "lookup verify", error)
                                0
                            }
                            if (score > best) {
                                best = score
                                bestUid = uid
                            }
                            if (best >= 90) break@lookup
                        }
                    }
                }
                Log.i(
                    TAG,
                    "lookup verify score=$best card=$bestUid palms=${book.size} extract=${hit.ms}ms compare=${System.currentTimeMillis() - started}ms",
                )
                activity.runOnUiThread {
                    if (!paying || token != collectGen.get() || task !is PalmTask.Lookup) return@runOnUiThread
                    if (best >= VERIFY_SCORE && bestUid.isNotEmpty()) {
                        acceptLookup(bestUid, best)
                    } else {
                        throttleReject(best)
                    }
                }
            } catch (error: Throwable) {
                Log.w(TAG, "lookup extract", error)
            } finally {
                grabBusy.set(false)
            }
        }
    }

    private fun acceptLookup(cardUid: String, score: Int) {
        val uid = cardUid.uppercase()
        lookupMatchedAt = System.currentTimeMillis()
        if (lookupSaidFor != uid) {
            lookupSaidFor = uid
            say("Palm matched")
        }
        post(PalmEvent.Identified(uid, score, true))
    }

    /** Clear the last picture, switch the lamp on, and read the live camera. */
    private fun beginLiveWait() {
        scanAfter = System.currentTimeMillis() + 300
        clearFrame()
        wakeVeinCamera()
    }

    private fun wakeVeinCamera() {
        resumeCameras()
        turnVeinLightOn(force = true)
    }

    /** No vein in the frame means the hand left the glass. Clear the student once. */
    private fun noteLookupLifted() {
        if (task !is PalmTask.Lookup || !paying) return
        if (lookupSaidFor.isEmpty()) return
        if (System.currentTimeMillis() - lookupMatchedAt < 1000) return
        lookupSaidFor = ""
        post(PalmEvent.Identified("", 0, false))
    }

    /** One infrared read, then a score against this card's saved samples only. */
    private fun pumpPay(token: Int, pump: Int) {
        if (pump != payPump.get() || token != collectGen.get() || !paying || verifyAccepted || !resumed) return
        if (task !is PalmTask.Verify) return
        grabPayFeature(token)
        activity.window.decorView.postDelayed({ pumpPay(token, pump) }, 60)
    }

    private fun grabPayFeature(token: Int) {
        if (!paying || verifyAccepted || token != collectGen.get()) return
        val now = System.currentTimeMillis()
        if (now - lastGrabAt < 40) return
        if (!grabBusy.compareAndSet(false, true)) return
        lastGrabAt = now
        val frame = latestIr
        val data = frame?.data?.copyOf()
        if (frame == null || data == null) {
            grabBusy.set(false)
            return
        }
        claimExecutor.execute {
            try {
                if (!paying || verifyAccepted || token != collectGen.get()) return@execute
                val hit = extractLiveVein(frame, data)
                if (hit.feature.isEmpty()) {
                    val nowLog = System.currentTimeMillis()
                    if (nowLog - lastExtractLogAt > 700) {
                        lastExtractLogAt = nowLog
                        Log.i(TAG, "pay extract code=${hit.code} ms=${hit.ms} bright=${hit.bright}")
                    }
                    return@execute
                }
                if (System.currentTimeMillis() < scanAfter) return@execute
                val search = try {
                    sdk.palmSearch
                } catch (error: Throwable) {
                    Log.w(TAG, "pay search", error)
                    null
                }
                var best = 0
                val started = System.currentTimeMillis()
                if (search != null) {
                    for (stored in paySamples) {
                        if (stored.length <= 32) continue
                        val score = try {
                            search.verify(hit.feature, stored)
                        } catch (error: Throwable) {
                            Log.w(TAG, "pay verify", error)
                            0
                        }
                        if (score > best) best = score
                        if (best >= VERIFY_SCORE) break
                    }
                }
                Log.i(
                    TAG,
                    "pay verify score=$best extract=${hit.ms}ms compare=${System.currentTimeMillis() - started}ms card=$payUid",
                )
                activity.runOnUiThread {
                    if (!paying || verifyAccepted || token != collectGen.get()) return@runOnUiThread
                    if (best >= VERIFY_SCORE) {
                        acceptVerify(payUid, best)
                    } else {
                        throttleReject(best)
                    }
                }
            } catch (error: Throwable) {
                Log.w(TAG, "pay extract", error)
            } finally {
                grabBusy.set(false)
            }
        }
    }

    private fun extractLiveVein(frame: PalmCamera, data: ByteArray): VeinHit {
        val owned = synchronized(yuvLock) {
            val turned = if (frame.rotation == 270) {
                com.hfims.android.lib.palm.impl.util.YUVUtils.rotateYUV180(data, frame.width, frame.height)
            } else {
                data
            }
            turned.copyOf()
        }
        val api = com.veinauthen.tool.JXPalmTool.getPalmInstance()
        val image = api.yuvToJXImage(owned, frame.width, frame.height)
        image.palmType = 0
        val started = System.currentTimeMillis()
        loosenNativeGates()
        val got = api.getFeature(image, 0, 0)
        val took = System.currentTimeMillis() - started
        try {
            image.recycle()
        } catch (_: Throwable) {
        }
        val bytes = got?.feature
        val code = got?.code ?: -1
        val feature = if (code == 0 && bytes != null && bytes.size >= 128) {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } else {
            ""
        }
        return VeinHit(code, took, bytes?.size ?: 0, got?.brightness ?: 0f, got?.validPoint ?: 0f, feature)
    }

    /** Keep reading the infrared frame. The strict enroll session is not used. */
    private fun pumpEnroll(uid: String, token: Int, pump: Int) {
        if (pump != enrollPump.get() || token != collectGen.get() || !enrolling || !resumed) return
        if (task !is PalmTask.Enroll) return
        grabEnrollFeature()
        activity.window.decorView.postDelayed({ pumpEnroll(uid, token, pump) }, 80)
    }

    /**
     * Queue the next capture after the SDK has finished stopCollect().
     * Calling startCollect inside onSuccess or onFailure is stopped immediately.
     */
    private fun continueCollect(uid: String) {
        val token = collectGen.get()
        val mark = collectRestart.incrementAndGet()
        activity.window.decorView.postDelayed({
            if (mark != collectRestart.get()) return@postDelayed
            if (token != collectGen.get() || !resumed || task !is PalmTask.Enroll) return@postDelayed
            armCollect(uid, PalmHand.Left)
        }, 280)
    }

    private fun armCollect(uid: String, hand: PalmHand) {
        enrolling = true
        activeCollectUid = uid
        activeCollectHand = hand
        enrollTaken = enrollSamples.size
        lastHoldStillAt = 0L
        try {
            sdk.resetCollect()
            // Probe id is not a saved student, so a hit means the infrared sample matched.
            sdk.startCollect(ENROLL_PROBE_ID, HashMap<String, Any>())
            enrollAwaitingIr = dualExpected && irFrameCount == 0
            Log.i(
                TAG,
                "startCollect card=$uid sample=${enrollSamples.size + 1} irFrames=$irFrameCount",
            )
            post(
                PalmEvent.Status(
                    "Place either palm. Infrared sample ${enrollSamples.size + 1} of $ENROLL_COUNT.",
                    true,
                ),
            )
        } catch (error: Throwable) {
            enrolling = false
            activeCollectUid = null
            Log.e(TAG, "startCollect", error)
            post(PalmEvent.EnrollFailed("Could not start capture."))
        }
    }

    /** One infrared capture. Either hand counts. Three matching samples are required. */
    private fun takeEnrollSample(feature: String, matchedThisCard: Boolean) {
        val uid = activeCollectUid ?: (task as? PalmTask.Enroll)?.uid?.uppercase() ?: return
        if (!irLive()) {
            post(PalmEvent.Status("Waiting for the infrared camera…", true))
            return
        }
        val clean = cleanBase64(feature)
        if (clean.length <= 32) return
        val now = System.currentTimeMillis()
        if (enrollSamples.isNotEmpty() && now - lastEnrollSampleAt < 180) return
        if (enrollSamples.isNotEmpty() && !matchedThisCard) {
            post(PalmEvent.Status("Hold the same palm. Infrared sample ${enrollSamples.size + 1} of $ENROLL_COUNT.", true))
            return
        }
        enrollSamples.add(clean)
        lastEnrollSampleAt = now
        if (enrollSamples.size == 1) registerSlot(uid, clean, 0)
        post(PalmEvent.EnrollStep(enrollSamples.size, ENROLL_COUNT))
        Log.i(TAG, "ir sample ${enrollSamples.size}/$ENROLL_COUNT card=$uid bytes=${clean.length} matched=$matchedThisCard")
        if (enrollSamples.size < ENROLL_COUNT) {
            post(PalmEvent.Status("Sample ${enrollSamples.size} saved. Keep the palm still.", true))
            return
        }
        enrolling = false
        claimUniquePalm(uid, enrollSamples.first(), packSamples(enrollSamples.toList()))
    }

    private fun irLive(): Boolean {
        if (irView == null && !dualExpected) return true
        return System.currentTimeMillis() - lastIrAt < 700
    }

    /**
     * The scanner's own enroll extract stays on the strict quality level and returns 352
     * for a palm that is already in the infrared picture. Read the same vein frame at the
     * verify level and keep that feature.
     */
    private fun grabEnrollFeature() {
        if (!enrolling || paying || enrollSamples.size >= ENROLL_COUNT) return
        val now = System.currentTimeMillis()
        if (now - lastGrabAt < 80) return
        if (!grabBusy.compareAndSet(false, true)) return
        lastGrabAt = now
        val frame = latestIr
        val data = frame?.data?.copyOf()
        if (frame == null || data == null) {
            grabBusy.set(false)
            return
        }
        claimExecutor.execute {
            try {
                if (!enrolling || paying || enrollSamples.size >= ENROLL_COUNT) return@execute
                val hit = extractLiveVein(frame, data)
                val nowLog = System.currentTimeMillis()
                if (hit.code == 0 || nowLog - lastExtractLogAt > 700) {
                    lastExtractLogAt = nowLog
                    Log.i(
                        TAG,
                        "direct extract code=${hit.code} ms=${hit.ms} bytes=${hit.bytes} bright=${hit.bright} " +
                            "points=${hit.points} have=${enrollSamples.size}",
                    )
                }
                if (hit.feature.isEmpty()) return@execute
                val matched = enrollSamples.isNotEmpty()
                activity.runOnUiThread {
                    if (!enrolling || paying) return@runOnUiThread
                    takeEnrollSample(hit.feature, matched)
                }
            } catch (error: Throwable) {
                Log.w(TAG, "direct extract", error)
            } finally {
                grabBusy.set(false)
            }
        }
    }

    /** Ignore a capture until the left palm has left the glass, then open the right-hand capture. */
    private fun deferUntilLifted(): Boolean {
        if (!acceptAfterLift || lifted || task !is PalmTask.Enroll) return false
        watchForLift()
        return true
    }

    private fun watchForLift() {
        activity.runOnUiThread {
            if (!acceptAfterLift || lifted || task !is PalmTask.Enroll) return@runOnUiThread
            enrolling = false
            try {
                sdk.stopCollect()
            } catch (_: Throwable) {
            }
            try {
                sdk.stopRecognize()
            } catch (_: Throwable) {
            }
            try {
                sdk.startRecognize(HashMap<String, Any>())
            } catch (error: Throwable) {
                Log.w(TAG, "lift watch", error)
                finishLift()
                return@runOnUiThread
            }
            val token = liftToken
            activity.window.decorView.postDelayed({
                if (token != liftToken || lifted || !acceptAfterLift || liftSawPalm) return@postDelayed
                if (task !is PalmTask.Enroll) return@postDelayed
                finishLift()
            }, 1600)
        }
    }

    private fun finishLift() {
        if (!acceptAfterLift || lifted) return
        val token = liftToken
        lifted = true
        activity.runOnUiThread {
            if (token != liftToken || task !is PalmTask.Enroll || !resumed) return@runOnUiThread
            val enroll = task as PalmTask.Enroll
            if (enroll.hand != PalmHand.Right) return@runOnUiThread
            try {
                sdk.stopRecognize()
            } catch (_: Throwable) {
            }
            val uid = activeCollectUid ?: enroll.uid.uppercase()
            say("Place your right palm")
            armCollect(uid, PalmHand.Right)
        }
    }

    private fun handFromMap(map: Map<String, Any>?): PalmHand? {
        val raw = map?.get("key_hand_type") ?: return null
        val code = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: return null
        return when (code) {
            HAND_TYPE_LEFT -> PalmHand.Left
            HAND_TYPE_RIGHT -> PalmHand.Right
            else -> null
        }
    }

    private fun noteFrameSize(data: ByteArray) {
        if (loggedFrameSize) return
        loggedFrameSize = true
        val expect = PREVIEW_W * PREVIEW_H * 3 / 2
        Log.i(TAG, "frame bytes=${data.size} expectNv21=$expect")
    }

    /**
     * Tag NV21 as 640×480. Preview TextureView uses PREVIEW_ROTATION for display;
     * algorithm metadata uses the same rotation/mirrors as Vp530Demo HCameraConfig defaults.
     */
    private fun palmFrame(data: ByteArray): PalmCamera =
        PalmCamera(
            data,
            PREVIEW_W,
            PREVIEW_H,
            PREVIEW_ROTATION,
            PREVIEW_W,
            PREVIEW_H,
            IMAGE_MIRROR_H,
            IMAGE_MIRROR_V,
        )

    /** Vein frame, upright on this till: same 270° and vertical mirror as the working preview. */
    private fun irFrame(data: ByteArray): PalmCamera =
        PalmCamera(
            data,
            PREVIEW_W,
            PREVIEW_H,
            PREVIEW_ROTATION,
            PREVIEW_W,
            PREVIEW_H,
            IMAGE_MIRROR_H,
            IMAGE_MIRROR_V,
        )

    private val veinQueue = ArrayBlockingQueue<PalmCamera>(1)

    private fun pushFrames() {
        if (!initialized) return
        val now = System.currentTimeMillis()
        // Vp530 CameraViewModel feeds only the IR camera. setDataSource(rgb, ir) is an empty method.
        // The color camera finds a hand and then never extracts a vein, so the till stays on "hold still".
        val ir = latestIr?.takeIf { now - lastIrAt < 700 }
        // Infrared only whenever the vein camera is present. The color frame is not a palm match.
        val frame = when {
            irView != null || dualExpected -> ir
            else -> latestRgb?.takeIf { now - lastRgbAt < 700 }
        } ?: return
        if (!veinQueue.offer(frame)) {
            veinQueue.poll()
            veinQueue.offer(frame)
        }
        ensureFeedLoop()
    }

    private fun ensureFeedLoop() {
        if (!feeding.compareAndSet(false, true)) return
        feedExecutor.execute {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val frame = try {
                        veinQueue.take()
                    } catch (_: InterruptedException) {
                        break
                    }
                    if (feedPaused.get()) continue
                    // Recording reads the infrared frame itself. Feeding the SDK here rotates
                    // the same static buffer and the vein read comes back empty.
                    if (enrolling || paying) continue
                    try {
                        synchronized(yuvLock) {
                            sdk.setDataSource(frame, HashMap())
                        }
                        pushCount++
                        if (pushCount % 60 == 0) {
                            Log.i(
                                TAG,
                                "fed ir push=$pushCount rgb=$rgbFrameCount ir=$irFrameCount " +
                                    "rgbAge=${System.currentTimeMillis() - lastRgbAt} irAge=${System.currentTimeMillis() - lastIrAt} " +
                                    "dual=$dualExpected enrolling=$enrolling task=$task",
                            )
                        }
                    } catch (error: Throwable) {
                        Log.w(TAG, "setDataSource", error)
                    }
                }
            } finally {
                feeding.set(false)
            }
        }
    }

    private data class CamIds(val rgbVid: Int, val rgbPid: Int, val irVid: Int, val irPid: Int, val dual: Boolean)

    private fun resolveCameras(): CamIds {
        val devices = try {
            activity.getSystemService(UsbManager::class.java)?.deviceList?.values.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        fun present(vid: Int, pid: Int) = devices.any { it.vendorId == vid && it.productId == pid }
        val sonix = present(SONIX_VID, SONIX_PID)
        val webcam = present(WEBCAM_VID, WEBCAM_PID)
        // Distinct rgb/ir VID/PIDs required. License/OpenDevice expects demo mapping:
        // Sonix → RGB slot, companion Webcam → IR slot (not 7532/259). Swapping slots → decode -44 + SIGSEGV.
        return when {
            sonix && webcam -> CamIds(SONIX_VID, SONIX_PID, WEBCAM_VID, WEBCAM_PID, true)
            sonix -> CamIds(SONIX_VID, SONIX_PID, SONIX_VID, SONIX_PID, false)
            webcam -> CamIds(WEBCAM_VID, WEBCAM_PID, WEBCAM_VID, WEBCAM_PID, false)
            else -> CamIds(SONIX_VID, SONIX_PID, WEBCAM_VID, WEBCAM_PID, true)
        }
    }

    private fun resumeCameras() {
        try {
            irView?.onResume()
            rgbView?.onResume()
        } catch (error: Throwable) {
            Log.w(TAG, "camera resume", error)
        }
    }

    private fun pauseCameras() {
        try {
            irView?.onPause()
            rgbView?.onPause()
        } catch (error: Throwable) {
            Log.w(TAG, "camera pause", error)
        }
    }

    private fun publishFrame(source: Bitmap) {
        val copy = try {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return
        } catch (_: Throwable) {
            return
        }
        synchronized(frameLock) {
            _frame.value = copy
        }
    }

    private fun clearFrame() {
        // Drop the still without recycling it. Compose may still be drawing that bitmap,
        // and recycling it left the payment and Verify boxes black.
        synchronized(frameLock) {
            _frame.value = null
        }
    }

    private fun logUsb() {
        try {
            val usb = activity.getSystemService(UsbManager::class.java)
            usb.deviceList.values.forEach { d ->
                Log.i(TAG, "usb vid=${d.vendorId} pid=${d.productId} name=${d.deviceName}")
            }
        } catch (_: Throwable) {
        }
    }

    /** Decimal id for Xinran/JXPalm (Long.parseLong). Slot 0 and 1 stay the old left and right ids. */
    private fun sdkSlotId(cardUid: String, slot: Int): String {
        if (slot <= 0) return sdkPalmId(cardUid, PalmHand.Left)
        if (slot == 1) return sdkPalmId(cardUid, PalmHand.Right)
        val base = sdkPalmId(cardUid, PalmHand.Left).toLongOrNull() ?: return sdkPalmId(cardUid, PalmHand.Left)
        val mixed = base xor (0x100000000L * slot)
        return if (mixed <= 0L) (base + 17L * slot).toString() else mixed.toString()
    }

    private fun registerSlot(cardUid: String, feature: String, slot: Int): Boolean {
        return try {
            val lib = sdk.palmLib ?: return false
            val id = sdkSlotId(cardUid, slot)
            try {
                lib.deleteFeature(id)
            } catch (_: Throwable) {
            }
            val result = lib.addFeature(id, feature)
            result.first == true
        } catch (error: Throwable) {
            Log.e(TAG, "registerSlot", error)
            false
        }
    }

    /** Decimal id for Xinran/JXPalm (Long.parseLong). Card UIDs stay hex in our store. */
    private fun sdkPalmId(cardUid: String, hand: PalmHand = PalmHand.Left): String {
        val hex = cardUid.trim().uppercase().removePrefix("0X").filter { it in "0123456789ABCDEF" }
        val asHex = hex.toLongOrNull(16)
        val base = when {
            asHex != null && asHex > 0L -> asHex
            else -> {
                val digits = cardUid.filter { it.isDigit() }
                if (digits.isNotEmpty()) digits.take(18).toLongOrNull() ?: (cardUid.hashCode().toLong() and Long.MAX_VALUE)
                else (cardUid.hashCode().toLong() and Long.MAX_VALUE)
            }
        }
        if (hand == PalmHand.Left) return base.toString()
        val right = base xor 0x100000000L
        return if (right == base || right <= 0L) (base + 7).toString() else right.toString()
    }

    private fun sameCard(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val left = com.xander.pos.reader.CardUid.lookupVariants(a).map { it.uppercase() }.toSet()
        if (b.uppercase() in left) return true
        return com.xander.pos.reader.CardUid.lookupVariants(b).any { it.uppercase() in left }
    }

    private fun handOfSdkId(sdkId: String, cardUid: String): PalmHand? = when (sdkId) {
        sdkPalmId(cardUid, PalmHand.Right) -> PalmHand.Right
        sdkPalmId(cardUid, PalmHand.Left) -> PalmHand.Left
        else -> null
    }

    private fun cardUidForSdkId(sdkId: String): String? {
        if (sdkId.isBlank()) return null
        store.all().forEach { rec ->
            if (rec.uid.equals(sdkId, ignoreCase = true)) return rec.uid
            if ((0 until ENROLL_COUNT).any { sdkSlotId(rec.uid, it) == sdkId }) return rec.uid
        }
        return null
    }

    /**
     * A captured vein is saved only after every sample is compared with every palm already on this till.
     * verify() is 0–100 and refuses at VERIFY_SCORE. identify stays at MATCH_SCORE.
     */
    private fun claimUniquePalm(cardUid: String, probe: String, saveFeature: String) {
        val token = synchronized(claimLock) { ++claimToken }
        val toSave = saveFeature.ifBlank { probe }
        post(PalmEvent.Status("Checking this palm…", true))
        claimExecutor.execute {
            feedPaused.set(true)
            val refusal = try {
                Thread.sleep(20)
                // Every sample is checked. One weak frame of the same vein can score far below the bar.
                duplicateRefusal(toSave, cardUid, PalmHand.Left)
            } catch (error: Throwable) {
                Log.e(TAG, "duplicate check", error)
                "Could not check this palm against recorded students. Hold still and try again."
            } finally {
                feedPaused.set(false)
            }
            activity.runOnUiThread {
                val current = synchronized(claimLock) { claimToken }
                if (current != token) return@runOnUiThread
                val enroll = task as? PalmTask.Enroll
                if (enroll == null || !enroll.uid.equals(cardUid, true)) return@runOnUiThread
                if (refusal != null) {
                    Log.w(TAG, "palm refused card=$cardUid reason=$refusal")
                    say("Already recorded")
                    post(PalmEvent.EnrollFailed(refusal))
                    return@runOnUiThread
                }
                palmSamples(toSave).forEachIndexed { slot, sample ->
                    registerSlot(cardUid, sample, slot)
                }
                say("Palm saved")
                post(PalmEvent.Enrolled(cardUid, toSave))
            }
        }
    }

    /** Exact copies only. A different capture of the same palm is caught when that palm is recorded. */
    private fun auditStoredPalms() {
        if (!initialized) return
        val seen = HashMap<String, String>()
        val hits = LinkedHashSet<String>()
        for (rec in store.all()) {
            val label = rec.name.trim().ifBlank { rec.uid }
            palmSamples(rec.feature).forEach { feat ->
                val key = feat.trim()
                if (key.length <= 32) return@forEach
                val owner = seen.put(key, label)
                if (owner != null && owner != label) hits.add("$owner and $label")
            }
        }
        if (hits.isNotEmpty()) {
            val text = "Same palm is saved for ${hits.joinToString("; ")}. Delete the extra copy before taking payment."
            Log.w(TAG, text)
            post(PalmEvent.Status(text, true))
        }
    }

    /**
     * One native library search, the same palmMatch the scanner uses while enrolling.
     * Pairwise verify was letting one palm through for several students.
     */
    private fun duplicateRefusal(feature: String, cardUid: String, hand: PalmHand): String? {
        val search = try {
            sdk.palmSearch
        } catch (error: Throwable) {
            null
        } ?: return "Palm check is not ready. Hold still and try again."
        val probes = palmSamples(feature).map { cleanBase64(it) }.filter { it.length > 32 }.distinct()
        if (probes.isEmpty()) return "Palm feature was empty. Try again."
        for (probe in probes) {
            val copied = copiedPalm(probe, cardUid, hand)
            if (copied != null) return copied
        }
        val foreign = foreignPalm(probes, cardUid)
        if (foreign != null) return foreign
        ensureGallery()
        val started = System.currentTimeMillis()
        var score = 0
        var sdkId = ""
        for (probe in probes) {
            val holder = arrayOfNulls<String>(1)
            val one = try {
                search.identify(probe, holder)
            } catch (error: Throwable) {
                Log.e(TAG, "identify", error)
                return "Could not check this palm against recorded students. Hold still and try again."
            }
            val id = holder[0].orEmpty()
            val ownerUid = if (id.isBlank()) null else cardUidForSdkId(id)
            if (ownerUid != null && ownerUid.equals(cardUid, true)) continue
            if (one > score) {
                score = one
                sdkId = id
            }
        }
        Log.i(
            TAG,
            "duplicate identify card=$cardUid hand=$hand score=$score sdkId=$sdkId probes=${probes.size} " +
                "in ${System.currentTimeMillis() - started}ms",
        )
        if (score >= MATCH_SCORE && sdkId.isNotBlank()) {
            val ownerUid = cardUidForSdkId(sdkId)
            val name = ownerUid?.let { store.find(it)?.name?.trim() }.orEmpty().ifBlank { "another student" }
            return "This palm is already recorded for $name. It cannot be saved on another student."
        }
        return null
    }

    /** Same vein, different student. Every new sample is compared with every palm saved on this till. */
    private fun foreignPalm(probes: List<String>, cardUid: String): String? {
        val search = try {
            sdk.palmSearch
        } catch (error: Throwable) {
            null
        } ?: return "Palm check is not ready. Hold still and try again."
        var best = 0
        var name = ""
        val started = System.currentTimeMillis()
        for (probe in probes) {
            for (rec in store.all()) {
                if (sameCard(rec.uid, cardUid)) continue
                for (stored in palmSamples(rec.feature)) {
                    val clean = cleanBase64(stored)
                    if (clean.length <= 32) continue
                    val score = try {
                        search.verify(probe, clean)
                    } catch (error: Throwable) {
                        Log.w(TAG, "duplicate verify", error)
                        0
                    }
                    if (score > best) {
                        best = score
                        name = rec.name.trim().ifBlank { rec.uid }
                    }
                }
            }
        }
        Log.i(
            TAG,
            "duplicate verify best=$best other=$name probes=${probes.size} in ${System.currentTimeMillis() - started}ms",
        )
        if (best >= VERIFY_SCORE) {
            return "This palm is already recorded for $name. It cannot be saved on another student."
        }
        return null
    }

    private fun copiedPalm(probe: String, cardUid: String, hand: PalmHand): String? {
        for (rec in store.all()) {
            palmSamples(rec.feature).forEach { stored ->
                if (stored.trim() != probe) return@forEach
                if (rec.uid.equals(cardUid, true)) return@forEach
                val name = rec.name.trim().ifBlank { "another student" }
                return "This palm is already recorded for $name. It cannot be saved on another student."
            }
        }
        return null
    }

    private fun registerFeature(cardUid: String, feature: String, hand: PalmHand = PalmHand.Left, quiet: Boolean = false): Boolean {
        return try {
            val lib = sdk.palmLib ?: return false
            val id = sdkPalmId(cardUid, hand)
            try {
                lib.deleteFeature(id)
            } catch (_: Throwable) {
            }
            val result = lib.addFeature(id, feature)
            if (!quiet || result.first != true) {
                Log.i(TAG, "registerFeature card=$cardUid hand=$hand sdkId=$id ok=${result.first} msg=${result.second} count=${lib.featureCount}")
            }
            result.first == true
        } catch (error: Throwable) {
            Log.e(TAG, "registerFeature", error)
            false
        }
    }

    private fun acceptVerify(cardUid: String, score: Int) {
        if (verifyAccepted) return
        verifyAccepted = true
        verifyPalmSeen = false
        paying = false
        enrolling = false
        say("Palm matched")
        post(PalmEvent.Status("Palm matched. Charging…", true))
        post(PalmEvent.Identified(cardUid.uppercase(), score, true))
        try {
            sdk.stopCollect()
        } catch (_: Throwable) {
        }
        try {
            sdk.stopRecognize()
        } catch (_: Throwable) {
        }
    }

    /** One capture, then a base64 compare against the two templates stored on this till. */
    private fun armPayCapture() {
        if (!paying || verifyAccepted || task !is PalmTask.Verify) return
        try {
            sdk.stopRecognize()
        } catch (_: Throwable) {
        }
        try {
            sdk.resetCollect()
            sdk.startCollect(PAY_PROBE_ID, HashMap<String, Any>())
            Log.i(TAG, "pay capture card=$payUid left=${payLeft.length} right=${payRight.length}")
        } catch (error: Throwable) {
            Log.e(TAG, "pay capture", error)
            post(PalmEvent.Status("Palm scanner did not start. Place the palm again.", false))
        }
    }

    private fun restartPayCapture() {
        // The payment read is the direct infrared compare. Do not open the strict collect session.
    }

    /**
     * The scanner already ran palmMatch against this card. A hit on the left or right
     * template saved on the till is the payment. Anything else is refused.
     */
    private fun onPayCapture(code: Int, feature: String?, matchedId: String?) {
        if (!paying || verifyAccepted) return
        if (code == 1) {
            val id = matchedId?.trim().orEmpty()
            val owner = cardUidForSdkId(id) ?: id
            if (id.isNotBlank() && sameCard(owner, payUid)) {
                Log.i(TAG, "pay palmMatch card=$payUid sdkId=$id")
                acceptVerify(payUid, MATCH_SCORE)
                return
            }
            throttleReject()
            restartPayCapture()
            return
        }
        val live = cleanBase64(feature.orEmpty())
        if (live.length > 32) {
            onPayFeature(live)
            return
        }
        restartPayCapture()
    }

    private fun onPayFeature(feature: String) {
        if (!paying || verifyAccepted) return
        val live = cleanBase64(feature)
        if (live.length <= 32) {
            restartPayCapture()
            return
        }
        if (!payBusy.compareAndSet(false, true)) return
        claimExecutor.execute {
            try {
                if (!paying || verifyAccepted) return@execute
                val started = System.currentTimeMillis()
                val search = try {
                    sdk.palmSearch
                } catch (error: Throwable) {
                    null
                }
                var best = 0
                if (search != null) {
                    paySamples.forEach { stored ->
                        if (stored.length > 32) best = maxOf(best, search.verify(live, stored))
                    }
                }
                Log.i(TAG, "pay base64 verify score=$best in ${System.currentTimeMillis() - started}ms")
                if (best >= VERIFY_SCORE) {
                    activity.runOnUiThread { acceptVerify(payUid, best) }
                    return@execute
                }
                val holder = arrayOfNulls<String>(1)
                val score = try {
                    search?.identify(live, holder) ?: 0
                } catch (error: Throwable) {
                    Log.w(TAG, "pay identify", error)
                    0
                }
                val owner = cardUidForSdkId(holder[0].orEmpty()) ?: holder[0].orEmpty()
                Log.i(TAG, "pay identify score=$score owner=$owner in ${System.currentTimeMillis() - started}ms")
                activity.runOnUiThread {
                    if (!paying || verifyAccepted) return@runOnUiThread
                    if (score >= MATCH_SCORE && sameCard(owner, payUid)) {
                        acceptVerify(payUid, score)
                    } else {
                        throttleReject()
                        restartPayCapture()
                    }
                }
            } finally {
                payBusy.set(false)
            }
        }
    }

    private fun cleanBase64(raw: String): String {
        val text = raw.trim().replace(WHITESPACE, "")
        if (text.length <= 32 || !text.matches(BASE64)) return ""
        return text
    }

    /**
     * The Java quality setters never reach libpalm_sdk. Factory gates are quality 70,
     * brightness 70 and pose 20 degrees, which is why a visible palm stays on code 352.
     */
    private fun loosenNativeGates() {
        val was = PalmTune.applyLoose()
        if (was > 1) Log.i(TAG, "native quality $was -> 1")
        else if (was < 0) Log.w(TAG, "native quality gates unchanged")
    }

    /** Enroll only. Payment keeps infrared liveness on. */
    private fun loosenEnrollCapture() {
        loosenPayCapture()
        try {
            if (savedLiveness == null) savedLiveness = com.veinauthen.palm.JXPalmConfig.isIrLivenessEnable()
            if (savedPointResponse == null) savedPointResponse = com.veinauthen.palm.JXPalmConfig.getMinPointResponse()
            com.veinauthen.palm.JXPalmConfig.setIrLivenessEnable(false)
            com.veinauthen.palm.JXPalmConfig.setMinPointResponse(0.01f)
            com.veinauthen.palm.JXPalmConfig.setMinVerifyQuality(1)
            com.veinauthen.palm.JXPalmConfig.setMinVerifySize(10)
            com.veinauthen.palm.JXPalmConfig.setMinCaptureDistance(0)
            com.veinauthen.palm.JXPalmConfig.setMaxCaptureDistance(2000)
        } catch (error: Throwable) {
            Log.w(TAG, "enroll speed", error)
        }
    }

    private fun loosenPayCapture() {
        try {
            if (savedEnrollQuality == null) savedEnrollQuality = com.veinauthen.palm.JXPalmConfig.getMinEnrollQuality()
            com.veinauthen.palm.JXPalmConfig.setMinEnrollQuality(1)
            com.veinauthen.palm.JXPalmConfig.setMinEnrollSize(10)
            com.veinauthen.palm.JXPalmConfig.setMinEnrollOccupyRatio(0.02f)
            com.veinauthen.palm.JXPalmConfig.setMinEnrollBrightness(1)
            com.veinauthen.palm.JXPalmConfig.setMaxEnrollBrightness(255)
            com.veinauthen.palm.JXPalmConfig.setMaxEnrollPitch(90)
            com.veinauthen.palm.JXPalmConfig.setMaxEnrollYaw(90)
            com.veinauthen.palm.JXPalmConfig.setMaxEnrollRoll(90)
            com.veinauthen.palm.JXPalmConfig.setForceCaptureEnable(true)
            com.veinauthen.palm.JXPalmConfig.setIrDetectFirst(true)
            loosenNativeGates()
        } catch (error: Throwable) {
            Log.w(TAG, "pay speed", error)
        }
    }

    private fun restorePayCapture() {
        val quality = savedEnrollQuality ?: return
        savedEnrollQuality = null
        try {
            com.veinauthen.palm.JXPalmConfig.setMinEnrollQuality(quality)
            savedLiveness?.let { com.veinauthen.palm.JXPalmConfig.setIrLivenessEnable(it) }
            savedPointResponse?.let { com.veinauthen.palm.JXPalmConfig.setMinPointResponse(it) }
            savedLiveness = null
            savedPointResponse = null
        } catch (error: Throwable) {
            Log.w(TAG, "restore enroll quality", error)
        }
    }

    private fun throttleReject(score: Int = 0) {
        val now = System.currentTimeMillis()
        if (now - lastVerifyRejectAt.get() < 1600) return
        lastVerifyRejectAt.set(now)
        post(PalmEvent.Identified("", score, false))
    }

    private fun say(text: String) {
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "palm")
    }

    private fun post(event: PalmEvent) {
        activity.runOnUiThread { events(event) }
    }

    private data class VeinHit(
        val code: Int,
        val ms: Long,
        val bytes: Int,
        val bright: Float,
        val points: Float,
        val feature: String,
    )

    private companion object {
        const val TAG = "PalmEngine"
        const val ENROLL_COUNT = 3
        /** Passed to PalmConfig.collectTimes — SDK uses it as failed-sample budget before abort. */
        const val COLLECT_RETRY_LIMIT = 40
        const val MATCH_SCORE = 650
        /** 1:1 verify() is 0–100. The same palm scores about 80–99. palmMatch identify stays at 650. */
        const val VERIFY_SCORE = 75
        /** Failed identify budget before SDK stops recognize (was 1 — payment went silent). */
        const val RECOGNIZE_RETRY_LIMIT = 40
        const val PREVIEW_W = 640
        const val PREVIEW_H = 480
        /** Vein camera. The color camera only stays open so the module keeps streaming. */
        const val IR_FPS = 30
        const val RGB_FPS = 15
        const val HAND_TYPE_LEFT = 1
        const val HAND_TYPE_RIGHT = 2
        /** Same as HTextureView.PREVIEW_ROTATION / PALM_MIRROR_* (Vp530Demo). */
        const val PREVIEW_ROTATION = 270
        const val IMAGE_MIRROR_H = false
        const val IMAGE_MIRROR_V = true
        /** Embedded Sonix palm camera (Vp530Demo RGB slot / AppSettings default). */
        const val SONIX_VID = 11391
        const val SONIX_PID = 19040
        /** Embedded Usb Webcam companion on X60 (fills the IR slot; demo default 7532/259 is absent). */
        const val WEBCAM_VID = 1287
        const val WEBCAM_PID = 1409
        val BASE64 = Regex("^[A-Za-z0-9+/=\\s]+$")
        val WHITESPACE = Regex("\\s+")
        /** Collect id that is not a student palm, so a hit is the stored template. */
        const val PAY_PROBE_ID = "9900000000001"
        const val ENROLL_PROBE_ID = "9900000000002"
        private val initExecutor = Executors.newSingleThreadExecutor()
    }
}

enum class PalmHand {
    Left,
    Right,
    ;

    val label: String get() = if (this == Left) "left" else "right"
}

sealed class PalmTask {
    data object Stop : PalmTask()
    data object Watch : PalmTask()
    data class Verify(val uid: String) : PalmTask()
    data object Lookup : PalmTask()
    data class Enroll(val uid: String, val hand: PalmHand = PalmHand.Left) : PalmTask()
}

sealed class PalmEvent {
    data class Status(val text: String, val ready: Boolean) : PalmEvent()
    data class Identified(val id: String, val score: Int, val accepted: Boolean) : PalmEvent()
    data class EnrollStep(val taken: Int, val total: Int) : PalmEvent()
    data class Enrolled(val id: String, val feature: String) : PalmEvent()
    data class EnrollFailed(val reason: String) : PalmEvent()
}
