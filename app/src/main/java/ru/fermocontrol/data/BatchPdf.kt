package ru.fermocontrol.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fermocontrol.model.Batch
import ru.fermocontrol.model.computeMetrics
import ru.fermocontrol.model.WeightLog
import ru.fermocontrol.model.computeMetrics

/**
 * Что выгружать в PDF.
 * extra: строки блока «Итог партии» (итог, причина порчи, заметка по технологии). Пустые значения пропускаются.
 * closedAtMs: дата закрытия, null если партия в работе.
 */
class PdfRequest(
    val batch: Batch,
    val extra: List<Pair<String, String>> = emptyList(),
    val cover: File? = null,
    val closedAtMs: Long? = null,
)

class BatchPdf(private val app: Context) {

    fun fileName(batch: Batch): String {
        val base = "Партия_${batch.number}_${batch.name}"
            .replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(60)
        return "$base.pdf"
    }

    suspend fun write(req: PdfRequest, out: OutputStream) =
        withContext(Dispatchers.IO) { render(req, out) }

    /** Готовит файл в cache/share (тот же каталог, что у копии данных) и возвращает адрес для «Поделиться». */
    suspend fun prepareShare(req: PdfRequest): Uri = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(req.batch))
        FileOutputStream(file).use { render(req, it) }
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    private fun render(req: PdfRequest, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val footer = "ФермоКонтроль · Партия №${req.batch.number} · ${fmt("d.MM.yyyy", System.currentTimeMillis())}"
            val sheet = PdfSheet(doc, footer)
            draw(sheet, req)
            sheet.close()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun draw(s: PdfSheet, req: PdfRequest) {
        val b = req.batch
        val mt = computeMetrics(b, req.closedAtMs ?: System.currentTimeMillis())
        s.newPage()

        // Шапка с обложкой
        val cover = req.cover?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        val textW = if (cover != null) s.cw - COVER - 14f else s.cw
        val top = s.y
        s.text("ФермоКонтроль · карточка партии", Pt.small, w = textW, after = 4f)
        s.text("№${b.number} ${b.name}", Pt.title, w = textW, after = 4f)
        s.text("Закладка: ${fmt("d MMMM yyyy", b.createdAtEpochMs)}", Pt.body, w = textW)
        if (cover != null) {
            val dst = RectF(s.m + s.cw - COVER, top, s.m + s.cw, top + COVER)
            val side = min(cover.width, cover.height)
            val src = Rect(
                (cover.width - side) / 2, (cover.height - side) / 2,
                (cover.width + side) / 2, (cover.height + side) / 2,
            )
            s.c.save()
            s.c.clipPath(Path().apply { addRoundRect(dst, 10f, 10f, Path.Direction.CW) })
            s.c.drawBitmap(cover, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            s.c.restore()
            cover.recycle()
            s.y = max(s.y, top + COVER)
        }
        s.y += 6f

        // Итог партии
        val extra = req.extra.filter { it.second.isNotBlank() }
        if (extra.isNotEmpty()) {
            s.section("Итог партии")
            extra.forEach { (k, v) -> s.kv(k, v) }
        }

        // Паспорт
        s.section("Паспорт")
        s.kv("Категория", b.category)
        s.kv("Сырьё", b.rawMaterial)
        s.kv("Оболочка", b.casing)
        s.kv("Специи", b.spices.split(';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"))
        s.kv("Вес закладки", grams(b.startWeightGrams))
        s.kv("Целевая усушка", "${num(b.targetLossPercent)}%")
        s.kv("Температура", b.temperatureC?.let { "${num(it)} °C" } ?: "")
        s.kv("Влажность", b.humidityPercent?.let { "${num(it)}%" } ?: "")
        s.kv("Примечание", b.note)

        // Показатели
        s.section("Показатели")
        val closed = req.closedAtMs != null
        val paceValue = mt.pacePercentPerDay?.let { String.format(RU, "%.2f %%/сут", it) } ?: "—"
        val paceLabel = when {
            closed -> "за время сушки"
            mt.estimatedDaysLeft != null -> "осталось ≈ ${mt.estimatedDaysLeft} дн."
            else -> "прогноза нет"
        }
        s.tiles(
            listOf(
                Triple("${num1(mt.lostPercent)}%", "усушка, цель ${num(mt.targetLossPercent)}%", 0),
                Triple(grams(mt.currentWeightGrams), "было ${grams(mt.startWeightGrams)}", 0),
                Triple("${mt.daysElapsed} дн.", if (closed) "до закрытия" else "с закладки", 0),
                Triple(paceValue, paceLabel, 0),
            ),
        )
        s.progress(mt.progress.toFloat(), "Выполнено ${num1(mt.progress * 100)}% от цели")

        // Взвешивания
        s.section("Взвешивания")
        data class Row(val ms: Long, val w: Double, val note: String)
        val rows = buildList {
            add(Row(b.createdAtEpochMs, b.startWeightGrams, "Закладка"))
            b.logs.sortedBy { it.atEpochMs }.forEach { l ->
                val note = listOfNotNull(
                    l.smellNote.takeIf { it.isNotBlank() }?.let { "Запах: $it" },
                    l.surfaceNote.takeIf { it.isNotBlank() }?.let { "Поверхность: $it" },
                    if (l.needsCheck) "Нужна проверка" else null,
                ).joinToString("\n")
                add(Row(l.atEpochMs, l.weightGrams, note))
            }
        }
        val x1 = s.m + 125f
        val x2 = s.m + 200f
        val x3 = s.m + 265f
        val nw = s.cw - 265f
        fun head() {
            s.ensure(22f)
            s.c.drawRect(s.m, s.y, s.m + s.cw, s.y + 18f, Pt.fill)
            val bl = s.y + 13f
            s.c.drawText("Дата", s.m + 6f, bl, Pt.th)
            s.c.drawText("Вес", x1, bl, Pt.th)
            s.c.drawText("Усушка", x2, bl, Pt.th)
            s.c.drawText("Заметки", x3, bl, Pt.th)
            s.y += 18f
        }
        head()
        rows.forEach { r ->
            val notes = layout(r.note, Pt.body, nw)
            val h = max(18f, notes.height + 8f)
            if (!s.fits(h)) {
                s.newPage()
                head()
            }
            val bl = s.y + 4f - Pt.body.ascent()
            val lossPct = if (b.startWeightGrams > 0) (b.startWeightGrams - r.w) / b.startWeightGrams * 100.0 else 0.0
            s.c.drawText(fmt("d MMM yyyy, HH:mm", r.ms), s.m + 6f, bl, Pt.body)
            s.c.drawText(grams(r.w), x1, bl, Pt.body)
            s.c.drawText("${num1(lossPct)}%", x2, bl, Pt.body)
            s.c.save()
            s.c.translate(x3, s.y + 4f)
            notes.draw(s.c)
            s.c.restore()
            s.y += h
            s.c.drawLine(s.m, s.y, s.m + s.cw, s.y, Pt.line)
        }
    }

    private companion object {
        const val COVER = 100f
        val RU: Locale = Locale.forLanguageTag("ru")

        fun fmt(pattern: String, ms: Long): String = SimpleDateFormat(pattern, RU).format(Date(ms))
        fun num(v: Double) = if (v == Math.rint(v)) String.format(RU, "%.0f", v) else String.format(RU, "%.1f", v)
        fun num1(v: Double) = String.format(RU, "%.1f", v)
        fun grams(v: Double) =
            if (v == Math.rint(v)) String.format(RU, "%,.0f г", v) else String.format(RU, "%,.1f г", v)
    }
}

private fun layout(s: String, p: TextPaint, w: Float): StaticLayout =
    StaticLayout.Builder.obtain(s, 0, s.length, p, w.toInt()).setLineSpacing(0f, 1.1f).build()

private object Pt {
    private const val INK = 0xFF1F1A16.toInt()
    private const val GRAY = 0xFF7A6F63.toInt()
    const val ACCENT = 0xFFC0562B.toInt()

    private fun tp(size: Float, bold: Boolean = false, col: Int = INK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = col
        }

    val title = tp(20f, true)
    val body = tp(10f)
    val label = tp(10f, col = GRAY)
    val small = tp(9f, col = GRAY)
    val th = tp(9f, true, GRAY)
    val h2 = tp(12f, true, ACCENT)
    val big = tp(15f, true)
    val foot = tp(8f, col = GRAY)
    val fill = Paint().apply { color = 0xFFF4EEE6.toInt() }
    val barBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7DDD0.toInt() }
    val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    val line = Paint().apply { color = 0xFFE0D6C8.toInt(); strokeWidth = 0.6f }
}

/** Страницы A4 (595×842 pt) с курсором y и автоматическим переносом. */
private class PdfSheet(private val doc: PdfDocument, private val footer: String) {
    private val pw = 595
    private val ph = 842
    val m = 40f
    val cw = pw - 2 * m
    private var no = 0
    private var page: PdfDocument.Page? = null
    lateinit var c: Canvas
    var y = m

    fun newPage() {
        close()
        no++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, no).create())
        page = p
        c = p.canvas
        y = m
    }

    fun close() {
        val p = page ?: return
        c.drawText("$footer · стр. $no", m, ph - 22f, Pt.foot)
        doc.finishPage(p)
        page = null
    }

    fun fits(h: Float) = page != null && y + h <= ph - m
    fun ensure(h: Float) { if (!fits(h)) newPage() }

    /** Текст с переносом по строкам и по страницам. */
    fun text(s: String, p: TextPaint, x: Float = m, w: Float = cw, after: Float = 0f) {
        if (s.isEmpty()) return
        val l = layout(s, p, w)
        for (i in 0 until l.lineCount) {
            val top = l.getLineTop(i).toFloat()
            val bot = l.getLineBottom(i).toFloat()
            ensure(bot - top)
            c.save()
            c.translate(x, y - top)
            c.clipRect(0f, top, w, bot + 2f)
            l.draw(c)
            c.restore()
            y += bot - top
        }
        y += after
    }

    fun section(title: String) {
        ensure(44f)
        y += 10f
        text(title, Pt.h2, after = 3f)
        c.drawLine(m, y, m + cw, y, Pt.line)
        y += 7f
    }

    fun kv(k: String, v: String) {
        if (v.isBlank()) return
        ensure(16f)
        c.drawText(k, m, y - Pt.body.ascent(), Pt.label)
        text(v, Pt.body, x = m + 150f, w = cw - 150f, after = 4f)
    }

    fun tiles(items: List<Triple<String, String, Int>>) {
        val gap = 8f
        val tw = (cw - gap * (items.size - 1)) / items.size
        val h = 52f
        ensure(h + 8f)
        items.forEachIndexed { i, (value, label, _) ->
            val x = m + i * (tw + gap)
            c.drawRoundRect(RectF(x, y, x + tw, y + h), 8f, 8f, Pt.fill)
            c.drawText(value, x + 10f, y + 25f, Pt.big)
            c.drawText(label, x + 10f, y + 41f, Pt.small)
        }
        y += h + 10f
    }

    fun progress(value: Float, caption: String) {
        ensure(30f)
        c.drawText(caption, m, y - Pt.small.ascent(), Pt.small)
        y += 14f
        val r = RectF(m, y, m + cw, y + 6f)
        c.drawRoundRect(r, 3f, 3f, Pt.barBack)
        if (value > 0f) c.drawRoundRect(RectF(m, y, m + cw * value.coerceIn(0f, 1f), y + 6f), 3f, 3f, Pt.barFill)
        y += 12f
    }
}