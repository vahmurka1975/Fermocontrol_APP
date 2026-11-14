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
    id = "honey", title = "Терракота",
    background = Color(0xFF241E18), sheet = Color(0xFF2A231C),
    card = Color(0xFF2F2720), cardEnd = Color(0xFF2A231C),
    cardLine = Color(0xFF3A3027), divider = Color(0xFF3A3027),
    chip = Color(0xFF3A3027), chipLine = Color(0xFF4A3D31), chipIcon = Color(0xFFE08A5F),
    field = Color(0xFF1C1712), fieldLine = Color(0xFF4A3D31),
    selected = Color(0xFF3A2A20), selectedLine = Color(0xFFC0562B),
    ink = Color(0xFFF0E8DC), strong = Color(0xFFF0E8DC), soft = Color(0xFFCBBFAE),
    label = Color(0xFFA99B88), action = Color(0xFFE08A5F),
    body = Color(0xFFCBBFAE), muted = Color(0xFFA99B88), kicker = Color(0xFFA99B88),
    accent = Color(0xFFC0562B), accentLine = Color(0xFFE08A5F), onAccent = Color(0xFFFFFFFF),
    secondary = Color(0xFF3A3027), secondaryLine = Color(0xFF4A3D31), onSecondary = Color(0xFFF0E8DC),
    danger = Color(0xFFF09A94),
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