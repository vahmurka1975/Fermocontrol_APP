package ru.fermocontrol.ui.theme

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
    background = Color(0xFF1A271D), sheet = Color(0xFF213126),
    card = Color(0xFF2B3F2F), cardEnd = Color(0xFF253628),
    cardLine = Color(0xFF4A6644), divider = Color(0xFF465F3F),
    chip = Color(0xFF3A5338), chipLine = Color(0xFF5E7C52), chipIcon = Color(0xFFD2E8C2),
    field = Color(0xFF1F2E23), fieldLine = Color(0xFF6A8A5A),
    selected = Color(0xFF33492F), selectedLine = Color(0xFF9BBF86),
    ink = Color(0xFFF2F8EC), strong = Color(0xFFE6F0DC), soft = Color(0xFFC8D8BC),
    label = Color(0xFFDCEACF), action = Color(0xFFD2E4C3),
    body = Color(0xFFBCD0AE), muted = Color(0xFFA5BC97), kicker = Color(0xFFA9C09B),
    accent = Color(0xFFC6E8B0), accentLine = Color(0xFFD6F0C4), onAccent = Color(0xFF13230F),
    secondary = Color(0xFF36502F), secondaryLine = Color(0xFF62825A), onSecondary = Color(0xFFE3F2D6),
    danger = Color(0xFFE8A097),
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
    background = Color(0xFF241C13), sheet = Color(0xFF2E241A),
    card = Color(0xFF3B2E20), cardEnd = Color(0xFF33281B),
    cardLine = Color(0xFF62503A), divider = Color(0xFF5A4934),
    chip = Color(0xFF4D3C28), chipLine = Color(0xFF7A6244), chipIcon = Color(0xFFF0D8A8),
    field = Color(0xFF2A2118), fieldLine = Color(0xFF86704A),
    selected = Color(0xFF4A3A25), selectedLine = Color(0xFFC09A62),
    ink = Color(0xFFFFF6E6), strong = Color(0xFFF5E9D3), soft = Color(0xFFDCCDB2),
    label = Color(0xFFEADBBF), action = Color(0xFFE2D2B4),
    body = Color(0xFFCDBDA2), muted = Color(0xFFB5A58A), kicker = Color(0xFFBBAB90),
    accent = Color(0xFFF2C879), accentLine = Color(0xFFFFDDA0), onAccent = Color(0xFF2A1A06),
    secondary = Color(0xFF5E4A2C), secondaryLine = Color(0xFF8A7048), onSecondary = Color(0xFFFFEFD0),
    danger = Color(0xFFE9A092),
)

val AppPalettes = listOf(GraphitePalette, ForestPalette, HoneyPalette)

// Принимает null, чтобы работать с любым типом сохранённого id.
fun paletteById(id: String): AppPalette =
    AppPalettes.firstOrNull { it.id == id } ?: GraphitePalette

val LocalAppPalette = staticCompositionLocalOf { GraphitePalette }

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
fun FermocontrolTheme(palette: AppPalette = GraphitePalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppPalette provides palette) {
        // Если в вашей теме есть typography или shapes, верните их сюда параметрами.
        MaterialTheme(colorScheme = palette.toColorScheme(), content = content)
    }
}