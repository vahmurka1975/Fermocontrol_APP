package ru.fermocontrol.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricsTest {

    @Test
    fun `усушка считается от стартового веса`() {
        // 1000 г -> 850 г = 15% усушки
        val batch = Batch(
            id = 1,
            number = 1,
            name = "Бастурма",
            startWeightGrams = 1000.0,
            targetLossPercent = 30.0,
            createdAtEpochMs = 0L,
            logs = listOf(WeightLog(atEpochMs = 86_400_000L, weightGrams = 850.0)),
        )
        val m = computeMetrics(batch, nowEpochMs = 86_400_000L)
        assertEquals(850.0, m.currentWeightGrams, 0.001)
        assertEquals(150.0, m.lostGrams, 0.001)
        assertEquals(15.0, m.lostPercent, 0.001)
        assertEquals(15.0, m.toTargetPercent, 0.001)
        assertEquals(0.5, m.progress, 0.001)
        assertEquals(1, m.daysElapsed)
    }

    @Test
    fun `темп усушки за сутки`() {
        val batch = Batch(
            id = 1,
            number = 1,
            name = "Балык",
            startWeightGrams = 2000.0,
            targetLossPercent = 30.0,
            createdAtEpochMs = 0L,
            logs = listOf(WeightLog(atEpochMs = 86_400_000L, weightGrams = 1900.0)),
        )
        val m = computeMetrics(batch, nowEpochMs = 86_400_000L)
        assertEquals(5.0, m.pacePercentPerDay!!, 0.001)
    }

    @Test
    fun `без взвешиваний текущий вес равен стартовому`() {
        val batch = Batch(
            id = 1,
            number = 1,
            name = "Колбаса",
            startWeightGrams = 1200.0,
            targetLossPercent = 40.0,
            createdAtEpochMs = 0L,
        )
        val m = computeMetrics(batch, nowEpochMs = 0L)
        assertEquals(1200.0, m.currentWeightGrams, 0.001)
        assertEquals(0.0, m.lostPercent, 0.001)
        assertEquals(0.0, m.progress, 0.001)
    }

    @Test
    fun `JSON кодируется и декодируется без потерь`() {
        val batches = listOf(
            Batch(
                id = 1, number = 1, name = "Бастурма",
                startWeightGrams = 1000.0, targetLossPercent = 30.0,
                createdAtEpochMs = 1000L,
                logs = listOf(WeightLog(atEpochMs = 2000L, weightGrams = 900.0, needsCheck = true)),
            )
        )
        val encoded = ru.fermocontrol.data.JournalCodec.encode(batches)
        val decoded = ru.fermocontrol.data.JournalCodec.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals("Бастурма", decoded[0].name)
        assertEquals(900.0, decoded[0].logs[0].weightGrams, 0.001)
        assertEquals(true, decoded[0].logs[0].needsCheck)
    }
}