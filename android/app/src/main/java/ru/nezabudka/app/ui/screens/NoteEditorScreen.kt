package ru.nezabudka.app.ui.screens

import android.content.ClipData

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.appShadowElevation
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val NoteEditorHorizontalPadding = 20.dp
private const val NoteEditHistoryLimit = 100
private val NoteEditorModifiedFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

@Composable
private fun NoteEditorActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val theme = AppTheme.tokens
    val contentColor = tint ?: if (enabled) {
        theme.colors.textPrimary
    } else {
        theme.colors.textPrimary.copy(alpha = 0.35f)
    }
    Surface(
        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = theme.colors.input,
        contentColor = contentColor,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(20.dp), tint = contentColor)
        }
    }
}

private data class NoteTextSnapshot(
    val title: TextFieldValue,
    val body: TextFieldValue,
)

internal enum class NoteTextField { TITLE, BODY }
private enum class NoteEditKind { INSERT, DELETE }

internal class NoteEditGrouping {
    private var field: NoteTextField? = null
    private var kind: NoteEditKind? = null
    private var nextPosition: Int = -1

    fun startsNewGroup(
        editedField: NoteTextField,
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
    ): Boolean {
        val edit = classifySingleCharacterEdit(oldValue, newValue)
        val continues = edit != null &&
            field == editedField &&
            kind == edit.kind &&
            edit.position == nextPosition
        val startsNew = !continues
        if (edit == null || edit.character.isWhitespace()) {
            reset()
        } else {
            field = editedField
            kind = edit.kind
            nextPosition = when (edit.kind) {
                NoteEditKind.INSERT -> edit.position + 1
                NoteEditKind.DELETE -> edit.position - 1
            }
        }
        return startsNew
    }

    fun reset() {
        field = null
        kind = null
        nextPosition = -1
    }

    private data class CharacterEdit(
        val kind: NoteEditKind,
        val character: Char,
        val position: Int,
    )

