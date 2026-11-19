package com.wakeupver.pijar.core

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Regex tanpa pembedaan huruf besar/kecil, di-cache; pola rusak menjadi "tidak cocok apa pun". */
object Rx {
    private val cache = ConcurrentHashMap<String, Regex>()
    private val NEVER = Regex("(?!)")

    fun ci(pattern: String): Regex = cache.getOrPut(pattern) {
        try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            NEVER
        }
    }
}

object Ids {
    private val HEX = "0123456789abcdef".toCharArray()
    private val SHA1 = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-1") }

    fun game(platformId: String, path: String): String {
        val md = SHA1.get()
        md.reset()
        val bytes = md.digest("$platformId\n$path".toByteArray(Charsets.UTF_8))
        val out = CharArray(16)
        for (i in 0 until 8) {
            val b = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[b ushr 4]
            out[i * 2 + 1] = HEX[b and 0x0F]
        }
        return String(out)
    }
}

/** Kumpulan definisi platform (bawaan + impor pengguna), diurutkan menurut nama. */
class PlatformCatalog(defs: Collection<PlatformDef>) {
    val all: List<PlatformDef> = defs.sortedBy { it.name.lowercase() }
    private val byId: Map<String, PlatformDef> = all.associateBy { it.uniqueId }

    operator fun get(id: String): PlatformDef? = byId[id]
    fun contains(id: String): Boolean = byId.containsKey(id)
}

object PlayerPicker {
    /** Pemutar yang regex-nya cocok dengan nama berkas. */
    fun compatible(platform: PlatformDef, fileName: String): List<PlayerDef> =
        platform.players.filter { Rx.ci(it.acceptedFilenameRegex).matches(fileName) }

    /** Pilihan pengguna bila masih cocok; jika tidak, pemutar pertama yang cocok. */
    fun choose(platform: PlatformDef, preferredKey: String, fileName: String): PlayerDef? {
        val list = compatible(platform, fileName)
        return list.firstOrNull { it.key == preferredKey } ?: list.firstOrNull()
    }
}

object LibraryMerge {
    /** Menggabungkan hasil pindai ke catatan lama sambil mempertahankan favorit/riwayat main. */
    fun merge(platformId: String, existing: List<GameRecord>, scanned: List<ScannedFile>, now: Long): List<GameRecord> {
        val old = existing.filter { it.platformId == platformId }.associateBy { it.id }
        return scanned.map { f ->
            val id = Ids.game(platformId, f.path)
            val prev = old[id]
            GameRecord(
                id = id,
                platformId = platformId,
                path = f.path,
                title = prev?.title ?: GameTitles.clean(f.fileName),
                addedAt = prev?.addedAt ?: now,
                lastPlayedAt = prev?.lastPlayedAt ?: 0L,
                playCount = prev?.playCount ?: 0,
                favorite = prev?.favorite ?: false,
                sizeBytes = f.sizeBytes,
                metadata = prev?.metadata ?: GameMetadata(),
            )
        }
    }
}

object FolderGuess {
    /** Nama folder yang lazim dipakai orang untuk ROM platform ini. */
    fun names(p: PlatformDef): List<String> {
        val raw = listOf(p.shortName, p.uniqueId, p.name, p.name.replace(" ", ""), p.name.replace(" ", "_"))
        return raw.filter { it.isNotBlank() }.distinct()
    }
}
