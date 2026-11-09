package com.xander.pos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Receipt image stored in the charge `signature` field after a palm match. */
fun palmProofPng(name: String, uid: String, score: Int): String {
    val bitmap = Bitmap.createBitmap(640, 280, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f
        isFakeBoldText = true
    }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 28f
    }
    canvas.drawText("PALM VEIN VERIFIED", 32f, 64f, title)
    canvas.drawText(name.ifBlank { "Student" }, 32f, 120f, body)
    canvas.drawText("Card $uid", 32f, 164f, body)
    canvas.drawText("Match score $score", 32f, 208f, body)
    canvas.drawText(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()), 32f, 252f, body)
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
