package com.wakeupver.pijar.core

/**
 * Daijishō Player Template (`.dpt`): baris pertama `# Daijishou Player Template` (atau `# DST`),
 * lalu baris `[nama_tag] nilai` yang mengisi `{tags.nama_tag}` pada argumen pemutar.
 */
object Dpt {
    const val EXTENSION = "dpt"

    fun isDptFile(fileName: String): Boolean = fileName.endsWith(".$EXTENSION", ignoreCase = true)

    /** Mengembalikan peta tag, atau null bila isi berkas bukan format .dpt. */
    fun parse(text: String): Map<String, String>? {
        val lines = text.removePrefix("\uFEFF").lines()
        val first = lines.firstOrNull()?.trim() ?: return null
        val ok = first.equals("# Daijishou Player Template", ignoreCase = true) || first.equals("# DST", ignoreCase = true)
        if (!ok) return null
        val tags = LinkedHashMap<String, String>()
        for (raw in lines.drop(1)) {
            val line = raw.trim()
            if (!line.startsWith("[")) continue
            val end = line.indexOf(']')
            if (end <= 1) continue
            tags[line.substring(1, end).trim()] = line.substring(end + 1).trim()
        }
        return tags
    }
}
