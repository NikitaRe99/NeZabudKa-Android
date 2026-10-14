package ru.nezabudka.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.DialogProperties
import ru.nezabudka.app.application.ActionEffect
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.ui.ActionNotice
import ru.nezabudka.app.ui.AppLayout
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.appShadowElevation
import ru.nezabudka.app.ui.VoiceActionUiState
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class NoteSort(val key: String) {
    MANUAL("manual"), CREATED("created"), UPDATED("updated");
    companion object { fun fromKey(value: String) = entries.firstOrNull { it.key == value } ?: UPDATED }
}
private enum class NoteFolderInterface(val key: String) {
    CAROUSEL("carousel"), DRAWER("drawer");
    companion object { fun fromKey(value: String) = entries.firstOrNull { it.key == value } ?: CAROUSEL }
}
internal enum class NoteSection { ACTIVE, ARCHIVE, TRASH }
private data class NotesDisplayInput(val notes: List<Note>, val sort: NoteSort, val folderId: Long?, val section: NoteSection, val query: String)

@Composable
private fun NoteSelectionTopBar(
    notes: List<Note>,
    close: () -> Unit,
    pin: () -> Unit,
    color: (String?) -> Unit,
    folders: () -> Unit,
    archive: () -> Unit,
    delete: () -> Unit,
) {
    var paletteOpen by remember { mutableStateOf(false) }
    val theme = AppTheme.tokens
    Surface(tonalElevation = 4.dp, shadowElevation = appShadowElevation(4.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = close) { Icon(Icons.Default.Clear, "Завершить выбор") }
            Text(notes.size.toString(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = pin) {
                Icon(Icons.Default.PushPin, if (notes.all { it.pinned }) "Открепить" else "Закрепить")
            }
            Box {
                IconButton(onClick = { paletteOpen = true }) { Icon(Icons.Default.ColorLens, "Выбрать цвет") }
                DropdownMenu(
                    expanded = paletteOpen,
                    onDismissRequest = { paletteOpen = false },
                    modifier = Modifier.width(300.dp),
                ) {
                    Text("Выберите цвет", Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
                    noteColorPaletteRows().forEach { rowColors ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            rowColors.forEach { option ->
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape).background(option.color)
                                        .border(2.dp, theme.colors.outline, CircleShape)
                                        .combinedClickable(onClick = { color(option.value); paletteOpen = false }, onLongClick = {}),
                                )
                            }
                        }
                    }
                }
            }
            IconButton(onClick = folders) { Icon(Icons.Default.Folder, "Папки") }
            val archived = notes.all { it.archived }
            IconButton(onClick = archive) {
                Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, if (archived) "Вернуть из архива" else "В архив")
            }
            IconButton(onClick = delete) { Icon(Icons.Default.DeleteSweep, "Удалить") }
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun NotesScreen(
    folders: List<NoteFolder>, notes: List<Note>, selectedFolderId: Long?,
    ui: VoiceActionUiState, actionNotice: ActionNotice?,
    select: (Long?) -> Unit, beginPushToTalk: () -> Unit, endPushToTalk: () -> Unit, primaryAction: () -> Unit,
    editRecognizedText: (String) -> Unit, undoLastAction: () -> Unit, dismissNotice: () -> Unit,
    noteSort: String, noteColumns: Int, noteFolderInterface: String,
    setNoteSort: (String) -> Unit, setNoteColumns: (Int) -> Unit, setNoteFolderInterface: (String) -> Unit,
    pinNotes: (List<Note>) -> Unit, colorNotes: (List<Note>, String?) -> Unit,
    archiveNotes: (List<Note>) -> Unit, trashNotes: (List<Note>) -> Unit, emptyTrash: () -> Unit,
    setNotesFolder: (List<Note>, String, Boolean) -> Unit,
    createFolder: (String, (NoteFolder) -> Unit) -> Unit,
    createFolderSilently: (String, (NoteFolder) -> Unit) -> Unit,
    renameFolder: (NoteFolder, String) -> Unit, moveFolder: (NoteFolder, Int) -> Unit, deleteFolder: (NoteFolder) -> Unit,
    moveNote: (Long, Long?) -> Unit,
    openNote: (Note) -> Unit, newNote: (Long?) -> Unit,
    reducedUiEffects: Boolean = false,
    isLoading: Boolean = false,
    openTrashSettings: () -> Unit = {},
) {
    val selectedFolder = selectedFolderId
    val columns = noteColumns.coerceIn(1, 2)
    val sort = NoteSort.fromKey(noteSort)
    val folderInterface = NoteFolderInterface.fromKey(noteFolderInterface)
    val theme = AppTheme.tokens
    var section by remember { mutableStateOf(NoteSection.ACTIVE) }
    var query by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var editFolders by remember { mutableStateOf(false) }
    var folderManager by remember { mutableStateOf(false) }
    var folderInterfaceDialog by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectionFolderDialog by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var gridContentReady by remember { mutableStateOf(!reducedUiEffects) }
    LaunchedEffect(reducedUiEffects) {
        if (reducedUiEffects && !gridContentReady) {
            withFrameNanos { }
            withFrameNanos { }
            gridContentReady = true
        }
    }
    var folderStripBounds by remember { mutableStateOf<Rect?>(null) }
    var notesRootOffset by remember { mutableStateOf(Offset.Zero) }
    var notesGridRootOffset by remember { mutableStateOf(Offset.Zero) }
    var carouselSwipeDirection by remember { mutableIntStateOf(0) }
    val manualPinned = remember { mutableStateListOf<Note>() }
    val manualOthers = remember { mutableStateListOf<Note>() }
    var draggingNoteId by remember { mutableStateOf<Long?>(null) }
    var draggingPinned by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragPointerX by remember { mutableFloatStateOf(0f) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var dragTouchOffsetX by remember { mutableFloatStateOf(0f) }
    var dragTouchOffsetY by remember { mutableFloatStateOf(0f) }
    var dragCardWidthPx by remember { mutableIntStateOf(0) }
    var dragInitialGroups by remember { mutableStateOf<Pair<List<Note>, List<Note>>?>(null) }
    var pendingManualOrder by remember { mutableStateOf<List<Long>?>(null) }
    val folderRowState = rememberLazyListState()
    val notesGridState = rememberLazyStaggeredGridState()
    val folderChipWidths = remember { mutableMapOf<Long?, Int>() }
    val drawerFolderListState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val shake = if (editFolders && !reducedUiEffects) {
        rememberInfiniteTransition(label = "folder-shake").animateFloat(
            initialValue = -1.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(360), RepeatMode.Reverse),
            label = "rotation",
        )
    } else {
        null
    }
    val folderFocus = remember { FocusRequester() }
    val folderSpacingPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(folders) {
        if (folders.isEmpty()) editFolders = false
        if (selectedFolder != null && folders.none { it.id == selectedFolder }) select(null)
    }
    LaunchedEffect(section) {
        if (section != NoteSection.ACTIVE) {
            editFolders = false
        }
    }
    LaunchedEffect(section, folderInterface) {
        if (section != NoteSection.ACTIVE || folderInterface != NoteFolderInterface.DRAWER) drawerState.close()
    }
    LaunchedEffect(actionNotice?.id) {
        val created = actionNotice?.effect as? ActionEffect.FolderCreated
        if (created != null) select(created.folder.id)
    }
    val folderIds = remember(folders) { listOf<Long?>(null) + folders.map { it.id } }
    val switchDrawerFolder: (Int) -> Unit = { step ->
        val current = folderIds.indexOf(selectedFolder).coerceAtLeast(0)
        val next = (current + step).coerceIn(0, folderIds.lastIndex)
        select(folderIds[next])
        scope.launch { drawerFolderListState.animateScrollToItem(next) }
    }
    LaunchedEffect(selectedFolder, folderIds) {
        val active = folderIds.indexOf(selectedFolder).coerceAtLeast(0)
        if (carouselSwipeDirection > 0 && active < folderIds.lastIndex) {
            val selectedWidth = folderChipWidths[folderIds[active]]
            val rightWidth = folderChipWidths[folderIds[active + 1]]
            val desiredOffset = if (selectedWidth != null && rightWidth != null) {
                (
                    folderRowState.layoutInfo.viewportEndOffset -
                        selectedWidth - rightWidth - folderSpacingPx
                    ).coerceAtLeast(0)
            } else 0
            folderRowState.animateScrollToItem(active, -desiredOffset)
        } else {
            folderRowState.animateScrollToItem((active - 1).coerceAtLeast(0))
        }
        carouselSwipeDirection = 0
    }
    val displayInput = remember(notes, sort, selectedFolder, section, query) {
        NotesDisplayInput(notes, sort, selectedFolder, section, query)
    }
    val projection by produceState<Pair<NotesDisplayInput, List<Note>>?>(null, displayInput) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            displayInput to filterNotesForDisplay(sortNotesForDisplay(notes, sort), selectedFolder, section, query)
        }
    }
    val displayReady = !isLoading && projection?.first == displayInput
    val visible = if (displayReady) projection!!.second else emptyList()
    val pinned = remember(visible) { visible.filter { it.pinned } }
    val others = remember(visible) { visible.filterNot { it.pinned } }
    val manualSortingEnabled = sort == NoteSort.MANUAL
    val manualPinnedMatches by remember(pinned) { derivedStateOf { sameNoteIds(manualPinned, pinned) } }
    val manualOthersMatch by remember(others) { derivedStateOf { sameNoteIds(manualOthers, others) } }
    val displayedPinned = if (manualSortingEnabled && manualPinnedMatches) manualPinned else pinned
    val displayedOthers = if (manualSortingEnabled && manualOthersMatch) manualOthers else others
    val trashHasNotes = remember(notes) { notes.any { it.trashed } }
    val selectedNotes = remember(visible, selectedNoteIds) { visible.filter { it.id in selectedNoteIds } }
    val selectionMode = selectedNoteIds.isNotEmpty()

    fun updateDraggedNotePosition() {
        val id = draggingNoteId ?: return
        val values = if (draggingPinned) manualPinned else manualOthers
        val currentIndex = values.indexOfFirst { it.id == id }
        if (currentIndex < 0) return
        val targetInfo = notesGridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            val targetId = info.key as? Long
            targetId != null && targetId != id && values.any { it.id == targetId } &&
                dragPointerX >= info.offset.x && dragPointerX <= info.offset.x + info.size.width &&
                dragPointerY >= info.offset.y && dragPointerY <= info.offset.y + info.size.height
        } ?: return
        val targetId = targetInfo.key as? Long ?: return
        val targetIndex = values.indexOfFirst { it.id == targetId }
        if (targetIndex < 0 || targetIndex == currentIndex) return
        val anchorIndex = notesGridState.firstVisibleItemIndex
        val anchorOffset = notesGridState.firstVisibleItemScrollOffset
        values.add(targetIndex, values.removeAt(currentIndex))
        notesGridState.requestScrollToItem(anchorIndex, anchorOffset)
    }

    val startNoteDrag: (Note, Boolean, Float, Float, Float, Float, Int) -> Unit =
        { note, isPinned, touchOffsetX, touchOffsetY, pointerX, pointerY, cardWidthPx ->
            val visibleIdsMatch = sameNoteIds(manualPinned, pinned) && sameNoteIds(manualOthers, others)
            if (pendingManualOrder == null || !visibleIdsMatch) {
                manualPinned.clear()
                manualPinned.addAll(pinned)
                manualOthers.clear()
                manualOthers.addAll(others)
            }
            val values = if (isPinned) manualPinned else manualOthers
            dragInitialGroups = manualPinned.toList() to manualOthers.toList()
            draggingPinned = isPinned
            draggingNoteId = note.id
            dragStartIndex = values.indexOfFirst { it.id == note.id }
            dragTouchOffsetX = touchOffsetX
            dragTouchOffsetY = touchOffsetY
            dragPointerX = pointerX
            dragPointerY = pointerY
            dragCardWidthPx = cardWidthPx
        }
    val stopNoteDrag: () -> Unit = {
        updateDraggedNotePosition()
        val id = draggingNoteId
        val values = if (draggingPinned) manualPinned else manualOthers
        val targetIndex = id?.let { movedId -> values.indexOfFirst { it.id == movedId } } ?: -1
        if (id != null && targetIndex >= 0 && targetIndex != dragStartIndex) {
            pendingManualOrder = (manualPinned + manualOthers).map(Note::id)
            moveNote(
                id,
                noteMoveBeforeId(
                    storedOrder = notes.sortedWith(compareBy<Note> { it.manualOrder }.thenBy { it.id }).map(Note::id),
                    displayedGroupOrder = values.map(Note::id),
                    movedId = id,
                ),
            )
        }
        dragTouchOffsetX = 0f
        dragTouchOffsetY = 0f
        dragPointerX = 0f
        dragPointerY = 0f
        dragCardWidthPx = 0
        dragStartIndex = null
        draggingNoteId = null
        dragInitialGroups = null
    }
    val cancelNoteDrag: () -> Unit = {
        dragInitialGroups?.let { (initialPinned, initialOthers) ->
            manualPinned.clear()
            manualPinned.addAll(initialPinned)
            manualOthers.clear()
            manualOthers.addAll(initialOthers)
        }
        dragTouchOffsetX = 0f
        dragTouchOffsetY = 0f
        dragPointerX = 0f
        dragPointerY = 0f
        dragCardWidthPx = 0
        dragStartIndex = null
        draggingNoteId = null
        dragInitialGroups = null
    }

    LaunchedEffect(pinned, others, manualSortingEnabled, draggingNoteId) {
        if (!manualSortingEnabled) {
            pendingManualOrder = null
            manualPinned.clear()
            manualOthers.clear()
        } else if (draggingNoteId == null) {
            val sourceOrder = (pinned + others).map(Note::id)
            if (pendingManualOrder == null || pendingManualOrder == sourceOrder) {
                manualPinned.clear()
                manualPinned.addAll(pinned)
                manualOthers.clear()
                manualOthers.addAll(others)
                pendingManualOrder = null
            } else if (sameNoteIds(manualPinned, pinned) && sameNoteIds(manualOthers, others)) {
                val currentNotes = (pinned + others).associateBy(Note::id)
                manualPinned.indices.forEach { index ->
                    currentNotes[manualPinned[index].id]?.let { manualPinned[index] = it }
                }
                manualOthers.indices.forEach { index ->
                    currentNotes[manualOthers[index].id]?.let { manualOthers[index] = it }
                }
            }
        }
    }

    LaunchedEffect(selectedFolder, section, query) {
        if (draggingNoteId == null) {
            pendingManualOrder = null
            manualPinned.clear()
            manualPinned.addAll(pinned)
            manualOthers.clear()
            manualOthers.addAll(others)
        }
    }

    val noteAutoScrollEdgePx = with(LocalDensity.current) { 72.dp.toPx() }
    val noteAutoScrollSpeedPx = with(LocalDensity.current) { 760.dp.toPx() }
    LaunchedEffect(draggingNoteId) {
        val id = draggingNoteId ?: return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            previousFrameNanos = frameNanos
            val layoutInfo = notesGridState.layoutInfo
            val values = if (draggingPinned) manualPinned else manualOthers
            val currentIndex = values.indexOfFirst { it.id == id }
            if (currentIndex < 0) continue
            val groupEndItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == values.lastOrNull()?.id }
            val draggingLastVisibleGroup = !draggingPinned || manualOthers.isEmpty()
            val needsBottomClearance = draggingLastVisibleGroup && currentIndex == values.lastIndex &&
                groupEndItem != null && groupEndItem.offset.y + groupEndItem.size.height > layoutInfo.viewportEndOffset
            val speed = when {
                currentIndex > 0 && dragPointerY < noteAutoScrollEdgePx -> -noteAutoScrollSpeedPx *
                    ((noteAutoScrollEdgePx - dragPointerY) / noteAutoScrollEdgePx).coerceIn(0f, 1f)
                (currentIndex < values.lastIndex || needsBottomClearance) &&
                    dragPointerY > layoutInfo.viewportSize.height - noteAutoScrollEdgePx -> noteAutoScrollSpeedPx *
                    ((dragPointerY - (layoutInfo.viewportSize.height - noteAutoScrollEdgePx)) / noteAutoScrollEdgePx)
                        .coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                val consumed = notesGridState.scrollBy(speed * elapsedSeconds)
                if (consumed != 0f) {
                    updateDraggedNotePosition()
                }
            }
        }
    }

    val hapticFeedback = LocalHapticFeedback.current
    val manualNoteReorderModifier = if (manualSortingEnabled && !selectionMode) {
        Modifier.pointerInput(hapticFeedback) {
            var ownsNoteDrag = false
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    ownsNoteDrag = false
                    val item = notesGridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                        offset.x >= info.offset.x && offset.x <= info.offset.x + info.size.width &&
                            offset.y >= info.offset.y && offset.y <= info.offset.y + info.size.height
                    }
                    item?.let { visibleItem ->
                        val id = visibleItem.key as? Long
                        val note = id?.let { noteId -> (manualPinned + manualOthers).firstOrNull { it.id == noteId } }
                        if (note != null) {
                            val isPinned = manualPinned.any { it.id == note.id }
                            startNoteDrag(
                                note,
                                isPinned,
                                offset.x - visibleItem.offset.x,
                                offset.y - visibleItem.offset.y,
                                offset.x,
                                offset.y,
                                visibleItem.size.width,
                            )
                            ownsNoteDrag = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
                onDragEnd = {
                    if (ownsNoteDrag) {
                        stopNoteDrag()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                    ownsNoteDrag = false
                },
                onDragCancel = {
                    if (ownsNoteDrag) cancelNoteDrag()
                    ownsNoteDrag = false
                },
                onDrag = { change, amount ->
                    if (ownsNoteDrag) {
                        change.consume()
                        dragPointerX += amount.x
                        dragPointerY += amount.y
                        updateDraggedNotePosition()
                    }
                },
            )
        }
    } else Modifier

    LaunchedEffect(visible) {
        selectedNoteIds = selectedNoteIds.intersect(visible.mapTo(mutableSetOf()) { it.id })
    }
    BackHandler(selectionMode) { selectedNoteIds = emptySet() }
    BackHandler(section != NoteSection.ACTIVE && !selectionMode) { section = NoteSection.ACTIVE }

    val carouselSwipeModifier = if (folderInterface == NoteFolderInterface.CAROUSEL && section == NoteSection.ACTIVE) {
        Modifier.pointerInput(selectedFolder, folderIds, folderStripBounds, notesGridRootOffset) {
            val swipeThreshold = 80.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val startedOnFolderStrip = folderStripBounds?.contains(down.position + notesGridRootOffset) == true
                var dragX = 0f
                var dragY = 0f
                var verticalScroll = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull { it.id == down.id }?.let { change ->
                        val delta = change.positionChange()
                        dragX += delta.x
                        dragY += delta.y
                    }
                    if (abs(dragY) > viewConfiguration.touchSlop && abs(dragY) > abs(dragX)) {
                        verticalScroll = true
                        break
                    }
                } while (event.changes.any { it.pressed })
                if (!verticalScroll && !startedOnFolderStrip && abs(dragX) >= swipeThreshold && abs(dragX) > abs(dragY)) {
                    val index = folderIds.indexOf(selectedFolder)
                    val next = if (dragX < 0f) {
                        (index + 1).coerceAtMost(folderIds.lastIndex)
                    } else {
                        (index - 1).coerceAtLeast(0)
                    }
                    if (next != index) {
                        carouselSwipeDirection = if (dragX < 0f) 1 else -1
                        select(folderIds[next])
                    }
                }
            }
        }
    } else Modifier

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = folderInterface == NoteFolderInterface.DRAWER && section == NoteSection.ACTIVE && !selectionMode,
            drawerContent = {
                ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
                Text(
                    "Папки",
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                HorizontalDivider()
                    LazyColumn(
                        state = drawerFolderListState,
                        modifier = Modifier.fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item(key = "all-notes", contentType = "folder-destination") {
                            NavigationDrawerItem(
                                label = { Text("Все заметки") },
                                selected = selectedFolder == null,
                                icon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                                onClick = { select(null); scope.launch { drawerState.close() } },
                            )
                        }
                        items(folders, key = { it.id }, contentType = { "folder-destination" }) { folder ->
                            NavigationDrawerItem(
                                label = { Text(folder.name) },
                                selected = selectedFolder == folder.id,
                                icon = { Icon(Icons.Default.Folder, null) },
                                onClick = { select(folder.id); scope.launch { drawerState.close() } },
                            )
                        }
                    }
                }
            },
        ) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .onGloballyPositioned { notesRootOffset = it.positionInRoot() }
            .pointerInput(editFolders, folderStripBounds, notesRootOffset) {
                if (editFolders) awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val rootPosition = Offset(
                        x = down.position.x + notesRootOffset.x,
                        y = down.position.y + notesRootOffset.y,
                    )
                    if (folderStripBounds?.contains(rootPosition) != true) editFolders = false
                }
            },
        topBar = {
            if (selectionMode) NoteSelectionTopBar(
                notes = selectedNotes,
                close = { selectedNoteIds = emptySet() },
                pin = { pinNotes(selectedNotes) },
                color = { color -> colorNotes(selectedNotes, color) },
                folders = { selectionFolderDialog = true },
                archive = { archiveNotes(selectedNotes); selectedNoteIds = emptySet() },
                delete = { trashNotes(selectedNotes); selectedNoteIds = emptySet() },
            )
        },
        bottomBar = { Column(horizontalAlignment = Alignment.End) {
            actionNotice?.let { notice ->
                AppNoticeCard(notice.message, dismissNotice, isError = notice.isError) {
                    val effect = notice.effect
                    if (effect is ActionEffect.NoteCreated) {
                        AppNoticeAction(Icons.Default.Edit, "Редактировать", { dismissNotice(); openNote(effect.note) })
                    }
                    if (effect is ActionEffect.NoteCreated || effect is ActionEffect.FolderCreated || effect is ActionEffect.FolderDeleted) {
                        AppNoticeAction(Icons.AutoMirrored.Filled.Undo, "Отменить", undoLastAction)
                    }
                }
            }
            ui.recordingPreview?.let { RecordingPreviewCard(it) }
            if (ui.awaitingSttConfirmation && ui.recognizedText.isNotBlank()) {
                SttConfirmationCard(ui.recognizedText, editRecognizedText)
            }
            if (draggingNoteId == null) AppVoiceActionBar(
                ui, beginPushToTalk, endPushToTalk, primaryAction, { addMenu = true }, "Добавить заметку или папку",
            )
        } },
    ) { inner ->
        Box(
            Modifier.fillMaxSize()
                .padding(top = inner.calculateTopPadding())
                .padding(horizontal = AppLayout.horizontalPadding)
                .then(manualNoteReorderModifier),
        ) {
        LazyVerticalStaggeredGrid(
            state = notesGridState,
            columns = StaggeredGridCells.Fixed(columns),
            contentPadding = PaddingValues(
                top = AppLayout.contentTopPadding,
                bottom = if (draggingNoteId != null) 200.dp else 110.dp,
            ),
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { notesGridRootOffset = it.positionInRoot() }
                .then(carouselSwipeModifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
        ) {
            if (!selectionMode) item(key = "notes-toolbar", contentType = "toolbar", span = StaggeredGridItemSpan.FullLine) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Поиск заметок и папок",
                        modifier = Modifier.weight(1f),
                        reducedEffects = reducedUiEffects,
                        trailingContent = {
                            IconButton(onClick = { setNoteColumns(if (columns == 1) 2 else 1) }) { Icon(if (columns == 1) Icons.Outlined.GridView else Icons.Outlined.ViewAgenda, "Один или два столбца") }
                            Box {
                                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Сортировка") }
                                AppDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    AppMenuItem(
                                        text = "В моём порядке",
                                        selected = sort == NoteSort.MANUAL,
                                        onClick = { setNoteSort(NoteSort.MANUAL.key); sortMenu = false },
                                    )
                                    AppMenuItem(
                                        text = "По дате создания",
                                        selected = sort == NoteSort.CREATED,
                                        onClick = { setNoteSort(NoteSort.CREATED.key); sortMenu = false },
                                    )
                                    AppMenuItem(
                                        text = "По дате изменения",
                                        selected = sort == NoteSort.UPDATED,
                                        onClick = { setNoteSort(NoteSort.UPDATED.key); sortMenu = false },
                                    )
                                }
                            }
                        },
                    )
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                        AppDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            AppMenuItem(
                                text = "Редактировать папки",
                                icon = Icons.Default.Edit,
                                onClick = { editFolders = false; folderManager = true; moreMenu = false },
                            )
                            AppMenuItem(
                                text = "Интерфейс папок",
                                icon = Icons.Default.FolderOpen,
                                onClick = { folderInterfaceDialog = true; moreMenu = false },
                            )
                            AppMenuItem(
                                text = "Архив",
                                icon = Icons.Default.Archive,
                                onClick = { section = NoteSection.ARCHIVE; moreMenu = false },
                            )
                            AppMenuItem(
                                text = "Корзина",
                                icon = Icons.Default.DeleteSweep,
                                onClick = { section = NoteSection.TRASH; moreMenu = false },
                            )
                            if (section != NoteSection.ACTIVE) {
                                AppMenuItem(
                                    text = "Вернуться к заметкам",
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    onClick = { section = NoteSection.ACTIVE; moreMenu = false },
                                )
                            }
                        }
                    }
                }
            }
            if (gridContentReady) {
            if (section == NoteSection.ACTIVE && folderInterface == NoteFolderInterface.CAROUSEL && folders.isNotEmpty()) item(key = "folder-carousel", contentType = "folder-navigation", span = StaggeredGridItemSpan.FullLine) {
                Surface(
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { folderStripBounds = it.boundsInRoot() },
                    shape = RoundedCornerShape(theme.shapes.panel),
                    color = theme.colors.folderArea,
                ) {
                    LazyRow(
                        state = folderRowState,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(6.dp),
                    ) {
                        item(key = "all-notes", contentType = "folder-chip") {
                            Surface(
                                modifier = Modifier
                                    .onSizeChanged { folderChipWidths[null] = it.width }
                                    .combinedClickable(
                                        onClick = { if (!editFolders) { carouselSwipeDirection = 0; select(null) } },
                                        onLongClick = { editFolders = true },
                                    ),
                                shape = RoundedCornerShape(theme.shapes.noteCard), color = if (selectedFolder == null) theme.colors.selected else Color.Transparent,
                            ) { Text("Все", Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                        }
                        items(folders, key = { it.id }, contentType = { "folder-chip" }) { folder ->
                            val chipModifier = Modifier
                                .onSizeChanged { folderChipWidths[folder.id] = it.width }
                                .graphicsLayer { rotationZ = shake?.value ?: 0f }
                                .combinedClickable(
                                    onClick = { if (!editFolders) { carouselSwipeDirection = 0; select(folder.id) } },
                                    onLongClick = { editFolders = true },
                                )
                            Surface(modifier = chipModifier, shape = RoundedCornerShape(theme.shapes.noteCard), color = if (selectedFolder == folder.id) theme.colors.selected else Color.Transparent) {
                                Row(Modifier.padding(start = 14.dp, end = if (editFolders) 4.dp else 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(folder.name)
                                    if (editFolders) IconButton(onClick = { if (selectedFolder == folder.id) select(null); deleteFolder(folder) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Clear, "Удалить папку") }
                                }
                            }
                        }
                    }
                }
            }
            if (section == NoteSection.ACTIVE && folderInterface == NoteFolderInterface.DRAWER) item(key = "drawer-folder-title", contentType = "folder-navigation", span = StaggeredGridItemSpan.FullLine) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        folders.firstOrNull { it.id == selectedFolder }?.name ?: "Все заметки",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (section != NoteSection.ACTIVE) item(key = "section-${section.name}", contentType = "section-header", span = StaggeredGridItemSpan.FullLine) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { section = NoteSection.ACTIVE }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Вернуться к заметкам")
                    }
                    Text(if (section == NoteSection.ARCHIVE) "Архив" else "Корзина", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    if (section == NoteSection.TRASH) TextButton(
                        onClick = { confirmEmptyTrash = true },
                        enabled = trashHasNotes,
                    ) {
                        Icon(Icons.Default.DeleteForever, null)
                        Text("Очистить корзину", Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (section == NoteSection.TRASH) item(key = "trash-retention-settings", span = StaggeredGridItemSpan.FullLine) {
                TextButton(onClick = openTrashSettings) { Text("Срок хранения в корзине") }
            }
            if (displayedPinned.isNotEmpty()) {
                item(key = "pinned-header", contentType = "section-header", span = StaggeredGridItemSpan.FullLine) { Text("Закреплённые", style = MaterialTheme.typography.labelLarge) }
                staggeredItems(displayedPinned, key = { it.id }, contentType = { "note-card" }) { note -> NoteCard(
                    note = note, selected = note.id in selectedNoteIds, selectionMode = selectionMode,
                    open = openNote, toggleSelection = { selectedNoteIds = selectedNoteIds.toggle(note.id) },
                    modifier = if (draggingNoteId == note.id) {
                        Modifier.graphicsLayer(alpha = 0f)
                    } else Modifier,
                    longClickEnabled = !manualSortingEnabled,
                ) }
                if (displayedOthers.isNotEmpty()) item(key = "others-header", contentType = "section-header", span = StaggeredGridItemSpan.FullLine) { Text("Другие", style = MaterialTheme.typography.labelLarge) }
            }
            staggeredItems(displayedOthers, key = { it.id }, contentType = { "note-card" }) { note -> NoteCard(
                note = note, selected = note.id in selectedNoteIds, selectionMode = selectionMode,
                open = openNote, toggleSelection = { selectedNoteIds = selectedNoteIds.toggle(note.id) },
                modifier = if (draggingNoteId == note.id) {
                    Modifier.graphicsLayer(alpha = 0f)
                } else Modifier,
                longClickEnabled = !manualSortingEnabled,
            ) }
            if (!displayReady) item(key = "notes-loading", span = StaggeredGridItemSpan.FullLine) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            if (displayReady && visible.isEmpty()) item(key = "notes-empty", contentType = "empty-state", span = StaggeredGridItemSpan.FullLine) {
                AppEmptyState(
                    icon = when {
                        section == NoteSection.ARCHIVE -> Icons.Default.Archive
                        section == NoteSection.TRASH -> Icons.Default.DeleteSweep
                        else -> Icons.AutoMirrored.Filled.Notes
                    },
                    title = when {
                        query.isNotBlank() -> "Ничего не найдено"
                        section == NoteSection.ARCHIVE -> "Архив пока пуст"
                        section == NoteSection.TRASH -> "Корзина пуста"
                        selectedFolder != null -> "В этой папке пока нет заметок"
                        else -> "Сохраните первую мысль"
                    },
                    description = when {
                        query.isNotBlank() -> "Попробуйте другое слово или измените запрос."
                        section == NoteSection.ARCHIVE -> "Здесь появятся заметки, которые вы отправите в архив."
                        section == NoteSection.TRASH -> "Здесь появятся удалённые заметки. Их можно восстановить."
                        else -> "Нажмите «+», чтобы создать заметку, или удерживайте микрофон и продиктуйте её."
                    },
                )
            }
            }
        }
        val draggedNote = draggingNoteId?.let { id ->
            (manualPinned + manualOthers).firstOrNull { it.id == id }
        }
        if (draggedNote != null && dragCardWidthPx > 0) {
            val cardWidth = with(LocalDensity.current) { dragCardWidthPx.toDp() }
            NoteCard(
                note = draggedNote,
                selected = false,
                selectionMode = false,
                open = {},
                toggleSelection = {},
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragPointerX - dragTouchOffsetX).roundToInt(),
                            (dragPointerY - dragTouchOffsetY).roundToInt(),
                        )
                    }
                    .width(cardWidth)
                    .zIndex(2f),
                longClickEnabled = false,
            )
        }
        }
    }
    }
        if (drawerState.isOpen) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(320.dp).fillMaxHeight())
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(selectedFolder, folderIds) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val swipeThreshold = 48.dp.toPx()
                                var dragX = 0f
                                var dragY = 0f
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.firstOrNull { it.id == down.id }?.let { change ->
                                        val delta = change.positionChange()
                                        dragX += delta.x
                                        dragY += delta.y
                                        if (abs(dragY) > viewConfiguration.touchSlop && abs(dragY) > abs(dragX)) {
                                            change.consume()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                when {
                                    abs(dragY) >= swipeThreshold && abs(dragY) > abs(dragX) -> {
                                        switchDrawerFolder(if (dragY < 0f) 1 else -1)
                                    }
                                    dragX <= -swipeThreshold && abs(dragX) > abs(dragY) -> {
                                        scope.launch { drawerState.close() }
                                    }
                                }
                            }
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        "Свайп вверх или вниз для переключения папки",
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
    if (folderInterfaceDialog) AppAlertDialog(
        onDismissRequest = { folderInterfaceDialog = false },
        title = { Text("Интерфейс папок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        setNoteFolderInterface(NoteFolderInterface.CAROUSEL.key)
                        folderInterfaceDialog = false
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = folderInterface == NoteFolderInterface.CAROUSEL,
                        onClick = null,
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("Карусель", style = MaterialTheme.typography.titleMedium)
                        Text("Папки над заметками", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        setNoteFolderInterface(NoteFolderInterface.DRAWER.key)
                        folderInterfaceDialog = false
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = folderInterface == NoteFolderInterface.DRAWER,
                        onClick = null,
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("Боковая панель", style = MaterialTheme.typography.titleMedium)
                        Text("Список папок открывается свайпом", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { folderInterfaceDialog = false }) { Text("Отмена") } },
    )
    if (addMenu) AppDialog(
        onDismissRequest = { addMenu = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { addMenu = false },
            ),
        ) {
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .padding(end = AppLayout.horizontalPadding, bottom = 84.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { addMenu = false; folderDialog = true },
                    shape = RoundedCornerShape(theme.shapes.dialog),
                ) { Icon(Icons.Default.Folder, null); Text("Папка", Modifier.padding(start = 10.dp)) }
                Button(
                    onClick = { addMenu = false; newNote(selectedFolder) },
                    shape = RoundedCornerShape(theme.shapes.dialog),
                ) { Icon(Icons.Default.Add, null); Text("Заметка", Modifier.padding(start = 10.dp)) }
                Surface(
                    shape = CircleShape,
                    color = theme.colors.selected,
                    modifier = Modifier.size(58.dp).clickable { addMenu = false },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Clear, "Закрыть") }
                }
            }
        }
    }
    if (confirmEmptyTrash) AppAlertDialog(
        onDismissRequest = { confirmEmptyTrash = false },
        title = { Text("Очистить корзину?") },
        text = { Text("Все заметки в корзине будут удалены без возможности восстановления.") },
        confirmButton = {
            TextButton(onClick = { confirmEmptyTrash = false; emptyTrash() }) { Text("Очистить") }
        },
        dismissButton = { TextButton(onClick = { confirmEmptyTrash = false }) { Text("Отмена") } },
    )
    if (folderManager) FolderManagerScreen(
        folders = folders,
        close = { folderManager = false },
        createFolder = createFolder,
        renameFolder = renameFolder,
        moveFolder = moveFolder,
        deleteFolder = deleteFolder,
    )
    if (selectionFolderDialog && selectedNotes.isNotEmpty()) NoteFolderPickerDialog(
        folders = folders,
        selectedNames = folders.filter { folder -> selectedNotes.all { folder.name in it.folderNames } }.mapTo(mutableSetOf()) { it.name },
        dismiss = { selectionFolderDialog = false },
        toggle = { folder, checked -> setNotesFolder(selectedNotes, folder.name, checked) },
        createFolder = createFolderSilently,
    )
    if (folderDialog) CreationSheet(onDismissRequest = { folderDialog = false }) {
        AppCreationTextField(
            value = folderName,
            onValueChange = { folderName = it },
            placeholder = "Новая папка",
            modifier = Modifier.fillMaxWidth(),
            focusRequester = folderFocus,
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                val requested = folderName
                createFolder(requested) { created -> select(created.id) }
                folderName = ""; folderDialog = false
            }, enabled = folderName.isNotBlank()) { Text("Сохранить") }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            folderFocus.requestFocus()
            keyboardController?.show()
        }
    }
}

internal fun noteMoveBeforeId(
    storedOrder: List<Long>,
    displayedGroupOrder: List<Long>,
    movedId: Long,
): Long? {
    val targetIndex = displayedGroupOrder.indexOf(movedId)
    if (targetIndex < 0) return null
    displayedGroupOrder.getOrNull(targetIndex + 1)?.let { return it }
    val previousId = displayedGroupOrder.getOrNull(targetIndex - 1) ?: return null
    val storedWithoutMoved = storedOrder.filterNot { it == movedId }
    val previousIndex = storedWithoutMoved.indexOf(previousId)
    return if (previousIndex >= 0) storedWithoutMoved.getOrNull(previousIndex + 1) else null
}

internal fun sameNoteIds(first: List<Note>, second: List<Note>): Boolean =
    first.size == second.size && first.mapTo(mutableSetOf(), Note::id) == second.mapTo(mutableSetOf(), Note::id)
