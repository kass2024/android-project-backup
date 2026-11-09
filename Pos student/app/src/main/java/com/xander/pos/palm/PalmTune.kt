package com.xander.pos.palm

import android.util.Log

/** Writes the vein scanner's native quality gates. The Java setters do not reach them. */
internal object PalmTune {
    private var loaded = false

    /** @return the previous native enroll quality, or -1 when the scanner library is not in memory. */
    fun applyLoose(): Int {
        if (!loaded) {
            try {
                System.loadLibrary("palm_tune")
                loaded = true
            } catch (error: Throwable) {
                Log.w(TAG, "load", error)
                return -1
            }
        }
        return try {
            // Loading this class maps libpalm_sdk.so. The gates are inside that library.
            Class.forName("com.veinauthen.palm.JXPalmSDK")
            nativeApply()
        } catch (error: Throwable) {
            Log.w(TAG, "apply", error)
            -1
        }
    }

    private external fun nativeApply(): Int

    private const val TAG = "PalmTune"
}
