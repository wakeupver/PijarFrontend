package com.wakeupver.pijar.core

/** Jenis extra pada perintah `am start` (-e/--es, --ez, --ei, dst). */
enum class ExtraType {
    STRING, NULL_STRING, BOOLEAN, INT, LONG, FLOAT, DOUBLE, URI, COMPONENT,
    STRING_ARRAY, INT_ARRAY, LONG_ARRAY, FLOAT_ARRAY,
}

/** Extra mentah: nilainya masih berupa template (mis. `{file.path}`). */
data class RawExtra(val type: ExtraType, val key: String, val values: List<String>)

/** Nilai extra yang sudah diketik, siap dipasang ke Intent oleh lapisan Android. */
sealed interface ExtraValue {
    data class Text(val value: String) : ExtraValue
    data object NullText : ExtraValue
    data class Bool(val value: Boolean) : ExtraValue
    data class IntNum(val value: Int) : ExtraValue
    data class LongNum(val value: Long) : ExtraValue
    data class FloatNum(val value: Float) : ExtraValue
    data class DoubleNum(val value: Double) : ExtraValue
    data class UriText(val value: String) : ExtraValue
    data class ComponentText(val value: String) : ExtraValue
    data class TextList(val value: List<String>) : ExtraValue
    data class IntList(val value: List<Int>) : ExtraValue
    data class LongList(val value: List<Long>) : ExtraValue
    data class FloatList(val value: List<Float>) : ExtraValue
}

data class Extra(val key: String, val value: ExtraValue)

/** Hasil akhir setelah semua template diisi; tinggal diterjemahkan menjadi `android.content.Intent`. */
data class ResolvedIntent(
    val action: String?,
    val data: String?,
    val mimeType: String?,
    val categories: List<String>,
    val componentPackage: String?,
    val componentClass: String?,
    val packageName: String?,
    val extras: List<Extra>,
    val flags: Int,
    /** true bila `{file.uri}` dipakai, sehingga pemanggil perlu memberi izin baca URI ke emulator. */
    val usesFileUri: Boolean,
) {
    val targetPackage: String? get() = componentPackage ?: packageName
}

/** Nilai konstanta flag Intent (sama dengan `android.content.Intent.FLAG_*`). */
object IntentFlags {
    const val ACTIVITY_NEW_TASK = 0x10000000

    val BY_OPTION: Map<String, Int> = mapOf(
        "--grant-read-uri-permission" to 0x00000001,
        "--grant-write-uri-permission" to 0x00000002,
        "--debug-log-resolution" to 0x00000008,
        "--exclude-stopped-packages" to 0x00000010,
        "--include-stopped-packages" to 0x00000020,
        "--grant-persistable-uri-permission" to 0x00000040,
        "--grant-prefix-uri-permission" to 0x00000080,
        "--activity-match-external" to 0x00000200,
        "--activity-task-on-home" to 0x00004000,
        "--activity-clear-task" to 0x00008000,
        "--activity-no-animation" to 0x00010000,
        "--activity-reorder-to-front" to 0x00020000,
        "--activity-no-user-action" to 0x00040000,
        "--activity-clear-when-task-reset" to 0x00080000,
        "--activity-launched-from-history" to 0x00100000,
        "--activity-reset-task-if-needed" to 0x00200000,
        "--activity-brought-to-front" to 0x00400000,
        "--activity-exclude-from-recents" to 0x00800000,
        "--activity-previous-is-top" to 0x01000000,
        "--activity-clear-top" to 0x04000000,
        "--activity-multiple-task" to 0x08000000,
        "--activity-new-task" to 0x10000000,
        "--activity-single-top" to 0x20000000,
        "--activity-no-history" to 0x40000000,
    )
}

object Components {
    /** "pkg/.Kelas" -> (pkg, pkg.Kelas); "pkg/a.b.Kelas" -> (pkg, a.b.Kelas). */
    fun split(raw: String): Pair<String, String>? {
        val slash = raw.indexOf('/')
        if (slash <= 0 || slash == raw.length - 1) return null
        val pkg = raw.substring(0, slash).trim()
        var cls = raw.substring(slash + 1).trim()
        if (cls.startsWith(".")) cls = pkg + cls
        return pkg to cls
    }
}

