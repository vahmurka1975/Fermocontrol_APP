package ru.fermocontrol.fermocontrol.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.fermocontrol.fermocontrol.ui.theme.*

/** Нижняя панель макета (.modal + .sheet). На ширине от 640 dp центрируется. */
@Composable
fun FcSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalAppPalette.current
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(FcScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (dismissible) onDismiss() },
        ) {
            val wide = maxWidth >= 640.dp
            val shape = if (wide) RoundedCornerShape(23.dp)
            else RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp)

            Column(
                modifier = Modifier
                    .align(if (wide) Alignment.Center else Alignment.BottomCenter)
                    .widthIn(max = if (wide) 620.dp else 700.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f)
                    .clip(shape)
                    .background(p.sheet)
                    .border(1.dp, p.cardLine, shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = 26.dp),
            ) {
                if (!wide) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 0.dp, bottom = 19.dp)
                            .size(width = 33.dp, height = 4.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(p.chipLine),
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = title,
                        color = p.ink,
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.6).sp,
                        modifier = Modifier.weight(1f).padding(top = 4.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, p.divider, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, enabled = dismissible, onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = p.ink, modifier = Modifier.size(20.dp))
                    }
                }
                if (subtitle != null) {
                    Text(subtitle, color = p.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

@Composable
fun FcTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    error: String? = null,
) {
    val p = LocalAppPalette.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.padding(bottom = 12.dp)) {
        Text(label, color = p.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = TextStyle(color = p.ink, fontSize = 14.sp),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 46.dp else 78.dp)
                .clip(shape)
                .background(p.field)
                .border(1.dp, if (error != null) p.danger else if (focused) p.selectedLine else p.fieldLine, shape)
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 12.dp, vertical = 13.dp),
            decorationBox = { inner ->
                Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                    if (value.isEmpty()) Text(placeholder, color = p.muted, fontSize = 14.sp)
                    inner()
                }
            },
        )
        if (error != null) Text(error, color = p.danger, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
fun FcSelect(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalAppPalette.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.padding(bottom = 12.dp)) {
        Text(label, color = p.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clip(shape)
                    .background(p.field)
                    .border(1.dp, p.fieldLine, shape)
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, color = p.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = p.muted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.background(p.field),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = if (option == value) p.accent else p.ink) },
                        onClick = { open = false; onSelect(option) },
                    )
                }
            }
        }
    }
}

@Composable
fun FcPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 47.dp)
            .clip(shape)
            .background(if (enabled) p.secondary else p.secondary.copy(alpha = 0.5f))
            .border(1.dp, p.secondaryLine.copy(alpha = if (enabled) 1f else 0.5f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = p.onSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}