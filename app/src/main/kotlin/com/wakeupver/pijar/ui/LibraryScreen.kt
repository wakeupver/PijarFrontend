@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.wakeupver.pijar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wakeupver.pijar.R
import com.wakeupver.pijar.core.AppState
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.PlatformCatalog

@Composable
fun LibraryScreen(
    state: AppState,
    catalog: PlatformCatalog,
    scanning: Set<String>,
    selectedPlatform: String?,
    onSelectPlatform: (String?) -> Unit,
    onOpenGame: (GameRecord) -> Unit,
    onLongOpenPlatform: (String) -> Unit,
    onLongOpenGame: (GameRecord) -> Unit,
    onAddPlatform: () -> Unit,
    onPlatformSettings: (String) -> Unit,
    onRescan: (String?) -> Unit,
    contentPadding: PaddingValues,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = LocalHapticFeedback.current

    val readyConfigs = remember(state.platforms) { state.platforms.filter { it.setupDone } }
    val readyIds = remember(readyConfigs) { readyConfigs.mapTo(HashSet()) { it.platformId } }
    val platforms = remember(readyConfigs, catalog) { readyConfigs.mapNotNull { catalog[it.platformId] } }
    val selectedConfig = readyConfigs.firstOrNull { it.platformId == selectedPlatform }
    val visibleGames = remember(state.games, readyIds) { state.games.filter { it.platformId in readyIds } }
    // Urutkan sekali per perubahan pustaka/mode, bukan setiap ketikan pencarian.
    val sortedGames = remember(visibleGames, sort) {
        when (sort) {
            1 -> visibleGames.sortedByDescending { it.addedAt }
            2 -> visibleGames.sortedByDescending { it.lastPlayedAt }
            else -> visibleGames.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }
    val games = remember(sortedGames, selectedPlatform, onlyFavorites, query) {
        val q = query.trim()
        sortedGames.filter { game ->
            (selectedPlatform == null || game.platformId == selectedPlatform) &&
                (!onlyFavorites || game.favorite) &&
                (q.isEmpty() || game.title.contains(q, ignoreCase = true))
        }
    }
    val sortLabel = stringResource(
        when (sort) {
            1 -> R.string.library_sort_added
            2 -> R.string.library_sort_played
            else -> R.string.library_sort_title
        },
    )

    Column(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        PijarTopBar(
            title = stringResource(R.string.library_title),
            scrollBehavior = scrollBehavior,
            actions = {
                if (scanning.isNotEmpty()) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { onRescan(selectedPlatform) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.cd_rescan))
                }
                if (selectedPlatform != null) {
                    IconButton(onClick = { onPlatformSettings(selectedPlatform) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.library_platform_settings))
                    }
                }
            },
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedPlatform == null && !onlyFavorites,
                    onClick = {
                        onSelectPlatform(null)
                        onlyFavorites = false
                    },
                    label = { Text(stringResource(R.string.library_filter_all)) },
                )
            }
            item {
                FilterChip(
                    selected = onlyFavorites,
                    onClick = { onlyFavorites = !onlyFavorites },
                    label = { Text(stringResource(R.string.library_filter_favorites)) },
                    leadingIcon = { Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            items(platforms, key = { it.uniqueId }, contentType = { "platform" }) { def ->
                val chipLabel = remember(def.shortName) { def.shortName.uppercase() }
                Box(
                    modifier = Modifier.pointerInput(def.uniqueId) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongOpenPlatform(def.uniqueId)
                            },
                        )
                    },
                ) {
                    FilterChip(
                        selected = selectedPlatform == def.uniqueId,
                        onClick = { onSelectPlatform(def.uniqueId) },
                        label = { Text(chipLabel) },
                    )
                }
            }
            item {
                AssistChip(
                    onClick = { sort = (sort + 1) % 3 },
                    label = { Text(sortLabel) },
                )
            }
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear))
                    }
                }
            },
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

        if (games.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    platforms.isEmpty() -> EmptyState(
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_no_platform),
                        actionLabel = stringResource(R.string.action_add_platform),
                        onAction = onAddPlatform,
                    )
                    selectedPlatform != null && selectedConfig != null && selectedConfig.folder.isBlank() -> EmptyState(
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_no_folder),
                        actionLabel = stringResource(R.string.action_choose_folder),
                        onAction = { onPlatformSettings(selectedPlatform) },
                    )
                    else -> EmptyState(
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_no_games),
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(games, key = { it.id }, contentType = { "game" }) { game ->
                    GameCard(
                        game = game,
                        platform = catalog[game.platformId],
                        haptics = haptics,
                        onClick = { onOpenGame(game) },
                        onLongClick = { onLongOpenGame(game) },
                    )
                }
            }
        }
    }
}
