package ru.fermocontrol.app

import org.junit.Assert.*
import org.junit.Test

class ProductTest {
    private fun product(number: Int, archived: Long? = null, id: String = "id-$number-${archived ?: 0}") = Product(
        id = id, number = number, name = "Тест", basis = "Говядина", spices = listOf("Без специй"),
        casing = "Без оболочки", initialWeight = 1000.0, targetLoss = 30.0,
        archivedAt = archived
    )

    @Test fun emptyListStartsAtOne() { assertEquals(1, firstFreeNumber(emptyList())) }
    @Test fun smallestGapIsUsed() {
        assertEquals(2, firstFreeNumber(listOf(product(1), product(3))))
    }
    @Test fun archiveReleasesNumber() {
        assertEquals(1, firstFreeNumber(listOf(product(1, 1L), product(2))))
    }
    @Test fun archiveAndActiveMayShareHistoricalNumber() {
        validateProducts(listOf(product(1, 1L), product(1)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun duplicateActiveNumbersAreRejected() {
        validateProducts(listOf(product(1), product(1)))
    }
    @Test fun commaAndPointAreAccepted() {
        assertEquals(123.5, parsePositiveNumber("123,5")!!, 0.001)
        assertEquals(123.5, parsePositiveNumber("123.5")!!, 0.001)
    }
    @Test fun invalidWeightsAreRejected() {
        listOf("", "-1", "0", "NaN", "Infinity", "abc").forEach {
            assertNull(parsePositiveNumber(it))
        }
    }
    @Test fun weightLossAndTargetAreCalculated() {
        val p = product(1).copy(weighings = listOf(Weighing(750.0, at = 1L)))
        assertEquals(750.0, p.currentWeight, 0.001)
        assertEquals(25.0, p.lossPercent, 0.001)
        assertEquals(700.0, p.targetWeight, 0.001)
    }
    @Test fun archiveDoesNotEraseJournal() {
        val p = product(1).copy(weighings = listOf(Weighing(900.0, at = 1L)))
        assertEquals(p.weighings, p.copy(archivedAt = 2L).weighings)
    }
    @Test fun restoreArchivedKeepsOriginalNumberWhenFree() {
        val archivedProd = product(2, archived = 100L, id = "arch-2")
        val list = listOf(product(1, id = "act-1"), archivedProd, product(3, id = "act-3"))
        val restored = restoreArchivedProduct(list, "arch-2")
        val target = restored.find { it.id == "arch-2" }!!
        assertNull(target.archivedAt)
        assertEquals(2, target.number)
    }
    @Test fun restoreArchivedAssignsFirstFreeNumberWhenOriginalIsTaken() {
        val archivedProd = product(1, archived = 100L, id = "arch-1")
        val list = listOf(product(1, id = "act-1"), product(2, id = "act-2"), archivedProd)
        val restored = restoreArchivedProduct(list, "arch-1")
        val target = restored.find { it.id == "arch-1" }!!
        assertNull(target.archivedAt)
        assertEquals(3, target.number)
    }
    @Test fun restoreArchivedPreservesWeighingHistory() {
        val w = listOf(Weighing(950.0, at = 10L, note = "День 3"), Weighing(880.0, at = 20L, note = "День 7"))
        val archivedProd = product(1, archived = 100L, id = "arch-1").copy(weighings = w)
        val restored = restoreArchivedProduct(listOf(archivedProd), "arch-1")
        val target = restored.single()
        assertNull(target.archivedAt)
        assertEquals(w, target.weighings)
    }
}
