package ru.fermocontrol.fermocontrol.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val RuLocale: Locale = Locale.forLanguageTag("ru-RU")

/** 2140.4 -> "2 140" (неразрывный пробел между разрядами). */
internal fun fmtGrams(value: Double): String {
    if (!value.isFinite()) return "—"
    val rounded = value.roundToLong()
    val digits = abs(rounded).toString()
    val grouped = digits.reversed().chunked(3).joinToString("\u00A0").reversed()
    return if (rounded < 0) "−$grouped" else grouped
}

/** Модуль числа с запятой: 14.43 -> "14,4". */
internal fun fmtDec(value: Double, digits: Int = 1): String {
    if (!value.isFinite()) return "—"
    return String.format(RuLocale, "%." + digits + "f", abs(value))
}

/** Потеря веса в % от начального: 14.4 -> "−14,4%", отрицательная -> "+2,0%". */
internal fun fmtPercentChange(lossPercent: Double): String {
    if (!lossPercent.isFinite()) return "—"
    val text = fmtDec(lossPercent, 1)
    return when {
        lossPercent > 0.05 -> "−$text%"
        lossPercent < -0.05 -> "+$text%"
        else -> "0%"
    }
}

internal fun fmtSignedGrams(delta: Double): String {
    if (!delta.isFinite()) return "—"
    val text = fmtGrams(abs(delta))
    return when {
        delta < -0.05 -> "−$text"
        delta > 0.05 -> "+$text"
        else -> "0"
    }
}

internal fun fmtDateShort(epochMs: Long): String =
    SimpleDateFormat("d MMM", RuLocale).format(Date(epochMs))
