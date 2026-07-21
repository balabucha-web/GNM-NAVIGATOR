package de.balabucha.reisepilot

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

data class DeletedPackingItem(val item: PackingItem, val formerPosition: Int)

class PackingRepository(context: Context) {
    companion object {
        const val PREFERENCES_NAME = "packing_list_v1"
        const val STATE_KEY = "state_json"
        const val BACKUP_KEY = "state_json_backup"
        const val INITIALIZED_KEY = "initialized"
    }

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var state by mutableStateOf(loadInitialState())
        private set

    private fun loadInitialState(): PackingState {
        if (!preferences.getBoolean(INITIALIZED_KEY, false)) {
            val initial = DefaultPackingList.create()
            preferences.edit()
                .putString(STATE_KEY, PackingStateJson.encode(initial))
                .putBoolean(INITIALIZED_KEY, true)
                .commit()
            return initial
        }

        PackingStateJson.decode(preferences.getString(STATE_KEY, null))?.let { return it }
        PackingStateJson.decode(preferences.getString(BACKUP_KEY, null))?.let { backup ->
            preferences.edit().putString(STATE_KEY, PackingStateJson.encode(backup)).commit()
            return backup
        }

        // Never silently recreate the standard list after the first initialization.
        return PackingState(categories = emptyList(), items = emptyList())
    }

    private fun update(transform: (PackingState) -> PackingState) {
        val oldJson = PackingStateJson.encode(state)
        val next = PackingLogic.normalized(transform(state))
        val nextJson = PackingStateJson.encode(next)
        val saved = preferences.edit()
            .putString(BACKUP_KEY, oldJson)
            .putString(STATE_KEY, nextJson)
            .putBoolean(INITIALIZED_KEY, true)
            .commit()
        if (saved) state = next
    }

    fun toggleItem(itemId: String, checked: Boolean) = update { current ->
        current.copy(items = current.items.map { item ->
            if (item.id == itemId) item.copy(checked = checked) else item
        })
    }

    fun addItem(
        name: String,
        quantity: String,
        categoryId: String,
        person: PackPerson?,
        note: String,
        targetPosition: Int? = null
    ): String {
        val id = "custom-item-${UUID.randomUUID()}"
        update { current ->
            val categoryItems = current.items
                .filter { it.categoryId == categoryId }
                .sortedBy { it.position }
                .toMutableList()
            val position = (targetPosition ?: categoryItems.size).coerceIn(0, categoryItems.size)
            categoryItems.add(
                position,
                PackingItem(
                    id = id,
                    name = name.trim(),
                    quantity = quantity.trim().ifBlank { "1" },
                    categoryId = categoryId,
                    person = person,
                    note = note.trim(),
                    position = position
                )
            )
            replaceCategoryItems(current, categoryId, categoryItems)
        }
        return id
    }

    fun updateItem(item: PackingItem, targetPosition: Int) = update { current ->
        val original = current.items.firstOrNull { it.id == item.id } ?: return@update current
        val without = current.copy(items = current.items.filterNot { it.id == item.id })
        val targetItems = without.items
            .filter { it.categoryId == item.categoryId }
            .sortedBy { it.position }
            .toMutableList()
        val position = targetPosition.coerceIn(0, targetItems.size)
        targetItems.add(
            position,
            item.copy(
                name = item.name.trim(),
                quantity = item.quantity.trim().ifBlank { "1" },
                note = item.note.trim(),
                checked = original.checked,
                position = position
            )
        )
        replaceCategoryItems(without, item.categoryId, targetItems)
    }

    fun moveItem(itemId: String, direction: Int) = update { current ->
        val item = current.items.firstOrNull { it.id == itemId } ?: return@update current
        val ordered = current.items.filter { it.categoryId == item.categoryId }
            .sortedBy { it.position }
            .toMutableList()
        val oldIndex = ordered.indexOfFirst { it.id == itemId }
        val newIndex = (oldIndex + direction).coerceIn(0, ordered.lastIndex)
        if (oldIndex < 0 || oldIndex == newIndex) return@update current
        ordered.add(newIndex, ordered.removeAt(oldIndex))
        replaceCategoryItems(current, item.categoryId, ordered)
    }

    fun deleteItem(itemId: String): DeletedPackingItem? {
        val item = state.items.firstOrNull { it.id == itemId } ?: return null
        val formerPosition = state.items.filter { it.categoryId == item.categoryId }
            .sortedBy { it.position }
            .indexOfFirst { it.id == itemId }
        update { current -> current.copy(items = current.items.filterNot { it.id == itemId }) }
        return DeletedPackingItem(item, formerPosition)
    }

