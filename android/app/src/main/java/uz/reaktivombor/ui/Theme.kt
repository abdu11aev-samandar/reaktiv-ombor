package uz.reaktivombor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import uz.reaktivombor.domain.ExpiryStatus

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE4FF),
    onPrimaryContainer = Color(0xFF0B1F66),
    secondaryContainer = Color(0xFFE3E8F2),
    onSecondaryContainer = Color(0xFF1F2937),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF1F2937),
    surface = Color(0xFFF4F6F9),
    onSurface = Color(0xFF1F2937),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF8FAFC),
    surfaceContainerHighest = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF5B6472),
    outline = Color(0xFFCBD2DB),
    outlineVariant = Color(0xFFE2E6EC),
    error = Color(0xFFB91C1C),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93B4FF),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBE4FF),
    secondaryContainer = Color(0xFF26303A),
    onSecondaryContainer = Color(0xFFE6EAEF),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE6EAEF),
    surface = Color(0xFF0F1419),
    onSurface = Color(0xFFE6EAEF),
    surfaceContainerLowest = Color(0xFF0B1015),
    surfaceContainerLow = Color(0xFF161D24),
    surfaceContainer = Color(0xFF161D24),
    surfaceContainerHigh = Color(0xFF1B232B),
    surfaceContainerHighest = Color(0xFF26303A),
    onSurfaceVariant = Color(0xFF9AA5B1),
    outline = Color(0xFF3B4754),
    outlineVariant = Color(0xFF2A343E),
    error = Color(0xFFF87171),
    onError = Color(0xFF2D0A0A),
)

/** Muddat ranglari: nom fonida (ikkala mavzuda bir xil) va “N kun qoldi” matnida. */
@Immutable
data class StatusPalette(
    val pill: Map<ExpiryStatus, Pair<Color, Color>>,
    val hint: Map<ExpiryStatus, Color>,
)

private val pills = mapOf(
    ExpiryStatus.GREEN to (Color(0xFF15803D) to Color.White),
    ExpiryStatus.YELLOW to (Color(0xFFFACC15) to Color(0xFF422006)),
    ExpiryStatus.RED to (Color(0xFFDC2626) to Color.White),
)

private val LightStatus = StatusPalette(
    pill = pills,
    hint = mapOf(
        ExpiryStatus.GREEN to Color(0xFF15803D),
        ExpiryStatus.YELLOW to Color(0xFF8A5A00),
        ExpiryStatus.RED to Color(0xFFB91C1C),
    ),
)

private val DarkStatus = StatusPalette(
    pill = pills,
    hint = mapOf(
        ExpiryStatus.GREEN to Color(0xFF4ADE80),
        ExpiryStatus.YELLOW to Color(0xFFFACC15),
        ExpiryStatus.RED to Color(0xFFF87171),
    ),
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatus }

@Composable
fun ReaktivTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusPalette provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
