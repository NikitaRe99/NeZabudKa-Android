package ru.nezabudka.app.ui.screens

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.ui.AppLayout
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.appShadowElevation

@Composable
internal fun FolderManagerScreen(
    folders: List<NoteFolder>,
    close: () -> Unit,
    createFolder: (String, (NoteFolder) -> Unit) -> Unit,
    renameFolder: (NoteFolder, String) -> Unit,
    moveFolder: (NoteFolder, Int) -> Unit,
    deleteFolder: (NoteFolder) -> Unit,
) {
    val theme = AppTheme.tokens
    val orderedFolders = remember { mutableStateListOf<NoteFolder>() }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var nameDialog by remember { mutableStateOf(false) }
    var editedFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var draftName by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val rowStridePx = with(density) { 72.dp.toPx() }
    val autoScrollEdgePx = with(density) { 72.dp.toPx() }
    val maxAutoScrollPxPerSecond = with(density) { 900.dp.toPx() }

    fun updateDraggedPosition() {
        val id = draggingId ?: return
        var currentIndex = orderedFolders.indexOfFirst { it.id == id }
        if (currentIndex < 0) return

        while (dragOffset > rowStridePx / 2f && currentIndex < orderedFolders.lastIndex) {
            orderedFolders.add(currentIndex + 1, orderedFolders.removeAt(currentIndex))
            dragOffset -= rowStridePx
            currentIndex++
        }
        while (dragOffset < -rowStridePx / 2f && currentIndex > 0) {
            orderedFolders.add(currentIndex - 1, orderedFolders.removeAt(currentIndex))
            dragOffset += rowStridePx
            currentIndex--
        }
    }

    LaunchedEffect(folders) {
        if (draggingId == null) {
            orderedFolders.clear()
            orderedFolders.addAll(folders.sortedBy { it.manualOrder })
        }
    }

    LaunchedEffect(draggingId) {
        val id = draggingId ?: return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = ((frameNanos - previousFrameNanos) / 1_000_000_000f)
                .coerceAtMost(0.05f)
            previousFrameNanos = frameNanos

            val layoutInfo = listState.layoutInfo
            val draggedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: continue
            val draggedCenter = draggedItem.offset + dragOffset + draggedItem.size / 2f
            val distanceFromTop = draggedCenter - layoutInfo.viewportStartOffset
            val distanceFromBottom = layoutInfo.viewportEndOffset - draggedCenter
            val speed = when {
                distanceFromTop < autoScrollEdgePx -> {
                    -maxAutoScrollPxPerSecond *
                        ((autoScrollEdgePx - distanceFromTop) / autoScrollEdgePx).coerceIn(0f, 1f)
                }
                distanceFromBottom < autoScrollEdgePx -> {
                    maxAutoScrollPxPerSecond *
                        ((autoScrollEdgePx - distanceFromBottom) / autoScrollEdgePx).coerceIn(0f, 1f)
                }
                else -> 0f
            }
            if (speed != 0f) {
                val consumed = listState.scrollBy(speed * elapsedSeconds)
                if (consumed != 0f) {
                    dragOffset += consumed
                    updateDraggedPosition()
                }
            }
        }
    }

    AppDialog(
        onDismissRequest = close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
        dimBackground = false,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Text("Папки с заметками", style = MaterialTheme.typography.headlineSmall)
                }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = AppLayout.horizontalPadding),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
                ) {
                    items(orderedFolders, key = { folder -> folder.id }, contentType = { "folder-row" }) { folder ->
                        var actionsOpen by remember(folder.id) { mutableStateOf(false) }
                        Surface(
                            color = theme.colors.folderArea,
                            shape = RoundedCornerShape(theme.shapes.panel),
                            shadowElevation = if (draggingId == folder.id) appShadowElevation(6.dp) else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(translationY = if (draggingId == folder.id) dragOffset else 0f),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(48.dp).draggable(
                                        state = rememberDraggableState { delta ->
                                            dragOffset += delta
                                            updateDraggedPosition()
                                        },
                                        orientation = Orientation.Vertical,
                                        onDragStarted = {
                                            draggingId = folder.id
                                            dragStartIndex = orderedFolders.indexOfFirst { it.id == folder.id }
                                            dragOffset = 0f
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            updateDraggedPosition()
                                            val start = dragStartIndex
                                            val target = orderedFolders.indexOfFirst { it.id == folder.id }
                                            if (start != null && target >= 0 && target != start) {
                                                moveFolder(folder, target - start)
                                            }
                                            dragOffset = 0f
                                            dragStartIndex = null
                                            draggingId = null
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        },
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Default.DragHandle, "Изменить порядок", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Column(Modifier.weight(1f)) {
                                    Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${folder.noteCount} заметок", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box {
                                    IconButton(onClick = { actionsOpen = true }) { Icon(Icons.Default.MoreVert, "Действия с папкой") }
                                    DropdownMenu(actionsOpen, { actionsOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Переименовать") },
                                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                                            onClick = {
                                                actionsOpen = false
                                                editedFolder = folder
                                                draftName = folder.name
                                                nameDialog = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Удалить") },
                                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = theme.colors.danger) },
                                            onClick = { actionsOpen = false; deleteFolder(folder) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "create-folder", contentType = "folder-action") {
                        Surface(
                            color = theme.colors.folderArea,
                            shape = RoundedCornerShape(theme.shapes.panel),
                        ) {
                            TextButton(
                                onClick = { editedFolder = null; draftName = ""; nameDialog = true },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                            ) {
                                Icon(Icons.Default.Add, null, tint = theme.colors.accent)
                                Text("Создать новую папку", Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (nameDialog) AppAlertDialog(
        onDismissRequest = { nameDialog = false },
        title = { Text(if (editedFolder == null) "Новая папка" else "Переименовать папку") },
        text = {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                singleLine = true,
                label = { Text("Название папки") },
                shape = RoundedCornerShape(theme.shapes.action),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(
                enabled = draftName.isNotBlank(),
                onClick = {
                    editedFolder?.let { renameFolder(it, draftName) } ?: createFolder(draftName) { }
                    nameDialog = false
                },
            ) { Text(if (editedFolder == null) "Создать" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = { nameDialog = false }) { Text("Отмена") } },
    )
}
