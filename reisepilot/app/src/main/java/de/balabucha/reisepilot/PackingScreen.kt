package de.balabucha.reisepilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private data class PackingItemEditorRequest(
    val item: PackingItem? = null,
    val categoryId: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingListScreen(
    activity: MainActivity,
    modifier: Modifier,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val repository = remember(activity) { PackingRepository(activity.applicationContext) }
    val state = repository.state
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PackingFilter.ALL) }
    var categoryFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var itemEditor by remember { mutableStateOf<PackingItemEditorRequest?>(null) }
    var categoryEditor by remember { mutableStateOf<PackingCategory?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<PackingItem?>(null) }
    var categoryToDelete by remember { mutableStateOf<PackingCategory?>(null) }
    var categoryToMove by remember { mutableStateOf<PackingCategory?>(null) }
    var showResetChecks by remember { mutableStateOf(false) }
    var showRestoreOptions by remember { mutableStateOf(false) }
    var showFullResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.categories, categoryFilterId) {
        if (categoryFilterId != null && state.categories.none { it.id == categoryFilterId }) {
            categoryFilterId = null
        }
    }

    val filteredItems = remember(state, query, filter, categoryFilterId) {
        PackingLogic.filteredItems(state, query, filter, categoryFilterId)
    }
    val filteredByCategory = remember(filteredItems) { filteredItems.groupBy { it.categoryId } }
    val forceExpanded = query.isNotBlank() || filter != PackingFilter.ALL || categoryFilterId != null
    val shownCategories = remember(state.categories, filteredByCategory, query, filter, categoryFilterId) {
        state.categories
            .sortedBy { it.position }
            .filterNot { it.hidden }
            .filter { category -> categoryFilterId == null || category.id == categoryFilterId }
            .filter { category ->
                val isUnfiltered = query.isBlank() && filter == PackingFilter.ALL && categoryFilterId == null
                isUnfiltered || !filteredByCategory[category.id].isNullOrEmpty()
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("packing-screen"),
        containerColor = Bg,
        topBar = {
            PackingProgressHeader(
                state = state,
                onBack = onBack,
                showMenu = showTopMenu,
                onMenuChange = { showTopMenu = it },
                onManageCategories = {
                    showTopMenu = false
                    showCategoryManager = true
                },
                onResetChecks = {
                    showTopMenu = false
                    showResetChecks = true
                },
                onRestore = {
                    showTopMenu = false
                    showRestoreOptions = true
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val preferred = categoryFilterId
                        ?: shownCategories.firstOrNull()?.id
                        ?: state.categories.minByOrNull { it.position }?.id
                    if (preferred != null) {
                        itemEditor = PackingItemEditorRequest(categoryId = preferred)
                    } else {
                        creatingCategory = true
                    }
                },
                containerColor = Blue,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("packing-new-item")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(if (state.categories.isEmpty()) "Kategorie anlegen" else "Neuer Eintrag")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("packing-list"),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item(key = "search-filter") {
                PackingSearchAndFilters(
                    state = state,
                    query = query,
                    onQueryChange = { query = it },
                    filter = filter,
                    onFilterChange = { filter = it },
                    categoryFilterId = categoryFilterId,
                    onCategoryFilterChange = { categoryFilterId = it },
                    onManageCategories = { showCategoryManager = true }
                )
            }

            if (state.categories.isEmpty()) {
                item(key = "no-categories") {
                    PackingEmptyCard(
                        title = "Noch keine Kategorie",
                        text = "Lege zuerst eine Kategorie an. Danach kannst du beliebig viele Gegenstände hinzufügen.",
                        button = "Kategorie erstellen",
                        onClick = { creatingCategory = true }
                    )
                }
            } else if (shownCategories.isEmpty()) {
                item(key = "no-results") {
                    PackingEmptyCard(
                        title = "Keine passenden Einträge",
                        text = "Suche oder Filter ändern. Ausgeblendete Kategorien findest du unter Kategorien verwalten.",
                        button = "Filter löschen",
                        onClick = {
                            query = ""
                            filter = PackingFilter.ALL
                            categoryFilterId = null
                        }
                    )
                }
            }

            shownCategories.forEach { category ->
                val categoryItems = filteredByCategory[category.id].orEmpty().sortedBy { it.position }
                val expanded = forceExpanded || !category.collapsed
                item(key = "category-header:${category.id}") {
                    PackingCategoryHeader(
                        state = state,
                        category = category,
                        expanded = expanded,
                        onExpandedChange = {
                            repository.setCategoryCollapsed(category.id, expanded)
                        },
                        onMove = { direction -> repository.moveCategory(category.id, direction) },
                        onEdit = { categoryEditor = category },
                        onHide = { repository.setCategoryHidden(category.id, true) },
                        onDelete = { categoryToDelete = category }
                    )
                }

                if (expanded) {
                    if (category.description.isNotBlank()) {
                        item(key = "category-description:${category.id}") {
                            Surface(
                                color = Blue.copy(alpha = .07f),
                                shape = RoundedCornerShape(13.dp),
                                border = BorderStroke(1.dp, Blue.copy(alpha = .16f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    category.description,
                                    color = Muted,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    items(
                        items = categoryItems,
                        key = { item -> "packing-item:${item.id}" }
                    ) { item ->
                        PackingItemRow(
                            item = item,
                            onChecked = { checked -> repository.toggleItem(item.id, checked) },
                            onMove = { direction -> repository.moveItem(item.id, direction) },
                            onEdit = { itemEditor = PackingItemEditorRequest(item = item) },
                            onDelete = { itemToDelete = item }
                        )
                    }

                    if (categoryItems.isEmpty() && (query.isNotBlank() || filter != PackingFilter.ALL)) {
                        item(key = "category-empty-filtered:${category.id}") {
                            Text(
                                "In dieser Kategorie passt derzeit kein Eintrag zum Filter.",
                                color = Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    item(key = "category-add:${category.id}") {
                        OutlinedButton(
                            onClick = { itemEditor = PackingItemEditorRequest(categoryId = category.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("packing-add:${category.id}"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Gegenstand hinzufügen", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    itemEditor?.let { request ->
        PackingItemEditorDialog(
            state = state,
            item = request.item,
            initialCategoryId = request.item?.categoryId ?: request.categoryId,
            onDismiss = { itemEditor = null },
            onSave = { edited, position ->
                if (request.item == null) {
                    repository.addItem(
                        name = edited.name,
                        quantity = edited.quantity,
                        categoryId = edited.categoryId,
                        person = edited.person,
                        note = edited.note,
                        targetPosition = position
                    )
                } else {
                    repository.updateItem(edited, position)
                }
                itemEditor = null
            },
            onDelete = request.item?.let { existing ->
                {
                    itemEditor = null
                    itemToDelete = existing
                }
            }
        )
    }

    if (creatingCategory || categoryEditor != null) {
        PackingCategoryEditorDialog(
            category = categoryEditor,
            categoryCount = state.categories.size,
            onDismiss = {
                creatingCategory = false
                categoryEditor = null
            },
            onSave = { edited, position ->
                if (categoryEditor == null) {
                    repository.addCategory(edited.name, edited.description, position)
                } else {
                    repository.updateCategory(edited, position)
                }
                creatingCategory = false
                categoryEditor = null
            }
        )
    }

    if (showCategoryManager) {
        PackingCategoryManagerDialog(
            state = state,
            onDismiss = { showCategoryManager = false },
            onAdd = {
                showCategoryManager = false
                creatingCategory = true
            },
            onEdit = { category ->
                showCategoryManager = false
                categoryEditor = category
            },
            onVisibilityChange = { category, hidden ->
                repository.setCategoryHidden(category.id, hidden)
            },
            onMove = { category, direction -> repository.moveCategory(category.id, direction) },
            onDelete = { category ->
                showCategoryManager = false
                categoryToDelete = category
            }
        )
    }

    itemToDelete?.let { item ->
        PackingConfirmDialog(
            title = "Eintrag entfernen?",
            text = "Diesen Eintrag wirklich aus der Packliste entfernen?",
            confirmLabel = "Entfernen",
            destructive = true,
            onDismiss = { itemToDelete = null },
            onConfirm = {
                itemToDelete = null
                repository.deleteItem(item.id)?.let { deleted ->
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = "„${item.name}“ wurde entfernt.",
                            actionLabel = "Rückgängig",
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            repository.restoreDeletedItem(deleted)
                        }
                    }
                }
            }
        )
    }

    categoryToDelete?.let { category ->
        PackingDeleteCategoryDialog(
            category = category,
            itemCount = state.items.count { it.categoryId == category.id },
            canMove = state.categories.any { it.id != category.id },
            onDismiss = { categoryToDelete = null },
            onDeleteItems = {
                repository.deleteCategoryAndItems(category.id)
                categoryToDelete = null
                if (categoryFilterId == category.id) categoryFilterId = null
            },
            onMoveItems = {
                categoryToDelete = null
                categoryToMove = category
            }
        )
    }

    categoryToMove?.let { category ->
        PackingMoveCategoryItemsDialog(
            source = category,
            destinations = state.categories.filter { it.id != category.id }.sortedBy { it.position },
            onDismiss = { categoryToMove = null },
            onDestination = { destination ->
                repository.deleteCategoryMovingItems(category.id, destination.id)
                categoryToMove = null
                if (categoryFilterId == category.id) categoryFilterId = destination.id
            }
        )
    }

    if (showResetChecks) {
        PackingConfirmDialog(
            title = "Alle Häkchen zurücksetzen?",
            text = "Nur die Abhakzustände werden gelöscht. Eigene Einträge, Kategorien, Löschungen und Sortierungen bleiben erhalten.",
            confirmLabel = "Häkchen zurücksetzen",
            onDismiss = { showResetChecks = false },
            onConfirm = {
                repository.resetChecks()
                showResetChecks = false
            }
        )
    }

    if (showRestoreOptions) {
        PackingRestoreOptionsDialog(
            onDismiss = { showRestoreOptions = false },
            onRestoreMissing = {
                repository.restoreMissingDefaults()
                showRestoreOptions = false
            },
            onFullReset = {
                showRestoreOptions = false
                showFullResetConfirm = true
            }
        )
    }

    if (showFullResetConfirm) {
        PackingConfirmDialog(
            title = "Packliste vollständig zurücksetzen?",
            text = "Alle eigenen Einträge, Änderungen, Löschungen, Kategorien, Sortierungen und Häkchen werden unwiderruflich durch die ursprüngliche Standard-Packliste ersetzt.",
            confirmLabel = "Vollständig zurücksetzen",
            destructive = true,
            onDismiss = { showFullResetConfirm = false },
            onConfirm = {
                repository.resetToDefaults()
                query = ""
                filter = PackingFilter.ALL
                categoryFilterId = null
                showFullResetConfirm = false
            }
        )
    }
}

@Composable
private fun PackingProgressHeader(
    state: PackingState,
    onBack: () -> Unit,
    showMenu: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onManageCategories: () -> Unit,
    onResetChecks: () -> Unit,
    onRestore: () -> Unit
) {
    val overall = PackingLogic.progress(state.items)
    Surface(color = Color.White, shadowElevation = 5.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 6.dp, top = 4.dp, end = 8.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück zu Mehr", tint = Navy)
                }
                Column(Modifier.weight(1f)) {
                    Text("Packliste", fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text(
                        "${overall.done} von ${overall.total} eingepackt · ${overall.percent} %",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("packing-total-progress")
                    )
                }
                Box {
                    IconButton(
                        onClick = { onMenuChange(true) },
                        modifier = Modifier.size(46.dp).testTag("packing-more-menu")
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Packliste verwalten", tint = Navy)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { onMenuChange(false) }) {
                        DropdownMenuItem(
                            text = { Text("Kategorien verwalten") },
                            onClick = onManageCategories
                        )
                        DropdownMenuItem(
                            text = { Text("Alle Häkchen zurücksetzen") },
                            onClick = onResetChecks
                        )
                        DropdownMenuItem(
                            text = { Text("Standard-Packliste wiederherstellen") },
                            onClick = onRestore
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { overall.fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 8.dp),
                color = Green,
                trackColor = Line
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(PackPerson.entries, key = { it.name }) { person ->
                    val progress = PackingLogic.personProgress(state, person)
                    Surface(
                        color = if (progress.total > 0 && progress.done == progress.total) {
                            Green.copy(alpha = .12f)
                        } else {
                            Bg
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Line)
                    ) {
                        Text(
                            "${person.displayName} ${progress.done}/${progress.total}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (progress.total > 0 && progress.done == progress.total) Green else Navy,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackingSearchAndFilters(
    state: PackingState,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: PackingFilter,
    onFilterChange: (PackingFilter) -> Unit,
    categoryFilterId: String?,
    onCategoryFilterChange: (String?) -> Unit,
    onManageCategories: () -> Unit
) {
    var categoryMenu by remember { mutableStateOf(false) }
    val categories = state.categories.filterNot { it.hidden }.sortedBy { it.position }
    AppCard {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Packliste durchsuchen") },
            placeholder = { Text("z. B. Ladekabel, Paris …") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Suche löschen")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("packing-search")
        )
        Spacer(Modifier.height(9.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(PackingFilter.entries, key = { it.name }) { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.testTag("packing-filter:${option.name}")
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { categoryMenu = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(
                        state.categories.firstOrNull { it.id == categoryFilterId }?.name ?: "Alle Kategorien",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ExpandMore, contentDescription = null)
                }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Alle Kategorien") },
                        onClick = {
                            onCategoryFilterChange(null)
                            categoryMenu = false
                        }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onCategoryFilterChange(category.id)
                                categoryMenu = false
                            }
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = onManageCategories,
                modifier = Modifier.heightIn(min = 48.dp).testTag("packing-manage-categories"),
                shape = RoundedCornerShape(13.dp)
            ) { Text("Kategorien") }
        }
    }
}

@Composable
private fun PackingCategoryHeader(
    state: PackingState,
    category: PackingCategory,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val progress = PackingLogic.categoryProgress(state, category.id)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().testTag("packing-category:${category.id}")
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 7.dp, top = 7.dp, end = 5.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onExpandedChange,
                    modifier = Modifier.size(46.dp).testTag("packing-collapse:${category.id}")
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Kategorie schließen" else "Kategorie öffnen",
                        tint = Blue
                    )
                }
                Column(
                    Modifier.weight(1f).clickable(onClick = onExpandedChange).padding(vertical = 6.dp)
                ) {
                    Text(category.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "${progress.done}/${progress.total} erledigt · ${progress.percent} %",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                PackingDragHandle(
                    testTag = "packing-category-drag:${category.id}",
                    onMove = onMove
                )
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(46.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Kategorie bearbeiten")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = {
                            menu = false
                            onEdit()
                        })
                        DropdownMenuItem(text = { Text("Nach oben") }, onClick = {
                            menu = false
                            onMove(-1)
                        })
                        DropdownMenuItem(text = { Text("Nach unten") }, onClick = {
                            menu = false
                            onMove(1)
                        })
                        DropdownMenuItem(text = { Text("Ausblenden") }, onClick = {
                            menu = false
                            onHide()
                        })
                        DropdownMenuItem(text = { Text("Löschen", color = Red) }, onClick = {
                            menu = false
                            onDelete()
                        })
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress.fraction },
                color = if (progress.total > 0 && progress.done == progress.total) Green else Blue,
                trackColor = Line,
                modifier = Modifier.fillMaxWidth().height(5.dp).padding(horizontal = 9.dp)
            )
        }
    }
}

@Composable
private fun PackingItemRow(
    item: PackingItem,
    onChecked: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (item.checked) Color(0xFFF2F6F4) else Color.White),
        border = BorderStroke(1.dp, if (item.checked) Green.copy(alpha = .22f) else Line),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.checked) .72f else 1f)
            .testTag("packing-row:${item.id}")
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 5.dp, top = 6.dp, end = 3.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = onChecked,
                modifier = Modifier.size(48.dp).testTag("packing-checkbox:${item.id}")
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onChecked(!item.checked) }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(
                    item.name,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                    lineHeight = 19.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    PackingMiniBadge("Menge ${item.quantity}", Blue)
                    item.person?.let { PackingMiniBadge(it.displayName, Green) }
                }
                if (item.note.isNotBlank()) {
                    Text(
                        item.note,
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            PackingDragHandle(testTag = "packing-item-drag:${item.id}", onMove = onMove)
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(46.dp).testTag("packing-item-menu:${item.id}")
                ) { Icon(Icons.Filled.MoreVert, contentDescription = "Eintrag bearbeiten") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = {
                        menu = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text("Nach oben") }, onClick = {
                        menu = false
                        onMove(-1)
                    })
                    DropdownMenuItem(text = { Text("Nach unten") }, onClick = {
                        menu = false
                        onMove(1)
                    })
                    DropdownMenuItem(text = { Text("Löschen", color = Red) }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
    }
}

@Composable
private fun PackingMiniBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = .09f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun PackingDragHandle(testTag: String, onMove: (Int) -> Unit) {
    val threshold = with(LocalDensity.current) { 34.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    Surface(
        color = Bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .size(width = 39.dp, height = 46.dp)
            .testTag(testTag)
            .pointerInput(testTag) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f },
                    onDragEnd = { dragDistance = 0f }
                ) { change, amount ->
                    change.consume()
                    dragDistance += amount.y
                    if (dragDistance >= threshold) {
                        onMove(1)
                        dragDistance = 0f
                    } else if (dragDistance <= -threshold) {
                        onMove(-1)
                        dragDistance = 0f
                    }
                }
            },
        contentColor = Muted
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DragHandle, contentDescription = "Zum Sortieren halten")
        }
    }
}

@Composable
private fun PackingEmptyCard(title: String, text: String, button: String, onClick: () -> Unit) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Text(button)
        }
    }
}

@Composable
private fun PackingItemEditorDialog(
    state: PackingState,
    item: PackingItem?,
    initialCategoryId: String?,
    onDismiss: () -> Unit,
    onSave: (PackingItem, Int) -> Unit,
    onDelete: (() -> Unit)?
) {
    val categories = state.categories.sortedBy { it.position }
    var name by remember(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var quantity by remember(item?.id) { mutableStateOf(item?.quantity ?: "1") }
    var categoryId by remember(item?.id, initialCategoryId) {
        mutableStateOf(item?.categoryId ?: initialCategoryId ?: categories.firstOrNull()?.id.orEmpty())
    }
    var person by remember(item?.id) { mutableStateOf(item?.person) }
    var note by remember(item?.id) { mutableStateOf(item?.note.orEmpty()) }
    val initialPosition = item?.position ?: state.items.count { it.categoryId == categoryId }
    var positionText by remember(item?.id) { mutableStateOf((initialPosition + 1).toString()) }
    var categoryMenu by remember { mutableStateOf(false) }
    var personMenu by remember { mutableStateOf(false) }
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val targetCount = state.items.count { it.categoryId == categoryId && it.id != item?.id }
    val valid = name.isNotBlank() && selectedCategory != null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(14.dp).heightIn(max = 760.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(
                    if (item == null) "Neuer Eintrag" else "Eintrag bearbeiten",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Bezeichnung, Menge, Kategorie und Zuordnung sind jederzeit änderbar.",
                    color = Muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Bezeichnung") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("packing-editor-name")
                    )
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Menge") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("packing-editor-quantity")
                    )
                    Spacer(Modifier.height(9.dp))
                    Text("Kategorie", color = Muted, fontSize = 12.sp)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryMenu = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("packing-editor-category"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(selectedCategory?.name ?: "Kategorie wählen", modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { category ->
                                DropdownMenuItem(text = { Text(category.name) }, onClick = {
                                    categoryId = category.id
                                    positionText = (state.items.count {
                                        it.categoryId == category.id && it.id != item?.id
                                    } + 1).toString()
                                    categoryMenu = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("Person · optional", color = Muted, fontSize = 12.sp)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { personMenu = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("packing-editor-person"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(person?.displayName ?: "Keine Zuordnung", modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(expanded = personMenu, onDismissRequest = { personMenu = false }) {
                            DropdownMenuItem(text = { Text("Keine Zuordnung") }, onClick = {
                                person = null
                                personMenu = false
                            })
                            PackPerson.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(option.displayName) }, onClick = {
                                    person = option
                                    personMenu = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Hinweis · optional") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("packing-editor-note")
                    )
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = positionText,
                        onValueChange = { positionText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Position in der Kategorie") },
                        supportingText = { Text("1 bis ${targetCount + 1}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("packing-editor-position")
                    )
                    if (onDelete != null) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                            border = BorderStroke(1.dp, Red.copy(alpha = .4f)),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Eintrag löschen") }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Abbrechen") }
                    Button(
                        onClick = {
                            val base = item ?: PackingItem(
                                id = "pending",
                                name = "",
                                categoryId = categoryId,
                                position = 0
                            )
                            onSave(
                                base.copy(
                                    name = name.trim(),
                                    quantity = quantity.trim().ifBlank { "1" },
                                    categoryId = categoryId,
                                    person = person,
                                    note = note.trim()
                                ),
                                ((positionText.toIntOrNull() ?: targetCount + 1) - 1)
                                    .coerceIn(0, targetCount)
                            )
                        },
                        enabled = valid,
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("packing-editor-save"),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Speichern") }
                }
            }
        }
    }
}

@Composable
private fun PackingCategoryEditorDialog(
    category: PackingCategory?,
    categoryCount: Int,
    onDismiss: () -> Unit,
    onSave: (PackingCategory, Int) -> Unit
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var description by remember(category?.id) { mutableStateOf(category?.description.orEmpty()) }
    var positionText by remember(category?.id) {
        mutableStateOf(((category?.position ?: categoryCount) + 1).toString())
    }
    var hidden by remember(category?.id) { mutableStateOf(category?.hidden ?: false) }
    val maxPosition = if (category == null) categoryCount + 1 else categoryCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Kategorie erstellen" else "Kategorie bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("packing-category-editor-name")
                )
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschreibung · optional") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = positionText,
                    onValueChange = { positionText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Position") },
                    supportingText = { Text("1 bis $maxPosition") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Kategorie ausblenden", fontWeight = FontWeight.Bold)
                        Text("Bleibt in der Verwaltung erhalten", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = hidden, onCheckedChange = { hidden = it })
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        confirmButton = {
            Button(
                onClick = {
                    val base = category ?: PackingCategory(
                        id = "pending",
                        name = "",
                        position = categoryCount,
                        collapsed = false
                    )
                    onSave(
                        base.copy(name = name.trim(), description = description.trim(), hidden = hidden),
                        ((positionText.toIntOrNull() ?: maxPosition) - 1).coerceIn(0, maxPosition - 1)
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("packing-category-editor-save")
            ) { Text("Speichern") }
        }
    )
}

@Composable
private fun PackingCategoryManagerDialog(
    state: PackingState,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PackingCategory) -> Unit,
    onVisibilityChange: (PackingCategory, Boolean) -> Unit,
    onMove: (PackingCategory, Int) -> Unit,
    onDelete: (PackingCategory) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Bg,
            modifier = Modifier.fillMaxSize().padding(10.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Kategorien verwalten", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Schließen")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).testTag("packing-category-manager"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.categories.sortedBy { it.position }, key = { it.id }) { category ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Line),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(1f).clickable { onEdit(category) }.padding(vertical = 8.dp)
                                ) {
                                    Text(category.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${state.items.count { it.categoryId == category.id }} Einträge · Position ${category.position + 1}",
                                        color = Muted,
                                        fontSize = 12.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (category.hidden) "aus" else "an", color = Muted, fontSize = 9.sp)
                                    Switch(
                                        checked = !category.hidden,
                                        onCheckedChange = { visible -> onVisibilityChange(category, !visible) },
                                        modifier = Modifier.height(34.dp)
                                    )
                                }
                                PackingDragHandle(
                                    testTag = "packing-manager-drag:${category.id}",
                                    onMove = { direction -> onMove(category, direction) }
                                )
                                var menu by remember(category.id) { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menu = true }, modifier = Modifier.size(45.dp)) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "Kategorie bearbeiten")
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = {
                                            menu = false
                                            onEdit(category)
                                        })
                                        DropdownMenuItem(text = { Text("Nach oben") }, onClick = {
                                            menu = false
                                            onMove(category, -1)
                                        })
                                        DropdownMenuItem(text = { Text("Nach unten") }, onClick = {
                                            menu = false
                                            onMove(category, 1)
                                        })
                                        DropdownMenuItem(text = { Text("Löschen", color = Red) }, onClick = {
                                            menu = false
                                            onDelete(category)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("packing-new-category"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Eigene Kategorie erstellen")
                }
            }
        }
    }
}

@Composable
private fun PackingConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = Red) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel) }
        }
    )
}

@Composable
private fun PackingDeleteCategoryDialog(
    category: PackingCategory,
    itemCount: Int,
    canMove: Boolean,
    onDismiss: () -> Unit,
    onDeleteItems: () -> Unit,
    onMoveItems: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("„${category.name}“ löschen?") },
        text = {
            Text(
                if (itemCount == 0) {
                    "Die Kategorie enthält keine Gegenstände."
                } else {
                    "Die Kategorie enthält $itemCount Gegenstände. Was soll damit passieren?"
                }
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (itemCount > 0 && canMove) {
                    TextButton(onClick = onMoveItems) { Text("Gegenstände verschieben") }
                }
                Button(
                    onClick = onDeleteItems,
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) {
                    Text(if (itemCount == 0) "Kategorie löschen" else "Gegenstände ebenfalls löschen")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun PackingMoveCategoryItemsDialog(
    source: PackingCategory,
    destinations: List<PackingCategory>,
    onDismiss: () -> Unit,
    onDestination: (PackingCategory) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gegenstände verschieben") },
        text = {
            Column {
                Text("Wähle die Zielkategorie für alle Gegenstände aus „${source.name}“.")
                Spacer(Modifier.height(9.dp))
                Column(
                    Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    destinations.forEach { destination ->
                        OutlinedButton(
                            onClick = { onDestination(destination) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(destination.name) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun PackingRestoreOptionsDialog(
    onDismiss: () -> Unit,
    onRestoreMissing: () -> Unit,
    onFullReset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Standard-Packliste wiederherstellen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Wähle, wie die Standardliste wiederhergestellt werden soll.")
                OutlinedButton(
                    onClick = onRestoreMissing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text("Fehlende Standardeinträge ergänzen\nEigene Einträge behalten")
                }
                OutlinedButton(
                    onClick = onFullReset,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                    border = BorderStroke(1.dp, Red.copy(alpha = .4f)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text("Vollständig auf den ursprünglichen Zustand zurücksetzen")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
