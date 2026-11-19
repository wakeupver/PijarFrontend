package com.wakeupver.pijar.data

import com.wakeupver.pijar.core.GameTitles
import com.wakeupver.pijar.core.ScrapeMatcher
import java.io.File
import java.net.URLDecoder
import java.util.Locale

object LibretroThumbnails {
    private const val ROOT = "https://thumbnails.libretro.com"
    private const val SUCCESS_TTL = 7L * 24 * 60 * 60 * 1000
    private const val FAILURE_TTL = 10L * 60 * 1000
    private val LINK = Regex("""<a\s+href="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
    private val IMAGE = Regex("""\.(png|jpg|jpeg|webp)$""", RegexOption.IGNORE_CASE)
    private val ILLEGAL = Regex("""[&*/:<>?|"]""")
    private val memory = HashMap<String, Entry>()
    private val matches = HashMap<String, String?>()

    fun match(system: String, fileName: String, query: String, cacheDir: File): String? {
        val key = "$system\n$fileName\n$query"
        val cached = synchronized(matches) { matches[key] }
        if (cached != null) return cached
        val result = matchUncached(system, fileName, query, cacheDir)
        if (result != null) synchronized(matches) { matches[key] = result }
        return result
    }

    private fun matchUncached(system: String, fileName: String, query: String, cacheDir: File): String? {
        if (system.isBlank()) return null
        val entries = names(system, cacheDir)
        if (entries.isEmpty()) return null

        val exact = exactCandidates(fileName, query)
        entries.firstOrNull { entry ->
            val base = GameTitles.baseName(entry)
            exact.any { candidate ->
                candidate.equals(base, ignoreCase = true) ||
                    candidate.equals(entry, ignoreCase = true) ||
                    "$candidate.png".equals(entry, ignoreCase = true)
            }
        }?.let { return link(system, it) }

        val queries = candidates(fileName, query)
        var bestName: String? = null
        var bestScore = Int.MIN_VALUE
        for (entry in entries) {
            val display = displayName(entry)
            var score = maxOf(ScrapeMatcher.score(query, display), ScrapeMatcher.score(query, entry))
            for (candidate in queries) {
                score = maxOf(score, ScrapeMatcher.score(candidate, display))
            }
            if (!isStrong(queries, display)) continue
            val adjusted = score + tagBonus(fileName, entry) - qualityPenalty(entry)
            if (adjusted > bestScore) {
                bestScore = adjusted
                bestName = entry
            }
        }
        return bestName?.let { link(system, it) }
    }

    @Synchronized
    private fun names(system: String, cacheDir: File): List<String> {
        val key = system.lowercase(Locale.ROOT)
        val cached = memory[key]
        if (cached != null && !expired(cached)) return cached.names

        val file = cacheFile(cacheDir, key)
        val stored = readCache(file)
        if (stored != null) {
            memory[key] = Entry(System.currentTimeMillis(), stored)
            return stored
        }

        val url = "$ROOT/${ScrapeHttp.encode(system)}/Named_Boxarts/"
        val found = ScrapeHttp.get(url)?.let(::parse).orEmpty()
        memory[key] = Entry(System.currentTimeMillis(), found)
        writeCache(file, found)
        return found
    }

    private fun parse(html: String): List<String> {
        val found = linkedSetOf<String>()
        for (result in LINK.findAll(html)) {
            val href = result.groupValues[1]
            val raw = href.substring(href.lastIndexOf('/') + 1)
            if (raw.isBlank() || raw == "." || raw == "..") continue
            if (!IMAGE.containsMatchIn(raw)) continue
            found += decode(raw)
        }
        return found.toList()
    }

    private fun decode(value: String): String {
        val replaced = value.replace("&amp;", "&").replace("+", "%2B")
        return try {
            URLDecoder.decode(replaced, "UTF-8")
        } catch (_: Exception) {
            replaced
        }
    }

    private fun candidates(fileName: String, query: String): List<String> {
        val result = linkedSetOf<String>()
        result += GameTitles.baseName(fileName).trim()
        result += query.trim()
        result += fileName.trim()
        return result.filter { it.isNotEmpty() }
    }

    private fun exactCandidates(fileName: String, query: String): List<String> =
        candidates(fileName, query)
            .map { ILLEGAL.replace(it, "_") }
            .filter { it.isNotBlank() }
            .distinct()

    private fun displayName(name: String): String {
        val base = GameTitles.baseName(name)
        return GameTitles.clean(name).ifBlank { base }
    }

    private fun isStrong(queries: List<String>, candidate: String): Boolean {
        for (query in queries) {
            val queryTokens = ScrapeMatcher.tokens(query)
            val candidateTokens = ScrapeMatcher.tokens(candidate)
            if (queryTokens.isEmpty() || candidateTokens.isEmpty()) continue
            val hits = queryTokens.count { token ->
                candidateTokens.any { it == token || it.startsWith(token) || token.startsWith(it) }
            }
            val coverage = hits * 100 / queryTokens.size
            if (hits >= minOf(2, queryTokens.size) && coverage >= 50) return true
            if (hits >= 1 && ScrapeMatcher.score(query, candidate) >= 850) return true
        }
        return false
    }

    private fun tagBonus(source: String, candidate: String): Int {
        val sourceRegions = regions(source)
        if (sourceRegions.isEmpty()) return 0
        val candidateRegions = regions(candidate)
        val overlap = sourceRegions.intersect(candidateRegions).size
        if (overlap > 0) return 80 * overlap
        if ("world" in sourceRegions) return 10
        return -20
    }

    private fun regions(value: String): Set<String> {
        val result = linkedSetOf<String>()
        for (tag in GameTitles.tags(value)) {
            val lower = tag.lowercase(Locale.ROOT)
            if ("world" in lower) result += "world"
            if ("usa" in lower || lower == "us" || "canada" in lower) result += "usa"
            if ("europe" in lower || lower == "eu") result += "europe"
            if ("japan" in lower || lower == "jp") result += "japan"
            if ("australia" in lower) result += "australia"
            if ("france" in lower) result += "france"
            if ("germany" in lower) result += "germany"
            if ("spain" in lower) result += "spain"
            if ("italy" in lower) result += "italy"
            if ("korea" in lower) result += "korea"
            if ("china" in lower) result += "china"
            if ("taiwan" in lower) result += "taiwan"
            if ("sweden" in lower) result += "sweden"
            if ("brazil" in lower) result += "brazil"
        }
        return result
    }

    private fun qualityPenalty(value: String): Int {
        val bad = listOf("beta", "proto", "unl", "pirate", "hack", "demo", "sample", "alt")
        return if (GameTitles.tags(value).any { tag -> bad.any { tag.contains(it, ignoreCase = true) } }) 70 else 0
    }

    private fun link(system: String, name: String): String =
        "$ROOT/${ScrapeHttp.encode(system)}/Named_Boxarts/${ScrapeHttp.encode(name)}"

    private fun cacheFile(cacheDir: File, key: String): File {
        val safe = key.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .take(80)
        return File(File(cacheDir, "libretro-thumbnails"), "$safe-${Integer.toHexString(key.hashCode())}.txt")
    }

    private fun readCache(file: File): List<String>? {
        if (!file.isFile) return null
        val text = try {
            file.readText()
        } catch (_: Exception) {
            return null
        }
        val names = if (text.isBlank()) emptyList() else text.lineSequence().filter { it.isNotBlank() }.toList()
        val age = System.currentTimeMillis() - file.lastModified()
        val ttl = if (names.isEmpty()) FAILURE_TTL else SUCCESS_TTL
        if (age < 0L || age > ttl) return null
        return names
    }

    private fun writeCache(file: File, names: List<String>) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(names.joinToString("\n"))
        } catch (_: Exception) {
        }
    }

    private fun expired(entry: Entry): Boolean {
        val age = System.currentTimeMillis() - entry.at
        val ttl = if (entry.names.isEmpty()) FAILURE_TTL else SUCCESS_TTL
        return age < 0L || age > ttl
    }

    private data class Entry(val at: Long, val names: List<String>)
}
