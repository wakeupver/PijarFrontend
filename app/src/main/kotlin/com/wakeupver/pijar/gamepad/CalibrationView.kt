package com.wakeupver.pijar.gamepad

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.wakeupver.pijar.R

class CalibrationView(
    context: Context,
    private val onTap: (Float, Float) -> Unit,
    onSkip: () -> Unit,
) : FrameLayout(context) {
    private val instruction = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
        setPadding(dp(28), 0, dp(28), 0)
    }
    private val skip = TextView(context).apply {
        text = context.getString(R.string.gamepad_calibrate_skip)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(dp(18), dp(10), dp(18), dp(10))
        isClickable = true
        setOnClickListener { onSkip() }
    }

    init {
        setBackgroundColor(Color.argb(190, 0, 0, 0))
        isClickable = true
        addView(
            instruction,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                topMargin = dp(96)
            },
        )
        addView(
            skip,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(18)
                marginEnd = dp(14)
            },
        )
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) onTap(event.rawX, event.rawY)
            true
        }
    }

    fun setMessage(value: String) {
        instruction.text = value
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
