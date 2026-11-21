package com.wakeupver.pijar.gamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.wakeupver.pijar.R
import kotlin.math.sqrt

class GamepadStickView(
    context: Context,
    private val side: GamepadStickSide,
    private val backend: GamepadBackend,
) : View(context) {
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 18, 18, 24)
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(185, 255, 255, 255)
        strokeWidth = 2f
    }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 98, 0, 238)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private var activePointer = -1
    private var knobX = 0f
    private var knobY = 0f

    init {
        isClickable = true
        isFocusable = false
        contentDescription = context.getString(
            if (side == GamepadStickSide.LEFT) R.string.gamepad_left_stick else R.string.gamepad_right_stick
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = event.getPointerId(0)
                update(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.getPointerId(0) == activePointer) update(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointer) reset()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                reset()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun update(x: Float, y: Float) {
        val radius = radius()
        var nx = (x - width / 2f) / radius
        var ny = (y - height / 2f) / radius
        val distance = sqrt(nx * nx + ny * ny)
        if (distance > 1f) {
            nx /= distance
            ny /= distance
        }
        knobX = nx
        knobY = ny
        backend.setStick(side, nx, ny)
        invalidate()
    }

    private fun reset() {
        if (activePointer == -1) return
        activePointer = -1
        knobX = 0f
        knobY = 0f
        backend.setStick(side, 0f, 0f)
        invalidate()
    }

    private fun radius(): Float = minOf(width, height) / 2f - 8f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val outer = radius()
        canvas.drawCircle(cx, cy, outer, base)
        canvas.drawCircle(cx, cy, outer, border)
        val travel = outer * 0.55f
        canvas.drawCircle(cx + knobX * travel, cy + knobY * travel, outer * 0.34f, knob)
        text.textSize = outer * 0.32f
        val metrics = text.fontMetrics
        canvas.drawText(side.name.first().toString(), cx, cy - (metrics.ascent + metrics.descent) / 2f, text)
    }
}
