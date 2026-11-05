package ru.fermocontrol.fermocontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FermoColorScheme = darkColorScheme(
    primary = Emerald,
    onPrimary = Bg,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = Emerald,

    secondary = Amber,
    onSecondary = Bg,
    secondaryContainer = AmberSoft,
    onSecondaryContainer = Amber,

    background = Bg,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = BgElev,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = CardHover,

    error = Danger,
    onError = Bg,
)

@Composable
fun FermocontrolTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FermoColorScheme,
        content = content
    )
}