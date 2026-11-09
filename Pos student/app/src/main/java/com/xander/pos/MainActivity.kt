package com.xander.pos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xander.pos.palm.PalmEngine
import com.xander.pos.reader.CardReader
import com.xander.pos.reader.DkUsbCardReader
import com.xander.pos.reader.PosNfcReader
import com.xander.pos.reader.UsbHidCardReader
import com.xander.pos.reader.UsbWedge
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: PosModel by viewModels()
    private var cardReader: CardReader? = null
    private var dkUsb: DkUsbCardReader? = null
    private var usbHid: UsbHidCardReader? = null
    private var wedge: UsbWedge? = null
    private var nfc: PosNfcReader? = null
    private var palm: PalmEngine? = null
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) palm?.ensureRunning()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = PalmEngine(this, model.palmsStore) { event ->
            model.onPalm(event)
        }
        palm = engine
        wedge = UsbWedge { uid -> model.onCard(uid) }
        setContent { PosApp(model, engine) }
        startReaders()
        askCameraPermission()
        lifecycleScope.launch {
            model.palmTask.collect { task -> engine.setTask(task) }
        }
        lifecycleScope.launch {
            model.palmReload.collect { engine.reloadDatabase() }
        }
        lifecycleScope.launch {
            model.deskWake.collect { bringVerifyForward() }
        }
    }

    private fun bringVerifyForward() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(launch)
    }

    private fun askCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        palm?.onHostResume()
        if (nfc == null) nfc = PosNfcReader()
        nfc?.start(object : PosNfcReader.Listener {
            override fun onCard(hexUid: String) {
                runOnUiThread { model.onCard(hexUid) }
            }

            override fun onReady() {
                runOnUiThread { model.onNfcReady() }
            }

            override fun onUnavailable(reason: String) {
                runOnUiThread { model.onNfcUnavailable(reason) }
            }
        })
    }

    override fun onPause() {
        nfc?.stop()
        palm?.onHostPause()
        super.onPause()
    }

    override fun onDestroy() {
        wedge?.reset()
        cardReader?.stop()
        dkUsb?.stop()
        usbHid?.stop()
        nfc?.stop()
        palm?.destroy()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (UsbWedge.isPhysicalKeyboard(event)) {
            wedge?.onKey(event)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun startReaders() {
        val listen = object : CardReader.Listener {
            override fun onCard(uid: String) {
                model.onCard(uid)
            }

            override fun onDebug(line: String) {
            }
        }
        if (cardReader == null) cardReader = CardReader()
        cardReader?.start(this, listen)
        if (DkUsbCardReader.dkDevicePresent(this)) {
            if (dkUsb == null) dkUsb = DkUsbCardReader()
            dkUsb?.start(this, listen)
        }
        if (usbHid == null) usbHid = UsbHidCardReader(this)
        usbHid?.start(listen)
    }
}
