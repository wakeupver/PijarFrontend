package com.wakeupver.pijar.data

import android.content.Context
import android.net.Uri
import java.io.File

object CoverCache {
    fun fetch(filesDir: File, gameId: String, url: String): String? {
        if (url.isBlank()) return null
        val extension = when {
            url.contains(".png", ignoreCase = true) -> "png"
            url.contains(".webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
        val bytes = ScrapeHttp.downloadCover(url) ?: return null
        if (bytes.isEmpty()) return null
        val dir = File(filesDir, "covers")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val file = File(dir, "$gameId.$extension")
        return try {
            file.writeBytes(bytes)
            file.path
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, gameId: String, uri: Uri): String? {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val extension = when {
            mime.contains("png", ignoreCase = true) -> "png"
            mime.contains("webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return null
        if (bytes.isEmpty()) return null
        val dir = File(context.filesDir, "covers")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val file = File(dir, "$gameId.$extension")
        return try {
            file.writeBytes(bytes)
            file.path
        } catch (_: Exception) {
            null
        }
    }
}
