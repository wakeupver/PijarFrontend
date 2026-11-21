package com.wakeupver.pijar.gamepad

import android.content.Context
import kotlin.math.min
import kotlin.math.roundToInt

data class ButtonPlacement(
    val control: GamepadControl,
    val x: Int,
    val y: Int,
    val size: Int,
)

data class StickPlacement(
    val side: GamepadStickSide,
    val x: Int,
    val y: Int,
    val size: Int,
)

data class GamepadPlacement(
    val buttons: List<ButtonPlacement>,
    val sticks: List<StickPlacement>,
)

object GamepadLayout {
    fun build(context: Context, width: Int, height: Int): GamepadPlacement {
        val density = context.resources.displayMetrics.density
        val widthDp = width / density
        val heightDp = height / density
        val scale = min(widthDp / 360f, heightDp / 640f).coerceIn(0.62f, 1.15f)

        fun px(value: Float): Int = (value * density * scale).roundToInt()
        fun place(cx: Float, cy: Float, size: Float): Pair<Int, Int> {
            val pixels = px(size)
            return px(cx) - pixels / 2 to px(cy) - pixels / 2
        }

        val buttons = ArrayList<ButtonPlacement>()
        fun button(control: GamepadControl, cx: Float, cy: Float, size: Float) {
            val (x, y) = place(cx, cy, size)
            buttons += ButtonPlacement(control, x, y, px(size))
        }

        val dpadX = 86f
        val dpadY = heightDp - 258f
        button(GamepadControl.DPAD_UP, dpadX, dpadY - 26f, 40f)
        button(GamepadControl.DPAD_DOWN, dpadX, dpadY + 26f, 40f)
        button(GamepadControl.DPAD_LEFT, dpadX - 26f, dpadY, 40f)
        button(GamepadControl.DPAD_RIGHT, dpadX + 26f, dpadY, 40f)

        val faceX = widthDp - 86f
        val faceY = heightDp - 258f
        button(GamepadControl.TRIANGLE, faceX, faceY - 30f, 44f)
        button(GamepadControl.CIRCLE, faceX + 30f, faceY, 44f)
        button(GamepadControl.CROSS, faceX, faceY + 30f, 44f)
        button(GamepadControl.SQUARE, faceX - 30f, faceY, 44f)

        button(GamepadControl.L1, 24f, 20f, 46f)
        button(GamepadControl.L2, 24f, 74f, 46f)
        button(GamepadControl.R1, widthDp - 24f, 20f, 46f)
        button(GamepadControl.R2, widthDp - 24f, 74f, 46f)
        button(GamepadControl.L3, 86f, heightDp - 170f, 36f)
        button(GamepadControl.R3, widthDp - 86f, heightDp - 170f, 36f)
        button(GamepadControl.SELECT, widthDp / 2f - 28f, heightDp - 24f, 44f)
        button(GamepadControl.START, widthDp / 2f + 28f, heightDp - 24f, 44f)

        val stickSize = 116f
        val leftStick = place(86f, heightDp - 96f, stickSize)
        val rightStick = place(widthDp - 86f, heightDp - 96f, stickSize)
        val sticks = listOf(
            StickPlacement(GamepadStickSide.LEFT, leftStick.first, leftStick.second, px(stickSize)),
            StickPlacement(GamepadStickSide.RIGHT, rightStick.first, rightStick.second, px(stickSize)),
        )
        return GamepadPlacement(buttons, sticks)
    }
}