/** Perintah `am start` hasil parse; nilainya masih template sampai [resolve] dipanggil. */
class AmCommand(
    val action: String?,
    val data: String?,
    val mimeType: String?,
    val categories: List<String>,
    val component: String?,
    val packageName: String?,
    val extras: List<RawExtra>,
    val flags: Int,
    val warnings: List<String>,
) {
    fun targetPackage(): String? =
        Components.split(component.orEmpty())?.first ?: packageName?.takeIf { it.isNotBlank() }

    fun resolve(ctx: TemplateContext): ResolvedIntent {
        val resolvedAction = action?.let { Templates.expand(it, ctx) }
        val resolvedData = data?.let { Templates.expand(it, ctx) }
        val resolvedMime = mimeType?.let { Templates.expand(it, ctx) }
        val resolvedCategories = categories.map { Templates.expand(it, ctx) }
        val comp = component?.let { Components.split(Templates.expand(it, ctx)) }
        val resolvedPackage = packageName?.let { Templates.expand(it, ctx) }
        val resolvedExtras = extras.map { it.resolve(ctx) }
        return ResolvedIntent(
            action = resolvedAction,
            data = resolvedData,
            mimeType = resolvedMime,
            categories = resolvedCategories,
            componentPackage = comp?.first,
            componentClass = comp?.second,
            packageName = resolvedPackage,
            extras = resolvedExtras,
            flags = flags,
            usesFileUri = ctx.uriUsed,
        )
    }
}

private fun parseBool(s: String, key: String): Boolean {
    val v = s.trim().lowercase()
    return when {
        v == "true" || v == "t" -> true
        v == "false" || v == "f" -> false
        else -> (v.toIntOrNull() ?: throw IllegalArgumentException("Nilai boolean tidak valid untuk '$key': $s")) != 0
    }
}

private fun invalid(opt: String, key: String, value: String): Nothing =
    throw IllegalArgumentException("Nilai $opt untuk '$key' tidak valid: $value")

internal fun RawExtra.resolve(ctx: TemplateContext): Extra {
    val first = Templates.expand(values.firstOrNull().orEmpty(), ctx)
    val all = values.map { Templates.expand(it, ctx) }
    val value: ExtraValue = when (type) {
        ExtraType.STRING -> ExtraValue.Text(first)
        ExtraType.NULL_STRING -> ExtraValue.NullText
        ExtraType.BOOLEAN -> ExtraValue.Bool(parseBool(first, key))
        ExtraType.INT -> ExtraValue.IntNum(first.trim().toIntOrNull() ?: invalid("--ei", key, first))
        ExtraType.LONG -> ExtraValue.LongNum(first.trim().toLongOrNull() ?: invalid("--el", key, first))
        ExtraType.FLOAT -> ExtraValue.FloatNum(first.trim().toFloatOrNull() ?: invalid("--ef", key, first))
        ExtraType.DOUBLE -> ExtraValue.DoubleNum(first.trim().toDoubleOrNull() ?: invalid("--ed", key, first))
        ExtraType.URI -> ExtraValue.UriText(first)
        ExtraType.COMPONENT -> ExtraValue.ComponentText(first)
        ExtraType.STRING_ARRAY -> ExtraValue.TextList(all)
        ExtraType.INT_ARRAY -> ExtraValue.IntList(all.map { it.trim().toIntOrNull() ?: invalid("--eia", key, it) })
        ExtraType.LONG_ARRAY -> ExtraValue.LongList(all.map { it.trim().toLongOrNull() ?: invalid("--ela", key, it) })
        ExtraType.FLOAT_ARRAY -> ExtraValue.FloatList(all.map { it.trim().toFloatOrNull() ?: invalid("--efa", key, it) })
    }
    return Extra(key, value)
}

/**
 * Parser argumen gaya `am start` yang dipakai kolom `amStartArguments` pada data Daijishō.
 * Mendukung -a -d -t -c -n -p -f, semua --e* (extra), dan --activity-* / --grant-* (flag).
 */
object AmArgs {
    private val EXTRA_OPTIONS: Map<String, ExtraType> = mapOf(
        "-e" to ExtraType.STRING,
        "--es" to ExtraType.STRING,
        "--esn" to ExtraType.NULL_STRING,
        "--ez" to ExtraType.BOOLEAN,
        "--ei" to ExtraType.INT,
        "--el" to ExtraType.LONG,
        "--ef" to ExtraType.FLOAT,
        "--ed" to ExtraType.DOUBLE,
        "--eu" to ExtraType.URI,
        "--ecn" to ExtraType.COMPONENT,
        "--esa" to ExtraType.STRING_ARRAY,
        "--esal" to ExtraType.STRING_ARRAY,
        "--eia" to ExtraType.INT_ARRAY,
        "--eial" to ExtraType.INT_ARRAY,
        "--ela" to ExtraType.LONG_ARRAY,
        "--elal" to ExtraType.LONG_ARRAY,
        "--efa" to ExtraType.FLOAT_ARRAY,
        "--efal" to ExtraType.FLOAT_ARRAY,
    )
    private val ARRAY_TYPES = setOf(
        ExtraType.STRING_ARRAY, ExtraType.INT_ARRAY, ExtraType.LONG_ARRAY, ExtraType.FLOAT_ARRAY,
    )
    private val IGNORED_FLAGS = setOf("-W", "-S", "-D", "-N")
    private val IGNORED_WITH_VALUE = setOf("--user", "--display", "-R", "--task", "--windowingMode", "--activityType", "-P")

