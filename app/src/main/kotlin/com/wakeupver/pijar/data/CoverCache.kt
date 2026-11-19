package com.wakeupver.pijar.data

import java.io.File

object CoverCache {
    fun fetch(filesDir: File, gameId: String, url: String): String? {
        if (url.isBlank()) return null
        val extension = when {
            url.contains(".png", ignoreCase = true) -> "png"
            url.contains(".webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
        val bytes = ScrapeHttp.download(url) ?: return null
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
}
