package com.wakeupver.pijar.data

import com.wakeupver.pijar.core.GameTitles
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Mencari gambar sampul lokal di samping berkas ROM (nama sama) atau di subfolder media umum. */
object ArtFinder {
    private val cache = ConcurrentHashMap<String, String>()
    private val EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")
    private val FOLDERS = listOf("", "media", "images", "boxart", "covers", "Named_Boxarts")

    fun find(romPath: String): File? {
        val cached = cache[romPath]
        if (cached != null) return if (cached.isEmpty()) null else File(cached)

        val rom = File(romPath)
        val dir = rom.parentFile
        var found: File? = null
        if (dir != null) {
            val base = GameTitles.baseName(rom.name)
            search@ for (folder in FOLDERS) {
                val parent = if (folder.isEmpty()) dir else File(dir, folder)
                for (ext in EXTENSIONS) {
                    val candidate = File(parent, "$base.$ext")
                    if (candidate.isFile) {
                        found = candidate
                        break@search
                    }
                }
            }
        }
        cache[romPath] = found?.path ?: ""
        return found
    }
}
