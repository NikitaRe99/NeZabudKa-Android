package ru.nezabudka.app.ui.screens

import kotlin.math.abs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ru.nezabudka.app.application.ActionEffect
import ru.nezabudka.app.domain.CustomReminderRecurrence
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.domain.ReminderRecurrence
import ru.nezabudka.app.domain.ReminderRecurrenceUnit
import ru.nezabudka.app.ui.ActionNotice
import ru.nezabudka.app.ui.AppLayout
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.appShadowElevation
import ru.nezabudka.app.ui.VoiceActionUiState
import ru.nezabudka.app.ui.ReminderSort
import ru.nezabudka.app.ui.isOverdueAt
import ru.nezabudka.app.ui.overdueReminderText
import ru.nezabudka.app.ui.nextReminderUiRefreshDelayMillis
import ru.nezabudka.app.ui.reminderRecurrenceLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun RemindersScreen(
    reminders: List<Reminder>,
    ui: VoiceActionUiState,
    actionNotice: ActionNotice?,
    popupDurationSeconds: Int,
    reducedUiEffects: Boolean,
    sort: ReminderSort,
    setSort: (ReminderSort) -> Unit,
    delete: (Long) -> Unit,
    move: (Long, Long?) -> Unit,
    restore: (Reminder) -> Unit,
    favorite: (Reminder) -> Unit,
    complete: (Reminder) -> Unit,
    undoCompleted: (Long) -> Unit,
    deleteOverdue: () -> Unit,
    deleteCompleted: () -> Unit,
    create: (String, LocalDateTime?, String?, Boolean, () -> Unit) -> Unit,
    beginPushToTalk: () -> Unit,
    endPushToTalk: () -> Unit,
    primaryAction: () -> Unit,
    editRecognizedText: (String) -> Unit,
    openReminder: (Reminder) -> Unit,
    changeReminderTime: (Reminder, LocalDateTime?, String?) -> Unit,
    undoLastAction: () -> Unit,
    dismissNotice: () -> Unit,
    answerReminderDateClarification: (String) -> Unit,
    requestCreate: Boolean = false,
    consumeCreateRequest: () -> Unit = {},
    openSettings: (() -> Unit)? = null,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val sectionContext = androidx.compose.ui.platform.LocalContext.current
    val sectionPreferences = remember(sectionContext) {
        sectionContext.getSharedPreferences("reminder_sections", android.content.Context.MODE_PRIVATE)
    }
    var overdueExpanded by remember { mutableStateOf(sectionPreferences.getBoolean("overdue_expanded", true)) }
    var completedExpanded by remember { mutableStateOf(sectionPreferences.getBoolean("completed_expanded", true)) }
    var showCreator by remember { mutableStateOf(false) }
    LaunchedEffect(requestCreate) {
        if (requestCreate) { showCreator = true; consumeCreateRequest() }
    }
    var noticeTimeReminder by remember { mutableStateOf<Reminder?>(null) }
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    val manualOverdue = remember { mutableStateListOf<Reminder>() }
    val manualActive = remember { mutableStateListOf<Reminder>() }
    var draggingReminderId by remember { mutableStateOf<Long?>(null) }
    var draggingOverdue by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragTouchOffsetY by remember { mutableFloatStateOf(0f) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var dragInitialGroups by remember { mutableStateOf<Pair<List<Reminder>, List<Reminder>>?>(null) }
    var pendingManualOrder by remember { mutableStateOf<List<Long>?>(null) }
    val dismissed = remember { mutableStateListOf<Long>() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var listContentReady by remember { mutableStateOf(!reducedUiEffects) }
    LaunchedEffect(reducedUiEffects) {
        if (reducedUiEffects && !listContentReady) {
            withFrameNanos { }
            withFrameNanos { }
            listContentReady = true
        }
    }
    val theme = AppTheme.tokens
    val density = LocalDensity.current
    val reminderRowStridePx = with(density) { 96.dp.toPx() }
    val reminderItemSpacingPx = with(density) { 8.dp.toPx() }
    val autoScrollTopEdgePx = with(density) { 60.dp.toPx() }
    val autoScrollBottomEdgePx = with(density) { 100.dp.toPx() }
    val dragEdgeRevealPx = with(density) { 64.dp.toPx() }
    val maxAutoScrollPxPerSecond = with(density) { 900.dp.toPx() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val hasTimedActiveReminders = remember(reminders) {
        reminders.any { it.status != "completed" && it.remindAt != null }
    }
    LaunchedEffect(lifecycle, hasTimedActiveReminders, reminders) {
        if (hasTimedActiveReminders) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val resumedAt = LocalDateTime.now()
                if (resumedAt.isAfter(currentTime.plusSeconds(1))) currentTime = resumedAt
                while (true) {
                    delay(nextReminderUiRefreshDelayMillis(reminders, currentTime))
                    if (listState.isScrollInProgress) {
                        snapshotFlow { listState.isScrollInProgress }.first { scrolling -> !scrolling }
                    }
                    currentTime = LocalDateTime.now()
                }
            }
        }
    }
    val dismissedSnapshot by remember { derivedStateOf { dismissed.toList() } }
    val currentDelete = rememberUpdatedState(delete)
    val currentRestore = rememberUpdatedState(restore)
    val swipeDelete: (Reminder) -> Unit = remember(snackbar, scope, popupDurationSeconds) {
        { reminder ->
            if (reminder.id !in dismissed) {
                dismissed += reminder.id
                currentDelete.value(reminder.id)
                scope.launch {
                    val timeout = if (popupDurationSeconds > 0) scope.launch {
                        delay(popupDurationSeconds * 1_000L)
                        snackbar.currentSnackbarData?.dismiss()
                    } else null
                    val result = snackbar.showSnackbar(
                        message = "Напоминание удалено",
                        actionLabel = "Отменить",
                        duration = SnackbarDuration.Indefinite,
                        withDismissAction = true,
                    )
                    timeout?.cancel()
                    if (result == SnackbarResult.ActionPerformed) currentRestore.value(reminder)
                    dismissed.remove(reminder.id)
                }
            }
        }
    }
    val currentComplete = rememberUpdatedState(complete)
    val currentUndoCompleted = rememberUpdatedState(undoCompleted)
    val completionFeedback = LocalHapticFeedback.current
    val completeWithUndo: (Reminder) -> Unit = remember(snackbar, scope, completionFeedback) {

        { reminder ->
            completionFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            currentComplete.value(reminder)
            if (reminder.status != "completed") {
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = "Напоминание выполнено",
                        actionLabel = "Отменить",
                        duration = SnackbarDuration.Long,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) currentUndoCompleted.value(reminder.id)
                }
            }
        }
    }
    val currentFavorite = rememberUpdatedState(favorite)
    val stableFavorite: (Reminder) -> Unit = remember { { reminder -> currentFavorite.value(reminder) } }
    val currentOpenReminder = rememberUpdatedState(openReminder)
    val stableOpenReminder: (Reminder) -> Unit = remember { { reminder -> currentOpenReminder.value(reminder) } }
    val comparator = remember(sort) { when (sort) {
        ReminderSort.MY_ORDER -> compareBy<Reminder> { it.manualOrder }
        ReminderSort.DATE -> compareBy<Reminder> { it.remindAt == null }.thenBy { it.remindAt }
        ReminderSort.RECENTLY_MARKED -> compareByDescending<Reminder> { it.favorite }.thenBy { it.manualOrder }
        ReminderSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.text }
    } }
    val allActive = remember(reminders, comparator, dismissedSnapshot) {
        reminders.filter { it.status != "completed" && it.id !in dismissedSnapshot }.sortedWith(comparator)
    }
    val allCompleted = remember(reminders, dismissedSnapshot) {
        reminders.filter { it.status == "completed" && it.id !in dismissedSnapshot }.sortedByDescending { it.completedAt }
    }
    val filteredActive = remember(allActive, query) {
        allActive.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }
    }
    val activeByDeadline = remember(filteredActive, currentTime) {
        filteredActive.partition { it.isOverdueAt(currentTime) }
    }
    val overdue = activeByDeadline.first
    val active = activeByDeadline.second
    val activeDateGroups = remember(active, currentTime.toLocalDate()) {
        val today = currentTime.toLocalDate()
        active.groupBy { it.remindAt?.toLocalDate() }
            .map { (date, values) -> Triple(date, dateSectionTitle(date, today), values) }
    }
    val favoriteGroups = remember(active) { active.partition(Reminder::favorite) }
    val completed = remember(allCompleted, query) {
        allCompleted.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }
    }
    val manualSortingEnabled = sort == ReminderSort.MY_ORDER && query.isBlank()
    val displayedOverdue = if (manualSortingEnabled && manualOverdue.isNotEmpty()) manualOverdue else overdue
    val displayedActive = if (manualSortingEnabled && manualActive.isNotEmpty()) manualActive else active
    val draggedValues = if (draggingOverdue) manualOverdue else manualActive
    val draggedIndex = draggingReminderId?.let { id -> draggedValues.indexOfFirst { it.id == id } } ?: -1
    val showDragStartSpace = draggedIndex == 0 && dragStartIndex != null && dragStartIndex != 0
    val showDragEndSpace = draggedIndex == draggedValues.lastIndex && dragStartIndex != null &&
        dragStartIndex != draggedValues.lastIndex

    fun updateDraggedReminderPosition() {
        val id = draggingReminderId ?: return
        val values = if (draggingOverdue) manualOverdue else manualActive
        val currentIndex = values.indexOfFirst { it.id == id }
        if (currentIndex < 0) return
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.firstOrNull { it.key == id }

        if (currentIndex < values.lastIndex) {
            val nextItem = visibleItems.firstOrNull { it.key == values[currentIndex + 1].id }
            val centerDistance = if (currentItem != null && nextItem != null) {
                (nextItem.offset + nextItem.size / 2f) - (currentItem.offset + currentItem.size / 2f)
            } else reminderRowStridePx
            if (dragOffset > centerDistance / 2f) {
                val placementShift = nextItem?.let { it.size + reminderItemSpacingPx } ?: reminderRowStridePx
                val anchorIndex = listState.firstVisibleItemIndex
                val anchorOffset = listState.firstVisibleItemScrollOffset
                values.add(currentIndex + 1, values.removeAt(currentIndex))
                listState.requestScrollToItem(anchorIndex, anchorOffset)
                dragOffset -= placementShift
                return
            }
        }
        if (currentIndex > 0) {
            val previousItem = visibleItems.firstOrNull { it.key == values[currentIndex - 1].id }
            val centerDistance = if (currentItem != null && previousItem != null) {
                (currentItem.offset + currentItem.size / 2f) - (previousItem.offset + previousItem.size / 2f)
            } else reminderRowStridePx
            if (dragOffset < -centerDistance / 2f) {
                val placementShift = previousItem?.let { it.size + reminderItemSpacingPx } ?: reminderRowStridePx
                val anchorIndex = listState.firstVisibleItemIndex
                val anchorOffset = listState.firstVisibleItemScrollOffset
                values.add(currentIndex - 1, values.removeAt(currentIndex))
                listState.requestScrollToItem(anchorIndex, anchorOffset)
                dragOffset += placementShift
            }
        }
    }

    val startReminderDrag: (Reminder, Boolean, Float, Float) -> Unit = { reminder, isOverdue, touchOffsetY, pointerY ->
        if (pendingManualOrder == null) {
            manualOverdue.clear()
            manualOverdue.addAll(overdue)
            manualActive.clear()
            manualActive.addAll(active)
        }
        val values = if (isOverdue) manualOverdue else manualActive
        dragInitialGroups = manualOverdue.toList() to manualActive.toList()
        draggingOverdue = isOverdue
        draggingReminderId = reminder.id
        dragStartIndex = values.indexOfFirst { it.id == reminder.id }
        dragOffset = 0f
        dragTouchOffsetY = touchOffsetY
        dragPointerY = pointerY
    }
    val stopReminderDrag: () -> Unit = {
        updateDraggedReminderPosition()
        val id = draggingReminderId
        val values = if (draggingOverdue) manualOverdue else manualActive
        val targetIndex = id?.let { draggedId -> values.indexOfFirst { it.id == draggedId } } ?: -1
        if (id != null && targetIndex >= 0 && targetIndex != dragStartIndex) {
            val combined = manualOverdue + manualActive
            val beforeId = reminderMoveBeforeId(
                storedOrder = allActive.map(Reminder::id),
                displayedGroupOrder = values.map(Reminder::id),
                movedId = id,
            )
            pendingManualOrder = combined.map(Reminder::id)
            move(id, beforeId)
        }
        dragOffset = 0f
        dragTouchOffsetY = 0f
        dragPointerY = 0f
        dragStartIndex = null
        draggingReminderId = null
        dragInitialGroups = null
    }
    val cancelReminderDrag: () -> Unit = {
        dragInitialGroups?.let { (initialOverdue, initialActive) ->
            manualOverdue.clear()
            manualOverdue.addAll(initialOverdue)
            manualActive.clear()
            manualActive.addAll(initialActive)
        }
        dragOffset = 0f
        dragTouchOffsetY = 0f
        dragPointerY = 0f
        dragStartIndex = null
        draggingReminderId = null
        dragInitialGroups = null
    }

    LaunchedEffect(overdue, active, manualSortingEnabled, draggingReminderId) {
        if (!manualSortingEnabled) {
            pendingManualOrder = null
            manualOverdue.clear()
            manualActive.clear()
        } else if (draggingReminderId == null && pendingManualOrder != null) {
            val sourceOrder = (overdue + active).map(Reminder::id)
            if (pendingManualOrder == sourceOrder) {
                manualOverdue.clear()
                manualActive.clear()
                pendingManualOrder = null
            }
        }
    }

    LaunchedEffect(draggingReminderId) {
        val id = draggingReminderId ?: return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            previousFrameNanos = frameNanos
            val layoutInfo = listState.layoutInfo
            layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.let { draggedItem ->
                dragOffset = dragPointerY - dragTouchOffsetY - draggedItem.offset
            }
            val values = if (draggingOverdue) manualOverdue else manualActive
            val currentIndex = values.indexOfFirst { it.id == id }
            if (currentIndex < 0) continue
            val groupStartItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == values.firstOrNull()?.id }
            val canRevealStart = dragStartIndex != null && dragStartIndex != 0
            val needsStartReveal = canRevealStart && currentIndex == 0 && groupStartItem != null &&
                groupStartItem.offset < dragEdgeRevealPx
            val canMoveUp = currentIndex > 0 || needsStartReveal
            val groupEndItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == values.lastOrNull()?.id }
            val canRevealEnd = dragStartIndex != null && dragStartIndex != values.lastIndex
            val needsEndReveal = canRevealEnd && currentIndex == values.lastIndex && groupEndItem != null &&
                groupEndItem.offset + groupEndItem.size > layoutInfo.viewportEndOffset - dragEdgeRevealPx
            val canMoveDown = currentIndex < values.lastIndex || needsEndReveal
            val distanceFromTop = dragPointerY
            val distanceFromBottom = layoutInfo.viewportSize.height - dragPointerY
            val speed = when {
                canMoveUp && listState.canScrollBackward && distanceFromTop < autoScrollTopEdgePx -> -maxAutoScrollPxPerSecond *
                    ((autoScrollTopEdgePx - distanceFromTop) / autoScrollTopEdgePx).coerceIn(0f, 1f)
                canMoveDown && listState.canScrollForward && distanceFromBottom < autoScrollBottomEdgePx -> maxAutoScrollPxPerSecond *
                    ((autoScrollBottomEdgePx - distanceFromBottom) / autoScrollBottomEdgePx).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                val consumed = listState.scrollBy(speed * elapsedSeconds)
                if (consumed != 0f) {
                    dragOffset += consumed
                    updateDraggedReminderPosition()
                }
            }
        }
    }
    val latestOverdue by rememberUpdatedState(overdue)
    val latestActive by rememberUpdatedState(active)
    val latestStartReminderDrag by rememberUpdatedState(startReminderDrag)
    val latestStopReminderDrag by rememberUpdatedState(stopReminderDrag)
    val latestCancelReminderDrag by rememberUpdatedState(cancelReminderDrag)
    val hapticFeedback = LocalHapticFeedback.current
    val manualReorderModifier = if (manualSortingEnabled) {
        Modifier.pointerInput(hapticFeedback) {
            var ownsReminderDrag = false
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    ownsReminderDrag = false
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                        offset.y >= info.offset && offset.y <= info.offset + info.size
                    }
                    item?.let { visibleItem ->
                        val id = visibleItem.key as? Long
                        val reminder = id?.let { reminderId ->
                            (manualOverdue + manualActive).firstOrNull { it.id == reminderId }
                                ?: (latestOverdue + latestActive).firstOrNull { it.id == reminderId }
                        }
                        if (reminder != null) {
                            val isOverdue = (manualOverdue.ifEmpty { latestOverdue }).any { it.id == reminder.id }
                            latestStartReminderDrag(
                                reminder,
                                isOverdue,
                                offset.y - visibleItem.offset,
                                offset.y,
                            )
                            ownsReminderDrag = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
                onDragEnd = {
                    if (ownsReminderDrag) {
                        latestStopReminderDrag()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                    ownsReminderDrag = false
                },
                onDragCancel = {
                    if (ownsReminderDrag) latestCancelReminderDrag()
                    ownsReminderDrag = false
                },
                onDrag = { change, amount ->
                    if (ownsReminderDrag) {
                        change.consume()
                        dragPointerY += amount.y
                        dragOffset += amount.y
                        updateDraggedReminderPosition()
                    }
                },
            )
        }
    } else Modifier
    val listRequest = actionNotice?.effect as? ActionEffect.RemindersListed
    LaunchedEffect(actionNotice?.id, sort, query, allActive) {
        if (listRequest != null) {
            if (query.isNotBlank()) {
                query = ""
            } else if (sort != ReminderSort.DATE) {
                setSort(ReminderSort.DATE)
            } else {
                listState.animateScrollToItem(reminderDateScrollIndex(allActive, listRequest.date, currentTime))
                dismissNotice()
            }
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { AppSnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.imePadding()) {
                val notice = actionNotice
                val reminderEffect = notice?.effect as? ActionEffect.ReminderCreated
                val reminderDeleted = notice?.effect as? ActionEffect.ReminderDeleted
                val dateRequested = notice?.effect as? ActionEffect.ReminderDateRequested
                if (notice != null && (notice.isError || reminderEffect != null || reminderDeleted != null || dateRequested != null)) {
                    AppNoticeCard(notice.message, dismissNotice, essential = dateRequested != null, isError = notice.isError) {
                        if (reminderEffect != null) {
                            AppNoticeAction(Icons.Default.Edit, "Редактировать", { dismissNotice(); openReminder(reminderEffect.reminder) })
                            AppNoticeAction(Icons.Default.Schedule, "Сменить время", { noticeTimeReminder = reminderEffect.reminder })
                        }
                        if (dateRequested != null) {
                            AppNoticeTextAction("Без времени", { answerReminderDateClarification("без даты") })
                            AppNoticeTextAction("Отмена", { answerReminderDateClarification("отмена") })
                        } else if (!notice.isError) {
                            AppNoticeAction(Icons.AutoMirrored.Filled.Undo, "Отменить", undoLastAction)
                        }
                    }
                }
                ui.recordingPreview?.let { RecordingPreviewCard(it) }
                if (ui.awaitingSttConfirmation && ui.recognizedText.isNotBlank()) {
                    SttConfirmationCard(ui.recognizedText, editRecognizedText)
                }
                if (draggingReminderId == null) AppVoiceActionBar(
                    ui, beginPushToTalk, endPushToTalk, primaryAction, { showCreator = true }, "Новое напоминание",
                )
            }
        },
    ) { inner ->
        Box(
            Modifier.fillMaxSize()
                .padding(top = inner.calculateTopPadding())
                .padding(horizontal = AppLayout.horizontalPadding)
                .then(manualReorderModifier),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = AppLayout.contentTopPadding, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = draggingReminderId == null,
            ) {
        item(key = "reminder-toolbar", contentType = "toolbar") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppSearchField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "Поиск напоминаний",
                    reducedEffects = reducedUiEffects,
                    trailingContent = {
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, "Сортировка")
                            }
                            AppDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                ReminderSort.entries.forEach { option ->
                                    AppMenuItem(
                                        text = option.title,
                                        selected = option == sort,
                                        onClick = { setSort(option); sortMenu = false },
                                    )
                                }
                            }
                        }
                    },
                )
                Box {
                    IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "Другие действия") }
                    AppDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            openSettings?.let { open ->
                                AppMenuItem(text = "Настройки", icon = androidx.compose.material.icons.Icons.Outlined.Settings,
                                    onClick = { moreMenu = false; open() })
                            }
                        AppMenuItem(
                            text = "Удалить все просроченные",
                            icon = Icons.Default.DeleteSweep,
                            enabled = overdue.isNotEmpty(),
                            onClick = { moreMenu = false; deleteOverdue() },
                        )
                        AppMenuItem(
                            text = "Удалить все выполненные",
                            icon = Icons.Default.DeleteSweep,
                            enabled = allCompleted.isNotEmpty(),
                            onClick = { moreMenu = false; deleteCompleted() },
                        )
                    }
                }
            }
        }
        if (listContentReady) {
        if (displayedOverdue.isNotEmpty()) {
            item(key = "overdue-category", contentType = "section-header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = theme.colors.card,
                        contentColor = theme.colors.textPrimary,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Просроченные (${displayedOverdue.size})",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.colors.danger,
                        )
                        IconButton(onClick = { overdueExpanded = !overdueExpanded; sectionPreferences.edit().putBoolean("overdue_expanded", overdueExpanded).apply() }) {
                            Icon(
                                if (overdueExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                if (overdueExpanded) "Свернуть" else "Развернуть",
                            )
                        }
                    }
                }
            }
            if (showDragStartSpace && draggingOverdue) {
                item(key = "overdue-drag-start-space", contentType = "drag-start-space") {
                    Spacer(Modifier.height(64.dp))
                }
            }
            if (overdueExpanded) items(displayedOverdue, key = { it.id }, contentType = { "reminder-row" }) { reminder ->
                SwipeableReminderRow(
                    reminder = reminder,
                    completed = false,
                    dateMode = false,
                    complete = completeWithUndo,
                    favorite = stableFavorite,
                    delete = swipeDelete,
                    open = stableOpenReminder,
                    overdueAt = currentTime,
                    dragging = draggingReminderId == reminder.id,
                    dragOffset = if (draggingReminderId == reminder.id) dragOffset else 0f,
                )
            }
            if (showDragEndSpace && draggingOverdue) {
                item(key = "overdue-drag-end-space", contentType = "drag-end-space") {
                    Spacer(Modifier.height(64.dp))
                }
            }
            if (active.isNotEmpty()) item(key = "overdue-divider", contentType = "section-divider") {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
        if (filteredActive.isEmpty()) item(key = "active-empty", contentType = "empty-state") {
            AppEmptyState(
                icon = Icons.Default.Schedule,
                title = when {
                    query.isNotBlank() && completed.isEmpty() -> "Ничего не найдено"
                    query.isNotBlank() -> "Активные напоминания не найдены"
                    else -> "Всё под контролем"
                },
                description = when {
                    query.isNotBlank() -> "Попробуйте другое слово или измените запрос."
                    else -> "Активных напоминаний пока нет. Нажмите «+» или скажите, о чём и когда напомнить."
                },
            )
        }
        if (showDragStartSpace && !draggingOverdue) {
            item(key = "active-drag-start-space", contentType = "drag-start-space") {
                Spacer(Modifier.height(64.dp))
            }
        }
        when (sort) {
            ReminderSort.MY_ORDER -> items(displayedActive, key = { it.id }, contentType = { "reminder-row" }) { reminder ->
                SwipeableReminderRow(
                    reminder = reminder,
                    completed = false,
                    dateMode = false,
                    complete = completeWithUndo,
                    favorite = stableFavorite,
                    delete = swipeDelete,
                    open = stableOpenReminder,
                    dragging = draggingReminderId == reminder.id,
                    dragOffset = if (draggingReminderId == reminder.id) dragOffset else 0f,
                )
            }
            ReminderSort.DATE -> activeDateGroups.forEach { (date, title, values) ->
                item(key = "date-${date ?: "none"}", contentType = "date-header") { Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) }
                items(values, key = { it.id }, contentType = { "reminder-row" }) { reminder -> SwipeableReminderRow(reminder, false, true, completeWithUndo, stableFavorite, swipeDelete, stableOpenReminder) }
            }
            ReminderSort.RECENTLY_MARKED -> {
                val marked = favoriteGroups.first
                val unmarked = favoriteGroups.second
                if (marked.isNotEmpty()) {
                    item(key = "marked-header", contentType = "section-header") { Text("Отмеченные", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) }
                    items(marked, key = { it.id }, contentType = { "reminder-row" }) { reminder -> SwipeableReminderRow(reminder, false, false, completeWithUndo, stableFavorite, swipeDelete, stableOpenReminder) }
                }
                if (unmarked.isNotEmpty()) {
                    item(key = "unmarked-header", contentType = "section-header") { Text("Без пометки", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) }
                    items(unmarked, key = { it.id }, contentType = { "reminder-row" }) { reminder -> SwipeableReminderRow(reminder, false, false, completeWithUndo, stableFavorite, swipeDelete, stableOpenReminder) }
                }
            }
            ReminderSort.TITLE -> items(active, key = { it.id }, contentType = { "reminder-row" }) { reminder -> SwipeableReminderRow(reminder, false, false, completeWithUndo, stableFavorite, swipeDelete, stableOpenReminder) }
        }
        if (showDragEndSpace && !draggingOverdue) {
            item(key = "active-drag-end-space", contentType = "drag-end-space") {
                Spacer(Modifier.height(64.dp))
            }
        }
        if (completed.isNotEmpty()) {
            if (filteredActive.isNotEmpty()) item(key = "completed-divider", contentType = "section-divider") {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            item(key = "completed-category", contentType = "section-header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = theme.colors.card,
                        contentColor = theme.colors.textPrimary,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Выполненные (${completed.size})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { completedExpanded = !completedExpanded; sectionPreferences.edit().putBoolean("completed_expanded", completedExpanded).apply() }) {
                            Icon(if (completedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (completedExpanded) "Свернуть" else "Развернуть")
                        }
                    }
                }
            }
            if (completedExpanded) items(completed, key = { "completed-${it.id}" }, contentType = { "reminder-row" }) { reminder -> SwipeableReminderRow(reminder, true, false, completeWithUndo, stableFavorite, swipeDelete, stableOpenReminder) }
        }
        }
            }
        }
    }

    if (showCreator) {
        NewReminderSheet(
            close = { showCreator = false },
            save = { text, remindAt, recurrence, isFavorite ->
                create(text, remindAt, recurrence, isFavorite) { showCreator = false }
            },
        )
    }
    noticeTimeReminder?.let { reminder ->
        ReminderDateTimeDialog(
            initialDate = reminder.remindAt?.toLocalDate(),
            initialTime = reminder.remindAt?.toLocalTime(),
            initialRecurrence = reminder.recurrence,
            dismiss = { noticeTimeReminder = null },
            clear = { changeReminderTime(reminder, null, null); noticeTimeReminder = null },
            confirm = { date, time ->
                changeReminderTime(reminder, date.atTime(time ?: LocalTime.of(9, 0)), reminder.recurrence)
                noticeTimeReminder = null
            },
            confirmWithRecurrence = { date, time, recurrence ->
                changeReminderTime(reminder, date.atTime(time ?: LocalTime.of(9, 0)), recurrence)
                noticeTimeReminder = null
            },
        )
    }
}

