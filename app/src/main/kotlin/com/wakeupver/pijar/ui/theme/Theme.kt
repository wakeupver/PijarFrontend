package com.wakeupver.pijar.ui.theme

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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeupver.pijar.core.UiPrefs
import kotlin.math.abs

/**
 * Tema Material 3 Expressive: skema warna dinamis (Android 12+) atau skema aksen buatan sendiri,
 * bentuk lebih bulat, tipografi lebih tebal, dan MotionScheme.expressive() (pegas memantul).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PijarTheme(prefs: UiPrefs, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val useDynamic = prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = remember(useDynamic, dark, prefs.accentHue) {
        if (useDynamic) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            accentColorScheme(prefs.accentHue, dark)
        }
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = PijarShapes,
        typography = PijarTypography,
        content = content,
    )
}

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

/** HSL (hue 0..360, saturasi/kecerahan 0..1) ke [Color] sRGB; ditulis sendiri agar tidak bergantung pada API pustaka. */
fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val r1: Float
    val g1: Float
    val b1: Float
    when {
        hp < 1f -> { r1 = c; g1 = x; b1 = 0f }
        hp < 2f -> { r1 = x; g1 = c; b1 = 0f }
        hp < 3f -> { r1 = 0f; g1 = c; b1 = x }
        hp < 4f -> { r1 = 0f; g1 = x; b1 = c }
        hp < 5f -> { r1 = x; g1 = 0f; b1 = c }
        else -> { r1 = c; g1 = 0f; b1 = x }
    }
    val m = l - c / 2f
    return Color(r1 + m, g1 + m, b1 + m)
}

private fun tone(hue: Float, saturation: Float, lightness: Float): Color = hslColor(hue, saturation, lightness)

/** Skema warna dari satu hue (0..359): primer = hue, sekunder = analog, tersier = triad. */
fun accentColorScheme(hue: Int, dark: Boolean): ColorScheme {
    val h = hue.toFloat()
    val h2 = h + 28f
    val h3 = h + 150f
    return if (dark) {
        darkColorScheme(
            primary = tone(h, 0.85f, 0.80f),
            onPrimary = tone(h, 0.70f, 0.17f),
            primaryContainer = tone(h, 0.55f, 0.28f),
            onPrimaryContainer = tone(h, 0.90f, 0.90f),
            inversePrimary = tone(h, 0.55f, 0.40f),
            secondary = tone(h2, 0.45f, 0.78f),
            onSecondary = tone(h2, 0.50f, 0.16f),
            secondaryContainer = tone(h2, 0.30f, 0.26f),
            onSecondaryContainer = tone(h2, 0.60f, 0.90f),
            tertiary = tone(h3, 0.50f, 0.78f),
            onTertiary = tone(h3, 0.55f, 0.16f),
            tertiaryContainer = tone(h3, 0.35f, 0.26f),
            onTertiaryContainer = tone(h3, 0.60f, 0.90f),
            background = tone(h, 0.16f, 0.08f),
            onBackground = tone(h, 0.20f, 0.90f),
            surface = tone(h, 0.16f, 0.08f),
            onSurface = tone(h, 0.20f, 0.90f),
            surfaceVariant = tone(h, 0.12f, 0.22f),
            onSurfaceVariant = tone(h, 0.14f, 0.76f),
            surfaceTint = tone(h, 0.85f, 0.80f),
            inverseSurface = tone(h, 0.20f, 0.90f),
            inverseOnSurface = tone(h, 0.16f, 0.16f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = tone(h, 0.08f, 0.58f),
            outlineVariant = tone(h, 0.10f, 0.30f),
            scrim = Color.Black,
            surfaceBright = tone(h, 0.12f, 0.24f),
            surfaceDim = tone(h, 0.16f, 0.06f),
            surfaceContainerLowest = tone(h, 0.18f, 0.05f),
            surfaceContainerLow = tone(h, 0.16f, 0.10f),
            surfaceContainer = tone(h, 0.15f, 0.12f),
            surfaceContainerHigh = tone(h, 0.14f, 0.16f),
            surfaceContainerHighest = tone(h, 0.13f, 0.20f),
        )
    } else {
        lightColorScheme(
            primary = tone(h, 0.55f, 0.38f),
            onPrimary = Color.White,
            primaryContainer = tone(h, 0.85f, 0.90f),
            onPrimaryContainer = tone(h, 0.65f, 0.12f),
            inversePrimary = tone(h, 0.90f, 0.78f),
            secondary = tone(h2, 0.30f, 0.38f),
            onSecondary = Color.White,
            secondaryContainer = tone(h2, 0.45f, 0.90f),
            onSecondaryContainer = tone(h2, 0.50f, 0.12f),
            tertiary = tone(h3, 0.35f, 0.36f),
            onTertiary = Color.White,
            tertiaryContainer = tone(h3, 0.50f, 0.90f),
            onTertiaryContainer = tone(h3, 0.55f, 0.12f),
            background = tone(h, 0.20f, 0.98f),
            onBackground = tone(h, 0.15f, 0.10f),
            surface = tone(h, 0.20f, 0.98f),
            onSurface = tone(h, 0.15f, 0.10f),
            surfaceVariant = tone(h, 0.20f, 0.90f),
            onSurfaceVariant = tone(h, 0.10f, 0.30f),
            surfaceTint = tone(h, 0.55f, 0.38f),
            inverseSurface = tone(h, 0.12f, 0.18f),
            inverseOnSurface = tone(h, 0.15f, 0.94f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            outline = tone(h, 0.08f, 0.46f),
            outlineVariant = tone(h, 0.12f, 0.80f),
            scrim = Color.Black,
            surfaceBright = tone(h, 0.20f, 0.98f),
            surfaceDim = tone(h, 0.15f, 0.86f),
            surfaceContainerLowest = tone(h, 0.20f, 1.00f),
            surfaceContainerLow = tone(h, 0.20f, 0.96f),
            surfaceContainer = tone(h, 0.18f, 0.94f),
            surfaceContainerHigh = tone(h, 0.17f, 0.92f),
            surfaceContainerHighest = tone(h, 0.16f, 0.90f),
        )
    }
}
