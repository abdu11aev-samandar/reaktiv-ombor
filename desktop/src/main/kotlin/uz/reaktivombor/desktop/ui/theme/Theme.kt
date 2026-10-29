package uz.reaktivombor.desktop.ui.theme

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dastur ranglari (DESIGN.md §6). Sahifalar rangni faqat shu yerdan oladi. */
object AppColors {
    val Navy = Color(0xFF0B2545)
    val NavyHover = Color(0xFF173761)
    val NavyLine = Color(0xFF2A4670)
    val NavyText = Color(0xFFC9D4E5)
    val NavyMuted = Color(0xFF8C9DB8)

    val Primary = Color(0xFF1E5AA8)
    val PrimaryDark = Color(0xFF174A8C)
    val PrimarySoft = Color(0xFFE3ECF8)

    val Background = Color(0xFFF2F4F7)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF7F8FA)
    val GroupHeader = Color(0xFFE9EEF5)

    val Text = Color(0xFF1B2430)
    val Muted = Color(0xFF5B6575)
    val Disabled = Color(0xFFA3ABB7)

    val Border = Color(0xFFD7DDE5)
    val Divider = Color(0xFFE6E9EE)
    val FieldBorder = Color(0xFFC2CAD5)

    val Success = Color(0xFF1E7B34)
    val Warning = Color(0xFF8A6100)
    val Error = Color(0xFFB42318)

    /**
     * Jadval qatori: sichqoncha ustida va tanlangan — holat rangi ustidan juda yengil (holat rangi o‘zgarmasin);
     * tanlangan qator qo‘shimcha ko‘k ramka va chapdagi chiziq bilan ajraladi.
     */
    val RowHover = Color(0x0D0B2545)
    val RowSelected = Color(0x141E5AA8)
}

/** Bo‘shliqlar — hamma joyda bir xil qadam. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

/**
 * Yaroqlilik holatining ko‘rinishi: qator/chip foni, matn rangi, grafik rangi va matni.
 * Holat doim rang VA matn bilan ko‘rsatiladi.
 */
@Immutable
data class StatusStyle(
    val key: String,
    val label: String,
    val background: Color,
    val foreground: Color,
    /** Grafik, nuqta va chiziqlar uchun to‘yinganroq rang. */
    val accent: Color,
    val bold: Boolean = false,
)

object StatusStyles {
    val GREEN = StatusStyle("GREEN", "Yaroqli", Color(0xFFE6F4EA), Color(0xFF1E7B34), Color(0xFF2E9B4C))
    val YELLOW = StatusStyle("YELLOW", "1–3 oy qoldi", Color(0xFFFFF4CC), Color(0xFF8A6100), Color(0xFFE0A800))
    val RED = StatusStyle("RED", "1 oy yoki kam qoldi", Color(0xFFFDE2E1), Color(0xFFB42318), Color(0xFFD92D20))
    val EXPIRED = StatusStyle("EXPIRED", "Muddati o‘tgan", Color(0xFFF4D3D3), Color(0xFF7A1212), Color(0xFF7A1212), bold = true)
    val UNKNOWN = StatusStyle("UNKNOWN", "Muddati kiritilmagan", Color(0xFFECEEF2), Color(0xFF5B6575), Color(0xFF98A2B3))

    /** Jiddiylik tartibida: muddati o‘tgan → noma’lum. */
    val all: List<StatusStyle> = listOf(EXPIRED, RED, YELLOW, GREEN, UNKNOWN)
}

/**
 * Holat nomidan ko‘rinish: core/Expiry.kt dagi ExpiryState nomlari (GREEN, YELLOW, RED, EXPIRED, UNKNOWN).
 * Ulash: `statusStyle(state.name)`. Noma’lum nom — kulrang (UNKNOWN).
 */
fun statusStyle(name: String?): StatusStyle =
    StatusStyles.all.firstOrNull { it.key.equals(name?.trim(), ignoreCase = true) } ?: StatusStyles.UNKNOWN

/** Xabar, banner va bildirishnomalar darajasi. */
enum class Severity { INFO, SUCCESS, WARNING, ERROR }

@Immutable
data class SeverityStyle(val background: Color, val foreground: Color, val accent: Color)

val Severity.style: SeverityStyle
    get() = when (this) {
        Severity.INFO -> SeverityStyle(Color(0xFFE8F0FB), Color(0xFF15427D), AppColors.Primary)
        Severity.SUCCESS -> SeverityStyle(Color(0xFFE6F4EA), Color(0xFF1E6B31), Color(0xFF2E9B4C))
        Severity.WARNING -> SeverityStyle(Color(0xFFFFF4CC), Color(0xFF6E4D00), Color(0xFFE0A800))
        Severity.ERROR -> SeverityStyle(Color(0xFFFDE2E1), Color(0xFF912018), Color(0xFFD92D20))
    }

private val AppColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimarySoft,
    onPrimaryContainer = AppColors.Navy,
    secondary = AppColors.Navy,
    onSecondary = Color.White,
    secondaryContainer = AppColors.PrimarySoft,
    onSecondaryContainer = AppColors.Navy,
    tertiary = AppColors.Primary,
    background = AppColors.Background,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.SurfaceAlt,
    onSurfaceVariant = AppColors.Muted,
    // Material3 menyu va oynalarni “surfaceContainer” bilan bo‘yaydi (standarti — binafsharang tus).
    surfaceTint = AppColors.Surface,
    surfaceContainerLowest = AppColors.Surface,
    surfaceContainerLow = AppColors.Surface,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.Surface,
    surfaceContainerHighest = AppColors.SurfaceAlt,
    surfaceBright = AppColors.Surface,
    surfaceDim = AppColors.Background,
    outline = AppColors.FieldBorder,
    outlineVariant = AppColors.Divider,
    error = AppColors.Error,
    onError = Color.White,
    errorContainer = StatusStyles.RED.background,
    onErrorContainer = AppColors.Error,
)

/** Matn o‘lchamlari: oddiy matn 13–14, sahifa sarlavhasi 20–22. */
val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

/** Butun dastur shu mavzu ichida chiziladi (ranglar, shriftlar, aylantirish chiziqlari). */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, typography = AppTypography, shapes = AppShapes) {
        CompositionLocalProvider(
            LocalContentColor provides AppColors.Text,
            LocalTextSelectionColors provides TextSelectionColors(AppColors.Primary, AppColors.Primary.copy(alpha = 0.25f)),
            LocalScrollbarStyle provides defaultScrollbarStyle().copy(
                thickness = 8.dp,
                shape = RoundedCornerShape(4.dp),
                unhoverColor = Color(0x40000000),
                hoverColor = Color(0x80000000),
            ),
            content = content,
        )
    }
}
