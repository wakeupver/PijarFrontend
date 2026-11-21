package com.wakeupver.pijar.gamepad

import android.content.Context

object GamepadLayout {
    data class Spec(val control: GamepadControl, val x: Int, val y: Int, val size: Int)
    data class Placement(val specs: List<Spec>, val height: Int)

    fun build(context: Context, width: Int, height: Int): Placement {
        val density = context.resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).toInt()
        val button = dp(42f)
        val shoulder = dp(38f)
        val aux = dp(30f)
        val left = dp(72f)
        val right = width - dp(72f)
        val centerX = width / 2
        val rowCenter = dp(104f)
        val spread = dp(44f)
        val shoulderCenter = shoulder / 2
        val auxCenter = dp(59f)
        val result = ArrayList<Spec>(GamepadControl.all().size)
        fun add(control: GamepadControl, cx: Int, cy: Int, buttonSize: Int = button) {
            result += Spec(
                control = control,
                x = (cx - buttonSize / 2).coerceIn(0, (width - buttonSize).coerceAtLeast(0)),
                y = (cy - buttonSize / 2).coerceIn(0, (height - buttonSize).coerceAtLeast(0)),
                size = buttonSize,
            )
        }
        add(GamepadControl.DPAD_UP, left, rowCenter - spread)
        add(GamepadControl.DPAD_DOWN, left, rowCenter + spread)
        add(GamepadControl.DPAD_LEFT, left - spread, rowCenter)
        add(GamepadControl.DPAD_RIGHT, left + spread, rowCenter)
        add(GamepadControl.TRIANGLE, right, rowCenter - spread)
        add(GamepadControl.CIRCLE, right + spread, rowCenter)
        add(GamepadControl.CROSS, right, rowCenter + spread)
        add(GamepadControl.SQUARE, right - spread, rowCenter)
        add(GamepadControl.L1, centerX - dp(78f), shoulderCenter, shoulder)
        add(GamepadControl.L2, centerX - dp(126f), shoulderCenter, shoulder)
        add(GamepadControl.R1, centerX + dp(78f), shoulderCenter, shoulder)
        add(GamepadControl.R2, centerX + dp(126f), shoulderCenter, shoulder)
        add(GamepadControl.L3, centerX - dp(52f), auxCenter, aux)
        add(GamepadControl.SELECT, centerX - dp(20f), auxCenter, aux)
        add(GamepadControl.START, centerX + dp(20f), auxCenter, aux)
        add(GamepadControl.R3, centerX + dp(52f), auxCenter, aux)
        val barHeight = result.maxOf { it.y + it.size }.coerceAtLeast(dp(120f))
        return Placement(result, barHeight)
    }
}
