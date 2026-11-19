package com.wakeupver.pijar.core

enum class FileHandling {
    DEFAULT,
    STANDARD,
    LEGACY;

    companion object {
        fun from(raw: String): FileHandling =
            FileHandling.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FileHandling.DEFAULT
    }
}

data class PlatformConfig(
    val platformId: String,
    val folder: String = "",
    /** Kunci [PlayerDef.key] pilihan pengguna; kosong = otomatis. */
    val playerKey: String = "",
    val addedAt: Long = 0L,
    val fileHandling: FileHandling = FileHandling.DEFAULT,
    val setupDone: Boolean = false,
)

data class GameRecord(
    val id: String,
    val platformId: String,
    val path: String,
    val title: String,
    val addedAt: Long,
    val lastPlayedAt: Long = 0L,
    val playCount: Int = 0,
    val favorite: Boolean = false,
    val sizeBytes: Long = 0L,
)

data class UiPrefs(
    val dynamicColor: Boolean = true,
    /** Hue aksen (0..359) saat warna dinamis mati. */
    val accentHue: Int = 265,
)

data class AppState(
    val platforms: List<PlatformConfig> = emptyList(),
    val games: List<GameRecord> = emptyList(),
    val prefs: UiPrefs = UiPrefs(),
)

/** Serialisasi status aplikasi ke JSON (disimpan di filesDir/state.json). */
object StateCodec {
    const val VERSION = 1

    fun encode(state: AppState): String {
        val root = linkedMapOf<String, Any?>(
            "version" to VERSION,
            "prefs" to linkedMapOf<String, Any?>(
                "dynamicColor" to state.prefs.dynamicColor,
                "accentHue" to state.prefs.accentHue,
            ),
            "platforms" to state.platforms.map {
                linkedMapOf<String, Any?>(
                    "id" to it.platformId,
                    "folder" to it.folder,
                    "player" to it.playerKey,
                    "addedAt" to it.addedAt,
                    "fileHandling" to it.fileHandling.name,
                    "setupDone" to it.setupDone,
                )
            },
            "games" to state.games.map {
                linkedMapOf<String, Any?>(
                    "id" to it.id,
                    "platform" to it.platformId,
                    "path" to it.path,
                    "title" to it.title,
                    "addedAt" to it.addedAt,
                    "lastPlayedAt" to it.lastPlayedAt,
                    "playCount" to it.playCount,
                    "favorite" to it.favorite,
                    "size" to it.sizeBytes,
                )
            },
        )
        return Json.stringify(root)
    }

    fun decode(text: String): AppState {
        val root = Json.parse(text).asObject() ?: throw JsonException("Berkas status bukan objek JSON")
        val p = root["prefs"].asObject()
        val prefs = if (p == null) {
            UiPrefs()
        } else {
            UiPrefs(
                dynamicColor = p.bool("dynamicColor", true),
                accentHue = p.int("accentHue", 265).coerceIn(0, 359),
            )
        }
        val platforms = root.objects("platforms").mapNotNull { o ->
            val id = o.string("id")
            if (id.isBlank()) {
                null
            } else {
                PlatformConfig(
                    platformId = id,
                    folder = o.string("folder"),
                    playerKey = o.string("player"),
                    addedAt = o.long("addedAt"),
                    fileHandling = FileHandling.from(o.string("fileHandling")),
                    setupDone = o.bool("setupDone", true),
                )
            }
        }
        val games = root.objects("games").mapNotNull { o ->
            val id = o.string("id")
            val platform = o.string("platform")
            val path = o.string("path")
            if (id.isBlank() || platform.isBlank() || path.isBlank()) {
                null
            } else {
                GameRecord(
                    id = id,
                    platformId = platform,
                    path = path,
                    title = o.string("title").ifBlank { GameTitles.clean(path.substringAfterLast('/')) },
                    addedAt = o.long("addedAt"),
                    lastPlayedAt = o.long("lastPlayedAt"),
                    playCount = o.int("playCount"),
                    favorite = o.bool("favorite"),
                    sizeBytes = o.long("size"),
                )
            }
        }
        return AppState(platforms, games, prefs)
    }
}
