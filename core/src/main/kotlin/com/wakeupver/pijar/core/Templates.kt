package com.wakeupver.pijar.core

import java.io.File

/**
 * Konteks pengisian template untuk satu file game.
 * [uriProvider] hanya dipanggil bila `{file.uri}` benar-benar dipakai.
 */
class TemplateContext(
    val path: String,
    val mime: String = "application/octet-stream",
    val tags: Map<String, String> = emptyMap(),
    /** Nilai `{file.path}`; default memakai path asli. */
    private val pathProvider: () -> String = { path },
    private val uriProvider: () -> String,
) {
    internal val file: File by lazy(LazyThreadSafetyMode.NONE) { File(path) }

    var uriUsed: Boolean = false
        private set

    fun filePath(): String = pathProvider()

    fun fileUri(): String {
        uriUsed = true
        return uriProvider()
    }
}

/**
 * Template yang dipakai data Daijishō: {file.path}, {file.uri}, {file.mime}, {tags.<nama>}.
 * Tambahan khusus aplikasi ini: {file.name}, {file.basename}, {file.ext}, {file.dir}.
 * Tag tak dikenal dibiarkan apa adanya; {tags.x} yang tidak ada nilainya menjadi string kosong.
 */
object Templates {
    private val TAG = Regex("\\{([A-Za-z0-9_.\\-]+)\\}")

    fun expand(template: String, ctx: TemplateContext): String {
        if (!template.contains('{')) return template
        return TAG.replace(template) { m -> lookup(m.groupValues[1], ctx) ?: m.value }
    }

    private fun lookup(name: String, ctx: TemplateContext): String? {
        return when {
            name == "file.path" -> ctx.filePath()
            name == "file.uri" -> ctx.fileUri()
            name == "file.mime" -> ctx.mime
            name == "file.name" -> ctx.file.name
            name == "file.basename" -> GameTitles.baseName(ctx.file.name)
            name == "file.ext" -> ctx.file.extension
            name == "file.dir" -> ctx.file.parent.orEmpty()
            name.startsWith("tags.") -> {
                val key = name.removePrefix("tags.")
                ctx.tags[key]
                    ?: ctx.tags.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
                    ?: ""
            }
            else -> null
        }
    }
}
