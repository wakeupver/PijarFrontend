package com.wakeupver.pijar.core

import java.text.Normalizer

enum class ScrapeSource {
    LIBRETRO;

    val storageName: String
        get() = "libretro"

    companion object {
        fun from(raw: String): ScrapeSource? = entries.firstOrNull {
            it.name.equals(raw, ignoreCase = true) || it.storageName.equals(raw, ignoreCase = true)
        }
    }
}

data class ScrapeSettings

data class GameMetadata(
    val source: ScrapeSource? = null,
    val title: String = "",
    val overview: String = "",
    val releaseDate: String = "",
    val genres: List<String> = emptyList(),
    val coverUrl: String = "",
    val coverPath: String = "",
    val scrapedAt: Long = 0L,
) {
    val hasText: Boolean
        get() = title.isNotBlank() || overview.isNotBlank() || releaseDate.isNotBlank() || genres.isNotEmpty()

    val hasData: Boolean
        get() = hasText || coverUrl.isNotBlank() || coverPath.isNotBlank()
}

object ScrapeMatcher {
    private val MARKS = Regex("\\p{M}")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val GROUPS = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val STOPWORDS = setOf("the", "a", "an", "and", "of", "us", "usa", "jp", "japan", "eu", "europe", "world")

    fun normalize(value: String): String {
        val withoutGroups = GROUPS.replace(value.replace("&", " and "), " ")
        val decomposed = Normalizer.normalize(withoutGroups.replace('_', ' '), Normalizer.Form.NFD)
        return NON_ALNUM.replace(MARKS.replace(decomposed, "").lowercase(), " ").trim()
    }

    fun tokens(value: String): List<String> =
        normalize(value).split(' ').filter { it.length > 1 && it !in STOPWORDS }

    fun score(query: String, candidate: String): Int {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isEmpty() || c.isEmpty()) return 0
        if (q == c) return 1000
        if (c.startsWith(q) || q.startsWith(c)) return 850
        if (c.contains(q) || q.contains(c)) return 750

        val qt = tokens(query)
        val ct = tokens(candidate)
        if (qt.isEmpty() || ct.isEmpty()) return 0
        val hits = qt.count { token -> ct.any { it == token || it.startsWith(token) || token.startsWith(it) } }
        if (hits == 0) return 0
        val coverage = hits * 100 / qt.size
        val penalty = kotlin.math.abs(qt.size - ct.size) * 8
        return (250 + coverage - penalty).coerceAtLeast(1)
    }

    fun <T> best(query: String, candidates: List<Pair<String, T>>, minScore: Int = 260): T? {
        var bestScore = 0
        var bestValue: T? = null
        for ((name, value) in candidates) {
            val score = score(query, name)
            if (score > bestScore) {
                bestScore = score
                bestValue = value
            }
        }
        return bestValue.takeIf { bestScore >= minScore }
    }
}
