package com.wakeupver.pijar

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wakeupver.pijar.core.AppState
import com.wakeupver.pijar.core.FileHandling
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.PlatformCatalog
import com.wakeupver.pijar.core.PlayerPicker
import com.wakeupver.pijar.core.ScrapeSettings
import com.wakeupver.pijar.core.UiPrefs
import com.wakeupver.pijar.data.AppEntry
import com.wakeupver.pijar.data.Apps
import com.wakeupver.pijar.data.CoverCache
import com.wakeupver.pijar.data.Repository
import com.wakeupver.pijar.data.Storage
import com.wakeupver.pijar.launch.GameLauncher
import com.wakeupver.pijar.launch.LaunchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: Repository = (application as PijarApp).repo

    val state: StateFlow<AppState> = repo.state
    val catalog: StateFlow<PlatformCatalog> = repo.catalog
    val scanning: StateFlow<Set<String>> = repo.scanning
    val scraping: StateFlow<Set<String>> = repo.scraping
    val ready: StateFlow<Boolean> = repo.ready

    /** Hanya berubah bila preferensi berubah, agar tema tidak ikut berkomposisi ulang saat pustaka berubah. */
    val prefs: StateFlow<UiPrefs> = repo.state
        .map { it.prefs }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.state.value.prefs)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homeEvents: SharedFlow<Unit> = _homeEvents.asSharedFlow()

    private fun say(id: Int, vararg args: Any) {
        _messages.tryEmit(getApplication<Application>().getString(id, *args))
    }

    /** Dipanggil Activity saat tombol/gestur Home ditekan ketika aplikasi sudah terbuka. */
    fun onHomePressed() {
        _homeEvents.tryEmit(Unit)
    }

    private var lastAppsRefresh = 0L

    /** Memuat ulang daftar aplikasi; paling sering sekali per 8 detik karena dipanggil tiap ON_RESUME. */
    fun refreshApps() {
        val now = SystemClock.elapsedRealtime()
        if (_apps.value.isNotEmpty() && now - lastAppsRefresh < 8_000L) return
        lastAppsRefresh = now
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _apps.value = Apps.load(app, _apps.value)
        }
    }

    fun addPlatform(id: String) {
        repo.addPlatform(id)
        viewModelScope.launch(Dispatchers.IO) {
            val def = catalog.value[id] ?: return@launch
            val guess = Storage.guessFolder(def)
            if (guess != null) {
                repo.setFolder(id, guess)
                val n = repo.scan(id)
                if (n >= 0) say(R.string.msg_scan_done, n)
            }
        }
    }

    fun removePlatform(id: String) = repo.removePlatform(id)

    fun completePlatformSetup(id: String) = repo.completeSetup(id)

    fun discardPlatformSetup(id: String) = repo.removeUnfinishedPlatform(id)

    fun setFolder(id: String, folder: String) {
        repo.setFolder(id, folder.trim())
        scan(id)
    }

    fun setPlayer(id: String, playerKey: String) = repo.setPlayer(id, playerKey)

    fun setFileHandling(id: String, fileHandling: FileHandling) = repo.setFileHandling(id, fileHandling)

    fun scan(id: String) {
        viewModelScope.launch {
            val n = repo.scan(id)
            if (n >= 0) say(R.string.msg_scan_done, n) else say(R.string.msg_need_folder)
        }
    }

    fun scanAll() {
        viewModelScope.launch {
            var total = 0
            for (config in state.value.platforms) {
                val n = repo.scan(config.platformId, autoScrape = false)
                if (n > 0) total += n
            }
            say(R.string.msg_scan_done, total)
            val (scraped, targets) = repo.scrapeMissing()
            if (scraped > 0) say(R.string.msg_scrape_done, scraped, targets)
        }
    }

    fun scrape(gameId: String) {
        viewModelScope.launch {
            if (!repo.scrape(gameId)) say(R.string.msg_scrape_none)
        }
    }

    fun scrapeAll() {
        viewModelScope.launch {
            val (updated, total) = repo.scrapeAll()
            if (updated == 0) {
                say(R.string.msg_scrape_none)
            } else {
                say(R.string.msg_scrape_done, updated, total)
            }
        }
    }

    fun toggleFavorite(gameId: String) = repo.toggleFavorite(gameId)

    fun renameGame(gameId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isNotBlank()) repo.renameGame(gameId, trimmed)
    }

    fun removeGame(gameId: String) = repo.removeGame(gameId)

    fun setCover(gameId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = CoverCache.save(getApplication(), gameId, uri)
            if (path == null) {
                say(R.string.msg_cover_failed)
            } else {
                repo.setCover(gameId, path)
                say(R.string.msg_cover_ok)
            }
        }
    }

    fun notifyCoverFailed() = say(R.string.msg_cover_failed)

    fun setDynamicColor(enabled: Boolean) = repo.setPrefs { it.copy(dynamicColor = enabled) }

    fun setAccentHue(hue: Int) = repo.setPrefs { it.copy(accentHue = hue) }

    fun setScrapeSettings(settings: ScrapeSettings) = repo.setPrefs { it.copy(scrape = settings) }

    fun notifyFolderUnsupported() = say(R.string.msg_folder_unsupported)

    fun notifyImportFailed(reason: String) = say(R.string.msg_import_failed, reason)

    fun importPlatform(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val def = withContext(Dispatchers.IO) {
                    val text = app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IOException("berkas tidak bisa dibuka")
                    repo.importPlatformJson(text)
                }
                say(R.string.msg_import_ok, def.name)
            } catch (e: Exception) {
                say(R.string.msg_import_failed, e.message.orEmpty())
            }
        }
    }

    /** Menjalankan game dengan pemutar pilihan platform (atau pemutar pertama yang cocok). */
    fun play(context: Context, game: GameRecord) {
        val def = catalog.value[game.platformId]
        if (def == null) {
            say(R.string.msg_no_player)
            return
        }
        val config = state.value.platforms.firstOrNull { it.platformId == game.platformId }
        val player = PlayerPicker.choose(def, config?.playerKey.orEmpty(), File(game.path).name)
        if (player == null) {
            say(R.string.msg_no_player)
            return
        }
        try {
            GameLauncher.launch(context, game, player, config?.fileHandling ?: FileHandling.DEFAULT)
            repo.markPlayed(game.id)
        } catch (e: LaunchException) {
            when (e.reason) {
                LaunchException.Reason.FILE_MISSING -> say(R.string.msg_launch_missing, e.detail)
                LaunchException.Reason.BAD_ARGUMENTS -> say(R.string.msg_launch_bad_args, e.detail)
                LaunchException.Reason.NO_TARGET -> say(R.string.msg_launch_no_target)
                LaunchException.Reason.NOT_INSTALLED -> say(R.string.msg_launch_not_installed, e.detail.ifEmpty { player.name })
                LaunchException.Reason.DENIED -> say(R.string.msg_launch_denied, e.detail)
                LaunchException.Reason.FAILED -> say(R.string.msg_launch_failed, e.detail)
            }
        }
    }
}
