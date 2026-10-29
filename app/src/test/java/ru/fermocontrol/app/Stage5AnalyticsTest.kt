package ru.fermocontrol.app

import org.junit.Assert.*
import org.junit.Test

class Stage5AnalyticsTest {

    private fun sampleProduct(
        startedAt: Long = 1_000_000_000L,
        archivedAt: Long? = null,
        weighings: List<Weighing> = emptyList()
    ) = Product(
        id = "p-1",
        number = 3,
        name = "Коппа домашняя",
        basis = "Свинина",
        spices = listOf("Чёрный перец", "Кориандр"),
        casing = "Коллагеновая",
        initialWeight = 1000.0,
        targetLoss = 30.0,
        startedAt = startedAt,
        archivedAt = archivedAt,
        weighings = weighings
    )

    @Test
    fun needsWeighingDetectsStaleActiveProductAndIgnoresArchived() {
        val start = 1_000_000_000L
        val twoDaysLater = start + 2 * DAY_MILLIS
        val fourDaysLater = start + 4 * DAY_MILLIS

        val fresh = sampleProduct(startedAt = start)
        assertFalse(needsWeighing(fresh, nowMs = twoDaysLater))
        assertTrue(needsWeighing(fresh, nowMs = fourDaysLater))

        val weighedRecently = sampleProduct(
            startedAt = start,
            weighings = listOf(Weighing(weight = 950.0, at = twoDaysLater))
        )
        assertFalse(needsWeighing(weighedRecently, nowMs = fourDaysLater))
        assertTrue(needsWeighing(weighedRecently, nowMs = twoDaysLater + 3 * DAY_MILLIS))

        val archived = sampleProduct(startedAt = start, archivedAt = twoDaysLater)
        assertFalse(needsWeighing(archived, nowMs = fourDaysLater + 10 * DAY_MILLIS))
    }

    @Test
    fun calculateDryingStatsComputesRateAndRemainingWeight() {
        val start = 1_000_000_000L
        val fiveDaysLater = start + 5 * DAY_MILLIS
        val product = sampleProduct(
            startedAt = start,
            weighings = listOf(
                Weighing(weight = 900.0, at = fiveDaysLater)
            )
        )

        val stats = calculateDryingStats(product, nowMs = fiveDaysLater)
        assertEquals(5.0, stats.elapsedDays, 0.001)
        assertEquals(100.0, stats.absoluteLossGrams, 0.001)
        assertEquals(10.0, stats.lossPercent, 0.001)
        assertEquals(200.0, stats.remainingGramsToTarget, 0.001)
        assertEquals(20.0, stats.remainingPercentToTarget, 0.001)
        assertNotNull(stats.avgGramsPerDay)
        assertEquals(20.0, stats.avgGramsPerDay!!, 0.001)
        assertEquals(2.0, stats.avgPercentPerDay!!, 0.001)
    }

    @Test
    fun buildChartPointsAlwaysStartsWithInitialAndSortsByTime() {
        val start = 1_000_000_000L
        val product = sampleProduct(
            startedAt = start,
            weighings = listOf(
                Weighing(weight = 850.0, at = start + 4 * DAY_MILLIS),
                Weighing(weight = 920.0, at = start + 2 * DAY_MILLIS)
            )
        )

        val pts = buildChartPoints(product)
        assertEquals(3, pts.size)
        assertTrue(pts[0].isInitial)
        assertEquals(1000.0, pts[0].weight, 0.001)
        assertEquals(920.0, pts[1].weight, 0.001)
        assertEquals(850.0, pts[2].weight, 0.001)
    }

    @Test
    fun matchesProductQueryFindsByNumberNameBasisAndSpice() {
        val product = sampleProduct()
        assertTrue(matchesProductQuery(product, ""))
        assertTrue(matchesProductQuery(product, "3"))
        assertTrue(matchesProductQuery(product, "№3"))
        assertTrue(matchesProductQuery(product, "коппа"))
        assertTrue(matchesProductQuery(product, "свинина"))
        assertTrue(matchesProductQuery(product, "кориандр"))
        assertFalse(matchesProductQuery(product, "говядина"))
    }
}