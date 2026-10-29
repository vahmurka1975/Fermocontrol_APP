package ru.fermocontrol.app

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun stage6TemplatesAndIntervalsWork() {
        assertTrue(defaultProductTemplates.isNotEmpty())
        val coppa = defaultProductTemplates.first()
        val now = 1_700_000_000_000L
        val product = Product(
            number = 1,
            name = coppa.defaultName,
            basis = coppa.basis,
            spices = coppa.spices,
            casing = coppa.casing,
            initialWeight = 1000.0,
            targetLoss = coppa.targetLoss,
            startedAt = now - 2 * DAY_MILLIS,
            reminderIntervalDays = 2,
            targetTemp = coppa.targetTemp,
            targetHumidity = coppa.targetHumidity,
            weighings = listOf(
                Weighing(weight = 640.0, at = now - 2 * DAY_MILLIS, temperature = 12.1, humidity = 74.5)
            )
        )
        assertTrue(needsWeighing(product, nowMs = now))
        assertTrue(isTargetReached(product))
        val encoded = BackupCodec.encode(listOf(product))
        val decoded = BackupCodec.decode(encoded).single()
        assertEquals(2, decoded.reminderIntervalDays)
        assertEquals(12.0, decoded.targetTemp!!, 0.001)
        assertEquals(75.0, decoded.targetHumidity!!, 0.001)
        assertEquals(12.1, decoded.weighings.single().temperature!!, 0.001)
        assertEquals(74.5, decoded.weighings.single().humidity!!, 0.001)
    }
}
