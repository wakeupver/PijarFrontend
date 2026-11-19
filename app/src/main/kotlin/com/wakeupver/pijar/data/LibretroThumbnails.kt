package com.wakeupver.pijar.data

import com.wakeupver.pijar.core.GameTitles
import com.wakeupver.pijar.core.ScrapeMatcher
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object LibretroThumbnails {
    private const val ROOT = "https://thumbnails.libretro.com"
    private const val SUCCESS_TTL = 7L * 24 * 60 * 60 * 1000
    private const val FAILURE_TTL = 10L * 60 * 1000
    private const val MAX_FUZZY_CANDIDATES = 400
    private const val MAX_TOKEN_POSTINGS = 4
    private val LINK = Regex("""<a\s+href="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
    private val IMAGE = Regex("""\.(png|jpg|jpeg|webp)$""", RegexOption.IGNORE_CASE)
    private val ILLEGAL = Regex("""[&*/:<>?|"]""")
    private val memory = ConcurrentHashMap<String, CachedIndex>()
    private val indexLocks = ConcurrentHashMap<String, Any>()
    private val matches = ConcurrentHashMap<String, String>()

    fun warm(system: String, cacheDir: File) {
        if (system.isBlank()) return
        index(system, cacheDir)
    }

    fun match(system: String, fileName: String, query: String, cacheDir: File): String? {
        val key = "$system\n$fileName\n$query"
        val cached = matches[key]
        if (cached != null) return cached
        val result = matchUncached(system, fileName, query, cacheDir)
        if (result != null) matches[key] = result
        return result
    }

    private fun matchUncached(system: String, fileName: String, query: String, cacheDir: File): String? {
        if (system.isBlank()) return null
        val index = index(system, cacheDir)
        if (index.items.isEmpty()) return null

        for (exact in exactCandidates(fileName, query)) {
            val found = index.exact[exact.lowercase(Locale.ROOT)] ?: continue
            return link(system, index.items[found].name)
        }

        val queries = candidates(fileName, query).map(::queryData)
        val candidateIndices = LinkedHashSet<Int>()
        for (entry in queries) {
            for (id in fuzzyCandidates(entry, index)) {
                if (!candidateIndices.add(id)) continue
                if (candidateIndices.size >= MAX_FUZZY_CANDIDATES) break
            }
            if (candidateIndices.size >= MAX_FUZZY_CANDIDATES) break
        }
        if (candidateIndices.isEmpty()) return null

        val sourceRegions = regions(fileName)
        var bestName: String? = null
        var bestScore = Int.MIN_VALUE
        for (candidateIndex in candidateIndices) {
            val item = index.items[candidateIndex]
            var score = 0
            var strong = false
            for (entry in queries) {
                score = maxOf(score, score(entry, item))
                if (!strong && isStrong(entry, item)) strong = true
            }
            if (!strong) continue
            val adjusted = score + tagBonus(sourceRegions, item.regions) - item.penalty
            if (adjusted > bestScore) {
                bestScore = adjusted
                bestName = item.name
                if (bestScore == 1000) break
            }
        }
        return bestName?.let { link(system, it) }
    }

    private fun fuzzyCandidates(query: QueryData, index: IndexedNames): Set<Int> {
        if (query.tokens.isEmpty()) return emptySet()
        val postings = ArrayList<Pair<String, Set<Int>>>(query.tokens.size)
        for (token in query.tokens) {
            val exact = index.byToken[token]
            if (exact != null) {
                postings += token to exact.toHashSet()
                continue
            }
            var near: List<Int>? = null
            for ((key, ids) in index.byToken) {
                if (key.startsWith(token) || token.startsWith(key)) {
                    if (near == null || ids.size < near.size) near = ids
                }
            }
            if (near != null) postings += token to near.toHashSet()
        }
        if (postings.isEmpty()) return emptySet()
        postings.sortBy { it.second.size }
        val rarest = postings.take(MAX_TOKEN_POSTINGS)
        val base = rarest.minByOrNull { it.second.size }?.second ?: return emptySet()
        val intersection = LinkedHashSet<Int>()
        for (id in base) {
            if (rarest.all { it.second.contains(id) }) {
                intersection += id
                if (intersection.size >= MAX_FUZZY_CANDIDATES) return intersection
            }
        }
        if (intersection.isNotEmpty()) return intersection
        val union = LinkedHashSet<Int>()
        for ((_, ids) in rarest) {
            for (id in ids) {
                union += id
                if (union.size >= MAX_FUZZY_CANDIDATES) return union
            }
        }
        return union
    }

    private fun index(system: String, cacheDir: File): IndexedNames {
        val key = system.lowercase(Locale.ROOT)
        memory[key]?.let { cached ->
            if (!expired(cached)) return cached.index
        }
        val lock = indexLocks.computeIfAbsent(key) { Any() }
        return synchronized(lock) {
            memory[key]?.let { cached ->
                if (!expired(cached)) return@synchronized cached.index
            }
            val file = cacheFile(cacheDir, key)
            val stored = readCache(file)
            val names = stored ?: run {
                val url = "$ROOT/${ScrapeHttp.encode(system)}/Named_Boxarts/"
                val found = ScrapeHttp.get(url)?.let(::parse).orEmpty()
                writeCache(file, found)
                found
            }
            val built = build(names)
            memory[key] = CachedIndex(System.currentTimeMillis(), built)
            built
        }
    }

    private fun build(names: List<String>): IndexedNames {
        val items = ArrayList<IndexedCover>(names.size)
        val byToken = HashMap<String, MutableList<Int>>()
        val exact = HashMap<String, Int>()
        names.forEachIndexed { index, name ->
            val base = GameTitles.baseName(name)
            val display = GameTitles.clean(name).ifBlank { base }
            val item = IndexedCover(
                name = name,
                normalized = ScrapeMatcher.normalize(display),
                tokens = ScrapeMatcher.tokens(display).toSet(),
                regions = regions(name),
                penalty = qualityPenalty(name),
            )
            items += item
            item.tokens.forEach { token ->
                byToken.getOrPut(token) { ArrayList() }.add(index)
            }
            putExact(exact, ILLEGAL.replace(base, "_"), index)
            putExact(exact, ILLEGAL.replace(name, "_"), index)
            putExact(exact, name, index)
        }
        return IndexedNames(items, byToken, exact)
    }

    private fun putExact(exact: HashMap<String, Int>, value: String, index: Int) {
        val key = value.lowercase(Locale.ROOT)
        if (key.isNotBlank() && !exact.containsKey(key)) exact[key] = index
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

    private fun queryData(value: String): QueryData =
        QueryData(ScrapeMatcher.normalize(value), ScrapeMatcher.tokens(value))

    private fun score(query: QueryData, item: IndexedCover): Int {
        val q = query.normalized
        val c = item.normalized
        if (q.isEmpty() || c.isEmpty()) return 0
        if (q == c) return 1000
        if (c.startsWith(q) || q.startsWith(c)) return 850
        if (c.contains(q) || q.contains(c)) return 750
        val hits = hits(query.tokens, item.tokens)
        if (hits == 0) return 0
        val coverage = hits * 100 / query.tokens.size
        val penalty = kotlin.math.abs(query.tokens.size - item.tokens.size) * 8
        return (250 + coverage - penalty).coerceAtLeast(1)
    }

    private fun isStrong(query: QueryData, item: IndexedCover): Boolean {
        if (query.tokens.isEmpty() || item.tokens.isEmpty()) return false
        val hits = hits(query.tokens, item.tokens)
        val coverage = hits * 100 / query.tokens.size
        if (hits >= minOf(2, query.tokens.size) && coverage >= 50) return true
        return hits >= 1 && score(query, item) >= 850
    }

    private fun hits(queryTokens: List<String>, candidateTokens: Set<String>): Int =
        queryTokens.count { token ->
            candidateTokens.any { it == token || it.startsWith(token) || token.startsWith(it) }
        }

    private fun tagBonus(source: Set<String>, candidate: Set<String>): Int {
        if (source.isEmpty()) return 0
        val overlap = source.intersect(candidate).size
        if (overlap > 0) return 80 * overlap
        if ("world" in source) return 10
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

    private fun expired(entry: CachedIndex): Boolean {
        val age = System.currentTimeMillis() - entry.at
        val ttl = if (entry.index.items.isEmpty()) FAILURE_TTL else SUCCESS_TTL
        return age < 0L || age > ttl
    }

    private data class QueryData(val normalized: String, val tokens: List<String>)

    private data class IndexedCover(
        val name: String,
        val normalized: String,
        val tokens: Set<String>,
        val regions: Set<String>,
        val penalty: Int,
    )

    private data class IndexedNames(
        val items: List<IndexedCover>,
        val byToken: Map<String, List<Int>>,
        val exact: Map<String, Int>,
    )

    private data class CachedIndex(val at: Long, val index: IndexedNames)
}
