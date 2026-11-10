package ru.fermocontrol.fermocontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        Catalog.items.forEach { item ->
            assertTrue(item.id, item.category in Catalog.categories)
            // null означает «пользователь выбирает сам»
            assertTrue(item.id, item.rawMaterial == null || item.rawMaterial in Catalog.rawMaterials)
            assertTrue(item.id, item.casing == null || item.casing in Catalog.casings)
            assertTrue(item.id, item.targetLossPercent in 1.0..80.0)
        }
    }

    @Test
    fun legacyIdsResolveToCurrentItems() {
        assertEquals("polendvitsa", Catalog.byId("lonza")?.id)
        assertEquals("dried_fish", Catalog.byId("cured_fish")?.id)
        assertNotNull(Catalog.byId("bresaola"))
    }
}