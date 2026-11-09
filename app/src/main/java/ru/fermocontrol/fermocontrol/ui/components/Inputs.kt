package ru.fermocontrol.fermocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.fermocontrol.fermocontrol.ui.theme.Amber
import ru.fermocontrol.fermocontrol.ui.theme.LocalAppPalette
import ru.fermocontrol.fermocontrol.ui.theme.WarnCheckBg
import ru.fermocontrol.fermocontrol.ui.theme.WarnCheckInk
import ru.fermocontrol.fermocontrol.ui.theme.WarnCheckLine

/** Поле макета: подпись сверху, фон и рамка из палитры, скругление 12. */
@Composable
fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minHeight: Dp = 46.dp,
    error: String? = null,
    hint: String? = null,
    autoFocus: Boolean = false,
) {
    val p = LocalAppPalette.current
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(200) // лист должен успеть отрисоваться, иначе requestFocus падает
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.padding(bottom = 12.dp)) {
        Text(
            label,
            color = p.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp),
        )
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
                .clip(shape)
                .background(p.field)
                .border(
                    1.dp,
                    if (error != null) p.danger else if (focused) p.selectedLine else p.fieldLine,
                    shape,
                )
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 12.dp, vertical = 13.dp),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = minHeight - 26.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = p.muted, fontSize = 14.sp)
                    }
                    inner()
                }
            },
        )
        if (error != null) {
            Text(error, color = p.danger, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
        } else if (hint != null) {
            Text(hint, color = p.body, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Два поля в ряд; на узком экране (до 390 dp) переходят в одну колонку. */
@Composable
fun FieldPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth > 390.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        } else {
            Column {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}

/** Раскрываемый раздел (details.additional-fields). */
@Composable
fun CollapsibleSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalAppPalette.current
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 3.dp, bottom = 19.dp)
            .clip(shape)
            .border(1.dp, p.divider, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = p.action, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = p.muted,
                modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
            )
        }
        if (open) {
            Column(Modifier.padding(start = 13.dp, end = 13.dp, top = 2.dp), content = content)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    val p = LocalAppPalette.current
    Text(
        text.uppercase(),
        color = p.kicker,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

/** Отметка «Требует проверки» (label.inspection-choice). */
@Composable
fun InspectionCheck(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    title: String,
    caption: String,
    enabled: Boolean = true,
) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(shape)
            .background(WarnCheckBg)
            .border(1.dp, WarnCheckLine, shape)
            .clickable(enabled = enabled, role = Role.Checkbox) { onChange(!checked) }
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = Amber,
                checkmarkColor = p.background,
                uncheckedColor = p.muted,
            ),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = WarnCheckInk, fontSize = 12.sp, lineHeight = 19.sp)
            Text(caption, color = p.muted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}