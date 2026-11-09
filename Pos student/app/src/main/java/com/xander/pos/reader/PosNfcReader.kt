package com.xander.pos.reader

import android.os.IBinder
import android.util.Log
import com.xzy.pos.sdk.card.PosCardManager

/**
 * Smart Pos X60 (K11) has no Android NFC service, so Settings never shows an NFC switch.
 * Student cards are read on the contactless pad through com.pos.cardService.
 * The UID is the chip byte order, same hex the discipline menu sends.
 */
class PosNfcReader {
    interface Listener {
        fun onCard(hexUid: String)
        fun onReady()
        fun onUnavailable(reason: String)
    }

    private val lock = Any()
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start(listener: Listener) {
        synchronized(lock) {
            if (running) return
            running = true
            thread = Thread({ poll(listener) }, "pos-nfc").also { it.start() }
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun poll(listener: Listener) {
        val binder = cardBinder()
        if (binder == null) {
            Log.e(TAG, "com.pos.cardService binder is null")
            listener.onUnavailable("Contactless service is hidden from this app")
            running = false
            return
        }
        Log.i(TAG, "card service bound")
        val manager = PosCardManager()
        try {
            val opened = manager.Card_picc_open()
            val field = manager.Card_picc_field_on()
            Log.i(TAG, "picc open=$opened field=$field")
        } catch (error: Throwable) {
            Log.e(TAG, "picc open failed", error)
        }
        var ready = false
        var failures = 0
        while (running) {
            try {
                Thread.sleep(80)
            } catch (error: InterruptedException) {
                break
            }
            val info = try {
                manager.Card_picc_detect_uid()
            } catch (error: Throwable) {
                failures += 1
                Log.e(TAG, "detect_uid failed #$failures", error)
                if (failures == 1) {
                    listener.onUnavailable("Contactless read failed: ${error.javaClass.simpleName}")
                }
                if (failures > 30) {
                    running = false
                    return
                }
                sleepQuiet(400)
                continue
            }
            failures = 0
            if (!ready) {
                ready = true
                listener.onReady()
            }
            val uid = info?.uid
            if (info == null || info.ret.toInt() != 0 || uid == null || uid.isEmpty()) continue
            val hex = toHex(uid)
            quiet(manager)
            if (hex.length >= 4) {
                Log.i(TAG, "card $hex")
                listener.onCard(hex)
                sleepQuiet(1400)
                try {
                    manager.Card_picc_open()
                    manager.Card_picc_field_on()
                } catch (error: Throwable) {
                    Log.w(TAG, "picc re-open", error)
                }
            }
        }
        quiet(manager)
        try {
            manager.Card_picc_close()
        } catch (error: Throwable) {
            Log.w(TAG, "picc close", error)
        }
    }

    private fun cardBinder(): IBinder? {
        return try {
            val manager = Class.forName("android.os.ServiceManager")
            val getService = manager.getMethod("getService", String::class.java)
            getService.invoke(null, "com.pos.cardService") as? IBinder
        } catch (error: Throwable) {
            Log.e(TAG, "ServiceManager.getService", error)
            null
        }
    }

    private fun quiet(manager: PosCardManager) {
        try {
            manager.Card_picc_field_off()
        } catch (error: Throwable) {
            Log.w(TAG, "field off", error)
        }
    }

    private fun sleepQuiet(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun toHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            out.append(String.format("%02X", b.toInt() and 0xFF))
        }
        return out.toString()
    }

    private companion object {
        const val TAG = "PosNfc"
    }
}
