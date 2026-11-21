package com.wakeupver.pijar.data

import android.content.Context
import android.net.Uri
import java.io.File

object CoverCache {
    data class Result(val path: String, val url: String)

    fun fetch(filesDir: File, gameId: String, urls: List<String>): Result? {
        val download = ScrapeHttp.downloadFirst(urls) ?: return null
        val path = write(File(filesDir, "covers"), gameId, download.bytes) ?: return null
        return Result(path, download.url)
    }

    fun save(context: Context, gameId: String, uri: Uri): String? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return null
        return write(File(context.filesDir, "covers"), gameId, bytes)
    }

    private fun write(dir: File, gameId: String, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val extension = when {
            bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
            bytes.size > 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> "jpg"
        }
        val file = File(dir, "$gameId.$extension")
        for (old in listOf("png", "jpg", "webp")) {
            if (old != extension) File(dir, "$gameId.$old").takeIf { it.isFile }?.delete()
        }
        return try {
            file.writeBytes(bytes)
            file.path
        } catch (_: Exception) {
            null
        }
    }
}
