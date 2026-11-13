package ru.fermocontrol.fermocontrol.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fermocontrol.fermocontrol.model.MAX_SPICE_GRAMS_PER_KG
import ru.fermocontrol.fermocontrol.model.NITRITE_SALT
import ru.fermocontrol.fermocontrol.model.SpiceItem
import ru.fermocontrol.fermocontrol.model.formatGrams
import ru.fermocontrol.fermocontrol.ui.theme.LocalAppPalette
import kotlin.math.round

/** Строка формы. id = 0 только у «Нитритной соли»: её нельзя удалить и переименовать. */
data class SpiceRow(val id: Int, val name: String, val grams: String) {
    val fixed: Boolean get() = id == 0
}

data class SpiceRowError(val name: String? = null, val grams: String? = null)

/** Сохраняет строки при повороте экрана. */
val SpiceRowsSaver = listSaver<List<SpiceRow>, String>(
    save = { rows -> rows.flatMap { listOf(it.id.toString(), it.name, it.grams) } },
    restore = { flat -> flat.chunked(3).map { SpiceRow(it[0].toInt(), it[1], it[2]) } },
)

fun parseSpiceGrams(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Начальные строки: «Нитритная соль» всегда первая, затем сохранённые специи. */
fun initialSpiceRows(saved: List<SpiceItem>): List<SpiceRow> {
    val nitrite = saved.firstOrNull { it.name.trim().equals(NITRITE_SALT, ignoreCase = true) }
    val others = saved.filter { it !== nitrite }
    return listOf(
        SpiceRow(0, NITRITE_SALT, nitrite?.let { formatGrams(it.gramsPerKg) }.orEmpty())
    ) + others.mapIndexed { i, s -> SpiceRow(i + 1, s.name, formatGrams(s.gramsPerKg)) }
}

/** Ошибки по id строки. Полностью пустые строки пропускаются. */
fun spiceErrors(rows: List<SpiceRow>): Map<Int, SpiceRowError> {
    val counts = rows.map { it.name.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }.eachCount()
    val result = mutableMapOf<Int, SpiceRowError>()
    rows.forEach { row ->
        val name = row.name.trim()
        val text = row.grams.trim()
        if (name.isEmpty() && text.isEmpty()) return@forEach
        val nameErr = when {
            name.isEmpty() -> "Укажите название"
            !row.fixed && (counts[name.lowercase()] ?: 0) > 1 -> "Уже есть в списке"
            else -> null
        }
        val g = parseSpiceGrams(text)
        val gramsErr = when {
            text.isEmpty() -> if (row.fixed) null else "Укажите граммы"
            g == null || g <= 0.0 || g > MAX_SPICE_GRAMS_PER_KG -> "От 0 до 100"
            else -> null
        }
        if (nameErr != null || gramsErr != null) result[row.id] = SpiceRowError(nameErr, gramsErr)
    }
    return result
}

/** Специи для сохранения. Вызывать, когда spiceErrors пуст. */
fun spiceItems(rows: List<SpiceRow>): List<SpiceItem> = rows.mapNotNull { row ->
    val g = parseSpiceGrams(row.grams) ?: return@mapNotNull null
    val name = row.name.trim()
    if (name.isEmpty()) null else SpiceItem(name, g)
}

private val SUGGESTED = listOf(
    "Чёрный перец", "Кориандр", "Мускатный орех", "Паприка",
    "Чеснок", "Сахар", "Декстроза", "Коньяк",
)

private fun round1(v: Double) = round(v * 10.0) / 10.0

@Composable
fun SpiceEditor(
    rows: List<SpiceRow>,
    onRowsChange: (List<SpiceRow>) -> Unit,
    errors: Map<Int, SpiceRowError>,
    showErrors: Boolean,
    batchWeightG: Double?,
    enabled: Boolean,
    legacyText: String = "",
) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(12.dp)

    fun update(id: Int, change: (SpiceRow) -> SpiceRow) =
        onRowsChange(rows.map { if (it.id == id) change(it) else it })

    fun nextId() = (rows.maxOfOrNull { it.id } ?: 0) + 1

    val total = rows.sumOf { r ->
        parseSpiceGrams(r.grams)?.takeIf { it > 0.0 && it <= MAX_SPICE_GRAMS_PER_KG } ?: 0.0
    }
    val available = SUGGESTED.filter { s -> rows.none { it.name.trim().equals(s, ignoreCase = true) } }

    Column(Modifier.padding(bottom = 12.dp)) {
        Text("Посол и специи", color = p.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            "Граммы на 1 кг сырья. Нитритная соль без граммов в паспорт не попадёт.",
            color = p.muted, fontSize = 11.5.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )

        rows.forEach { row ->
            key(row.id) {
                val err = if (showErrors) errors[row.id] else null
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    FcTextField(
                        label = "Название",
                        value = row.name,
                        onValueChange = { v -> update(row.id) { r -> r.copy(name = v.take(60)) } },
                        modifier = Modifier.weight(1f),
                        placeholder = "Например, Чёрный перец",
                        enabled = enabled && !row.fixed,
                        error = err?.name,
                    )
                    FcTextField(
                        label = "г на 1 кг",
                        value = row.grams,
                        onValueChange = { v -> update(row.id) { r -> r.copy(grams = v.take(7)) } },
                        modifier = Modifier.width(110.dp),
                        placeholder = "0",
                        keyboardType = KeyboardType.Decimal,
                        enabled = enabled,
                        error = err?.grams,
                    )
                    if (!row.fixed) {
                        Box(
                            modifier = Modifier
                                .padding(top = 22.dp)
                                .size(46.dp)
                                .clip(shape)
                                .border(1.dp, p.divider, shape)
                                .clickable(enabled = enabled, role = Role.Button) {
                                    onRowsChange(rows.filter { it.id != row.id })
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Удалить специю",
                                tint = p.ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                val g = parseSpiceGrams(row.grams)
                if (g != null && g > 0.0 && g <= MAX_SPICE_GRAMS_PER_KG && batchWeightG != null) {
                    Text(
                        "≈ ${formatGrams(round1(g * batchWeightG / 1000.0))} г на партию",
                        color = p.muted, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(shape)
                .border(1.dp, p.divider, shape)
                .clickable(enabled = enabled, role = Role.Button) {
                    onRowsChange(rows + SpiceRow(nextId(), "", ""))
                }
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = p.ink, modifier = Modifier.size(18.dp))
            Text("Добавить специю", color = p.ink, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
        }

        if (available.isNotEmpty()) {
            Column(Modifier.padding(top = 12.dp)) {
                FcSelect(
                    label = "Быстрый выбор",
                    value = "Выбрать из списка",
                    options = available,
                    onSelect = { name -> onRowsChange(rows + SpiceRow(nextId(), name, "")) },
                )
            }
        }

        if (total > 0.0) {
            val onBatch = batchWeightG?.let { " · на партию ${formatGrams(round1(total * it / 1000.0))} г" }.orEmpty()
            Text(
                "Итого: ${formatGrams(round1(total))} г на 1 кг$onBatch",
                color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            )
        }
        if (legacyText.isNotBlank()) {
            Text(
                "Прежняя запись: «$legacyText». Она заменится списком, когда вы укажете граммы.",
                color = p.muted, fontSize = 11.5.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}