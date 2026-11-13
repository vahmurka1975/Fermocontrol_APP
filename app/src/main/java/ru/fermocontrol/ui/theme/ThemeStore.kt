package ru.fermocontrol.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ThemeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("theme", Context.MODE_PRIVATE)

    var palette by mutableStateOf(
        paletteById(prefs.getString("palette", "").orEmpty())
    )
        private set

    fun select(p: AppPalette) {
        palette = p
        prefs.edit().putString("palette", p.id).apply()
    }
}

@Composable
fun FermoTheme(palette: AppPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                background = palette.background,
                surface = palette.sheet,
                onSurface = palette.ink,
            ),
            content = content,
        )
    }
}