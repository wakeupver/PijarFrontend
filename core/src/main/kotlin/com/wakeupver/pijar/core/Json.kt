package com.wakeupver.pijar.core

/** Galat saat membaca JSON. */
class JsonException(message: String) : RuntimeException(message)

/**
 * JSON minimal tanpa dependensi, supaya `core` murni Kotlin dan bisa diuji di JVM.
 * Hasil parse: Map<String, Any?>, List<Any?>, String, Long, Double, Boolean, atau null.
 */
object Json {
    fun parse(text: String): Any? {
        val parser = Parser(text.removePrefix("\uFEFF"))
        parser.skipWhitespace()
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonException("Karakter berlebih di posisi ${parser.pos}")
        return value
    }

    fun stringify(value: Any?, pretty: Boolean = false): String {
        val sb = StringBuilder()
        write(sb, value, pretty, 0)
        return sb.toString()
    }

    private class Parser(private val s: String) {
        var pos = 0
            private set

        fun atEnd(): Boolean = pos >= s.length

        private fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'

        fun skipWhitespace() {
            while (pos < s.length) {
                val c = s[pos]
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++ else break
            }
        }

        fun readValue(): Any? {
            if (atEnd()) throw JsonException("Akhir data tak terduga")
            val c = s[pos]
            return when {
                c == '{' -> readObject()
                c == '[' -> readArray()
                c == '"' -> readString()
                c == 't' -> literal("true", true)
                c == 'f' -> literal("false", false)
                c == 'n' -> literal("null", null)
                c == '-' || c in '0'..'9' -> readNumber()
                else -> throw JsonException("Karakter tak terduga '$c' di posisi $pos")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw JsonException("Literal tidak valid di posisi $pos")
            pos += word.length
            return value
        }

        private fun expect(c: Char) {
            if (peek() != c) throw JsonException("Diharapkan '$c' di posisi $pos")
            pos++
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw JsonException("Kunci objek harus string di posisi $pos")
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                map[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw JsonException("Diharapkan ',' atau '}' di posisi $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            pos++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                skipWhitespace()
                list += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw JsonException("Diharapkan ',' atau ']' di posisi $pos")
                }
            }
        }

        private fun readString(): String {
            pos++ // tanda kutip pembuka
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("String tidak ditutup")
                val c = s[pos++]
                if (c == '"') return sb.toString()
                if (c != '\\') {
                    sb.append(c)
                    continue
                }
                if (atEnd()) throw JsonException("Escape tidak lengkap")
                when (val e = s[pos++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (pos + 4 > s.length) throw JsonException("Escape \\u tidak lengkap")
                        val code = s.substring(pos, pos + 4).toIntOrNull(16)
                            ?: throw JsonException("Escape \\u tidak valid di posisi $pos")
                        sb.append(code.toChar())
                        pos += 4
                    }
                    else -> throw JsonException("Escape tidak dikenal '\\$e'")
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (peek() in '0'..'9') pos++
            var isFloat = false
            if (peek() == '.') {
                isFloat = true
                pos++
                while (peek() in '0'..'9') pos++
            }
            if (peek() == 'e' || peek() == 'E') {
                isFloat = true
                pos++
                if (peek() == '+' || peek() == '-') pos++
                while (peek() in '0'..'9') pos++
            }
            val t = s.substring(start, pos)
            if (!isFloat) {
                t.toLongOrNull()?.let { return it }
            }
            return t.toDoubleOrNull() ?: throw JsonException("Angka tidak valid '$t' di posisi $start")
        }
    }

    private fun write(sb: StringBuilder, value: Any?, pretty: Boolean, depth: Int) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Double -> writeDouble(sb, value)
            is Float -> writeDouble(sb, value.toDouble())
            is Number -> sb.append(value.toLong())
            is Map<*, *> -> writeObject(sb, value, pretty, depth)
            is Iterable<*> -> writeArray(sb, value, pretty, depth)
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeDouble(sb: StringBuilder, d: Double) {
        if (d.isNaN() || d.isInfinite()) {
            sb.append("null")
        } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            sb.append(d.toLong())
        } else {
            sb.append(d)
        }
    }

    private fun newline(sb: StringBuilder, pretty: Boolean, depth: Int) {
        if (!pretty) return
        sb.append('\n')
        repeat(depth) { sb.append("  ") }
    }

    private fun writeObject(sb: StringBuilder, map: Map<*, *>, pretty: Boolean, depth: Int) {
        if (map.isEmpty()) {
            sb.append("{}")
            return
        }
        sb.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            newline(sb, pretty, depth + 1)
            writeString(sb, k.toString())
            sb.append(if (pretty) ": " else ":")
            write(sb, v, pretty, depth + 1)
        }
        newline(sb, pretty, depth)
        sb.append('}')
    }

    private fun writeArray(sb: StringBuilder, items: Iterable<*>, pretty: Boolean, depth: Int) {
        val iterator = items.iterator()
        if (!iterator.hasNext()) {
            sb.append("[]")
            return
        }
        sb.append('[')
        var first = true
        while (iterator.hasNext()) {
            if (!first) sb.append(',')
            first = false
            newline(sb, pretty, depth + 1)
            write(sb, iterator.next(), pretty, depth + 1)
        }
        newline(sb, pretty, depth)
        sb.append(']')
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u").append(String.format("%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}

@Suppress("UNCHECKED_CAST")
fun Any?.asObject(): Map<String, Any?>? = this as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Any?.asArray(): List<Any?>? = this as? List<Any?>

fun Map<String, Any?>.string(key: String, default: String = ""): String = (this[key] as? String) ?: default

fun Map<String, Any?>.bool(key: String, default: Boolean = false): Boolean = (this[key] as? Boolean) ?: default

fun Map<String, Any?>.long(key: String, default: Long = 0L): Long = (this[key] as? Number)?.toLong() ?: default

fun Map<String, Any?>.int(key: String, default: Int = 0): Int = (this[key] as? Number)?.toInt() ?: default

fun Map<String, Any?>.objects(key: String): List<Map<String, Any?>> =
    (this[key] as? List<*>)?.mapNotNull { it.asObject() } ?: emptyList()
