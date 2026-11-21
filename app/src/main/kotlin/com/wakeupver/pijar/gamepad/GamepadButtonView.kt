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
    private val onTap: (GamepadControl) -> Unit,
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
    private val density = resources.displayMetrics.density
    private var touchCount = 0

    init {
        isClickable = true
        isFocusable = false
        contentDescription = context.getString(control.labelRes)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchCount = 1
                setPressedState(true)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                touchCount++
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                touchCount = (touchCount - 1).coerceAtLeast(0)
                if (touchCount == 0) setPressedState(false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchCount = 0
                setPressedState(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                touchCount = 0
                setPressedState(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun setPressedState(value: Boolean) {
        if (isPressed == value) return
        isPressed = value
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap(control)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - dp(1f)
        fill.color = if (isPressed) Color.argb(235, 98, 0, 238) else Color.argb(155, 18, 18, 24)
        canvas.drawCircle(cx, cy, radius, fill)
        border.color = if (isPressed) Color.WHITE else Color.argb(185, 255, 255, 255)
        border.strokeWidth = dp(1.5f)
        canvas.drawCircle(cx, cy, radius, border)
        val text = control.face
        label.textSize = when {
            text.length >= 5 -> size() * 0.2f
            text.length >= 3 -> size() * 0.25f
            text.length == 2 -> size() * 0.33f
            else -> size() * 0.42f
        }
        val metrics = label.fontMetrics
        canvas.drawText(text, cx, cy - (metrics.ascent + metrics.descent) / 2f, label)
    }

    private fun size(): Float = minOf(width, height).toFloat()

    private fun dp(value: Float): Float = value * density
}
