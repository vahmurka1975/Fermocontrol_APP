package ru.fermocontrol.fermocontrol.ui.components

import androidx.compose.foundation.*
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fermocontrol.fermocontrol.model.SpiceCatalog
import ru.fermocontrol.fermocontrol.model.SpiceCodec
import ru.fermocontrol.fermocontrol.model.SpiceRow
import ru.fermocontrol.fermocontrol.ui.theme.LocalAppPalette

private const val SUFFIX = " г/кг"

val SpiceRowsSaver = listSaver<List<SpiceRow>, String>(
    save = { rows -> rows.flatMap { listOf(it.name, it.grams) } },
    restore = { list -> list.chunked(2).map { SpiceRow(it[0], it[1]) } },
)

/** Подставляет «г/кг» после числа. Курсор в суффикс не заходит. */
private class GramsSuffix(private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val out = buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = color)) { append(SUFFIX) }
        }
        val len = text.length
        return TransformedText(out, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(len)
        })
    }
}

private fun cleanGrams(v: String): String {
    val s = v.filter { it.isDigit() || it == ',' || it == '.' }.replace('.', ',')
    val i = s.indexOf(',')
    val r = if (i >= 0) s.substring(0, i + 1) + s.substring(i + 1).replace(",", "") else s
    return r.take(6)
}

private fun fmt(x: Double): String {
    val r = Math.round(x * 100) / 100.0
    return (if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()).replace('.', ',')
}

@Composable
private fun GramsField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(cleanGrams(it)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(color = p.ink, fontSize = 14.sp),
        cursorBrush = SolidColor(p.accent),
        visualTransformation = GramsSuffix(p.muted),
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(p.field)
            .border(1.dp, if (error) p.danger else p.fieldLine, shape)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text("г/кг", color = p.muted, fontSize = 14.sp)
                inner()
            }
        },
    )
}

@Composable
private fun SpicePicker(
    value: String,
    options: List<String>,
    enabled: Boolean,
    error: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalAppPalette.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .heightIn(min = 46.dp)
                .clip(shape)
                .background(p.field)
                .border(1.dp, if (error) p.danger else p.fieldLine, shape)
                .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifEmpty { "Выберите специю" },
                color = if (value.isEmpty()) p.muted else p.ink,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = p.muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(p.field)) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o, color = if (o == value) p.accent else p.ink) },
                    onClick = { open = false; onSelect(o) },
                )
            }
        }
    }
}

/**
 * Блок «Посол и специи»: нитритная соль первой (не удаляется), затем специи только из списка.
 * Хранит граммы на 1 кг сырья. startWeightGrams нужен только для подсказки пересчёта.
 */
@Composable
fun SpiceEditor(
    nitrite: String,
    onNitrite: (String) -> Unit,
    rows: List<SpiceRow>,
    onRows: (List<SpiceRow>) -> Unit,
    enabled: Boolean,
    submitted: Boolean,
    startWeightGrams: Double?,
    legacy: String,
) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(12.dp)
    val taken = rows.map { it.name }
    val free = SpiceCatalog.items.count { it !in taken }
    val pending = rows.lastOrNull()?.name?.isBlank() == true
    val canAdd = enabled && !pending && free > 0

    fun ok(s: String) = SpiceCodec.parseGrams(s)?.takeIf { it > 0 && it <= SpiceCatalog.MAX_GRAMS_PER_KG } ?: 0.0
    val total = ok(nitrite) + rows.sumOf { if (it.name.isNotBlank()) ok(it.grams) else 0.0 }
    val kg = startWeightGrams?.takeIf { it > 0 }?.div(1000.0)

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text("Посол и специи", color = p.label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp))
        Text(
            "Сначала укажите нитритную соль. Затем нажмите «Добавить специю», выберите её из списка и введите граммы на 1 кг сырья.",
            color = p.muted, fontSize = 11.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 10.dp),
        )
        if (legacy.isNotBlank()) {
            Text(
                "Прежняя запись: $legacy. После сохранения её заменит список ниже.",
                color = p.muted, fontSize = 11.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        val nErr = SpiceCodec.gramsError(nitrite)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).heightIn(min = 46.dp).clip(shape).background(p.field)
                    .border(1.dp, p.fieldLine, shape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(SpiceCatalog.NITRITE, color = p.ink, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text("по умолчанию", color = p.muted, fontSize = 10.sp, maxLines = 1)
            }
            GramsField(nitrite, onNitrite, enabled, submitted && nErr != null, Modifier.width(118.dp))
            Spacer(Modifier.size(36.dp))
        }
        if (submitted && nErr != null) Text(nErr, color = p.danger, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))

        rows.forEachIndexed { i, r ->
            val err = SpiceCodec.rowError(r)
            val show = submitted && err != null
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SpicePicker(
                    value = r.name,
                    options = SpiceCatalog.items.filter { it == r.name || it !in taken },
                    enabled = enabled,
                    error = show && r.name.isBlank(),
                    onSelect = { n -> onRows(rows.toMutableList().also { it[i] = r.copy(name = n) }) },
                    modifier = Modifier.weight(1f),
                )
                GramsField(
                    r.grams,
                    { g -> onRows(rows.toMutableList().also { it[i] = r.copy(grams = g) }) },
                    enabled && r.name.isNotBlank(),
                    show && r.name.isNotBlank(),
                    Modifier.width(118.dp),
                )
                Box(
                    Modifier.size(36.dp).clip(shape)
                        .clickable(enabled = enabled, role = Role.Button) {
                            onRows(rows.toMutableList().also { it.removeAt(i) })
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, "Удалить", tint = p.muted, modifier = Modifier.size(18.dp)) }
            }
            if (show) Text(err!!, color = p.danger, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            else if (kg != null && r.grams.isNotBlank() && SpiceCodec.gramsError(r.grams) == null)
                Text("${fmt(ok(r.grams) * kg)} г на ${fmt(kg)} кг сырья", color = p.muted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 5.dp))
        }

        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape)
                .background(p.secondary.copy(alpha = if (canAdd) 1f else 0.4f))
                .border(1.dp, p.secondaryLine.copy(alpha = if (canAdd) 1f else 0.4f), shape)
                .clickable(enabled = canAdd, role = Role.Button) { onRows(rows + SpiceRow("", "")) },
            contentAlignment = Alignment.Center,
        ) { Text("+ Добавить специю", color = p.onSecondary.copy(alpha = if (canAdd) 1f else 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        val step = when {
            free == 0 -> "Все специи из списка уже добавлены"
            pending -> "Выберите специю из списка"
            else -> null
        }
        if (step != null) Text(step, color = p.muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
        if (total > 0) {
            Text(
                "Всего на 1 кг сырья: ${fmt(total)} г" + if (kg != null) ". На ${fmt(kg)} кг сырья: ${fmt(total * kg)} г" else "",
                color = p.ink, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
