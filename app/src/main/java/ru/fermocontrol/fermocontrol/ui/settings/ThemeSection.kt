package ru.fermocontrol.fermocontrol.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fermocontrol.fermocontrol.ui.theme.AppPalette
import ru.fermocontrol.fermocontrol.ui.theme.LocalAppPalette
import ru.fermocontrol.fermocontrol.ui.theme.AppPalettes

@Composable
fun ThemeSection(
    selected: AppPalette,
    onSelect: (AppPalette) -> Unit,
) {
    val p = LocalAppPalette.current
    val cardShape = RoundedCornerShape(19.dp)
    Column(
        Modifier.fillMaxWidth().clip(cardShape).background(p.card)
            .border(1.dp, p.cardLine, cardShape).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("ОФОРМЛЕНИЕ", color = p.muted, fontSize = 9.sp)
            Text("Цветовые темы", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        AppPalettes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { option ->
                    PaletteOption(option, option.id == selected.id, Modifier.weight(1f)) {
                        onSelect(option)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PaletteOption(
    option: AppPalette,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier
            .clip(shape)
            .background(option.sheet)
            .border(
                BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) option.accent else option.cardLine),
                shape,
            )
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(option.accent, option.secondaryLine, option.card).forEach {
                Box(Modifier.size(16.dp).clip(CircleShape).background(it)
                    .border(1.dp, option.cardLine, CircleShape))
            }
        }
        Text(option.title, color = option.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}