package com.wakeupver.pijar.core

object GameTitles {
    private val GROUP = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val SPACES = Regex("\\s+")
    private val PAREN = Regex("""\(([^)]{1,24})\)""")

    /** Nama berkas tanpa ekstensi (ekstensi = sampai 6 karakter setelah titik terakhir). */
    fun baseName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0 && fileName.length - dot <= 7) fileName.substring(0, dot) else fileName
    }

    /** Judul tampilan: tanpa ekstensi, tanpa tag (USA)/[!], garis bawah jadi spasi. */
    fun clean(fileName: String): String {
        val base = baseName(fileName).replace('_', ' ')
        val stripped = SPACES.replace(GROUP.replace(base, ""), " ").trim()
        return stripped.ifEmpty { base.trim().ifEmpty { fileName } }
    }

    /** Tag dalam kurung, mis. ["USA", "Rev 1"]. */
    fun tags(fileName: String): List<String> =
        PAREN.findAll(baseName(fileName)).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
}
