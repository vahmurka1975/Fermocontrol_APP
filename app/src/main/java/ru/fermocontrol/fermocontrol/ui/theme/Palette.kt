package ru.fermocontrol.fermocontrol.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppPalette(
    val id: String, val title: String,
    val background: Color, val sheet: Color,
    val card: Color, val cardEnd: Color, val cardLine: Color, val divider: Color,
    val chip: Color, val chipLine: Color, val chipIcon: Color,
    val field: Color, val fieldLine: Color,
    val selected: Color, val selectedLine: Color,
    val ink: Color, val strong: Color, val soft: Color, val label: Color, val action: Color,
    val body: Color, val muted: Color, val kicker: Color,
    val accent: Color, val accentLine: Color, val onAccent: Color,
    val secondary: Color, val secondaryLine: Color, val onSecondary: Color,
    val danger: Color,
)

// Значения «Леса» взяты из CSS окна настроек. Фон экрана (background) подставьте свой.
val ForestPalette = AppPalette(
    id = "forest", title = "Лес",
    background = Color(0xFF111A14), sheet = Color(0xFF162019),
    card = Color(0xFF1E2B20), cardEnd = Color(0xFF19241C),
    cardLine = Color(0xFF354631), divider = Color(0xFF34452E),
    chip = Color(0xFF2A3A27), chipLine = Color(0xFF43553A), chipIcon = Color(0xFFBDD4AD),
    field = Color(0xFF17221A), fieldLine = Color(0xFF465D3A),
    selected = Color(0xFF243320), selectedLine = Color(0xFF6F8D5F),
    ink = Color(0xFFE0E9D8), strong = Color(0xFFD3DFC9), soft = Color(0xFFB0BFA5),
    label = Color(0xFFC7D7BB), action = Color(0xFFBDCCAF),
    body = Color(0xFF9FB293), muted = Color(0xFF8D9F83), kicker = Color(0xFF91A386),
    accent = Color(0xFFB7D5A8), accentLine = Color(0xFFC4DFB6), onAccent = Color(0xFF1A2A16),
    secondary = Color(0xFF283B26), secondaryLine = Color(0xFF4C6441), onSecondary = Color(0xFFD1E2C4),
    danger = Color(0xFFC58A82),
)

val GraphitePalette = AppPalette(
    id = "graphite", title = "Графит",
    background = Color(0xFF0F1114), sheet = Color(0xFF16191D),
    card = Color(0xFF1F242A), cardEnd = Color(0xFF1A1E23),
    cardLine = Color(0xFF353C45), divider = Color(0xFF2F363E),
    chip = Color(0xFF2A313A), chipLine = Color(0xFF414A56), chipIcon = Color(0xFFB9C7D8),
    field = Color(0xFF171B20), fieldLine = Color(0xFF48525F),
    selected = Color(0xFF232C36), selectedLine = Color(0xFF6C8299),
    ink = Color(0xFFE3E8EE), strong = Color(0xFFD2DAE3), soft = Color(0xFFB2BDC9),
    label = Color(0xFFC9D3DE), action = Color(0xFFBAC6D3),
    body = Color(0xFF9BA8B6), muted = Color(0xFF8A97A5), kicker = Color(0xFF8E9BAA),
    accent = Color(0xFFA9C4E0), accentLine = Color(0xFFBDD3EA), onAccent = Color(0xFF121C28),
    secondary = Color(0xFF2A3644), secondaryLine = Color(0xFF4A5C70), onSecondary = Color(0xFFD0DDEB),
    danger = Color(0xFFC9877F),
)

val HoneyPalette = AppPalette(
    id = "honey", title = "Мёд",
    background = Color(0xFF18130E), sheet = Color(0xFF211A13),
    card = Color(0xFF2D241A), cardEnd = Color(0xFF261E15),
    cardLine = Color(0xFF4A3D2D), divider = Color(0xFF43372A),
    chip = Color(0xFF3A2E21), chipLine = Color(0xFF5C4B36), chipIcon = Color(0xFFE0C79C),
    field = Color(0xFF1E1812), fieldLine = Color(0xFF65523A),
    selected = Color(0xFF35291B), selectedLine = Color(0xFF9C7F52),
    ink = Color(0xFFEFE6D8), strong = Color(0xFFE2D6C3), soft = Color(0xFFC4B7A2),
    label = Color(0xFFD6C9B3), action = Color(0xFFCDBFA6),
    body = Color(0xFFB0A28B), muted = Color(0xFF9C8E78), kicker = Color(0xFFA39580),
    accent = Color(0xFFE0BE86), accentLine = Color(0xFFEBCFA0), onAccent = Color(0xFF2A1E0E),
    secondary = Color(0xFF4A3A25), secondaryLine = Color(0xFF6E5A3C), onSecondary = Color(0xFFEBDDC4),
    danger = Color(0xFFD08F82),
)

val AppPalettes = listOf(ForestPalette, GraphitePalette, HoneyPalette)

// Принимает null, чтобы работать с любым типом сохранённого id.
fun paletteById(id: String?): AppPalette =
    AppPalettes.firstOrNull { it.id == id } ?: ForestPalette

val LocalAppPalette = staticCompositionLocalOf { ForestPalette }

private fun AppPalette.toColorScheme(): ColorScheme = darkColorScheme(
    primary = accent, onPrimary = onAccent,
    primaryContainer = secondary, onPrimaryContainer = onSecondary,
    secondary = secondary, onSecondary = onSecondary,
    secondaryContainer = secondary, onSecondaryContainer = onSecondary,
    background = background, onBackground = ink,
    surface = background, onSurface = ink,
    surfaceVariant = card, onSurfaceVariant = muted,
    surfaceContainerLowest = background,
    surfaceContainerLow = sheet,
    surfaceContainer = card,
    surfaceContainerHigh = card,
    surfaceContainerHighest = card,
    outline = cardLine, outlineVariant = divider,
    error = danger,
)

@Composable
fun FermocontrolTheme(palette: AppPalette = ForestPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppPalette provides palette) {
        // Если в вашей теме есть typography или shapes, верните их сюда параметрами.
        MaterialTheme(colorScheme = palette.toColorScheme(), content = content)
    }
}