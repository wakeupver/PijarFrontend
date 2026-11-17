@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.wakeupver.pijar.ui

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wakeupver.pijar.R
import com.wakeupver.pijar.core.AppState
import com.wakeupver.pijar.core.PlatformCatalog
import com.wakeupver.pijar.ui.theme.hslColor

private val ACCENT_HUES = listOf(265, 215, 175, 140, 48, 18, 340, 300)

@Composable
fun SettingsScreen(
    state: AppState,
    catalog: PlatformCatalog,
    isDefaultHome: Boolean,
    storageGranted: Boolean,
    onOpenPlatform: (String) -> Unit,
    onAddPlatform: () -> Unit,
    onRescanAll: () -> Unit,
    onImport: () -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAccent: (Int) -> Unit,
    onWallpaper: (Boolean) -> Unit,
    onRequestHome: () -> Unit,
    onRequestStorage: () -> Unit,
    contentPadding: PaddingValues,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val counts = remember(state.games) { state.games.groupingBy { it.platformId }.eachCount() }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicActive = state.prefs.dynamicColor && dynamicSupported
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    Column(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            scrollBehavior = scrollBehavior,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        ) {
            item { SettingsSection(stringResource(R.string.settings_section_platforms)) }
            items(state.platforms.size) { index ->
                val config = state.platforms[index]
                val def = catalog[config.platformId]
                if (def != null) {
                    ListItem(
                        headlineContent = { Text(def.name) },
                        supportingContent = {
                            Text(
                                if (config.folder.isBlank()) {
                                    stringResource(R.string.settings_no_folder)
                                } else {
                                    config.folder
                                },
                                maxLines = 1,
                            )
                        },
                        leadingContent = { PlatformBadge(shortName = def.shortName, key = def.uniqueId, size = 44.dp) },
                        trailingContent = {
                            Text(
                                text = stringResource(R.string.home_game_count, counts[def.uniqueId] ?: 0),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = transparent,
                        modifier = Modifier.clickable { onOpenPlatform(def.uniqueId) },
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_add_platform)) },
                    leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    colors = transparent,
                    modifier = Modifier.clickable(onClick = onAddPlatform),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_rescan_all)) },
                    leadingContent = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                    colors = transparent,
                    modifier = Modifier.clickable(onClick = onRescanAll),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_import)) },
                    supportingContent = { Text(stringResource(R.string.settings_import_desc)) },
                    leadingContent = { Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null) },
                    colors = transparent,
                    modifier = Modifier.clickable(onClick = onImport),
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_appearance)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_desc)) },
                    trailingContent = {
                        Switch(
                            checked = dynamicActive,
                            onCheckedChange = onDynamicColor,
                            enabled = dynamicSupported,
                        )
                    },
                    colors = transparent,
                )
            }
            if (!dynamicActive) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_accent),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 0.dp, vertical = 4.dp),
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ACCENT_HUES.forEach { hue ->
                                val swatch = hslColor(hue.toFloat(), 0.62f, 0.52f)
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .clickable { onAccent(hue) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.prefs.accentHue == hue) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_wallpaper)) },
                    supportingContent = { Text(stringResource(R.string.settings_wallpaper_desc)) },
                    trailingContent = {
                        Switch(checked = state.prefs.showWallpaper, onCheckedChange = onWallpaper)
                    },
                    colors = transparent,
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_system)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_default_home)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (isDefaultHome) R.string.settings_default_home_on else R.string.settings_default_home_off,
                            ),
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = if (isDefaultHome) Icons.Rounded.CheckCircle else Icons.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    colors = transparent,
                    modifier = Modifier.clickable(enabled = !isDefaultHome, onClick = onRequestHome),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_storage)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (storageGranted) R.string.settings_storage_on else R.string.settings_storage_off,
                            ),
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = if (storageGranted) Icons.Rounded.CheckCircle else Icons.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    colors = transparent,
                    modifier = Modifier.clickable(enabled = !storageGranted, onClick = onRequestStorage),
                )
            }
            item {
                InfoCard(
                    title = stringResource(R.string.settings_gestures_title),
                    body = stringResource(R.string.settings_gestures_body),
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_about)) }
            item {
                InfoCard(
                    title = stringResource(R.string.settings_about_title),
                    body = stringResource(R.string.settings_about_body),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