    fun parseOrNull(text: String): AmCommand? = try {
        parse(text)
    } catch (e: Exception) {
        null
    }

    fun parse(text: String): AmCommand {
        val tokens = tokenize(text)
        var action: String? = null
        var data: String? = null
        var mime: String? = null
        var component: String? = null
        var pkg: String? = null
        var flags = 0
        var positionalLaunch = false
        val categories = ArrayList<String>()
        val extras = ArrayList<RawExtra>()
        val warnings = ArrayList<String>()
        var i = 0

        fun next(opt: String): String? {
            return if (i + 1 < tokens.size) {
                i++
                tokens[i]
            } else {
                warnings += "Opsi $opt tanpa nilai"
                null
            }
        }

        while (i < tokens.size) {
            val t = tokens[i]
            val extraType = EXTRA_OPTIONS[t]
            val flag = IntentFlags.BY_OPTION[t]
            when {
                t == "-a" -> action = next(t) ?: action
                t == "-d" -> data = next(t) ?: data
                t == "-t" -> mime = next(t) ?: mime
                t == "-n" -> component = next(t) ?: component
                t == "-p" -> pkg = next(t) ?: pkg
                t == "-i" -> next(t)
                t == "-c" -> {
                    val v = next(t)
                    if (v != null) categories += v
                }
                t == "-f" -> {
                    val v = next(t)
                    if (v != null) {
                        val parsed = parseFlagsValue(v)
                        if (parsed != null) {
                            flags = flags or parsed
                        } else {
                            warnings += "Nilai -f tidak valid: $v"
                        }
                    }
                }
                flag != null -> flags = flags or flag
                extraType == ExtraType.NULL_STRING -> {
                    val key = next(t)
                    if (key != null) extras += RawExtra(ExtraType.NULL_STRING, key, emptyList())
                }
                extraType != null -> {
                    val key = next(t)
                    val value = if (key != null) next(t) else null
                    if (key != null && value != null) {
                        val values = if (extraType in ARRAY_TYPES) splitArray(value) else listOf(value)
                        extras += RawExtra(extraType, key, values)
                    }
                }
                t in IGNORED_FLAGS -> Unit
                t in IGNORED_WITH_VALUE -> next(t)
                t.startsWith("-") && t.length > 1 -> warnings += "Opsi tidak dikenal diabaikan: $t"
                t.contains(':') -> if (data == null) data = t
                t.contains('/') -> {
                    if (component == null) component = t
                    positionalLaunch = true
                }
                else -> {
                    if (pkg == null) pkg = t
                    positionalLaunch = true
                }
            }
            i++
        }

        if (positionalLaunch && action == null) {
            action = "android.intent.action.MAIN"
            if ("android.intent.category.LAUNCHER" !in categories) categories += "android.intent.category.LAUNCHER"
        }
        return AmCommand(action, data, mime, categories, component, pkg, extras, flags, warnings)
    }

    /** Pecah teks jadi token ala shell: spasi/baris baru = pemisah; '...' dan "..." menjaga spasi. */
    internal fun tokenize(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inToken = false
        var quote = '\u0000'
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quote != '\u0000') {
                if (c == quote) {
                    quote = '\u0000'
                } else if (c == '\\' && quote == '"' && i + 1 < text.length && (text[i + 1] == '"' || text[i + 1] == '\\')) {
                    cur.append(text[i + 1])
                    i++
                } else {
                    cur.append(c)
                }
            } else if (c == '"' || c == '\'') {
                quote = c
                inToken = true
            } else if (c == '\\' && i + 1 < text.length &&
                (text[i + 1].isWhitespace() || text[i + 1] == '"' || text[i + 1] == '\'' || text[i + 1] == '\\')
            ) {
                cur.append(text[i + 1])
                inToken = true
                i++
            } else if (c.isWhitespace()) {
                if (inToken) {
                    out += cur.toString()
                    cur.setLength(0)
                    inToken = false
                }
            } else {
                cur.append(c)
                inToken = true
            }
            i++
        }
        if (inToken) out += cur.toString()
        return out
    }

    /** Pecah nilai array berdasarkan koma; `\,` berarti koma literal. */
    internal fun splitArray(raw: String): List<String> {
        val parts = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length && raw[i + 1] == ',') {
                cur.append(',')
                i += 2
                continue
            }
            if (c == ',') {
                parts += cur.toString()
                cur.setLength(0)
            } else {
                cur.append(c)
            }
            i++
        }
        parts += cur.toString()
        return parts
    }

    internal fun parseFlagsValue(v: String): Int? {
        val s = v.trim()
        return if (s.startsWith("0x") || s.startsWith("0X")) {
            s.substring(2).toLongOrNull(16)?.toInt()
        } else {
            s.toLongOrNull()?.toInt()
        }
    }
}
