@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.wakeupver.pijar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalMultiBrowseCarousel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberCarouselState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wakeupver.pijar.R
import com.wakeupver.pijar.core.AppState
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.PlatformCatalog
import com.wakeupver.pijar.core.PlatformDef
import com.wakeupver.pijar.data.ArtFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

@Composable
fun HomeScreen(
    state: AppState,
    catalog: PlatformCatalog,
    storageGranted: Boolean,
    onOpenPlatform: (String) -> Unit,
    onOpenGame: (GameRecord) -> Unit,
    onPlay: (GameRecord) -> Unit,
    onAddPlatform: () -> Unit,
    onGrantStorage: () -> Unit,
    contentPadding: PaddingValues,
) {
    val platforms = remember(state.platforms, catalog) { state.platforms.mapNotNull { catalog[it.platformId] } }
    val counts = remember(state.games) { state.games.groupingBy { it.platformId }.eachCount() }
    val recent = remember(state.games) {
        state.games.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }.take(12)
    }
    val favorites = remember(state.games) {
        state.games.filter { it.favorite }.sortedBy { it.title.lowercase() }.take(20)
    }

    // Jangan taruh kartu yang bisa digeser di bawah zona gestur "kembali" sistem di tepi layar.
    val layoutDirection = LocalLayoutDirection.current
    val gestureInsets = WindowInsets.systemGestures.asPaddingValues()
    val edge = maxOf(20.dp, gestureInsets.calculateStartPadding(layoutDirection))
    val hero = recent.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { HomeHeader(platformCount = platforms.size, gameCount = state.games.size) }

        if (!storageGranted) {
            item { StorageBanner(onGrant = onGrantStorage) }
        }

        if (platforms.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    actionLabel = stringResource(R.string.action_add_platform),
                    onAction = onAddPlatform,
                )
            }
        } else {
            if (hero != null) {
                item {
                    HeroCard(
                        game = hero,
                        platform = catalog[hero.platformId],
                        onOpen = { onOpenGame(hero) },
                        onPlay = { onPlay(hero) },
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        text = stringResource(R.string.home_platforms),
                        trailing = {
                            TextButton(onClick = onAddPlatform) {
                                Text(stringResource(R.string.action_add_platform))
                            }
                        },
                    )
                    key(platforms.size) {
                        HorizontalMultiBrowseCarousel(
                            state = rememberCarouselState { platforms.size },
                            preferredItemWidth = 232.dp,
                            modifier = Modifier.fillMaxWidth().height(208.dp),
                            itemSpacing = 10.dp,
                            contentPadding = PaddingValues(horizontal = edge),
                        ) { index ->
                            val def = platforms[index]
                            PlatformCard(
                                def = def,
                                count = counts[def.uniqueId] ?: 0,
                                onClick = { onOpenPlatform(def.uniqueId) },
                                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge),
                            )
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item {
                    GameShelf(
                        title = stringResource(R.string.home_recent),
                        games = recent,
                        catalog = catalog,
                        edge = edge,
                        onOpen = onOpenGame,
                    )
                }
            }
            if (favorites.isNotEmpty()) {
                item {
                    GameShelf(
                        title = stringResource(R.string.home_favorites),
                        games = favorites,
                        catalog = catalog,
                        edge = edge,
                        onOpen = onOpenGame,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(platformCount: Int, gameCount: Int) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when (hour) {
            in 5..10 -> R.string.greeting_morning
            in 11..14 -> R.string.greeting_afternoon
            in 15..17 -> R.string.greeting_evening
            else -> R.string.greeting_night
        },
    )
    Column(modifier = Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
        Text(text = greeting, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
        Text(
            text = stringResource(R.string.home_summary, platformCount, gameCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageBanner(onGrant: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.tertiaryContainer,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.storage_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.storage_banner_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onTertiaryContainer.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.width(12.dp))
            ExpressiveButton(text = stringResource(R.string.action_allow), onClick = onGrant)
        }
    }
}

@Composable
private fun HeroCard(game: GameRecord, platform: PlatformDef?, onOpen: () -> Unit, onPlay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.primaryContainer,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GameArt(game = game, platform = platform, modifier = Modifier.size(116.dp), badgeSize = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_continue),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = platform?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                ExpressiveButton(
                    text = stringResource(R.string.action_play),
                    onClick = onPlay,
                    icon = Icons.Rounded.PlayArrow,
                )
            }
        }
    }
}

@Composable
private fun PlatformCard(def: PlatformDef, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = badgeColors(def.uniqueId)
    val decoShape = PlatformShapes.forKey(def.uniqueId + "#deco").toShape()
    Box(modifier = modifier.fillMaxSize().background(colors.first).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 36.dp, y = (-36).dp)
                .size(148.dp)
                .clip(decoShape)
                .background(colors.second.copy(alpha = 0.12f)),
        )
        Text(
            text = def.shortName.uppercase().take(6),
            color = colors.second,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(
                text = def.name,
                color = colors.second,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.home_game_count, count),
                color = colors.second.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun GameShelf(
    title: String,
    games: List<GameRecord>,
    catalog: PlatformCatalog,
    edge: Dp,
    onOpen: (GameRecord) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(text = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = edge),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(games, key = { it.id }) { game ->
                GameCard(
                    game = game,
                    platform = catalog[game.platformId],
                    onClick = { onOpen(game) },
                    onLongClick = { onOpen(game) },
                    modifier = Modifier.width(132.dp),
                )
            }
        }
    }
}
