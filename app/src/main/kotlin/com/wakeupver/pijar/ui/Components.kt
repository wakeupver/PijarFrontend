@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.wakeupver.pijar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import coil.compose.AsyncImage
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.PlatformDef
import com.wakeupver.pijar.data.ArtFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Spesifikasi gerak mengikuti skema "expressive" Material 3 (pegas memantul untuk spasial, kaku untuk efek). */
object Motion {
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
}

/** Ikon sederhana buatan sendiri (kotak-kotak), supaya tidak perlu paket material-icons-extended. */
object PijarIcons {
    private fun ImageVector.Builder.square(x: Float, y: Float, side: Float) {
        path(fill = SolidColor(Color.Black)) {
            moveTo(x, y)
            horizontalLineToRelative(side)
            verticalLineToRelative(side)
            horizontalLineToRelative(-side)
            close()
        }
    }

    /** 2x2 kotak: pustaka. */
    val Library: ImageVector by lazy {
        ImageVector.Builder("PijarLibrary", 24.dp, 24.dp, 24f, 24f).apply {
            square(3f, 3f, 8f)
            square(13f, 3f, 8f)
            square(3f, 13f, 8f)
            square(13f, 13f, 8f)
        }.build()
    }

    /** 3x3 kotak: semua aplikasi. */
    val Apps: ImageVector by lazy {
        ImageVector.Builder("PijarApps", 24.dp, 24.dp, 24f, 24f).apply {
            for (row in 0..2) {
                for (col in 0..2) square(3.5f + col * 6f, 3.5f + row * 6f, 4f)
            }
        }.build()
    }
}

/** Bentuk "expressive" (cookie, clover, dst.) yang dipakai bergantian untuk lencana platform. */
object PlatformShapes {
    private val polygons: List<RoundedPolygon> = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.SoftBurst,
        MaterialShapes.Flower,
        MaterialShapes.Puffy,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Cookie12Sided,
    )

    fun forKey(key: String): RoundedPolygon = polygons[(key.hashCode() and Int.MAX_VALUE) % polygons.size]
}

/** Pasangan (latar, isi) yang stabil per platform, diambil dari skema warna aktif. */
@Composable
fun badgeColors(key: String): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val options = listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.primary to c.onPrimary,
        c.tertiary to c.onTertiary,
        c.secondary to c.onSecondary,
    )
    return options[(key.hashCode() and Int.MAX_VALUE) % options.size]
}

@Composable
fun PlatformBadge(shortName: String, key: String, size: Dp, modifier: Modifier = Modifier) {
    val shape = PlatformShapes.forKey(key).toShape()
    val colors = badgeColors(key)
    Box(
        modifier = modifier.size(size).clip(shape).background(colors.first),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shortName.uppercase().take(4),
            color = colors.second,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.26f).sp,
            maxLines = 1,
        )
    }
}

/** Gambar sampul lokal bila ada; jika tidak, lencana platform. */
@Composable
fun GameArt(game: GameRecord, platform: PlatformDef?, modifier: Modifier = Modifier, badgeSize: Dp = 64.dp) {
    val art by produceState<File?>(null, game.path) {
        value = withContext(Dispatchers.IO) { ArtFinder.find(game.path) }
    }
    val colors = badgeColors(game.platformId)
    Box(
        modifier = modifier.clip(MaterialTheme.shapes.large).background(colors.first.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        val file = art
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlatformBadge(shortName = platform?.shortName ?: "?", key = game.platformId, size = badgeSize)
        }
    }
}

@Composable
fun GameCard(
    game: GameRecord,
    platform: PlatformDef?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
        ),
    ) {
        Box {
            GameArt(game = game, platform = platform, modifier = Modifier.fillMaxWidth().aspectRatio(0.8f))
            if (game.favorite) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                )
            }
        }
        Text(
            text = game.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

/** Tombol "expressive": pil penuh yang bentuknya memantul menjadi kotak bulat saat ditekan. */
@Composable
fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tonal: Boolean = false,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val radius by animateDpAsState(
        targetValue = if (pressed) 14.dp else 28.dp,
        animationSpec = Motion.fastSpatial(),
        label = "buttonRadius",
    )
    val shape = RoundedCornerShape(radius)
    val buttonModifier = modifier.heightIn(min = 56.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, fontWeight = FontWeight.Bold)
    }
    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            interactionSource = source,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            interactionSource = source,
            content = content,
        )
    }
}

/**
 * Top app bar dengan latar statis. Warna container tidak lagi dianimasikan antara transparan dan
 * `surfaceContainer` saat discroll, sehingga bilah tidak "berkedip" ketika status gulir berubah.
 */
@Composable
fun PijarTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(title) },
        modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = MaterialShapes.Cookie12Sided.toShape()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(112.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PijarIcons.Library,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            ExpressiveButton(text = actionLabel, onClick = onAction)
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector)

/** Bilah navigasi melayang berbentuk pil; item terpilih melebar dan menampilkan label. */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavPill(item = item, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
private fun NavPill(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Satu animasi untuk lebar, latar, dan warna ikon. Interpolasi antara dua warna opak
    // (bukan Color.Transparent) menghindari kedip gelap saat pil terpilih dianimasikan.
    val expansion by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.defaultSpatial(),
        label = "navPillExpansion",
    )
    val container = lerp(colors.surfaceContainerHigh, colors.primaryContainer, expansion)
    val tint = lerp(colors.onSurfaceVariant, colors.onPrimaryContainer, expansion)
    Row(
        modifier = Modifier
            .height(56.dp)
            .width((56 + 76 * expansion).dp)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(role = Role.Tab, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(24.dp))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(animationSpec = Motion.defaultSpatial()) + fadeIn(animationSpec = Motion.fastEffects()),
            exit = shrinkHorizontally(animationSpec = Motion.defaultSpatial()) + fadeOut(animationSpec = Motion.fastEffects()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.label,
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