@Composable
private fun NewReminderSheet(
    close: () -> Unit,
    save: (String, LocalDateTime?, String?, Boolean) -> Unit,
) {
    val theme = AppTheme.tokens
    var text by rememberSaveable { mutableStateOf("") }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var recurrence by remember { mutableStateOf<String?>(null) }
    var showDateTime by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val requestClose = { if (text.isBlank()) close() else confirmDiscard = true }
    CreationSheet(onDismissRequest = requestClose) {
        AppCreationTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "Новое напоминание",
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focusRequester,
            minLines = 1,
            maxLines = 3,
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                showDateTime = true
            }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Default.Schedule, "Дата и время")
                if (date != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(formatReminderDateTimeCompact(date!!, time), maxLines = 1)
                }
            }
            if (date != null) IconButton(
                onClick = { date = null; time = null; recurrence = null },
                modifier = Modifier.size(36.dp),
            ) { Icon(Icons.Default.Clear, "Убрать дату") }
            IconButton(onClick = { favorite = !favorite }) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    if (favorite) "Убрать из избранного" else "Добавить в избранное",
                    tint = if (favorite) theme.colors.warning else theme.colors.textSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    val remindAt = date?.atTime(time ?: LocalTime.of(9, 0))
                    save(text.trim(), remindAt, recurrence, favorite)
                },
            ) { Text("Сохранить") }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }
    if (showDateTime) {
        ReminderDateTimeDialog(
            initialDate = date,
            initialTime = time,
            initialRecurrence = recurrence,
            dismiss = { showDateTime = false },
            clear = { date = null; time = null; recurrence = null; showDateTime = false },
            confirm = { selectedDate, selectedTime ->
                date = selectedDate
                time = selectedTime
                showDateTime = false
            },
            confirmWithRecurrence = { selectedDate, selectedTime, selectedRecurrence ->
                date = selectedDate
                time = selectedTime
                recurrence = selectedRecurrence
                showDateTime = false
            },
        )
    }
    if (confirmDiscard) AppAlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Подтвердите удаление") },
        text = { Text("Удалить текущее напоминание?") },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Отмена") } },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; close() }) { Text("Удалить") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderDateTimeDialog(
    initialDate: LocalDate?,
    initialTime: LocalTime?,
    initialRecurrence: String? = null,
    dismiss: () -> Unit,
    clear: () -> Unit,
    confirm: (LocalDate, LocalTime?) -> Unit,
    confirmWithRecurrence: ((LocalDate, LocalTime?, String?) -> Unit)? = null,
    title: String? = null,
) {
    val theme = AppTheme.tokens
    val feedback = LocalHapticFeedback.current
    var selectedDate by remember { mutableStateOf(initialDate ?: LocalDate.now()) }
    ru.nezabudka.app.ui.SelectionHapticEffect(selectedDate)
    var displayedMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var selectedTime by remember { mutableStateOf(initialTime) }
    var selectedRecurrence by remember(initialRecurrence) { mutableStateOf(initialRecurrence) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showRecurrenceDialog by remember { mutableStateOf(false) }
    val monthTitle = displayedMonth.format(REMINDER_MONTH_FORMATTER)
        .replaceFirstChar { it.titlecase(RU_LOCALE) }
    val cells = remember(displayedMonth) {
        val leadingEmpty = displayedMonth.atDay(1).dayOfWeek.value - 1
        List<LocalDate?>(leadingEmpty) { null } +
            (1..displayedMonth.lengthOfMonth()).map(displayedMonth::atDay)
    }
    AppDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppLayout.horizontalPadding),
            shape = RoundedCornerShape(theme.shapes.dialog),
            tonalElevation = 6.dp,
            shadowElevation = appShadowElevation(8.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                if (title != null) {
                    Text(
                        title,
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, "Предыдущий месяц")
                    }
                    Text(monthTitle, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, "Следующий месяц")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf("П", "В", "С", "Ч", "П", "С", "В").forEach { day ->
                        Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                            Text(day, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            (week + List(7 - week.size) { null }).forEach { day ->
                                Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                                    if (day != null) Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (day == selectedDate) theme.colors.accent else Color.Transparent)
                                            .clickable { selectedDate = day },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            day.dayOfMonth.toString(),
                                            color = if (day == selectedDate) theme.colors.onAccent else theme.colors.textPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(
                    onClick = { showTimeDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                ) {
                    Icon(Icons.Default.Schedule, null)
                    Spacer(Modifier.width(14.dp))
                    Text(selectedTime?.format(REMINDER_TIME_FORMATTER) ?: "Укажите время", Modifier.weight(1f), textAlign = TextAlign.Start)
                }
                if (confirmWithRecurrence != null) {
                    HorizontalDivider()
                    TextButton(
                        onClick = { showRecurrenceDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Default.Repeat, null)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            reminderRecurrenceLabel(selectedRecurrence),
                            Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    if (initialDate != null) TextButton(onClick = clear) { Text("Без даты") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = dismiss) { Text("Отмена") }
                    TextButton(onClick = {
                        if (confirmWithRecurrence != null) {
                            feedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            confirmWithRecurrence(selectedDate, selectedTime, selectedRecurrence)
                        } else {
                            feedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            confirm(selectedDate, selectedTime)
                        }
                    }) { Text("Готово") }
                }
            }
        }
    }
    if (showTimeDialog) ReminderTimePickerDialog(
        initialTime = selectedTime ?: LocalTime.of(9, 0),
        dismiss = { showTimeDialog = false },
        confirm = { selectedTime = it; showTimeDialog = false },
    )
    if (showRecurrenceDialog) ReminderRecurrenceDialog(
        selected = selectedRecurrence,
        startDateTime = selectedDate.atTime(selectedTime ?: LocalTime.of(9, 0)),
        dismiss = { showRecurrenceDialog = false },
        select = { newRecurrence, newStart ->
            selectedRecurrence = newRecurrence
            newStart?.let {
                selectedDate = it.toLocalDate()
                selectedTime = it.toLocalTime()
            }
            showRecurrenceDialog = false
        },
    )
}

@Composable
internal fun ReminderRecurrenceDialog(
    selected: String?,
    startDateTime: LocalDateTime,
    dismiss: () -> Unit,
    select: (String?, LocalDateTime?) -> Unit,
) {
    var showCustom by remember { mutableStateOf(false) }
    if (showCustom) {
        CustomReminderRecurrenceDialog(
            selected = selected,
            initialStart = startDateTime,
            dismiss = { showCustom = false },
            select = { recurrence, start -> select(recurrence, start) },
        )
        return
    }
    val options = remember {
        listOf<String?>(null) + ReminderRecurrence.entries.map(ReminderRecurrence::storedValue)
    }
    val customSelected = CustomReminderRecurrence.fromStored(selected) != null
    AppAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Повтор") },
        text = {
            Column {
                options.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth().clickable { select(value, null) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { select(value, null) })
                        Spacer(Modifier.width(8.dp))
                        Text(reminderRecurrenceLabel(value))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { showCustom = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = customSelected, onClick = { showCustom = true })
                    Spacer(Modifier.width(8.dp))
                    Text(if (customSelected) reminderRecurrenceLabel(selected) else "Настроить…")
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Отмена") } },
    )
}

@Composable
private fun CustomReminderRecurrenceDialog(
    selected: String?,
    initialStart: LocalDateTime,
    dismiss: () -> Unit,
    select: (String, LocalDateTime) -> Unit,
) {
    val parsed = remember(selected) { CustomReminderRecurrence.fromStored(selected) }
    val preset = remember(selected) { ReminderRecurrence.fromStored(selected) }
    val initialUnit = parsed?.unit ?: when (preset) {
        ReminderRecurrence.WEEKDAYS, ReminderRecurrence.WEEKLY -> ReminderRecurrenceUnit.WEEK
        ReminderRecurrence.MONTHLY -> ReminderRecurrenceUnit.MONTH
        ReminderRecurrence.YEARLY -> ReminderRecurrenceUnit.YEAR
        else -> ReminderRecurrenceUnit.DAY
    }
    var intervalText by remember { mutableStateOf((parsed?.interval ?: 1).toString()) }
    var unit by remember { mutableStateOf(initialUnit) }
    var unitMenu by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(parsed?.start?.toLocalDate() ?: initialStart.toLocalDate()) }
    var startTime by remember { mutableStateOf(parsed?.start?.toLocalTime() ?: initialStart.toLocalTime()) }
    var weekdays by remember {
        val initialDays = parsed?.weekdays ?: when (preset) {
            ReminderRecurrence.WEEKDAYS -> DayOfWeek.entries.filter { it.value <= 5 }.toSet()
            ReminderRecurrence.WEEKLY -> setOf(initialStart.dayOfWeek)
            else -> emptySet()
        }
        mutableStateOf(initialDays)
    }
    var endMode by remember {
        mutableStateOf(
            when {
                parsed?.endDate != null -> "date"
                parsed?.repeatCount != null -> "count"
                else -> "never"
            },
        )
    }
    var endDate by remember { mutableStateOf(parsed?.endDate ?: startDate.plusMonths(3)) }
    var repeatCountText by remember { mutableStateOf((parsed?.repeatCount ?: 1).toString()) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    var showEndDateDialog by remember { mutableStateOf(false) }
    val interval = intervalText.toIntOrNull()
    val repeatCount = repeatCountText.toIntOrNull()
    val valid = interval != null && interval in 1..999 &&
        (endMode != "date" || !endDate.isBefore(startDate)) &&
        (endMode != "count" || (repeatCount != null && repeatCount in 1..999))
    val unitLabels = remember {
        mapOf(
            ReminderRecurrenceUnit.DAY to "день",
            ReminderRecurrenceUnit.WEEK to "неделя",
            ReminderRecurrenceUnit.MONTH to "месяц",
            ReminderRecurrenceUnit.YEAR to "год",
        )
    }
    val shortDays = remember { listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") }

    AppAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Повторяется") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { value ->
                            if (value.length <= 3 && value.all(Char::isDigit)) intervalText = value
                        },
                        modifier = Modifier.width(96.dp),
                        label = { Text("Каждые") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { unitMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(unitLabels.getValue(unit))
                        }
                        DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                            ReminderRecurrenceUnit.entries.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(unitLabels.getValue(value)) },
                                    onClick = { unit = value; unitMenu = false },
                                )
                            }
                        }
                    }
                }
                if (unit == ReminderRecurrenceUnit.WEEK) {
                    Text("Дни недели", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in weekdays,
                                onClick = {
                                    weekdays = if (day in weekdays) weekdays - day else weekdays + day
                                },
                                label = { Text(shortDays[day.value - 1]) },
                            )
                        }
                    }
                }
                Text("Время", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { showTimeDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(startTime.format(REMINDER_TIME_FORMATTER))
                }
                Text("Начало", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { showStartDateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(startDate.format(REMINDER_COMPACT_DATE_FORMATTER))
                }
                Text("Окончание", style = MaterialTheme.typography.labelLarge)
                RepeatEndRow("never", endMode, "Никогда") { endMode = "never" }
                RepeatEndRow("date", endMode, "В дату") { endMode = "date" }
                if (endMode == "date") {
                    OutlinedButton(onClick = { showEndDateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(endDate.format(REMINDER_COMPACT_DATE_FORMATTER))
                    }
                }
                RepeatEndRow("count", endMode, "После количества повторов") { endMode = "count" }
                if (endMode == "count") {
                    OutlinedTextField(
                        value = repeatCountText,
                        onValueChange = { value ->
                            if (value.length <= 3 && value.all(Char::isDigit)) repeatCountText = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Количество повторов") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Назад") } },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val start = startDate.atTime(startTime)
                    val rule = CustomReminderRecurrence(
                            interval = interval!!,
                            unit = unit,
                            weekdays = if (unit == ReminderRecurrenceUnit.WEEK) {
                                weekdays.ifEmpty { setOf(startDate.dayOfWeek) }
                            } else {
                                emptySet()
                            },
                            start = start,
                            endDate = endDate.takeIf { endMode == "date" },
                            repeatCount = repeatCount.takeIf { endMode == "count" },
                        ).storedValue()
                    select(rule, ru.nezabudka.app.domain.alignReminderOccurrence(start, rule))
                },
            ) { Text("Готово") }
        },
    )
    if (showTimeDialog) ReminderTimePickerDialog(
        initialTime = startTime,
        dismiss = { showTimeDialog = false },
        confirm = { startTime = it; showTimeDialog = false },
    )
    if (showStartDateDialog) ReminderDateTimeDialog(
        initialDate = startDate,
        initialTime = startTime,
        dismiss = { showStartDateDialog = false },
        clear = { showStartDateDialog = false },
        confirm = { date, time ->
            startDate = date
            time?.let { startTime = it }
            if (endDate.isBefore(date)) endDate = date
            showStartDateDialog = false
        },
    )
    if (showEndDateDialog) ReminderDateTimeDialog(
        initialDate = endDate,
        initialTime = null,
        dismiss = { showEndDateDialog = false },
        clear = { showEndDateDialog = false },
        confirm = { date, _ ->
            endDate = date
            showEndDateDialog = false
        },
    )
}

