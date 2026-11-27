package com.example.multivo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Multivo palette: deep plum night, violet light, orchid-pink highlights.
val Plum950 = Color(0xFF0E0918)
val Plum900 = Color(0xFF150E24)
val Plum850 = Color(0xFF1C1430)
val Plum800 = Color(0xFF241A3D)
val Plum700 = Color(0xFF2F234F)
val Plum600 = Color(0xFF3B2C66)
val Violet300 = Color(0xFFC4B2FF)
val Violet400 = Color(0xFFA68BFF)
val Violet500 = Color(0xFF8B6CFF)
val Orchid400 = Color(0xFFD16BFF)
val Pink400 = Color(0xFFFF7EB6)
val Lavender = Color(0xFFEEE8FF)
val LavenderMuted = Color(0xFFA99DCB)
val Online = Color(0xFF4ADE9B)

private val colors = darkColorScheme(
    primary = Violet400,
    onPrimary = Color(0xFF1A0F33),
    primaryContainer = Plum600,
    onPrimaryContainer = Color(0xFFE6DCFF),
    secondary = Pink400,
    onSecondary = Color(0xFF2E0A1E),
    secondaryContainer = Color(0xFF45204A),
    onSecondaryContainer = Color(0xFFFFD6EC),
    tertiary = Orchid400,
    onTertiary = Color(0xFF2A0838),
    background = Plum950,
    onBackground = Lavender,
    surface = Plum900,
    onSurface = Lavender,
    surfaceVariant = Plum800,
    onSurfaceVariant = LavenderMuted,
    surfaceContainerLowest = Plum950,
    surfaceContainerLow = Plum900,
    surfaceContainer = Plum850,
    surfaceContainerHigh = Plum800,
    surfaceContainerHighest = Plum700,
    outline = Color(0xFF5E4F86),
    outlineVariant = Color(0xFF30264C),
    error = Color(0xFFFF8F9C),
    onError = Color(0xFF3B0710),
    inverseSurface = Lavender,
    inverseOnSurface = Plum900,
    inversePrimary = Violet500,
    scrim = Color(0xCC07040D)
)

/** Gradients shared by every screen. */
object MultivoBrush {
    /** Main highlight: buttons, my message bubbles, selected states. */
    val accent: Brush = Brush.linearGradient(listOf(Violet500, Orchid400, Pink400))

    /** Softer highlight for borders and rings. */
    val accentSoft: Brush = Brush.linearGradient(listOf(Violet400.copy(alpha = 0.7f), Pink400.copy(alpha = 0.5f)))

    /** Screen backdrop: plum glow at the top fading into night. */
    val screen: Brush = Brush.verticalGradient(
        0f to Color(0xFF221641),
        0.45f to Plum900,
        1f to Plum950
    )

    /** Card fill with a faint violet sheen. */
    val card: Brush = Brush.verticalGradient(listOf(Plum800.copy(alpha = 0.92f), Plum850.copy(alpha = 0.92f)))
}

/** Slow, gentle motion used everywhere in the app. */
object Motion {
    /** Standard duration for entrances and state changes. */
    const val SLOW = 700
    /** Longer duration for large surfaces (screen and tab changes). */
    const val SLOWER = 950
    /** Delay between items that enter one after another. */
    const val STAGGER = 80
    /** Max items that wait for their turn; later ones enter together. */
    const val MAX_STAGGERED = 8

    /** Long ease-out: moves start softly and settle without a bounce. */
    val Ease: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun <T> slow(delayMillis: Int = 0): FiniteAnimationSpec<T> = tween(SLOW, delayMillis, Ease)
    fun <T> slower(delayMillis: Int = 0): FiniteAnimationSpec<T> = tween(SLOWER, delayMillis, Ease)
}

private val display = FontFamily.Serif
private val body = FontFamily.SansSerif

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp)
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun MultivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
        // Light text by default on the dark screens, even outside a Surface.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
