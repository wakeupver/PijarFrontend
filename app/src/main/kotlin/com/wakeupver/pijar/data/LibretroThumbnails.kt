package com.wakeupver.pijar.data

import com.wakeupver.pijar.core.GameTitles
import com.wakeupver.pijar.core.ScrapeMatcher
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

object LibretroThumbnails {
    private const val ROOT = "https://thumbnails.libretro.com"
    private const val SUCCESS_TTL = 7L * 24 * 60 * 60 * 1000
    private const val FAILURE_TTL = 10L * 60 * 1000
    private const val MAX_CANDIDATES = 160
    private const val MAX_URLS = 8
    private const val MAX_TOKEN_POSTINGS = 4
    private val JSON_HEADERS = mapOf("Accept" to "application/vnd.github+json")
    private val STOPWORDS = setOf(
        "the", "a", "an", "and", "of", "us", "usa", "jp", "japan", "eu", "europe", "world",
        "version", "edition", "rev", "revision", "ver",
    )
    private val TOKEN_ALIASES = mapOf(
        "ii" to "2", "iii" to "3", "iv" to "4", "vi" to "6",
        "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10",
    )
    private val LINK = Regex("""<a\s+href="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
    private val IMAGE = Regex("""\.(png|jpg|jpeg|webp)$""", RegexOption.IGNORE_CASE)
    private val indexes = ConcurrentHashMap<String, CachedIndex>()
    private val locks = ConcurrentHashMap<String, Any>()

    private enum class Format { TREE, HTML, CONTENTS }

    fun warm(system: String, cacheDir: File) {
        if (system.isNotBlank()) index(system, cacheDir)
    }

    fun coverUrls(system: String, titles: Collection<String>, cacheDir: File): List<String> {
        if (system.isBlank() || titles.isEmpty()) return emptyList()
        val index = index(system, cacheDir)
        if (index.covers.isEmpty()) return emptyList()
        val queries = titles.map(::query).filter { it.normalized.isNotBlank() || it.tokens.isNotEmpty() }
        val scores = HashMap<Int, Int>()
        for (q in queries) {
            index.byNormalized[q.normalized]?.forEach { id ->
                val value = 1000 + regionBonus(q, index.covers[id])
                scores[id] = maxOf(scores[id] ?: Int.MIN_VALUE, value)
            }
            for (id in candidates(q, index)) {
                val item = index.covers[id]
                val value = similarity(q, item) + regionBonus(q, item) - item.penalty
                if (value > 0) scores[id] = maxOf(scores[id] ?: Int.MIN_VALUE, value)
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(MAX_URLS)
            .flatMap { ScrapeHttp.libretroImageUrls(system, index.covers[it.key].name) }
            .distinct()
    }

    private fun query(value: String): Query {
        val normalized = ScrapeMatcher.normalize(value)
        return Query(normalized, tokens(normalized), regions(value))
    }

    private fun candidates(q: Query, index: Index): Set<Int> {
        if (q.tokens.isEmpty()) return emptySet()
        val postings = ArrayList<Set<Int>>(q.tokens.size)
        for (token in q.tokens) {
            val exact = index.byToken[token]
            if (exact != null) {
                postings += exact.toHashSet()
                continue
            }
            var near: Set<Int>? = null
            for ((key, ids) in index.byToken) {
                if (key.startsWith(token) || token.startsWith(key)) {
                    if (near == null || ids.size < near.size) near = ids.toHashSet()
                }
            }
            if (near != null) postings += near
        }
        if (postings.isEmpty()) return emptySet()
        postings.sortBy { it.size }
        val rarest = postings.take(MAX_TOKEN_POSTINGS)
        val intersection = LinkedHashSet<Int>()
        for (id in rarest.first()) {
            if (rarest.all { it.contains(id) }) {
                intersection += id
                if (intersection.size >= MAX_CANDIDATES) return intersection
            }
        }
        if (intersection.isNotEmpty()) return intersection
        val union = LinkedHashSet<Int>()
        for (posting in rarest) {
            for (id in posting) {
                union += id
                if (union.size >= MAX_CANDIDATES) return union
            }
        }
        return union
    }

    private fun similarity(q: Query, item: IndexedCover): Int {
        if (q.normalized == item.normalized) return 1000
        val qt = q.tokens
        val ct = item.tokens.toList()
        if (qt.isEmpty() || ct.isEmpty()) return 0
        if (digits(qt) != digits(ct)) return 0
        val matchedQuery = qt.count { token -> ct.any { similar(it, token) } }
        val matchedCandidate = ct.count { token -> qt.any { similar(it, token) } }
        if (matchedQuery < 2 || matchedCandidate < 2) return 0
        val coverageQuery = matchedQuery * 100 / qt.size
        val coverageCandidate = matchedCandidate * 100 / ct.size
        val base = when {
            coverageQuery >= 90 && coverageCandidate >= 90 -> 900
            coverageQuery >= 70 && coverageCandidate >= 70 -> 760
            coverageQuery >= 50 && coverageCandidate >= 60 -> 560
            else -> 0
        }
        if (base == 0) return 0
        return (base - (ct.size - matchedCandidate) * 20 - (qt.size - matchedQuery) * 10).coerceAtLeast(1)
    }

    private fun digits(tokens: List<String>): Set<String> =
        tokens.filter { token -> token.any { it.isDigit() } }.toSet()

    private fun similar(a: String, b: String): Boolean =
        a == b || (a.length >= 3 && b.length >= 3 && (a.startsWith(b) || b.startsWith(a)))

    private fun regionBonus(q: Query, item: IndexedCover): Int {
        if (q.regions.isEmpty() || item.regions.isEmpty()) return 0
        val overlap = q.regions.intersect(item.regions).size
        return if (overlap > 0) 12 * overlap else -8
    }

    private fun index(system: String, cacheDir: File): Index {
        val key = system.lowercase(Locale.ROOT)
        indexes[key]?.let { cached ->
            if (!cached.expired()) return cached.index
        }
        val lock = locks.computeIfAbsent(key) { Any() }
        return synchronized(lock) {
            indexes[key]?.let { cached ->
                if (!cached.expired()) return@synchronized cached.index
            }
            val file = cacheFile(cacheDir, key)
            val stored = readCache(file)
            val names = stored ?: loadNames(system).also { writeCache(file, it) }
            val built = build(names)
            indexes[key] = CachedIndex(System.currentTimeMillis(), built)
            built
        }
    }

    private fun loadNames(system: String): List<String> {
        val found = linkedSetOf<String>()
        val tree = ScrapeHttp.get(ScrapeHttp.libretroTreeApi(system), JSON_HEADERS)
        if (!tree.isNullOrBlank()) parse(tree, Format.TREE, found)
        val html = ScrapeHttp.get("$ROOT/${ScrapeHttp.encode(system)}/Named_Boxarts/")
        if (!html.isNullOrBlank()) parse(html, Format.HTML, found)
        val contents = ScrapeHttp.get(ScrapeHttp.libretroContentsApi(system), JSON_HEADERS)
        if (!contents.isNullOrBlank()) parse(contents, Format.CONTENTS, found)
        return found.toList()
    }

    private fun parse(text: String, format: Format, output: MutableSet<String>) {
        when (format) {
            Format.HTML -> {
                for (result in LINK.findAll(text)) {
                    val href = result.groupValues[1]
                    addName(href.substring(href.lastIndexOf('/') + 1), output, true)
                }
            }
            Format.CONTENTS -> {
                try {
                    val array = JSONArray(text)
                    for (i in 0 until array.length()) {
                        addName(array.optJSONObject(i)?.optString("name").orEmpty(), output, false)
                    }
                } catch (_: Exception) {
                }
            }
            Format.TREE -> {
                try {
                    val tree = JSONObject(text).optJSONArray("tree") ?: return
                    for (i in 0 until tree.length()) {
                        val path = tree.optJSONObject(i)?.optString("path").orEmpty()
                        if (!path.startsWith("Named_Boxarts/")) continue
                        addName(path.substringAfterLast('/'), output, false)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun addName(raw: String, output: MutableSet<String>, decodeValue: Boolean) {
        val value = (if (decodeValue) decode(raw) else raw).trim()
        if (value.isNotBlank() && IMAGE.containsMatchIn(value)) output += value
    }

    private fun decode(value: String): String {
        val replaced = value.replace("&amp;", "&").replace("+", "%2B")
        return try {
            URLDecoder.decode(replaced, "UTF-8")
        } catch (_: Exception) {
            replaced
        }
    }

    private fun build(names: List<String>): Index {
        val covers = ArrayList<IndexedCover>(names.size)
        val byToken = HashMap<String, MutableList<Int>>()
        val byNormalized = HashMap<String, MutableList<Int>>()
        names.forEachIndexed { i, name ->
            val normalized = ScrapeMatcher.normalize(GameTitles.clean(name))
                .ifBlank { ScrapeMatcher.normalize(GameTitles.baseName(name)) }
            val item = IndexedCover(
                name = name,
                normalized = normalized,
                tokens = tokens(normalized).toSet(),
                regions = regions(name),
                penalty = qualityPenalty(name),
            )
            covers += item
            if (normalized.isNotBlank()) byNormalized.getOrPut(normalized) { ArrayList() }.add(i)
            item.tokens.forEach { token -> byToken.getOrPut(token) { ArrayList() }.add(i) }
        }
        return Index(covers, byToken, byNormalized)
    }

    private fun tokens(value: String): List<String> =
        value.split(' ')
            .map { TOKEN_ALIASES[it] ?: it }
            .filter { it.isNotBlank() && it !in STOPWORDS && (it.length > 1 || it[0].isDigit()) }

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
        val ttl = if (names.isEmpty()) FAILURE_TTL else SUCCESS_TTL
        val age = System.currentTimeMillis() - file.lastModified()
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

    private data class Query(
        val normalized: String,
        val tokens: List<String>,
        val regions: Set<String>,
    )

    private data class IndexedCover(
        val name: String,
        val normalized: String,
        val tokens: Set<String>,
        val regions: Set<String>,
        val penalty: Int,
    )

    private data class Index(
        val covers: List<IndexedCover>,
        val byToken: Map<String, List<Int>>,
        val byNormalized: Map<String, List<Int>>,
    )

    private data class CachedIndex(val at: Long, val index: Index) {
        fun expired(): Boolean {
            val ttl = if (index.covers.isEmpty()) FAILURE_TTL else SUCCESS_TTL
            val age = System.currentTimeMillis() - at
            return age < 0L || age > ttl
        }
    }
}
