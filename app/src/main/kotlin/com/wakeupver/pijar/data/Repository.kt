package com.wakeupver.pijar.data

import android.content.Context
import android.util.Log
import com.wakeupver.pijar.core.AppState
import com.wakeupver.pijar.core.DaijishouFormat
import com.wakeupver.pijar.core.LibraryMerge
import com.wakeupver.pijar.core.LibraryScanner
import com.wakeupver.pijar.core.PlatformCatalog
import com.wakeupver.pijar.core.PlatformConfig
import com.wakeupver.pijar.core.PlatformDef
import com.wakeupver.pijar.core.Rx
import com.wakeupver.pijar.core.StateCodec
import com.wakeupver.pijar.core.UiPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sumber kebenaran aplikasi: katalog platform (aset Daijishō + impor pengguna),
 * platform yang dipilih, pustaka game, dan preferensi tampilan. Disimpan di filesDir/state.json.
 */
class Repository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateFile = File(context.filesDir, "state.json")
    private val userPlatformDir = File(context.filesDir, "platforms")
    private val lock = Any()
    private var saveJob: Job? = null

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _catalog = MutableStateFlow(PlatformCatalog(emptyList()))
    val catalog: StateFlow<PlatformCatalog> = _catalog.asStateFlow()

    private val _scanning = MutableStateFlow<Set<String>>(emptySet())
    val scanning: StateFlow<Set<String>> = _scanning.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch { load() }
    }

    private fun load() {
        val defs = LinkedHashMap<String, PlatformDef>()
        val assets = context.assets
        for (name in assets.list(ASSET_DIR).orEmpty().sorted()) {
            if (!name.endsWith(".json") || name == "index.json") continue
            try {
                val text = assets.open("$ASSET_DIR/$name").use { it.readBytes().toString(Charsets.UTF_8) }
                val def = DaijishouFormat.parsePlatform(text)
                defs[def.uniqueId] = def
            } catch (e: Exception) {
                Log.w(TAG, "Lewati aset $name: ${e.message}")
            }
        }
        userPlatformDir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.forEach { f ->
            try {
                val def = DaijishouFormat.parsePlatform(f.readText())
                defs[def.uniqueId] = def
            } catch (e: Exception) {
                Log.w(TAG, "Lewati platform impor ${f.name}: ${e.message}")
            }
        }
        _catalog.value = PlatformCatalog(defs.values)
        _state.value = readState()
        _ready.value = true
    }

    private fun readState(): AppState {
        if (!stateFile.isFile) return AppState()
        return try {
            StateCodec.decode(stateFile.readText())
        } catch (e: Exception) {
            Log.w(TAG, "state.json rusak, disalin ke .bak: ${e.message}")
            runCatching { stateFile.copyTo(File(stateFile.parentFile, "state.json.bak"), overwrite = true) }
            AppState()
        }
    }

    private fun update(transform: (AppState) -> AppState) {
        if (!_ready.value) return
        _state.update(transform)
        scheduleSave()
    }

    private fun scheduleSave() {
        synchronized(lock) {
            saveJob?.cancel()
            saveJob = scope.launch {
                delay(400)
                persist()
            }
        }
    }

    private fun persist() {
        synchronized(lock) {
            val text = StateCodec.encode(_state.value)
            val tmp = File(stateFile.parentFile, "state.json.tmp")
            try {
                tmp.writeText(text)
                if (!tmp.renameTo(stateFile)) {
                    stateFile.writeText(text)
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menyimpan state: ${e.message}")
            }
        }
    }

    fun addPlatform(id: String) = update { s ->
        if (s.platforms.any { it.platformId == id }) {
            s
        } else {
            s.copy(platforms = s.platforms + PlatformConfig(platformId = id, addedAt = System.currentTimeMillis()))
        }
    }

    fun removePlatform(id: String) = update { s ->
        s.copy(
            platforms = s.platforms.filterNot { it.platformId == id },
            games = s.games.filterNot { it.platformId == id },
        )
    }

    fun setFolder(id: String, folder: String) = update { s ->
        s.copy(platforms = s.platforms.map { if (it.platformId == id) it.copy(folder = folder) else it })
    }

    fun setPlayer(id: String, playerKey: String) = update { s ->
        s.copy(platforms = s.platforms.map { if (it.platformId == id) it.copy(playerKey = playerKey) else it })
    }

    fun setPrefs(transform: (UiPrefs) -> UiPrefs) = update { s -> s.copy(prefs = transform(s.prefs)) }

    fun toggleFavorite(gameId: String) = update { s ->
        s.copy(games = s.games.map { if (it.id == gameId) it.copy(favorite = !it.favorite) else it })
    }

    fun markPlayed(gameId: String) = update { s ->
        val now = System.currentTimeMillis()
        s.copy(games = s.games.map { if (it.id == gameId) it.copy(lastPlayedAt = now, playCount = it.playCount + 1) else it })
    }

    /** Memindai folder platform. Mengembalikan jumlah game, atau -1 bila platform/folder belum siap. */
    suspend fun scan(platformId: String): Int = withContext(Dispatchers.IO) {
        val config = _state.value.platforms.firstOrNull { it.platformId == platformId }
        val def = _catalog.value[platformId]
        if (config == null || def == null || config.folder.isBlank()) return@withContext -1
        _scanning.update { it + platformId }
        try {
            val files = LibraryScanner.scan(File(config.folder), Rx.ci(def.acceptedFilenameRegex))
            val now = System.currentTimeMillis()
            update { s ->
                val merged = LibraryMerge.merge(platformId, s.games, files, now)
                s.copy(games = s.games.filterNot { it.platformId == platformId } + merged)
            }
            files.size
        } finally {
            _scanning.update { it - platformId }
        }
    }

    /** Mengimpor satu berkas platform berformat Daijishō dan menambahkannya ke daftar platform pengguna. */
    fun importPlatformJson(text: String): PlatformDef {
        val def = DaijishouFormat.parsePlatform(text)
        userPlatformDir.mkdirs()
        val safeName = def.uniqueId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        File(userPlatformDir, "$safeName.json").writeText(text)
        _catalog.value = PlatformCatalog(_catalog.value.all.filterNot { it.uniqueId == def.uniqueId } + def)
        addPlatform(def.uniqueId)
        return def
    }

    private companion object {
        const val TAG = "PijarRepo"
        const val ASSET_DIR = "daijishou/platforms"
    }
}
