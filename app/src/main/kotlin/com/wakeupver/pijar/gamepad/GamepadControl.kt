package com.wakeupver.pijar.gamepad

import androidx.annotation.StringRes
import com.wakeupver.pijar.R

enum class GamepadControl(
    val id: String,
    @StringRes val labelRes: Int,
    val face: String,
) {
    DPAD_UP("dpad_up", R.string.gamepad_dpad_up, "▲"),
    DPAD_DOWN("dpad_down", R.string.gamepad_dpad_down, "▼"),
    DPAD_LEFT("dpad_left", R.string.gamepad_dpad_left, "◀"),
    DPAD_RIGHT("dpad_right", R.string.gamepad_dpad_right, "▶"),
    TRIANGLE("triangle", R.string.gamepad_triangle, "△"),
    CIRCLE("circle", R.string.gamepad_circle, "○"),
    CROSS("cross", R.string.gamepad_cross, "✕"),
    SQUARE("square", R.string.gamepad_square, "□"),
    L1("l1", R.string.gamepad_l1, "L1"),
    L2("l2", R.string.gamepad_l2, "L2"),
    R1("r1", R.string.gamepad_r1, "R1"),
    R2("r2", R.string.gamepad_r2, "R2"),
    L3("l3", R.string.gamepad_l3, "L3"),
    R3("r3", R.string.gamepad_r3, "R3"),
    SELECT("select", R.string.gamepad_select, "SELECT"),
    START("start", R.string.gamepad_start, "START");

    companion object {
        fun all(): List<GamepadControl> = values().toList()
    }
}
