package com.wakeupver.pijar.data

import com.wakeupver.pijar.core.GameMetadata
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.GameTitles
import com.wakeupver.pijar.core.PlatformDef
import com.wakeupver.pijar.core.ScrapeSource
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32

object Scraper {
    private const val HASH_DB_TTL = 7L * 24 * 60 * 60 * 1000
    private const val MAX_HASH_BYTES = 2L * 1024 * 1024 * 1024

    fun scrape(game: GameRecord, platform: PlatformDef?, filesDir: File, cacheDir: File): GameMetadata {
        val now = System.currentTimeMillis()
        val system = platform?.libretroName?.takeIf { it.isNotBlank() } ?: platform?.name.orEmpty()
        if (system.isBlank()) return GameMetadata()
        return resolve(game, system, filesDir, cacheDir).copy(scrapedAt = now)
    }

    private fun resolve(
        game: GameRecord,
        system: String,
        filesDir: File,
        cacheDir: File,
    ): GameMetadata {
        val fileName = game.path.substringAfterLast('/', game.path)
        val titles = linkedSetOf<String>()
        RomIdentity.resolve(game.path, system)?.let { titles += it }
        titles += fileName
        titles += GameTitles.clean(fileName)
        titles += game.title
        val urls = LibretroThumbnails.coverUrls(system, titles, cacheDir)
        val hit = CoverCache.fetch(filesDir, game.id, urls) ?: return GameMetadata()
        return GameMetadata(
            source = ScrapeSource.LIBRETRO,
            coverUrl = hit.url,
            coverPath = hit.path,
            scrapedAt = System.currentTimeMillis(),
        )
    }

    private object RomIdentity {
        private val SOURCES = listOf("no-intro", "redump")
        private val NAME_ATTR = Regex("\\bname\\s+\"([^\"]+)\"")
        private val MD5_ATTR = Regex("\\bmd5\\s+([0-9a-fA-F]+)")
        private val SHA1_ATTR = Regex("\\bsha1\\s+([0-9a-fA-F]+)")
        private val CRC_ATTR = Regex("\\bcrc\\s+([0-9a-fA-F]+)")
        private val dbs = ConcurrentHashMap<String, CachedDatabase>()
        private val digests = ConcurrentHashMap<String, FileHashes>()

        fun resolve(path: String, system: String): String? {
            val db = database(system) ?: return null
            val file = File(path)
            val length = file.length()
            if (!file.isFile || length <= 0L || length > MAX_HASH_BYTES) return null
            val values = digests(file, length) ?: return null
            return db.titles["md5:${values.md5}"]
                ?: db.titles["sha1:${values.sha1}"]
                ?: db.titles["crc:${values.crc}"]
        }

        private fun database(system: String): HashDatabase? {
            val key = system.lowercase(Locale.ROOT)
            val cached = dbs[key]
            if (cached != null && System.currentTimeMillis() - cached.at < HASH_DB_TTL) {
                return cached.db.takeIf { it.titles.isNotEmpty() }
            }
            val titles = HashMap<String, String>()
            for (source in SOURCES) {
                val text = ScrapeHttp.getFirst(ScrapeHttp.hashDatUrls(source, system)) ?: continue
                titles.putAll(parse(text).titles)
            }
            val db = HashDatabase(titles)
            dbs[key] = CachedDatabase(System.currentTimeMillis(), db)
            return db.takeIf { it.titles.isNotEmpty() }
        }

        private fun parse(text: String): HashDatabase {
            val titles = HashMap<String, String>()
            var gameName: String? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.startsWith("game (")) {
                    gameName = null
                    continue
                }
                val name = gameName
                if (name == null) {
                    if (line.startsWith("name ")) {
                        gameName = NAME_ATTR.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                    }
                    continue
                }
                if (!line.startsWith("rom (")) {
                    if (line == ")") gameName = null
                    continue
                }
                MD5_ATTR.find(line)?.groupValues?.get(1)?.lowercase(Locale.ROOT)?.let {
                    titles.putIfAbsent("md5:$it", name)
                }
                SHA1_ATTR.find(line)?.groupValues?.get(1)?.lowercase(Locale.ROOT)?.let {
                    titles.putIfAbsent("sha1:$it", name)
                }
                CRC_ATTR.find(line)?.groupValues?.get(1)?.let { value ->
                    val crc = try {
                        value.toLong(16)
                    } catch (_: Exception) {
                        null
                    }
                    if (crc != null) titles.putIfAbsent("crc:${hex(crc)}", name)
                }
            }
            return HashDatabase(titles)
        }

        private fun digests(file: File, length: Long): FileHashes? {
            val key = "${file.path}\n$length\n${file.lastModified()}"
            digests[key]?.let { return it }
            return try {
                val md5 = MessageDigest.getInstance("MD5")
                val sha1 = MessageDigest.getInstance("SHA-1")
                val crc = CRC32()
                val buffer = ByteArray(64 * 1024)
                file.inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        md5.update(buffer, 0, read)
                        sha1.update(buffer, 0, read)
                        crc.update(buffer, 0, read)
                    }
                }
                val result = FileHashes(hex(md5.digest()), hex(sha1.digest()), hex(crc.value))
                digests[key] = result
                result
            } catch (_: Exception) {
                null
            }
        }

        private fun hex(bytes: ByteArray): String {
            val digits = "0123456789abcdef"
            val out = CharArray(bytes.size * 2)
            for (i in bytes.indices) {
                val value = bytes[i].toInt() and 0xFF
                out[i * 2] = digits[value ushr 4]
                out[i * 2 + 1] = digits[value and 0x0F]
            }
            return String(out)
        }

        private fun hex(value: Long): String {
            val digits = "0123456789abcdef"
            var current = value
            val out = CharArray(8)
            for (i in 7 downTo 0) {
                out[i] = digits[(current and 0xFL).toInt()]
                current = current ushr 4
            }
            return String(out)
        }

        private data class FileHashes(val md5: String, val sha1: String, val crc: String)
        private data class HashDatabase(val titles: Map<String, String>)
        private data class CachedDatabase(val at: Long, val db: HashDatabase)
    }
}
