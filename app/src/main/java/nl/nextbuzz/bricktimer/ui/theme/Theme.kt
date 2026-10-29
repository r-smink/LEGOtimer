package nl.nextbuzz.bricktimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import nl.nextbuzz.bricktimer.R
import nl.nextbuzz.bricktimer.data.ThemeMode
import kotlin.math.abs

/** Fyndable brand tokens (tokens/colors.css). */
object Brand {
    val Purple = Color(0xFF8F39AC)
    val Blue = Color(0xFF379FD3)
    val Success = Color(0xFF10B981)
    val SuccessLight = Color(0xFFD1FAE5)
    val SuccessDark = Color(0xFF047857)
    val SuccessBorder = Color(0xFFA7F3D0)
    val Gray300 = Color(0xFFD1D5DB)
    val Gray400 = Color(0xFF9CA3AF)
    val Gold = Color(0xFFF59E0B)
    val Danger = Color(0xFFEF4444)
    val DangerDark = Color(0xFFB91C1C)

    /** --fyn-gradient: 135°, purple -> blue */
    val gradient = Brush.linearGradient(listOf(Purple, Blue))
    val stopGradient = Brush.linearGradient(listOf(Danger, DangerDark))

    private val setGradients = listOf(
        0xFF334155 to 0xFF0F172A, 0xFF475569 to 0xFF1E293B, 0xFF7C2D12 to 0xFF431407,
        0xFF1E3A8A to 0xFF0C2461, 0xFFBE123C to 0xFF7F1D1D, 0xFFBE185D to 0xFF86198F,
        0xFF1D4ED8 to 0xFF1E3A8A, 0xFF0E7490 to 0xFF164E63, 0xFFCA8A04 to 0xFF854D0E,
        0xFF0D9488 to 0xFF134E4A,
    )

    /** Stable per-theme gradient for set tiles and headers. */
    fun setGradient(themeId: Int): Brush =
        setGradients[abs(themeId) % setGradients.size].let { (a, b) -> Brush.linearGradient(listOf(Color(a), Color(b))) }
}

/** The design's --app-* tokens, light and dark. */
@Immutable
data class AppColors(
    val bg: Color,
    val card: Color,
    val text: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val border: Color,
    val sunken: Color,
    val track: Color,
    val tint: Color,
    val accent: Color,
    val isDark: Boolean,
)

val LightAppColors = AppColors(
    bg = Color(0xFFF4F5F7), card = Color.White, text = Color(0xFF111827), textMuted = Color(0xFF6B7280),
    textSubtle = Color(0xFF9CA3AF), border = Color(0xFFE5E7EB), sunken = Color(0xFFF9FAFB), track = Color(0xFFE5E7EB),
    tint = Color(0xFFF4E9F8), accent = Brand.Purple, isDark = false,
)

val DarkAppColors = AppColors(
    bg = Color(0xFF140D24), card = Color(0xFF211A38), text = Color(0xFFF3F0FA), textMuted = Color(0xFFA79CC0),
    textSubtle = Color(0xFF7D7196), border = Color.White.copy(alpha = .09f), sunken = Color.White.copy(alpha = .05f),
    track = Color.White.copy(alpha = .12f), tint = Brand.Purple.copy(alpha = .30f), accent = Color(0xFFC08ADF), isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: Int) =
    Font(R.font.outfit, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Outfit = FontFamily(outfit(300), outfit(400), outfit(500), outfit(600), outfit(700))

private val baseType = Typography()
private val AppTypography = Typography(
    displayLarge = baseType.displayLarge.copy(fontFamily = Outfit),
    displayMedium = baseType.displayMedium.copy(fontFamily = Outfit),
    displaySmall = baseType.displaySmall.copy(fontFamily = Outfit),
    headlineLarge = baseType.headlineLarge.copy(fontFamily = Outfit),
    headlineMedium = baseType.headlineMedium.copy(fontFamily = Outfit),
    headlineSmall = baseType.headlineSmall.copy(fontFamily = Outfit),
    titleLarge = baseType.titleLarge.copy(fontFamily = Outfit),
    titleMedium = baseType.titleMedium.copy(fontFamily = Outfit),
    titleSmall = baseType.titleSmall.copy(fontFamily = Outfit),
    bodyLarge = baseType.bodyLarge.copy(fontFamily = Outfit),
    bodyMedium = baseType.bodyMedium.copy(fontFamily = Outfit),
    bodySmall = baseType.bodySmall.copy(fontFamily = Outfit),
    labelLarge = baseType.labelLarge.copy(fontFamily = Outfit),
    labelMedium = baseType.labelMedium.copy(fontFamily = Outfit),
    labelSmall = baseType.labelSmall.copy(fontFamily = Outfit),
)

@Composable
fun isDarkTheme(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun BrickTimerTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkAppColors else LightAppColors
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, background = c.bg, surface = c.card, onSurface = c.text, onBackground = c.text, surfaceVariant = c.sunken, outline = c.border)
    } else {
        lightColorScheme(primary = c.accent, background = c.bg, surface = c.card, onSurface = c.text, onBackground = c.text, surfaceVariant = c.sunken, outline = c.border)
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
