package com.xander.pos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import java.io.ByteArrayOutputStream

/** White slip the student signs. The bitmap is the receipt image. */
class SignBoard(context: Context) : View(context) {
    private val bitmap = Bitmap.createBitmap(720, 320, Bitmap.Config.ARGB_8888)
    private val sheet = Canvas(bitmap)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    var hasInk = false
        private set
    var onInk: (Boolean) -> Unit = {}

    init {
        setBackgroundColor(Color.WHITE)
        sheet.drawColor(Color.WHITE)
    }

    fun clear() {
        sheet.drawColor(Color.WHITE)
        hasInk = false
        invalidate()
        onInk(false)
    }

    fun png(): String {
        if (!hasInk) return ""
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x / width.coerceAtLeast(1) * bitmap.width
        val y = event.y / height.coerceAtLeast(1) * bitmap.height
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x
                lastY = y
                moved = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                sheet.drawLine(lastX, lastY, x, y, ink)
                lastX = x
                lastY = y
                moved = true
                if (!hasInk) {
                    hasInk = true
                    onInk(true)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!moved) {
                    sheet.drawCircle(x, y, 3f, ink.apply { style = Paint.Style.FILL })
                    ink.style = Paint.Style.STROKE
                    if (!hasInk) {
                        hasInk = true
                        onInk(true)
                    }
                    invalidate()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, width, height), null)
    }
}
