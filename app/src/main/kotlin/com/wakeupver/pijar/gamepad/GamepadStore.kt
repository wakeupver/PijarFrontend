package com.wakeupver.pijar.gamepad

import android.content.Context
import android.graphics.PointF
import java.util.Locale

object GamepadStore {
    private const val PREFS = "virtual_gamepad"

    fun load(context: Context, platformId: String, targetPackage: String): Map<GamepadControl, PointF>? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(platformId, targetPackage), null)
            ?: return null
        val parsed = LinkedHashMap<GamepadControl, PointF>()
        for (part in raw.split(';')) {
            val id = part.substringBefore(':', "")
            val rest = part.substringAfter(':', "")
            val x = rest.substringBefore(':').toFloatOrNull() ?: continue
            val y = rest.substringAfter(':', "").toFloatOrNull() ?: continue
            val control = GamepadControl.all().firstOrNull { it.id == id } ?: continue
            if (x !in 0f..1f || y !in 0f..1f) continue
            parsed[control] = PointF(x, y)
        }
        return parsed.takeIf { map -> GamepadControl.all().all { it in map } }
    }

    fun save(context: Context, platformId: String, targetPackage: String, values: Map<GamepadControl, PointF>) {
        val text = GamepadControl.all().mapNotNull { control ->
            val point = values[control] ?: return@mapNotNull null
            "${control.id}:${format(point.x)}:${format(point.y)}"
        }.joinToString(";")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(platformId, targetPackage), text)
            .apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun key(platformId: String, targetPackage: String): String =
        "$platformId|${targetPackage.ifBlank { "unknown" }}"

    private fun format(value: Float): String = String.format(Locale.US, "%.5f", value)
}