@Composable
private fun RepeatEndRow(
    value: String,
    selected: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialTime: LocalTime,
    dismiss: () -> Unit,
    confirm: (LocalTime) -> Unit,
) {
    AppTimePickerDialog(initialTime, dismiss, confirm)
}

private fun formatReminderDateTimeCompact(date: LocalDate, time: LocalTime?): String {
    val dateText = date.format(REMINDER_COMPACT_DATE_FORMATTER)
    return if (time == null) dateText else "$dateText ${time.format(REMINDER_TIME_FORMATTER)}"
}

@Composable
internal fun SwipeableReminderRow(
    reminder: Reminder,
    completed: Boolean,
    dateMode: Boolean,
    complete: (Reminder) -> Unit,
    favorite: (Reminder) -> Unit,
    delete: (Reminder) -> Unit,
    open: (Reminder) -> Unit,
    overdueAt: LocalDateTime? = null,
    dragging: Boolean = false,
    dragOffset: Float = 0f,
) {
    val theme = AppTheme.tokens
    val feedback = LocalHapticFeedback.current
    val currentReminder by rememberUpdatedState(reminder)
    val currentDelete by rememberUpdatedState(delete)
    val swipeStateHolder = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }
    var swipeWidth by remember { mutableStateOf(0) }
    val minimumSwipe = with(LocalDensity.current) { 72.dp.toPx() }
    val confirmDismiss = remember(minimumSwipe) {
        { value: SwipeToDismissBoxValue ->
            val offset = swipeStateHolder[0]?.let { runCatching { abs(it.requireOffset()) }.getOrDefault(0f) } ?: 0f
            if (value == SwipeToDismissBoxValue.EndToStart && swipeWidth > 0 &&
                offset >= maxOf(minimumSwipe, swipeWidth * 0.5f)) {
                true
            } else value == SwipeToDismissBoxValue.Settled
        }
    }
    val threshold = remember { { distance: Float -> distance * 0.5f } }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = confirmDismiss,
        positionalThreshold = threshold,
    )
    LaunchedEffect(state.targetValue) {
        if (state.targetValue == SwipeToDismissBoxValue.EndToStart)
            feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    swipeStateHolder[0] = state
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            currentDelete(currentReminder)
            // A restored item must start settled, rather than delete itself again.
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    val secondaryText = remember(reminder.remindAt, reminder.recurrence, reminder.completedAt, completed, dateMode, overdueAt) {
        val base = if (completed) "Выполнено: ${reminder.completedAt?.format(REMINDER_DATE_TIME_FORMATTER) ?: "—"}"
        else if (overdueAt != null && reminder.remindAt != null) overdueReminderText(reminder.remindAt, overdueAt)
        else if (dateMode) reminder.remindAt?.format(REMINDER_TIME_FORMATTER) ?: "Без даты"
        else reminder.remindAt?.format(REMINDER_DATE_TIME_FORMATTER) ?: "Без даты"
        if (!completed && reminder.recurrence != null) {
            "$base • ${reminderRecurrenceLabel(reminder.recurrence)}"
        } else {
            base
        }
    }
    val preview = remember(reminder.text) { noteCardPreview(reminder.text) }
    SwipeToDismissBox(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { swipeWidth = it.width }
            .then(if (dragging) Modifier.graphicsLayer { translationY = dragOffset } else Modifier),
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !dragging,
        backgroundContent = {
            if (state.dismissDirection != SwipeToDismissBoxValue.Settled) {
            Box(
                Modifier.fillMaxSize().heightIn(min = 88.dp)
                    .background(theme.colors.dangerContainer, RoundedCornerShape(theme.shapes.card))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Default.DeleteSweep, "Удалить", tint = theme.colors.onDangerContainer) }
            }
        },
    ) {
        Card(
            onClick = { open(reminder) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
            shape = RoundedCornerShape(theme.shapes.card),
            colors = CardDefaults.cardColors(
                containerColor = theme.colors.card,
                contentColor = theme.colors.textPrimary,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = appShadowElevation(2.dp)),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { complete(reminder) }) { Icon(if (completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, if (completed) "Вернуть в активные" else "Отметить выполненным") }
                Column(Modifier.weight(1f)) {
                    Text(preview, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (overdueAt != null) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = theme.colors.danger,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            secondaryText,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdueAt != null) theme.colors.danger else theme.colors.textSecondary,
                        )
                    }
                }
                IconButton(onClick = { favorite(reminder) }) { Icon(if (reminder.favorite) Icons.Default.Star else Icons.Default.StarBorder, if (reminder.favorite) "Убрать из избранного" else "В избранное", tint = if (reminder.favorite) theme.colors.warning else theme.colors.textSecondary) }
            }
        }
    }
}

