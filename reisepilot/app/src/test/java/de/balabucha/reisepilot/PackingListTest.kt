package de.balabucha.reisepilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackingListTest {
    private val defaults = DefaultPackingList.create()

    @Test
    fun standardList_containsEveryCategoryAndStableUniqueIds() {
        assertEquals(
            listOf(
                "Dokumente und Geld",
                "Andrey",
                "Julia",
                "Edward",
                "Sofia",
                "Unterkunft und Haushalt",
                "Strand und Pool",
                "SUP",
                "Technik",
                "Essen und Getränke",
                "Auto und Fahrt",
                "Körperpflege und persönliche Mittel",
                "Montbéliard",
                "Malibu Village",
                "Paris",
                "Gepäckaufteilung"
            ),
            defaults.categories.sortedBy { it.position }.map { it.name }
        )
        assertEquals(16, defaults.categories.size)
        assertEquals(268, defaults.items.size)
        assertEquals(defaults.categories.size, defaults.categories.map { it.id }.toSet().size)
        assertEquals(defaults.items.size, defaults.items.map { it.id }.toSet().size)
        assertTrue(defaults.categories.all { it.collapsed && it.isStandard })
        assertTrue(defaults.items.all { it.isStandard && it.quantity.isNotBlank() })
    }

    @Test
    fun standardList_respectsExplicitPackingExclusions() {
        val names = defaults.items.map { it.name.lowercase() }
        listOf(
            "drohne",
            "bluetooth-lautsprecher",
            "spielekonsole",
            "laptop",
            "föhn",
            "schwimmweste",
            "eigene bettwäsche",
            "55-liter-kompressor-kühlbox"
        ).forEach { forbidden ->
            assertFalse("Unexpected standard item: $forbidden", names.any { forbidden in it })
        }
    }

    @Test
    fun progressAndFilters_recalculateFromCurrentState() {
        val andreyItems = defaults.items.filter { it.person == PackPerson.ANDREY }
        assertEquals(14, andreyItems.size)
        val changed = defaults.copy(
            items = defaults.items.map { item ->
                if (item.id in andreyItems.take(3).map { it.id }) item.copy(checked = true) else item
            }
        )

        assertEquals(PackingProgress(3, 268), PackingLogic.progress(changed.items))
        assertEquals(PackingProgress(3, 14), PackingLogic.personProgress(changed, PackPerson.ANDREY))
        assertEquals(265, PackingLogic.filteredItems(changed, "", PackingFilter.OPEN, null).size)
        assertEquals(3, PackingLogic.filteredItems(changed, "", PackingFilter.DONE, null).size)
        assertEquals(14, PackingLogic.filteredItems(changed, "", PackingFilter.ANDREY, null).size)
        assertTrue(
            PackingLogic.filteredItems(changed, "kreditkarte", PackingFilter.ALL, null)
                .any { it.name == "Kreditkarte" }
        )
    }

    @Test
    fun normalization_removesDuplicateIdsAndRepairsPositions() {
        val firstCategory = defaults.categories.first()
        val firstItem = defaults.items.first()
        val broken = defaults.copy(
            categories = listOf(
                firstCategory.copy(position = 9),
                firstCategory.copy(name = "duplicate", position = 0)
            ),
            items = listOf(
                firstItem.copy(position = 8),
                firstItem.copy(name = "duplicate", position = 0),
                defaults.items.last().copy(categoryId = "missing-category")
            )
        )
        val repaired = PackingLogic.normalized(broken)
        assertEquals(1, repaired.categories.size)
        assertEquals(1, repaired.items.size)
        assertEquals(0, repaired.categories.single().position)
        assertEquals(0, repaired.items.single().position)
        assertNotEquals("duplicate", repaired.items.single().name)
    }

    @Test
    fun everyDestination_hasMultipleSpecificImageQueries() {
        val places = TravelRegion.entries.flatMap(DestinationCatalog::forRegion)
        assertEquals(93, places.size)
        places.forEach { place ->
            assertTrue("Missing curated media identity for ${place.title}", DestinationMediaCatalog.hasExplicitIdentity(place))
            assertTrue("Empty media identity for ${place.title}", DestinationMediaCatalog.identities(place).all { it.length >= 4 })
            val queries = WikiImageResolver.exactQueriesForTest(place)
            assertTrue("Too few queries for ${place.title}: $queries", queries.size >= 2)
            assertTrue("Original image query missing for ${place.title}", place.imageQuery in queries)
            assertEquals(queries.size, queries.distinct().size)
        }
    }
}
