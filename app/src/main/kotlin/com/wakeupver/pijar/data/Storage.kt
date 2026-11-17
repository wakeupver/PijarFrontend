package com.wakeupver.pijar.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import com.wakeupver.pijar.core.FolderGuess
import com.wakeupver.pijar.core.PlatformDef
import java.io.File

object Storage {
    /** Android 11+: "akses semua berkas"; di bawahnya cukup izin baca penyimpanan. */
    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun allFilesAccessIntent(context: Context): Intent {
        val pkg = Uri.parse("package:${context.packageName}")
        return if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
    }

    /**
     * Mengubah URI pohon SAF milik ExternalStorageProvider menjadi path file nyata,
     * mis. primary:ROMs/psx -> /storage/emulated/0/ROMs/psx. Null bila bukan penyimpanan eksternal.
     */
    fun treeUriToPath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val colon = docId.indexOf(':')
        val volume = if (colon >= 0) docId.substring(0, colon) else docId
        val relative = if (colon >= 0) docId.substring(colon + 1) else ""
        val base = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().path
        } else {
            "/storage/$volume"
        }
        return if (relative.isEmpty()) base else "$base/$relative"
    }

    /** Penyimpanan internal + kartu SD/USB yang terbaca. */
    fun externalRoots(): List<File> {
        val roots = ArrayList<File>()
        roots += Environment.getExternalStorageDirectory()
        File("/storage").listFiles()?.forEach { f ->
            if (f.isDirectory && f.name != "emulated" && f.name != "self" && f.canRead()) roots += f
        }
        return roots.distinct()
    }

    /** Menebak folder ROM platform dari konvensi umum (ROMs/psx, Roms/Sony PlayStation, ...). */
    fun guessFolder(def: PlatformDef): String? {
        val parents = listOf("ROMs", "Roms", "roms", "ROM", "Games", "Emulation/roms", "RetroArch/roms")
        val names = FolderGuess.names(def)
        for (root in externalRoots()) {
            for (parent in parents) {
                for (name in names) {
                    val candidate = File(File(root, parent), name)
                    if (candidate.isDirectory) return candidate.path
                }
            }
        }
        return null
    }
}