private fun reminderDateScrollIndex(
    reminders: List<Reminder>,
    targetDate: LocalDate?,
    now: LocalDateTime,
): Int {
    if (targetDate == null || reminders.isEmpty()) return 0
    val overdue = reminders.filter { it.isOverdueAt(now) }
    if (overdue.any { it.remindAt?.toLocalDate() == targetDate }) return 1
    var itemIndex = 1 + if (overdue.isEmpty()) 0 else 1 + overdue.size // screen and overdue headers
    var lastDatedHeader = 0
    reminders.filterNot { it in overdue }.groupBy { it.remindAt?.toLocalDate() }.forEach { (date, values) ->
        if (date != null) {
            lastDatedHeader = itemIndex
            if (!date.isBefore(targetDate)) return itemIndex
        }
        itemIndex += 1 + values.size
    }
    return lastDatedHeader
}

internal fun reminderMoveBeforeId(
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

private fun dateSectionTitle(date: LocalDate?, today: LocalDate): String {
    if (date == null) return "Без даты"
    return when (date) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> date.format(REMINDER_SECTION_FORMATTER).replaceFirstChar { it.titlecase(RU_LOCALE) }
    }
}

private val RU_LOCALE: Locale = Locale.forLanguageTag("ru")
private val REMINDER_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val REMINDER_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
private val REMINDER_SECTION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM.", RU_LOCALE)
private val REMINDER_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy 'г.'", RU_LOCALE)
private val REMINDER_COMPACT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
