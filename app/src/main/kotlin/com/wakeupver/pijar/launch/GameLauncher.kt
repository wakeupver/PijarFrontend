package com.wakeupver.pijar.launch

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.wakeupver.pijar.core.AmArgs
import com.wakeupver.pijar.core.Dpt
import com.wakeupver.pijar.core.Extra
import com.wakeupver.pijar.core.ExtraValue
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.IntentFlags
import com.wakeupver.pijar.core.PlayerDef
import com.wakeupver.pijar.core.ResolvedIntent
import com.wakeupver.pijar.core.TemplateContext
import java.io.File

class LaunchException(val reason: Reason, val detail: String = "") : Exception(detail) {
    enum class Reason { FILE_MISSING, BAD_ARGUMENTS, NO_TARGET, NOT_INSTALLED, DENIED, FAILED }
}

/**
 * Menjalankan game dengan pemutar (emulator) berformat Daijishō:
 * argumen gaya `am start` -> template diisi -> [Intent] -> startActivity.
 */
object GameLauncher {
    fun launch(context: Context, game: GameRecord, player: PlayerDef) {
        val file = File(game.path)
        if (!file.isFile) throw LaunchException(LaunchException.Reason.FILE_MISSING, game.path)

        val tags: Map<String, String> = if (Dpt.isDptFile(file.name)) {
            Dpt.parse(runCatching { file.readText() }.getOrDefault("")) ?: emptyMap()
        } else {
            emptyMap()
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

        var contentUri: Uri? = null
        val ctx = TemplateContext(game.path, mime, tags) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            contentUri = uri
            uri.toString()
        }

        val resolved = try {
            AmArgs.parse(player.amStartArguments).resolve(ctx)
        } catch (e: IllegalArgumentException) {
            throw LaunchException(LaunchException.Reason.BAD_ARGUMENTS, e.message.orEmpty())
        }

        val target = resolved.targetPackage
        if (target == null && resolved.action == null) {
            throw LaunchException(LaunchException.Reason.NO_TARGET)
        }

        val intent = buildIntent(resolved)

        // {file.uri} dipakai: beri emulator izin baca/tulis ke URI FileProvider kita.
        val uri = contentUri
        if (resolved.usesFileUri && uri != null) {
            val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            intent.addFlags(grant)
            intent.clipData = ClipData.newRawUri("rom", uri)
            if (target != null) {
                try {
                    context.grantUriPermission(target, uri, grant)
                } catch (e: Exception) {
                    // Tidak fatal: izin lewat clipData/flag tetap berlaku.
                }
            }
        }

        if (player.killPackageProcesses && target != null) {
            try {
                context.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(target)
            } catch (e: Exception) {
                // Tidak fatal.
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw LaunchException(LaunchException.Reason.NOT_INSTALLED, target.orEmpty())
        } catch (e: SecurityException) {
            throw LaunchException(LaunchException.Reason.DENIED, e.message.orEmpty())
        } catch (e: Exception) {
            throw LaunchException(LaunchException.Reason.FAILED, e.message.orEmpty())
        }
    }

    internal fun buildIntent(r: ResolvedIntent): Intent {
        val intent = Intent()
        val action = r.action
        if (action != null) intent.action = action
        for (category in r.categories) intent.addCategory(category)

        val componentPackage = r.componentPackage
        val componentClass = r.componentClass
        val packageName = r.packageName
        if (componentPackage != null && componentClass != null) {
            intent.component = ComponentName(componentPackage, componentClass)
        } else if (packageName != null) {
            intent.setPackage(packageName)
        }

        val data = r.data?.let { Uri.parse(it) }
        val mime = r.mimeType
        if (data != null && mime != null) {
            intent.setDataAndType(data, mime)
        } else if (data != null) {
            intent.data = data
        } else if (mime != null) {
            intent.type = mime
        }

        for (extra in r.extras) putExtra(intent, extra)
        intent.addFlags(r.flags or IntentFlags.ACTIVITY_NEW_TASK)
        return intent
    }

    private fun putExtra(intent: Intent, extra: Extra) {
        val key = extra.key
        when (val v = extra.value) {
            is ExtraValue.Text -> intent.putExtra(key, v.value)
            is ExtraValue.NullText -> intent.putExtra(key, null as String?)
            is ExtraValue.Bool -> intent.putExtra(key, v.value)
            is ExtraValue.IntNum -> intent.putExtra(key, v.value)
            is ExtraValue.LongNum -> intent.putExtra(key, v.value)
            is ExtraValue.FloatNum -> intent.putExtra(key, v.value)
            is ExtraValue.DoubleNum -> intent.putExtra(key, v.value)
            is ExtraValue.UriText -> intent.putExtra(key, Uri.parse(v.value))
            is ExtraValue.ComponentText -> {
                val component = ComponentName.unflattenFromString(v.value)
                if (component != null) intent.putExtra(key, component)
            }
            is ExtraValue.TextList -> intent.putExtra(key, v.value.toTypedArray())
            is ExtraValue.IntList -> intent.putExtra(key, v.value.toIntArray())
            is ExtraValue.LongList -> intent.putExtra(key, v.value.toLongArray())
            is ExtraValue.FloatList -> intent.putExtra(key, v.value.toFloatArray())
        }
    }
}
