package com.wakeupver.pijar.gamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

class GamepadButtonView(
    context: Context,
    private val control: GamepadControl,
    private val backend: GamepadBackend,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private var activePointer = -1
    private var pressed = false

    init {
        isClickable = true
        isFocusable = false
        contentDescription = context.getString(control.labelRes)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = event.getPointerId(0)
                updatePressed(true)
                backend.setButton(control, true)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointer) release()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointer) release()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                release()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun release() {
        if (activePointer == -1) return
        activePointer = -1
        updatePressed(false)
        backend.setButton(control, false)
    }

    private fun updatePressed(value: Boolean) {
        if (pressed == value) return
        pressed = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - 1f
        fill.color = if (pressed) Color.argb(235, 98, 0, 238) else Color.argb(155, 18, 18, 24)
        canvas.drawCircle(cx, cy, radius, fill)
        border.color = if (pressed) Color.WHITE else Color.argb(185, 255, 255, 255)
        border.strokeWidth = 2f
        canvas.drawCircle(cx, cy, radius, border)
        val text = control.face
        label.textSize = when {
            text.length >= 5 -> width * 0.19f
            text.length >= 3 -> width * 0.25f
            text.length == 2 -> width * 0.34f
            else -> width * 0.42f
        }
        val metrics = label.fontMetrics
        canvas.drawText(text, cx, cy - (metrics.ascent + metrics.descent) / 2f, label)
    }
}
