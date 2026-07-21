package de.balabucha.reisepilot

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackingRepositoryDeviceTest {
    private lateinit var context: Context

    @Before
    fun clearPackingStorage() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(PackingRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun cleanUpPackingStorage() {
        context.getSharedPreferences(PackingRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun itemCrudMoveDeleteUndoAndRestart_arePersistent() {
        val repository = PackingRepository(context)
        val categoryId = repository.state.categories.first().id
        val id = repository.addItem(
            name = "Test-Regenjacke",
            quantity = "2",
            categoryId = categoryId,
            person = PackPerson.SOFIA,
            note = "oben griffbereit",
            targetPosition = 1
        )
        repository.toggleItem(id, true)
        val created = repository.state.items.first { it.id == id }
        repository.updateItem(
            created.copy(
                name = "Test-Regenjacken",
                quantity = "3",
                person = PackPerson.EDWARD,
                note = "neuer Hinweis"
            ),
            targetPosition = 0
        )
        repository.moveItem(id, 1)

        val restarted = PackingRepository(context)
        val saved = restarted.state.items.first { it.id == id }
        assertEquals("Test-Regenjacken", saved.name)
        assertEquals("3", saved.quantity)
        assertEquals(PackPerson.EDWARD, saved.person)
        assertEquals("neuer Hinweis", saved.note)
        assertTrue(saved.checked)

        val deleted = restarted.deleteItem(id)
        assertNotNull(deleted)
        assertNull(PackingRepository(context).state.items.firstOrNull { it.id == id })
        restarted.restoreDeletedItem(deleted!!)
        assertNotNull(PackingRepository(context).state.items.firstOrNull { it.id == id })
    }

    @Test
    fun categoriesCanBeCreatedRenamedReorderedHiddenAndDeletedWithMove() {
        val repository = PackingRepository(context)
        val newId = repository.addCategory("Noch besorgen", "vor Abfahrt", 0)
        val category = repository.state.categories.first { it.id == newId }
        assertEquals(0, category.position)
        repository.updateCategory(category.copy(name = "Einkaufen", hidden = true), 2)
        assertTrue(repository.state.categories.first { it.id == newId }.hidden)
        assertEquals("Einkaufen", repository.state.categories.first { it.id == newId }.name)

        val itemId = repository.addItem("Sonnenhut", "1", newId, PackPerson.JULIA, "", 0)
        val destination = repository.state.categories.first { it.id != newId }
        repository.deleteCategoryMovingItems(newId, destination.id)
        assertNull(repository.state.categories.firstOrNull { it.id == newId })
        assertEquals(destination.id, repository.state.items.first { it.id == itemId }.categoryId)
        assertEquals(destination.id, PackingRepository(context).state.items.first { it.id == itemId }.categoryId)
    }

    @Test
    fun resetChecksKeepsPersonalChangesAndRestoreModesBehaveDifferently() {
        val repository = PackingRepository(context)
        val categoryId = repository.addCategory("Persönlich")
        val personalId = repository.addItem("Eigener Punkt", "7", categoryId, null, "bleibt", 0)
        val standardId = repository.state.items.first { it.isStandard }.id
        repository.toggleItem(personalId, true)
        repository.toggleItem(standardId, true)
        repository.deleteItem(standardId)

        repository.resetChecks()
        assertFalse(repository.state.items.first { it.id == personalId }.checked)
        assertNotNull(repository.state.categories.firstOrNull { it.id == categoryId })
        assertNull(repository.state.items.firstOrNull { it.id == standardId })

        repository.restoreMissingDefaults()
        assertNotNull(repository.state.items.firstOrNull { it.id == standardId })
        assertNotNull(repository.state.items.firstOrNull { it.id == personalId })

        repository.resetToDefaults()
        assertEquals(16, repository.state.categories.size)
        assertEquals(268, repository.state.items.size)
        assertNull(repository.state.items.firstOrNull { it.id == personalId })
        assertTrue(repository.state.items.none { it.checked })
    }

    @Test
    fun deletedStandardItemDoesNotReturnOnOrdinaryRestart() {
        val repository = PackingRepository(context)
        val standardId = repository.state.items.first().id
        repository.deleteItem(standardId)
        repeat(3) {
            assertNull(PackingRepository(context).state.items.firstOrNull { item -> item.id == standardId })
        }
    }

    @Test
    fun hundredsOfCustomEntriesRemainUniqueAfterRestart() {
        val repository = PackingRepository(context)
        val categoryId = repository.state.categories.first().id
        val ids = (1..350).map { index ->
            repository.addItem("Eigener Eintrag $index", index.toString(), categoryId, null, "", null)
        }
        assertEquals(ids.size, ids.toSet().size)
        val restarted = PackingRepository(context)
        assertEquals(350, restarted.state.items.count { it.id in ids })
        assertEquals(restarted.state.items.size, restarted.state.items.map { it.id }.toSet().size)
    }
}
