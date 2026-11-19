package com.wakeupver.pijar.core

/**
 * Pembaca format data terbuka Daijishō (repo TapiocaFox/Daijishou, lisensi MIT):
 * berkas JSON per platform dan index.json di folder platforms.
 */
object DaijishouFormat {
    fun parsePlatform(text: String): PlatformDef {
        val root = Json.parse(text).asObject() ?: throw JsonException("Berkas platform bukan objek JSON")
        val platform = root["platform"].asObject() ?: throw JsonException("Bidang 'platform' tidak ada")
        val shortName = platform.string("shortname")
        val uniqueId = platform.string("uniqueId").ifBlank { shortName }
        if (uniqueId.isBlank()) throw JsonException("Platform tanpa uniqueId/shortname")
        val name = platform.string("name").ifBlank { uniqueId }
        val libretroName = (platform["scraperSourceList"] as? List<*>)
            .orEmpty()
            .mapNotNull { it as? String }
            .firstOrNull { it.startsWith("LIBRETRO:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
        val players = root.objects("playerList").map { p ->
            val args = p.string("amStartArguments")
            val playerId = p.string("uniqueId")
            PlayerDef(
                uniqueId = playerId,
                name = p.string("name").ifBlank { playerId },
                description = p.string("description"),
                acceptedFilenameRegex = p.string("acceptedFilenameRegex", ".*"),
                amStartArguments = args,
                killPackageProcesses = p.bool("killPackageProcesses"),
                packageName = AmArgs.parseOrNull(args)?.targetPackage(),
            )
        }
        return PlatformDef(
            uniqueId = uniqueId,
            name = name,
            shortName = shortName.ifBlank { uniqueId },
            acceptedFilenameRegex = platform.string("acceptedFilenameRegex", ".*"),
            revision = root.int("revisionNumber"),
            players = players,
            libretroName = libretroName,
        )
    }

    fun parseIndex(text: String): List<CatalogEntry> {
        val root = Json.parse(text).asObject() ?: throw JsonException("index.json bukan objek JSON")
        return root.objects("platformList").map { o ->
            CatalogEntry(
                filename = o.string("filename"),
                name = o.string("platformName"),
                shortName = o.string("platformShortname"),
                uniqueId = o.string("platformUniqueId"),
                revision = o.int("revisionNumber"),
            )
        }.filter { it.filename.isNotBlank() }
    }
}