    fun restoreDeletedItem(deleted: DeletedPackingItem) = update { current ->
        if (current.categories.none { it.id == deleted.item.categoryId }) return@update current
        val ordered = current.items.filter { it.categoryId == deleted.item.categoryId }
            .sortedBy { it.position }
            .toMutableList()
        ordered.add(deleted.formerPosition.coerceIn(0, ordered.size), deleted.item)
        replaceCategoryItems(current, deleted.item.categoryId, ordered)
    }

    fun setCategoryCollapsed(categoryId: String, collapsed: Boolean) = update { current ->
        current.copy(categories = current.categories.map { category ->
            if (category.id == categoryId) category.copy(collapsed = collapsed) else category
        })
    }

    fun setCategoryHidden(categoryId: String, hidden: Boolean) = update { current ->
        current.copy(categories = current.categories.map { category ->
            if (category.id == categoryId) category.copy(hidden = hidden) else category
        })
    }

    fun addCategory(
        name: String,
        description: String = "",
        targetPosition: Int? = null
    ): String {
        val id = "custom-category-${UUID.randomUUID()}"
        update { current ->
            val ordered = current.categories.sortedBy { it.position }.toMutableList()
            val position = (targetPosition ?: ordered.size).coerceIn(0, ordered.size)
            ordered.add(
                position,
                PackingCategory(
                    id = id,
                    name = name.trim(),
                    description = description.trim(),
                    position = position,
                    collapsed = false
                )
            )
            current.copy(categories = ordered)
        }
        return id
    }

    fun updateCategory(category: PackingCategory, targetPosition: Int) = update { current ->
        val ordered = current.categories.filterNot { it.id == category.id }
            .sortedBy { it.position }
            .toMutableList()
        val position = targetPosition.coerceIn(0, ordered.size)
        ordered.add(
            position,
            category.copy(
                name = category.name.trim(),
                description = category.description.trim(),
                position = position
            )
        )
        current.copy(categories = ordered)
    }

    fun moveCategory(categoryId: String, direction: Int) = update { current ->
        val ordered = current.categories.sortedBy { it.position }.toMutableList()
        val oldIndex = ordered.indexOfFirst { it.id == categoryId }
        val newIndex = (oldIndex + direction).coerceIn(0, ordered.lastIndex)
        if (oldIndex < 0 || oldIndex == newIndex) return@update current
        ordered.add(newIndex, ordered.removeAt(oldIndex))
        current.copy(categories = ordered)
    }

    fun deleteCategoryAndItems(categoryId: String) = update { current ->
        current.copy(
            categories = current.categories.filterNot { it.id == categoryId },
            items = current.items.filterNot { it.categoryId == categoryId }
        )
    }

    fun deleteCategoryMovingItems(categoryId: String, destinationCategoryId: String) = update { current ->
        if (categoryId == destinationCategoryId) return@update current
        val destinationItems = current.items.filter { it.categoryId == destinationCategoryId }
            .sortedBy { it.position }
        val movedItems = current.items.filter { it.categoryId == categoryId }
            .sortedBy { it.position }
            .mapIndexed { index, item ->
                item.copy(
                    categoryId = destinationCategoryId,
                    position = destinationItems.size + index
                )
            }
        current.copy(
            categories = current.categories.filterNot { it.id == categoryId },
            items = current.items.filterNot { it.categoryId == categoryId } + movedItems
        )
    }

    fun resetChecks() = update { current ->
        current.copy(items = current.items.map { it.copy(checked = false) })
    }

    fun restoreMissingDefaults() = update { current ->
        val defaults = DefaultPackingList.create()
        val currentCategoryIds = current.categories.mapTo(mutableSetOf()) { it.id }
        val restoredCategories = current.categories.toMutableList()
        defaults.categories.forEach { defaultCategory ->
            if (defaultCategory.id !in currentCategoryIds) {
                restoredCategories += defaultCategory.copy(position = restoredCategories.size)
                currentCategoryIds += defaultCategory.id
            }
        }

        val currentItemIds = current.items.mapTo(mutableSetOf()) { it.id }
        val restoredItems = current.items.toMutableList()
        defaults.items.forEach { defaultItem ->
            if (defaultItem.id !in currentItemIds) {
                val nextPosition = restoredItems.count { it.categoryId == defaultItem.categoryId }
                restoredItems += defaultItem.copy(position = nextPosition)
                currentItemIds += defaultItem.id
            }
        }
        current.copy(categories = restoredCategories, items = restoredItems)
    }

    fun resetToDefaults() = update { DefaultPackingList.create() }

    private fun replaceCategoryItems(
        current: PackingState,
        categoryId: String,
        ordered: List<PackingItem>
    ): PackingState {
        val otherItems = current.items.filterNot { it.categoryId == categoryId }
        val positioned = ordered.mapIndexed { index, item ->
            item.copy(categoryId = categoryId, position = index)
        }
        return current.copy(items = otherItems + positioned)
    }
}
