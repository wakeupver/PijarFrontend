package com.wakeupver.pijar.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.wakeupver.pijar.core.UiPrefs

private val SPEC_2025 = ColorSpec.SpecVersion.SPEC_2025
private const val TONAL_SPOT_CHROMA = 36.0
private const val SOURCE_TONE = 50.0
private const val DEFAULT_CONTRAST = 0.0

/**
 * Tema Material 3 Expressive dengan skema Material You **Tonal Spot** sesuai spesifikasi warna
 * 2025. Mode dinamis mengambil hue palet aksen sistem (Android 12+), sedangkan mode aksen memakai
 * hue pilihan pengguna sebagai sumber palette.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PijarTheme(prefs: UiPrefs, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val useDynamic = prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicHue = if (useDynamic) systemAccentHue(context) else null
    val scheme = remember(prefs.accentHue, dynamicHue, dark) {
        tonalSpotScheme(dynamicHue ?: prefs.accentHue.toDouble(), dark)
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = PijarShapes,
        typography = PijarTypography,
        content = content,
    )
}

/** Skema Tonal Spot (HCT) 2025 dari satu hue sumber, dipetakan ke seluruh peran [ColorScheme]. */
fun tonalSpotScheme(hue: Double, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(
        sourceColorHct = Hct.from(hue, TONAL_SPOT_CHROMA, SOURCE_TONE),
        isDark = dark,
        contrastLevel = DEFAULT_CONTRAST,
        specVersion = SPEC_2025,
    )
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        inversePrimary = Color(scheme.inversePrimary),
        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        tertiary = Color(scheme.tertiary),
        onTertiary = Color(scheme.onTertiary),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        background = Color(scheme.background),
        onBackground = Color(scheme.onBackground),
        surface = Color(scheme.surface),
        onSurface = Color(scheme.onSurface),
        surfaceVariant = Color(scheme.surfaceVariant),
        onSurfaceVariant = Color(scheme.onSurfaceVariant),
        surfaceTint = Color(scheme.surfaceTint),
        inverseSurface = Color(scheme.inverseSurface),
        inverseOnSurface = Color(scheme.inverseOnSurface),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        onErrorContainer = Color(scheme.onErrorContainer),
        outline = Color(scheme.outline),
        outlineVariant = Color(scheme.outlineVariant),
        scrim = Color(scheme.scrim),
        surfaceBright = Color(scheme.surfaceBright),
        surfaceDim = Color(scheme.surfaceDim),
        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        surfaceContainerLow = Color(scheme.surfaceContainerLow),
        surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
        primaryFixed = Color(scheme.primaryFixed),
        primaryFixedDim = Color(scheme.primaryFixedDim),
        onPrimaryFixed = Color(scheme.onPrimaryFixed),
        onPrimaryFixedVariant = Color(scheme.onPrimaryFixedVariant),
        secondaryFixed = Color(scheme.secondaryFixed),
        secondaryFixedDim = Color(scheme.secondaryFixedDim),
        onSecondaryFixed = Color(scheme.onSecondaryFixed),
        onSecondaryFixedVariant = Color(scheme.onSecondaryFixedVariant),
        tertiaryFixed = Color(scheme.tertiaryFixed),
        tertiaryFixedDim = Color(scheme.tertiaryFixedDim),
        onTertiaryFixed = Color(scheme.onTertiaryFixed),
        onTertiaryFixedVariant = Color(scheme.onTertiaryFixedVariant),
    )
}

/** Warna primer dan on-primary Tonal Spot untuk pratinjau pilihan aksen di Pengaturan. */
fun tonalSpotAccent(hue: Int): Pair<Color, Color> {
    val scheme = tonalSpotScheme(hue.toDouble(), dark = false)
    return scheme.primary to scheme.onPrimary
}

/** Hue palet aksen utama sistem (Material You), bila tersedia. */
private fun systemAccentHue(context: Context): Double? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Hct.fromInt(context.getColor(android.R.color.system_accent1_500)).hue
    } else {
        null
    }
}.getOrNull()

val PijarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(44.dp),
)

val PijarTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Black),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.25).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}
