package ru.fermocontrol.fermocontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.fermocontrol.fermocontrol.model.Catalog

class CatalogTest {

    @Test
    fun idsAndNamesAreUnique() {
        assertEquals(Catalog.items.size, Catalog.items.map { it.id }.toSet().size)
        assertEquals(Catalog.items.size, Catalog.items.map { it.name }.toSet().size)
    }

    @Test
    fun itemsUseKnownDictionaries() {
        Catalog.items.forEach {
            assertTrue(it.category in Catalog.categories)
            assertTrue(it.rawMaterial in Catalog.rawMaterials)
            assertTrue(it.casing in Catalog.casings)
            assertTrue(it.targetLossPercent in 1.0..80.0)
        }
    }
}