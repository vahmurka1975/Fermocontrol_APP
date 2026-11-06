package ru.fermocontrol.fermocontrol.model

import java.util.Locale
import kotlin.math.max

const val WEIGH_INTERVAL_DAYS = 3
const val FAST_PACE_PERCENT_PER_DAY = 1.5
private const val DAY_MS = 86_400_000L

/**
 * Состояние партии по правилам макета v0.4.1 (calcMetrics):
 * "Взвесить" = isDueWeigh, кнопка внимания = needsAttention.
 */
data class BatchStatus(
    val lastMeasuredEpochMs: Long,
    val daysSinceWeigh: Int,
    val recentPacePercentPerDay: Double?,
    val isDueWeigh: Boolean,
    val isFastPace: Boolean,
    val isWeightGain: Boolean,
    val isClockIssue: Boolean,
    val isTargetReached: Boolean,
    val isInspectionDue: Boolean,
    val needsAttention: Boolean,
)

fun computeStatus(
    batch: Batch,
    nowEpochMs: Long = System.currentTimeMillis(),
    intervalDays: Int = WEIGH_INTERVAL_DAYS,
    fastPace: Double = FAST_PACE_PERCENT_PER_DAY,
): BatchStatus {
    val logs = batch.logs.sortedBy { it.atEpochMs }
    val last = logs.lastOrNull()
    val start = batch.startWeightGrams
    val prevAt = if (logs.size >= 2) logs[logs.size - 2].atEpochMs else batch.createdAtEpochMs
    val prevWeight = if (logs.size >= 2) logs[logs.size - 2].weightGrams else start
    val current = last?.weightGrams ?: start
    val lastMeasured = last?.atEpochMs ?: batch.createdAtEpochMs
    val active = !batch.archived

    val clock = active && lastMeasured > nowEpochMs
    val daysSince = max(0L, (nowEpochMs - lastMeasured) / DAY_MS).toInt()
    val due = active && !clock && daysSince >= intervalDays

    val pace: Double? = if (last != null && start > 0.0) {
        val segDays = (last.atEpochMs - prevAt).toDouble() / DAY_MS
        if (segDays >= 1.0 / 24.0) ((prevWeight - last.weightGrams) / start * 100.0) / segDays else null
    } else null

    val fast = active && pace != null && pace > fastPace
    val gain = active && last != null && current > prevWeight
    val lost = if (start > 0.0) (start - current) / start * 100.0 else 0.0
    val reached = batch.targetLossPercent > 0.0 && lost >= batch.targetLossPercent
    val inspection = active && last?.needsCheck == true
    val needs = active && (due || fast || reached || gain || clock || inspection)

    return BatchStatus(lastMeasured, daysSince, pace, due, fast, gain, clock, reached, inspection, needs)
}

fun nextActionText(
    batch: Batch,
    s: BatchStatus,
    intervalDays: Int = WEIGH_INTERVAL_DAYS,
): String = when {
    batch.archived -> "В архиве"
    s.isClockIssue -> "Дата замера в будущем · проверьте часы"
    s.isInspectionDue -> "Есть наблюдения · требует проверки"
    s.isWeightGain -> "Вес вырос · проверьте запись"
    s.isFastPace -> "Быстрая усушка · " +
        String.format(Locale.forLanguageTag("ru-RU"), "%.2f", s.recentPacePercentPerDay ?: 0.0) + "%/сут"
    s.isTargetReached -> "Плановая усушка достигнута"
    s.isDueWeigh -> "Пора взвесить · ${s.daysSinceWeigh} дн. без замера"
    else -> {
        val days = max(1, intervalDays - s.daysSinceWeigh)
        if (days == 1) "Следующий замер завтра" else "Следующий замер через $days дн."
    }
}
