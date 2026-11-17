package com.wakeupver.pijar.core

import java.io.File

data class ScannedFile(val path: String, val fileName: String, val sizeBytes: Long, val modifiedAt: Long)

object LibraryScanner {
    /**
     * Menelusuri [root] (tanpa folder/berkas tersembunyi) dan mengembalikan berkas yang namanya cocok [accept].
     */
    fun scan(
        root: File,
        accept: Regex,
        maxDepth: Int = 8,
        maxFiles: Int = 50_000,
        isCancelled: () -> Boolean = { false },
    ): List<ScannedFile> {
        val result = ArrayList<ScannedFile>()
        if (!root.isDirectory) return result
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty() && result.size < maxFiles) {
            if (isCancelled()) break
            val (dir, depth) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                val name = child.name
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (depth < maxDepth) stack.addLast(child to depth + 1)
                } else if (accept.matches(name)) {
                    result += ScannedFile(child.path, name, child.length(), child.lastModified())
                    if (result.size >= maxFiles) break
                }
            }
        }
        result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
        return result
    }
}
