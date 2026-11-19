@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.wakeupver.pijar.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wakeupver.pijar.R
import com.wakeupver.pijar.core.FileHandling
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.GameTitles
import com.wakeupver.pijar.core.PlatformCatalog
import com.wakeupver.pijar.core.PlatformConfig
import com.wakeupver.pijar.core.PlatformDef
import com.wakeupver.pijar.core.PlayerDef
import com.wakeupver.pijar.core.PlayerPicker
import com.wakeupver.pijar.data.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun SheetList(
    spacing: Dp = 12.dp,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Detail game: sampul, favorit, tombol Main, statistik, dan pilihan pemutar yang cocok untuk berkas ini. */
@Composable
fun GameSheet(
    game: GameRecord,
    platform: PlatformDef?,
    config: PlatformConfig?,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectPlayer: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fileName = remember(game.path) { File(game.path).name }
    val players = remember(platform, fileName) {
        if (platform == null) emptyList<PlayerDef>() else PlayerPicker.compatible(platform, fileName)
    }
    val activeKey = remember(platform, config, fileName) {
        if (platform == null) "" else PlayerPicker.choose(platform, config?.playerKey.orEmpty(), fileName)?.key.orEmpty()
    }
    val tags = remember(fileName) { GameTitles.tags(fileName).take(3) }
    val installedPackages by produceState(initialValue = emptySet<String>(), key1 = players) {
        value = withContext(Dispatchers.IO) {
            players.mapNotNull { it.packageName }.distinct().filterTo(HashSet()) { Apps.isInstalled(context, it) }
        }
    }

    val lastPlayed = if (game.lastPlayedAt > 0L) {
        stringResource(
            R.string.game_last_played,
            DateUtils.getRelativeTimeSpanString(game.lastPlayedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
        )
    } else {
        stringResource(R.string.game_never_played)
    }
    val playCount = if (game.playCount > 0) stringResource(R.string.game_play_count, game.playCount) else ""
    val fileSize = if (game.sizeBytes > 0L) Formatter.formatShortFileSize(context, game.sizeBytes) else ""
    val stats = listOf(lastPlayed, playCount, fileSize).filter { it.isNotEmpty() }.joinToString(" · ")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
    ) {
        SheetList(spacing = 16.dp) {
            item(key = "header", contentType = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameArt(game = game, platform = platform, modifier = Modifier.size(104.dp), badgeSize = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = platform?.name.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        if (tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                tags.forEach { tag ->
                                    Surface(shape = CircleShape, color = colors.secondaryContainer) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(
                                if (game.favorite) R.string.game_favorite_remove else R.string.game_favorite_add,
                            ),
                            tint = if (game.favorite) colors.tertiary else colors.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "play", contentType = "action") {
                ExpressiveButton(
                    text = stringResource(R.string.action_play),
                    onClick = onPlay,
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                    pill = true,
                )
            }
            if (stats.isNotEmpty()) {
                item(key = "stats", contentType = "text") {
                    Text(text = stats, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            if (players.isNotEmpty()) {
                item(key = "players_header", contentType = "header") {
                    Text(text = stringResource(R.string.game_players), style = MaterialTheme.typography.titleMedium)
                }
                items(players, key = { it.key }, contentType = { "player" }) { player ->
                    val packageName = player.packageName
                    val installed = packageName != null && packageName in installedPackages
                    ChoiceRow(
                        title = player.name,
                        subtitle = packageName,
                        selected = player.key == activeKey,
                        trailing = stringResource(
                            if (installed) R.string.game_player_installed else R.string.game_player_missing,
                        ),
                        trailingColor = if (installed) colors.primary else colors.error,
                        onClick = { onSelectPlayer(player.key) },
                    )
                }
            }
        }
    }
}

/** Daftar platform bawaan (data Daijishō) yang belum ditambahkan, dengan pencarian. */
@Composable
fun AddPlatformSheet(
    catalog: PlatformCatalog,
    added: Set<String>,
    onPick: (PlatformDef) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val results = remember(catalog, added, query) {
        val q = query.trim()
        catalog.all.filter { def ->
            def.uniqueId !in added &&
                (q.isEmpty() || def.name.contains(q, ignoreCase = true) || def.shortName.contains(q, ignoreCase = true))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.sheet_add_platform_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.sheet_add_platform_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (results.isEmpty()) {
                Text(
                    text = stringResource(R.string.sheet_add_platform_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(results, key = { it.uniqueId }, contentType = { "platform" }) { def ->
                        ListItem(
                            headlineContent = { Text(def.name) },
                            supportingContent = {
                                Text(stringResource(R.string.sheet_add_platform_players, def.players.size))
                            },
                            leadingContent = {
                                PlatformBadge(shortName = def.shortName, key = def.uniqueId, size = 44.dp)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onPick(def) },
                        )
                    }
                }
            }
        }
    }
}

/** Setup singkat setelah platform ditambahkan: folder ROM dan aplikasi pemutar yang terpasang. */
@Composable
fun SetupPlatformSheet(
    platform: PlatformDef,
    config: PlatformConfig,
    onPickFolder: () -> Unit,
    onSelectPlayer: (String) -> Unit,
    onFileHandling: (FileHandling) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canFinish = config.folder.isNotBlank() && config.playerKey.isNotBlank()
    val installed by produceState<Set<String>?>(initialValue = null, key1 = platform) {
        value = withContext(Dispatchers.IO) {
            platform.players.mapNotNull { it.packageName }.distinct().filterTo(HashSet()) { Apps.isInstalled(context, it) }
        }
    }
    val installedPlayers = remember(platform, installed) {
        val packages = installed ?: emptySet()
        platform.players.filter { player -> player.packageName?.let { it in packages } == true }
    }
    var showAdvanced by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
    ) {
        SheetList {
            item(key = "title", contentType = "header") {
                Text(
                    text = stringResource(R.string.sheet_setup_platform_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            item(key = "platform_name", contentType = "text") {
                Text(
                    text = platform.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "folder_header", contentType = "header") {
                Text(
                    text = stringResource(R.string.sheet_platform_folder),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item(key = "folder_value", contentType = "text") {
                Text(
                    text = if (config.folder.isBlank()) stringResource(R.string.settings_no_folder) else config.folder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item(key = "folder_button", contentType = "action") {
                ExpressiveButton(
                    text = stringResource(R.string.action_choose_folder),
                    onClick = onPickFolder,
                    tonal = true,
                )
            }
            item(key = "player_header", contentType = "header") {
                Text(
                    text = stringResource(R.string.sheet_platform_player),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when {
                installed == null -> item(key = "players_loading", contentType = "loading") {
                    LoadingIndicator(modifier = Modifier.size(32.dp))
                }
                installedPlayers.isEmpty() -> item(key = "players_empty", contentType = "text") {
                    Text(
                        text = stringResource(R.string.sheet_choose_player_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    item(key = "players_count", contentType = "text") {
                        Text(
                            text = stringResource(R.string.sheet_choose_player_installed, installedPlayers.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(installedPlayers, key = { it.key }, contentType = { "player" }) { player ->
                        ChoiceRow(
                            title = player.name,
                            subtitle = player.packageName,
                            selected = config.playerKey == player.key,
                            trailing = null,
                            trailingColor = Color.Unspecified,
                            onClick = { onSelectPlayer(player.key) },
                        )
                    }
                }
            }
            item(key = "advanced_toggle", contentType = "action") {
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(stringResource(R.string.sheet_platform_file_handling))
                }
            }
            if (showAdvanced) {
                item(key = "advanced_options", contentType = "options") {
                    FileHandlingOptions(selected = config.fileHandling, onSelect = onFileHandling)
                }
            }
            if (!canFinish) {
                item(key = "setup_hint", contentType = "text") {
                    Text(
                        text = stringResource(R.string.sheet_setup_incomplete_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "footer", contentType = "action") {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = onDone, enabled = canFinish) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

/** Folder ROM, pemutar bawaan, penanganan berkas lanjutan, pindai ulang, dan hapus platform. */
@Composable
fun PlatformSettingsSheet(
    platform: PlatformDef,
    config: PlatformConfig,
    gameCount: Int,
    scanning: Boolean,
    onPickFolder: () -> Unit,
    onApplyFolder: (String) -> Unit,
    onSelectPlayer: (String) -> Unit,
    onFileHandling: (FileHandling) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var folderText by remember(config.folder) { mutableStateOf(config.folder) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
    ) {
        SheetList(spacing = 16.dp) {
            item(key = "header", contentType = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformBadge(shortName = platform.shortName, key = platform.uniqueId, size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = platform.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = stringResource(R.string.sheet_platform_games_found, gameCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (scanning) {
                        LoadingIndicator(modifier = Modifier.size(40.dp))
                    }
                }
            }
            item(key = "folder_header", contentType = "header") {
                Text(text = stringResource(R.string.sheet_platform_folder), style = MaterialTheme.typography.titleMedium)
            }
            item(key = "folder_field", contentType = "input") {
                OutlinedTextField(
                    value = folderText,
                    onValueChange = { folderText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.sheet_platform_folder_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "folder_actions", contentType = "action") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExpressiveButton(
                        text = stringResource(R.string.action_choose_folder),
                        onClick = onPickFolder,
                        tonal = true,
                    )
                    ExpressiveButton(
                        text = stringResource(R.string.action_scan),
                        onClick = { onApplyFolder(folderText) },
                        enabled = folderText.isNotBlank() && !scanning,
                    )
                }
            }
            item(key = "player_header", contentType = "header") {
                Text(text = stringResource(R.string.sheet_platform_player), style = MaterialTheme.typography.titleMedium)
            }
            item(key = "player_auto", contentType = "player") {
                ChoiceRow(
                    title = stringResource(R.string.sheet_platform_player_auto),
                    subtitle = null,
                    selected = config.playerKey.isEmpty(),
                    trailing = null,
                    trailingColor = Color.Unspecified,
                    onClick = { onSelectPlayer("") },
                )
            }
            items(platform.players, key = { it.key }, contentType = { "player" }) { player ->
                ChoiceRow(
                    title = player.name,
                    subtitle = player.packageName,
                    selected = config.playerKey == player.key,
                    trailing = null,
                    trailingColor = Color.Unspecified,
                    onClick = { onSelectPlayer(player.key) },
                )
            }
            item(key = "file_handling_header", contentType = "header") {
                Text(text = stringResource(R.string.sheet_platform_file_handling), style = MaterialTheme.typography.titleMedium)
            }
            item(key = "file_handling_options", contentType = "options") {
                FileHandlingOptions(selected = config.fileHandling, onSelect = onFileHandling)
            }
            item(key = "remove", contentType = "action") {
                TextButton(
                    onClick = {
                        if (confirmRemove) onRemove() else confirmRemove = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(
                        stringResource(
                            if (confirmRemove) R.string.action_remove_confirm else R.string.action_remove_platform,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FileHandlingOptions(
    selected: FileHandling,
    onSelect: (FileHandling) -> Unit,
) {
    FileHandling.entries.forEach { mode ->
        val title = when (mode) {
            FileHandling.DEFAULT -> R.string.file_handling_default
            FileHandling.STANDARD -> R.string.file_handling_standard
            FileHandling.LEGACY -> R.string.file_handling_legacy
        }
        val description = when (mode) {
            FileHandling.DEFAULT -> R.string.file_handling_default_desc
            FileHandling.STANDARD -> R.string.file_handling_standard_desc
            FileHandling.LEGACY -> R.string.file_handling_legacy_desc
        }
        ChoiceRow(
            title = stringResource(title),
            subtitle = stringResource(description),
            selected = selected == mode,
            trailing = null,
            trailingColor = Color.Unspecified,
            onClick = { onSelect(mode) },
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    trailing: String?,
    trailingColor: Color,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Text(text = trailing, style = MaterialTheme.typography.labelMedium, color = trailingColor)
            }
        }
    }
}
