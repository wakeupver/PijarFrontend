@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.wakeupver.pijar.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeupver.pijar.MainViewModel
import com.wakeupver.pijar.R
import com.wakeupver.pijar.data.Apps
import com.wakeupver.pijar.data.Storage
import com.wakeupver.pijar.data.SystemUi

private const val TAB_HOME = 0
private const val TAB_LIBRARY = 1
private const val TAB_APPS = 2

/** Akar UI: tiga tab + pengaturan, bilah navigasi melayang, dan semua sheet. */
@Composable
fun PijarRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var libraryPlatform by rememberSaveable { mutableStateOf<String?>(null) }
    var gameSheetId by remember { mutableStateOf<String?>(null) }
    var platformSheetId by remember { mutableStateOf<String?>(null) }
    var showAddPlatform by remember { mutableStateOf(false) }
    var folderTarget by remember { mutableStateOf<String?>(null) }
    var storageGranted by remember { mutableStateOf(Storage.hasAllFilesAccess(context)) }
    var isDefaultHome by remember { mutableStateOf(SystemUi.isDefaultHome(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        storageGranted = Storage.hasAllFilesAccess(context)
        isDefaultHome = SystemUi.isDefaultHome(context)
        vm.refreshApps()
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { message -> snackbar.showSnackbar(message) }
    }
    // Tombol/gestur Home saat aplikasi sudah terbuka: kembali ke layar awal dan tutup semua sheet.
    LaunchedEffect(Unit) {
        vm.homeEvents.collect {
            tab = TAB_HOME
            libraryPlatform = null
            gameSheetId = null
            platformSheetId = null
            showAddPlatform = false
        }
    }
    // Gestur/tombol kembali (termasuk predictive back) dari tab lain menuju Beranda.
    BackHandler(enabled = tab != TAB_HOME) { tab = TAB_HOME }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val target = folderTarget
        folderTarget = null
        if (uri != null && target != null) {
            val path = Storage.treeUriToPath(uri)
            if (path != null) vm.setFolder(target, path) else vm.notifyFolderUnsupported()
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPlatform(uri)
    }
    val homeRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultHome = SystemUi.isDefaultHome(context)
    }

    val navItems = listOf(
        NavItem(stringResource(R.string.nav_home), Icons.Rounded.Home),
        NavItem(stringResource(R.string.nav_library), PijarIcons.Library),
        NavItem(stringResource(R.string.nav_apps), PijarIcons.Apps),
        NavItem(stringResource(R.string.nav_settings), Icons.Rounded.Settings),
    )
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val scrimAlpha = if (state.prefs.showWallpaper) 0.88f else 1f

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = scrimAlpha),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbar) },
            bottomBar = {
                if (!imeVisible) {
                    Box(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FloatingNavBar(items = navItems, selectedIndex = tab, onSelect = { tab = it })
                    }
                }
            },
        ) { padding ->
            // Ganti tab langsung tanpa crossfade seluruh layar: transisi lama menduplikasi
            // top app bar/judul sehingga tampak berkedip saat item navigasi diketuk.
            Box(modifier = Modifier.fillMaxSize().imePadding()) {
                when (tab) {
                    TAB_HOME -> HomeScreen(
                        state = state,
                        catalog = catalog,
                        storageGranted = storageGranted,
                        onOpenPlatform = { id ->
                            libraryPlatform = id
                            tab = TAB_LIBRARY
                        },
                        onOpenGame = { game -> gameSheetId = game.id },
                        onPlay = { game -> vm.play(context, game) },
                        onAddPlatform = { showAddPlatform = true },
                        onGrantStorage = { SystemUi.openSafely(context, Storage.allFilesAccessIntent(context)) },
                        contentPadding = padding,
                    )
                    TAB_LIBRARY -> LibraryScreen(
                        state = state,
                        catalog = catalog,
                        scanning = scanning,
                        selectedPlatform = libraryPlatform,
                        onSelectPlatform = { id -> libraryPlatform = id },
                        onOpenGame = { game -> gameSheetId = game.id },
                        onAddPlatform = { showAddPlatform = true },
                        onPlatformSettings = { id -> platformSheetId = id },
                        onRescan = { id ->
                            if (id != null) vm.scan(id) else vm.scanAll()
                        },
                        contentPadding = padding,
                    )
                    TAB_APPS -> AppsScreen(
                        apps = apps,
                        onLaunch = { app ->
                            try {
                                Apps.launch(context, app)
                            } catch (e: Exception) {
                                // Aplikasi sudah dihapus/dinonaktifkan: abaikan.
                            }
                        },
                        onInfo = { app -> Apps.openInfo(context, app.packageName) },
                        contentPadding = padding,
                    )
                    else -> SettingsScreen(
                        state = state,
                        catalog = catalog,
                        isDefaultHome = isDefaultHome,
                        storageGranted = storageGranted,
                        onOpenPlatform = { id -> platformSheetId = id },
                        onAddPlatform = { showAddPlatform = true },
                        onRescanAll = { vm.scanAll() },
                        onImport = {
                            try {
                                importPicker.launch(arrayOf("*/*"))
                            } catch (e: ActivityNotFoundException) {
                                vm.notifyImportFailed("no file picker")
                            }
                        },
                        onDynamicColor = { enabled -> vm.setDynamicColor(enabled) },
                        onAccent = { hue -> vm.setAccentHue(hue) },
                        onWallpaper = { show -> vm.setShowWallpaper(show) },
                        onRequestHome = {
                            try {
                                homeRequest.launch(SystemUi.homeRequestIntent(context))
                            } catch (e: ActivityNotFoundException) {
                                // Tanpa layar peran/Home di ROM ini: tidak ada yang bisa dilakukan.
                            }
                        },
                        onRequestStorage = { SystemUi.openSafely(context, Storage.allFilesAccessIntent(context)) },
                        contentPadding = padding,
                    )
                }
            }
        }

        val sheetGame = gameSheetId?.let { id -> state.games.firstOrNull { it.id == id } }
        if (sheetGame != null) {
            GameSheet(
                game = sheetGame,
                platform = catalog[sheetGame.platformId],
                config = state.platforms.firstOrNull { it.platformId == sheetGame.platformId },
                onDismiss = { gameSheetId = null },
                onPlay = {
                    vm.play(context, sheetGame)
                    gameSheetId = null
                },
                onToggleFavorite = { vm.toggleFavorite(sheetGame.id) },
                onSelectPlayer = { key -> vm.setPlayer(sheetGame.platformId, key) },
            )
        }

        if (showAddPlatform) {
            AddPlatformSheet(
                catalog = catalog,
                added = state.platforms.map { it.platformId }.toSet(),
                onPick = { def ->
                    vm.addPlatform(def.uniqueId)
                    showAddPlatform = false
                    platformSheetId = def.uniqueId
                },
                onDismiss = { showAddPlatform = false },
            )
        }

        val sheetPlatformId = platformSheetId
        if (sheetPlatformId != null) {
            val def = catalog[sheetPlatformId]
            val config = state.platforms.firstOrNull { it.platformId == sheetPlatformId }
            if (def != null && config != null) {
                PlatformSettingsSheet(
                    platform = def,
                    config = config,
                    gameCount = state.games.count { it.platformId == sheetPlatformId },
                    scanning = sheetPlatformId in scanning,
                    onPickFolder = {
                        folderTarget = sheetPlatformId
                        try {
                            folderPicker.launch(null)
                        } catch (e: ActivityNotFoundException) {
                            folderTarget = null
                            vm.notifyFolderUnsupported()
                        }
                    },
                    onApplyFolder = { folder -> vm.setFolder(sheetPlatformId, folder) },
                    onSelectPlayer = { key -> vm.setPlayer(sheetPlatformId, key) },
                    onRemove = {
                        vm.removePlatform(sheetPlatformId)
                        if (libraryPlatform == sheetPlatformId) libraryPlatform = null
                        platformSheetId = null
                    },
                    onDismiss = { platformSheetId = null },
                )
            }
        }
    }
}
