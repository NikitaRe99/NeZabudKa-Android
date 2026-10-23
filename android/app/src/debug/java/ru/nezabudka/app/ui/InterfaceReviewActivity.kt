package ru.nezabudka.app.ui

import android.os.Bundle
import android.graphics.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.ui.screens.*
import java.time.LocalDateTime

/** Isolated UI review; gestures never touch the user's records. */
class InterfaceReviewActivity : ComponentActivity() {
    var deletions = 0
    var galleryBounds = Rect()
    var lastReminderCompleted: Boolean? = null
    var editorClosed = false
    var savedTheme: ru.nezabudka.app.domain.CustomThemeSettings? = null
    var selectedNoteColor: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixtureTheme = ru.nezabudka.app.domain.defaultCustomTheme("light").let {
            it.copy(notePalette = it.notePalette + listOf("#123456", "#654321"))
        }
        val settings = SettingsStore(this).state.value.let {
            when (intent.getStringExtra("scenario")) {
                "theme-editor", "note-colors" -> it.copy(theme = "custom", customTheme = fixtureTheme)
                else -> if (intent.hasExtra("gallery_message")) it.copy(theme = "dark") else it
            }
        }
        setContent {
            LocalAssistantTheme(settings) {
                Surface(Modifier.fillMaxSize(), color = AppTheme.tokens.colors.background) {
                    if (intent.getStringExtra("scenario") == "reminder-editor") {
                        val reminder = remember { LocalDateTime.now().let { Reminder(900002, "Проверка редактора", it.plusHours(1), null, "active", it) } }
                        ReminderEditorScreen(reminder, back = { editorClosed = true },
                            save = { _, _, _, _, _, completed, done -> lastReminderCompleted = completed; done() },
                            delete = { _, done -> done() })
                    } else if (intent.getStringExtra("scenario") == "theme-editor") {
                        CustomThemeEditorDialog(fixtureTheme, dismiss = {}, save = {
                            savedTheme = it
                            SettingsStore(this).setCustomTheme(it)
                        })
                    } else if (intent.getStringExtra("scenario") == "note-colors") {
                        var selected by remember { mutableStateOf<String?>(null) }
                        Column(Modifier.padding(top = 100.dp, start = 20.dp, end = 20.dp)) {
                            NoteColorGrid(selected, { selected = it; selectedNoteColor = it })
                        }
                    } else if (intent.getStringExtra("scenario") == "swipe") {
                        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 100.dp)) {
                            val now = LocalDateTime.now()
                            SwipeableReminderRow(Reminder(900001, "Проверка безопасного свайпа", now.plusHours(1), null, "active", now),
                                false, false, {}, {}, { deletions++ }, {})
                        }
                    } else if (intent.getStringExtra("scenario") == "welcome") {
                        var visible by remember { mutableStateOf(true) }
                        AssistantScreen(state = AssistantUiState(result = "Результат команды"), actionNotice = null,
                            settings = settings.copy(showAssistantWelcomeCard = visible), edit = {}, primaryAction = {},
                            beginPushToTalk = {}, endPushToTalk = {}, play = {}, editCreatedNote = {}, changeCreatedFolder = {},
                            openReminder = {}, changeReminderTime = { _, _, _ -> }, undoLastAction = {}, dismissNotice = {},
                            answerReminderDateClarification = {}, hideWelcomeCard = { visible = false })
                    } else {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                            Box(Modifier.fillMaxSize()) {
                            Column(Modifier.padding(top = 100.dp).width(280.dp)) {
                                AppNoticeCard(intent.getStringExtra("gallery_message") ?: "Заметка добавлена. Проверьте текст и выберите папку.", {},
                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                        val bounds = coordinates.boundsInWindow()
                                        galleryBounds = Rect(bounds.left.toInt(), bounds.top.toInt(), kotlin.math.ceil(bounds.right).toInt(), kotlin.math.ceil(bounds.bottom).toInt())
                                    }) {
                                    val actions = intent.getStringArrayListExtra("gallery_actions") ?: arrayListOf("edit", "folder", "undo")
                                    for (action in actions.sortedBy { if (it == "cancel" || it == "undo") 1 else 0 }) when (action) {
                                        "edit" -> AppNoticeAction(Icons.Default.Edit, "Редактировать", {})
                                        "folder" -> AppNoticeAction(Icons.Default.FolderOpen, "Сменить папку", {})
                                        "undo" -> AppNoticeAction(Icons.AutoMirrored.Filled.Undo, "Отменить", {})
                                        "clock" -> AppNoticeAction(Icons.Default.Schedule, "Сменить время", {})
                                        "cancel" -> AppNoticeTextAction("Отмена", {})
                                        "no-time" -> AppNoticeTextAction("Без времени", {})
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}