    private fun classifySingleCharacterEdit(
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
    ): CharacterEdit? {
        if (!oldValue.selection.collapsed || !newValue.selection.collapsed) return null
        val oldCursor = oldValue.selection.end
        val newCursor = newValue.selection.end
        return when {
            newValue.text.length == oldValue.text.length + 1 &&
                newCursor == oldCursor + 1 && oldCursor in 0 until newValue.text.length ->
                CharacterEdit(NoteEditKind.INSERT, newValue.text[oldCursor], oldCursor)

            oldValue.text.length == newValue.text.length + 1 &&
                newCursor == oldCursor - 1 && newCursor in oldValue.text.indices ->
                CharacterEdit(NoteEditKind.DELETE, oldValue.text[newCursor], newCursor)

            else -> null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun NoteCard(
    note: Note,
    selected: Boolean,
    selectionMode: Boolean,
    open: (Note) -> Unit,
    toggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    longClickEnabled: Boolean = true,
) {
    val theme = AppTheme.tokens
    val background = resolveNoteColor(note.color)
    val contentColor = theme.colors.textPrimary
    val shape = RoundedCornerShape(theme.shapes.noteCard)
    val preview = remember(note.text) { noteCardPreview(note.text) }
    Card(
        modifier = Modifier
            .then(modifier)
            .then(if (selected) Modifier.border(3.dp, theme.colors.accent, shape) else Modifier)
            .combinedClickable(
                onClick = { if (selectionMode) toggleSelection() else open(note) },
                onLongClick = toggleSelection.takeIf { longClickEnabled },
            ),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = background, contentColor = contentColor),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = appShadowElevation(2.dp),
        ),
        shape = shape,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (note.title.isNotBlank()) {
                Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                if (note.text.isNotBlank()) Spacer(Modifier.height(6.dp))
            }
            if (note.text.isNotBlank()) {
                Row { Text(preview, Modifier.weight(1f), maxLines = 8, overflow = TextOverflow.Ellipsis) }
            }
            if (note.folderNames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    note.folderNames.forEach { name ->
                        Surface(
                            shape = RoundedCornerShape(theme.shapes.tag),
                            color = theme.colors.folderArea,
                            contentColor = theme.colors.textPrimary,
                            border = BorderStroke(1.dp, theme.colors.outline.copy(alpha = 0.55f)),
                        ) {
                            Text(name, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditorScreen(
    note: Note?, folders: List<NoteFolder>, initialFolderId: Long?, back: () -> Unit,
    editorSessionId: Long,
    save: (Long?, List<String>, String, String, Boolean, Boolean, String?) -> Unit,
    createFolder: (String, (NoteFolder) -> Unit) -> Unit,
    createReminder: (String, LocalDateTime, String?, () -> Unit) -> Unit,
    trash: (Note) -> Unit,
    dictatedText: String?,
    dictationActive: Boolean,
    dictationRecording: Boolean,
    beginDictation: () -> Unit,
    endDictation: () -> Unit,
    consumeDictation: () -> Unit,
    cancelDictation: () -> Unit,
    dictationPreview: String? = null,
) {
    val theme = AppTheme.tokens
    var titleValue by remember(note?.id, editorSessionId) { mutableStateOf(TextFieldValue(note?.title.orEmpty())) }
    var textValue by remember(note?.id, editorSessionId) { mutableStateOf(TextFieldValue(note?.text.orEmpty())) }
    var undoHistory by remember(note?.id, editorSessionId) { mutableStateOf<List<NoteTextSnapshot>>(emptyList()) }
    var redoHistory by remember(note?.id, editorSessionId) { mutableStateOf<List<NoteTextSnapshot>>(emptyList()) }
    val editGrouping = remember(note?.id, editorSessionId) { NoteEditGrouping() }
    val currentSnapshot = { NoteTextSnapshot(titleValue, textValue) }
    val updateTitle: (TextFieldValue) -> Unit = { newValue ->
        if (
            newValue.text != titleValue.text &&
            editGrouping.startsNewGroup(NoteTextField.TITLE, titleValue, newValue)
        ) {
            undoHistory = (undoHistory + currentSnapshot()).takeLast(NoteEditHistoryLimit)
            redoHistory = emptyList()
        }
        titleValue = newValue
    }
    val updateBody: (TextFieldValue) -> Unit = { newValue ->
        if (
            newValue.text != textValue.text &&
            editGrouping.startsNewGroup(NoteTextField.BODY, textValue, newValue)
        ) {
            undoHistory = (undoHistory + currentSnapshot()).takeLast(NoteEditHistoryLimit)
            redoHistory = emptyList()
        }
        textValue = newValue
    }
    val undo: () -> Unit = {
        undoHistory.lastOrNull()?.let { previous ->
            redoHistory = (redoHistory + currentSnapshot()).takeLast(NoteEditHistoryLimit)
            undoHistory = undoHistory.dropLast(1)
            titleValue = previous.title
            textValue = previous.body
        }
        editGrouping.reset()
        Unit
    }
    val redo: () -> Unit = {
        redoHistory.lastOrNull()?.let { next ->
            undoHistory = (undoHistory + currentSnapshot()).takeLast(NoteEditHistoryLimit)
            redoHistory = redoHistory.dropLast(1)
            titleValue = next.title
            textValue = next.body
        }
        editGrouping.reset()
        Unit
    }
    val title = titleValue.text
    val text = textValue.text
    var pinned by remember(note?.id, editorSessionId) { mutableStateOf(note?.pinned ?: false) }
    var archived by remember(note?.id, editorSessionId) { mutableStateOf(note?.archived ?: false) }
    var color by remember(note?.id, editorSessionId) { mutableStateOf(note?.color) }
    val originalFolderNames = remember(note?.id, note?.folderNames) { note?.folderNames?.toSet().orEmpty() }
    var selectedFolders by remember(note?.id, initialFolderId, editorSessionId) {
        mutableStateOf(note?.folderNames?.toSet() ?: setOfNotNull(folders.firstOrNull { it.id == initialFolderId }?.name))
    }
    var folderMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
    var reminderDialog by remember { mutableStateOf(false) }
    var reminderCreated by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val titleFocus = remember { FocusRequester() }
    val editorFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(note?.id) {
        if (note == null) {
            // Let the editor publish its first frame before starting the IME
            // transition; otherwise both costs share the same 90 Hz deadline.
            withFrameNanos { }
            editorFocus.requestFocus()
            keyboardController?.show()
        }
    }
    DisposableEffect(Unit) {
        onDispose(cancelDictation)
    }
    LaunchedEffect(dictatedText) {
        dictatedText?.takeIf(String::isNotBlank)?.let { recognized ->
            val separator = if (textValue.text.isBlank() || textValue.text.lastOrNull()?.isWhitespace() == true) "" else " "
            val combined = textValue.text + separator + recognized
            editGrouping.reset()
            updateBody(TextFieldValue(combined, selection = androidx.compose.ui.text.TextRange(combined.length)))
            editGrouping.reset()
            consumeDictation()
            editorFocus.requestFocus()
        }
    }
    LaunchedEffect(reminderCreated) {
        if (reminderCreated) {
            snackbarHostState.showSnackbar("Напоминание создано", withDismissAction = true)
            reminderCreated = false
        }
    }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val reminderText = remember(title, text) {
        listOf(title.trim(), text.trim())
            .filter(String::isNotBlank)
            .joinToString(" - ")
    }
    val selectedFolderNames = remember(selectedFolders) { selectedFolders.toList() }
    val changed = note == null || title != note.title || text != note.text ||
        selectedFolders != originalFolderNames || pinned != note.pinned ||
        archived != note.archived || color != note.color
    val exitEditor = {
        cancelDictation()
        if (changed && (note != null || title.isNotBlank() || text.isNotBlank())) {
            save(note?.id, selectedFolderNames, title, text, pinned, archived, color)
        } else {
            back()
        }
    }
    BackHandler(onBack = exitEditor)
    val editorColor = if (color == null) theme.colors.background else resolveNoteColor(color)
    val editorContentColor = theme.colors.textPrimary
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val editorScroll = rememberScrollState()
    var editorViewportHeight by remember { mutableIntStateOf(0) }
    var cursorBottom by remember { mutableIntStateOf(0) }
    LaunchedEffect(imeBottom, textValue.selection, cursorBottom, editorViewportHeight) {
        if (imeBottom > 0 && editorViewportHeight > 0) {
            val safety = with(density) { 56.dp.roundToPx() }
            val target = (cursorBottom - editorViewportHeight + safety).coerceAtLeast(0)
            if (target > editorScroll.value) editorScroll.animateScrollTo(target)
        }
    }
    AppEditorScaffold(
        containerColor = editorColor,
        contentColor = editorContentColor,
        onBack = exitEditor,
        imeAware = true,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        actions = {
            NoteEditorActionButton(
                icon = Icons.Default.PushPin,
                description = "Закрепить",
                onClick = { pinned = !pinned },
                tint = if (pinned) theme.colors.accent else editorContentColor,
            )
            Spacer(Modifier.size(4.dp))
            NoteEditorActionButton(
                icon = if (archived) Icons.Default.Unarchive else Icons.Default.Archive,
                description = if (archived) "Вернуть из архива" else "В архив",
                onClick = { archived = !archived },
                tint = if (archived) theme.colors.accent else editorContentColor,
            )
        },
        bottomBar = {
            Column {
            dictationPreview?.let { RecordingPreviewCard(it) }
            Row(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = NoteEditorHorizontalPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NoteEditorActionButton(Icons.Default.MoreVert, "Другие действия", { actionMenu = true })
                NoteEditorActionButton(Icons.Default.ColorLens, "Цвет", { colorMenu = true })
                NoteEditorActionButton(Icons.Default.Folder, "Выбрать папки", { folderMenu = true })
                Spacer(Modifier.weight(1f))
                NoteEditorActionButton(
                    Icons.AutoMirrored.Filled.Undo,
                    "Отменить изменение",
                    undo,
                    enabled = undoHistory.isNotEmpty(),
                )
                NoteEditorActionButton(
                    Icons.AutoMirrored.Filled.Redo,
                    "Повторить изменение",
                    redo,
                    enabled = redoHistory.isNotEmpty(),
                )
                Surface(
                    modifier = Modifier.size(64.dp).clip(CircleShape).pushToTalkGesture(
                        enabled = !dictationActive,
                        hasRecognizedText = false,
                        beginPushToTalk = beginDictation,
                        endPushToTalk = endDictation,
                        primaryAction = {},
                    ),
                    shape = CircleShape,
                    color = if (dictationActive) theme.colors.accent else theme.colors.talkButton,
                    contentColor = if (dictationActive) theme.colors.onAccent else theme.colors.onTalkButton,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            dictationActive && !dictationRecording -> CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = theme.colors.onAccent,
                                strokeWidth = 3.dp,
                            )
                            dictationRecording -> Icon(Icons.Default.Stop, "Остановить голосовой ввод", Modifier.size(30.dp))
                            else -> Icon(Icons.Default.Mic, "Голосовой ввод заметки", Modifier.size(30.dp))
                        }
                    }
                }
            }
            }
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = NoteEditorHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppEditorTextField(
                value = titleValue,
                onValueChange = updateTitle,
                placeholder = "Название",
                textStyle = MaterialTheme.typography.headlineSmall,
                textColor = editorContentColor,
                placeholderColor = editorContentColor.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(titleFocus),
                singleLine = true,
            )
            AppEditorTextField(
                value = textValue,
                onValueChange = updateBody,
                placeholder = "Текст заметки",
                textStyle = MaterialTheme.typography.bodyLarge,
                textColor = editorContentColor,
                placeholderColor = editorContentColor.copy(alpha = 0.65f),
                modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(editorFocus).onSizeChanged { editorViewportHeight = it.height }.verticalScroll(editorScroll),
                onTextLayout = { layout -> cursorBottom = layout.getCursorRect(textValue.selection.end.coerceIn(0, textValue.text.length)).bottom.toInt() },
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = selectedFolderNames,
                    key = { name -> name },
                    contentType = { "selected-folder" },
                ) { name ->
                    Surface(
                        shape = RoundedCornerShape(theme.shapes.tag),
                        color = theme.colors.folderArea,
                        contentColor = theme.colors.textPrimary,
                    ) {
                        Text(
                            name,
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
    if (colorMenu) ModalBottomSheet(
        onDismissRequest = { colorMenu = false },
        shape = RoundedCornerShape(topStart = theme.shapes.dialog, topEnd = theme.shapes.dialog),
        scrimColor = theme.colors.scrim,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text("Цвет заметки", style = MaterialTheme.typography.titleMedium)
            NoteColorGrid(color, { selected -> color = selected; colorMenu = false }, Modifier.padding(vertical = 18.dp))
        }
    }
    if (actionMenu) ModalBottomSheet(
        onDismissRequest = { actionMenu = false },
        shape = RoundedCornerShape(topStart = theme.shapes.dialog, topEnd = theme.shapes.dialog),
        scrimColor = theme.colors.scrim,
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
        ) {
            TextButton(
                enabled = reminderText.isNotBlank(),
                onClick = {
                    actionMenu = false
                    reminderDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null)
                    Text("Напоминание", Modifier.padding(start = 20.dp))
                }
            }
            if (note != null) TextButton(onClick = {
                actionMenu = false
                cancelDictation()
                trash(note)
            }, modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DeleteSweep, null); Text("Удалить", Modifier.padding(start = 20.dp)) }
            }
            TextButton(onClick = {
                val copiedText = listOf(title, text).filter(String::isNotBlank).joinToString("\n\n")
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Заметка", copiedText)))
                }
                actionMenu = false
            }, modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ContentCopy, null); Text("Скопировать", Modifier.padding(start = 20.dp)) }
            }
            if (note != null) Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 2.dp),
            ) {
                Text(
                    "Создано ${note.createdAt.format(NoteEditorModifiedFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.colors.textSecondary,
                )
                Text(
                    "Изменено ${note.updatedAt.format(NoteEditorModifiedFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.colors.textSecondary,
                )
            }
        }
    }
    if (reminderDialog) ReminderDateTimeDialog(
        initialDate = null,
        initialTime = null,
        dismiss = { reminderDialog = false },
        clear = {},
        confirm = { date, time ->
            createReminder(reminderText, date.atTime(time ?: LocalTime.of(9, 0)), null) {
                reminderCreated = true
            }
            reminderDialog = false
        },
        confirmWithRecurrence = { date, time, recurrence ->
            createReminder(reminderText, date.atTime(time ?: LocalTime.of(9, 0)), recurrence) {
                reminderCreated = true
            }
            reminderDialog = false
        },
        title = "Когда напомнить?",
    )
    if (folderMenu) NoteFolderPickerDialog(
        folders = folders,
        selectedNames = selectedFolders,
        dismiss = { folderMenu = false },
        toggle = { folder, checked -> selectedFolders = if (checked) selectedFolders + folder.name else selectedFolders - folder.name },
        createFolder = createFolder,
    )
}
