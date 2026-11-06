package ru.fermocontrol.fermocontrol.model

import kotlin.math.max
import kotlin.math.round

/**
 * Результат расчёта усушки партии.
 */
data class Metrics(
    val currentWeightGrams: Double,   // последний известный вес (или стартовый)
    val startWeightGrams: Double,
    val lostGrams: Double,            // потерянный вес, г
    val lostPercent: Double,          // усушка, % (0..100)
    val targetLossPercent: Double,
    val toTargetPercent: Double,      // сколько % осталось до цели (>=0)
    val toTargetGrams: Double,        // сколько грамм осталось сбросить
    val progress: Double,             // 0..1 от цели (для прогресс-бара)
    val daysElapsed: Int,             // дней с закладки (>=0)
    val pacePercentPerDay: Double?,   // темп усушки, %/сут (null, если данных мало)
    val estimatedTotalDays: Int?,     // прогноз дней до цели (null, если темп <= 0)
    val estimatedDaysLeft: Int?,      // прогноз остатка дней
)

/**
 * Единый расчёт всех метрик на основе партии.
 * Повторяет логику massMetrics/calcMetrics из HTML-макета.
 */
fun computeMetrics(batch: Batch, nowEpochMs: Long = System.currentTimeMillis()): Metrics {
    val start = batch.startWeightGrams
    val lastLog = batch.logs.maxByOrNull { it.atEpochMs }
    val current = lastLog?.weightGrams ?: start

    val lost = max(0.0, start - current)
    val lostPercent = if (start > 0) lost / start * 100.0 else 0.0
    val targetLoss = batch.targetLossPercent

    val toTargetPercent = max(0.0, targetLoss - lostPercent)
    val toTargetGrams = max(0.0, (targetLoss / 100.0) * start - lost)
    val progress = if (targetLoss > 0) (lostPercent / targetLoss).coerceIn(0.0, 1.0) else 0.0

    val daysElapsed = max(0, ((nowEpochMs - batch.createdAtEpochMs) / 86_400_000L).toInt())

    // Темп усушки: от закладки до последнего взвешивания
    val pace = computePace(batch)

    // Прогноз дней до цели: проценты делим на проценты за сутки — получаем сутки.
    val estimatedTotalDays: Int? = if (pace != null && pace > 0 && targetLoss > 0) {
        (targetLoss / pace).roundToIntSafe()
    } else null

    val estimatedDaysLeft: Int? = if (pace != null && pace > 0 && targetLoss > 0) {
        (toTargetPercent / pace).roundToIntSafe()
    } else null

    return Metrics(
        currentWeightGrams = current,
        startWeightGrams = start,
        lostGrams = lost,
        lostPercent = lostPercent,
        targetLossPercent = targetLoss,
        toTargetPercent = toTargetPercent,
        toTargetGrams = toTargetGrams,
        progress = progress,
        daysElapsed = daysElapsed,
        pacePercentPerDay = pace,
        estimatedTotalDays = estimatedTotalDays,
        estimatedDaysLeft = estimatedDaysLeft,
    )
}

/**
 * Темп усушки, %/сут. Считается от закладки до последнего взвешивания.
 * Возвращает null, если нет ни одного взвешивания или недостаточно данных.
 */
private fun computePace(batch: Batch): Double? {
    val lastLog = batch.logs.maxByOrNull { it.atEpochMs } ?: return null
    val startEpoch = batch.createdAtEpochMs
    val startWeight = batch.startWeightGrams

    val days = (lastLog.atEpochMs - startEpoch) / 86_400_000.0
    if (days <= 0.0 || startWeight <= 0.0) return null

    val lostPercent = (startWeight - lastLog.weightGrams) / startWeight * 100.0
    return lostPercent / days
}

/**
 * Округление Double к Int с защитой от бесконечности/NaN.
 */
private fun Double.roundToIntSafe(): Int {
    if (this.isNaN() || this.isInfinite()) return 0
    return round(this).toInt()
}

/**
 * Форматирование усушки в строку, например "12.4".
 */
fun Double.toPercentString(digits: Int = 1): String {
    val mul = 10.0.pow(digits)
    return (round(this * mul) / mul).toString()
}

private fun Double.pow(n: Int): Double {
    var result = 1.0
    repeat(n) { result *= this }
    return result
}
/**
 * Единое правило внимания для счётчиков, фильтра и карточки.
 * Достижение цели усушки не означает оценку безопасности продукта.
 */
fun Batch.requiresAttention(
    metrics: Metrics = computeMetrics(this),
): Boolean {
    if (archived) return false

    val lastLog = logs.maxByOrNull { it.atEpochMs }

    val targetReached =
        metrics.targetLossPercent > 0.0 &&
            metrics.lostPercent >= metrics.targetLossPercent

    return lastLog?.needsCheck == true || targetReached
}