package com.wakeupver.pijar.core

/** Satu pemutar (emulator) dari definisi platform berformat Daijishō. */
data class PlayerDef(
    val uniqueId: String,
    val name: String,
    val description: String,
    val acceptedFilenameRegex: String,
    val amStartArguments: String,
    val killPackageProcesses: Boolean,
    val packageName: String?,
) {
    /** `uniqueId` di data Daijishō tidak selalu unik, jadi kunci = uniqueId + nama. */
    val key: String get() = "$uniqueId|$name"
}

/** Definisi platform (konsol/sistem) dari berkas JSON Daijishō. */
data class PlatformDef(
    val uniqueId: String,
    val name: String,
    val shortName: String,
    val acceptedFilenameRegex: String,
    val revision: Int,
    val players: List<PlayerDef>,
)

/** Satu entri di `platforms/index.json`. */
data class CatalogEntry(
    val filename: String,
    val name: String,
    val shortName: String,
    val uniqueId: String,
    val revision: Int,
)
