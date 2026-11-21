package com.wakeupver.pijar.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

object ScrapeHttp {
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 20_000
    private const val TOTAL_TIMEOUT = 45_000
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val LIBRETRO_PRIMARY = "https://thumbnails.libretro.com/"
    private const val LIBRETRO_RAW = "https://raw.githubusercontent.com/libretro-thumbnails"
    private const val LIBRETRO_CDN = "https://cdn.jsdelivr.net/gh/libretro-thumbnails"
    private val LIBRETRO_ALIASES = mapOf(
        "atari - 8-bit" to "Atari - 8-bit Family",
        "nec - pc-8001 - pc-8801" to "NEC - PC-88",
        "microsoft - xbox" to "Microsoft - Xbox",
    )

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun libretroRepo(system: String): String =
        LIBRETRO_ALIASES[system.lowercase(Locale.ROOT)] ?: system

    fun libretroApi(system: String): String {
        val repo = libretroRepo(system).trim().replace(' ', '_')
        return "https://api.github.com/repos/libretro-thumbnails/$repo/contents/Named_Boxarts"
    }

    fun libretroFallbacks(url: String): List<String> {
        if (!url.startsWith(LIBRETRO_PRIMARY)) return emptyList()
        val parts = url.removePrefix(LIBRETRO_PRIMARY).split('/', limit = 3)
        if (parts.size != 3) return emptyList()
        val system = URLDecoder.decode(parts[0], "UTF-8")
        val repo = libretroRepo(system).trim().replace(' ', '_')
        val tail = "${parts[1]}/${parts[2]}"
        return listOf(
            "$LIBRETRO_RAW/$repo/HEAD/$tail",
            "$LIBRETRO_CDN/$repo@HEAD/$tail",
        )
    }

    fun downloadCover(url: String): ByteArray? {
        download(url)?.let { return it }
        for (candidate in libretroFallbacks(url)) {
            download(candidate)?.let { return it }
        }
        return null
    }

    fun existsCover(url: String): Boolean {
        if (exists(url)) return true
        return libretroFallbacks(url).any { exists(it) }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? =
        bytes(url, "GET", headers, null)?.toString(Charsets.UTF_8)

    fun post(url: String, headers: Map<String, String>, body: String): String? =
        bytes(url, "POST", headers, body.toByteArray(Charsets.UTF_8))?.toString(Charsets.UTF_8)

    fun download(url: String): ByteArray? = bytes(url, "GET", emptyMap(), null)

    fun exists(url: String): Boolean {
        var connection: HttpURLConnection? = null
        try {
            connection = open(url, "HEAD", emptyMap(), null)
            val code = connection.responseCode
            if (code in 200..299) return true
            if (code == 405 || code == 501) return download(url) != null
            return false
        } catch (e: Exception) {
            return false
        } finally {
            connection?.disconnect()
        }
    }

    private fun bytes(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): ByteArray? {
        var connection: HttpURLConnection? = null
        try {
            connection = open(url, method, headers, body)
            if (connection.responseCode !in 200..299) return null
            val stream = connection.inputStream ?: return null
            stream.use {
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                val deadline = System.currentTimeMillis() + TOTAL_TIMEOUT
                while (true) {
                    if (System.currentTimeMillis() > deadline) throw IOException("timeout total")
                    val read = it.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) throw IOException("respons terlalu besar")
                    out.write(buffer, 0, read)
                }
                return out.toByteArray()
            }
        } catch (e: Exception) {
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Pijar/0.1")
        connection.setRequestProperty("Accept", "application/json,image/*,*/*")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        if (body != null) {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
        }
        return connection
    }
}
