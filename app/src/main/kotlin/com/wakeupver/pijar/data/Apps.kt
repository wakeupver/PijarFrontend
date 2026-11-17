package com.wakeupver.pijar.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

data class AppEntry(
    val label: String,
    val packageName: String,
    val activity: String,
    val icon: Bitmap,
)

object Apps {
    private const val ICON_PX = 128

    /** Semua aplikasi yang punya ikon peluncur (kecuali aplikasi ini), diurutkan menurut nama. */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val result = ArrayList<AppEntry>()
        for (info in pm.queryIntentActivities(query, 0)) {
            val activity = info.activityInfo ?: continue
            if (activity.packageName == context.packageName) continue
            try {
                val label = info.loadLabel(pm).toString()
                val icon = info.loadIcon(pm).toBitmapSquare(ICON_PX)
                result += AppEntry(label, activity.packageName, activity.name, icon)
            } catch (e: Exception) {
                // Lewati aplikasi yang ikonnya gagal dimuat.
            }
        }
        result.sortBy { it.label.lowercase() }
        return result
    }

    fun launch(context: Context, app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activity))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    }

    fun openInfo(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun Drawable.toBitmapSquare(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bitmap
    }
}
