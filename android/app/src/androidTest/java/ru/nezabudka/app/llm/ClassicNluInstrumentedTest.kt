package ru.nezabudka.app.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ru.nezabudka.app.domain.CommandContext
import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.domain.CommandScope
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only integration test: no command is sent to Dispatcher or a repository. */
@RunWith(AndroidJUnit4::class)
class ClassicNluInstrumentedTest {
    @Test
    fun recognizesRepresentativeCommandsAndRejectsUnsafeFalsePositives() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = ClassicNluClient(context, ClassicNluCommandMapper(), ru.nezabudka.app.data.UserModelsStorage(context))
        assumeTrue("NLU model is optional and is not installed", client.modelState.value.installed)
        val cases = listOf(
            Case("надо не забыть выбросить мусор", CommandIntent.CREATE_REMINDER),
            Case("мне через два часа нужно забрать посылку", CommandIntent.CREATE_REMINDER),
            Case("какие дела запланированы", CommandIntent.LIST_REMINDERS),
            Case("сохрани мысль приложение должно работать офлайн", CommandIntent.CREATE_NOTE),
            Case("создай раздел Важные документы", CommandIntent.CREATE_FOLDER),
            Case("папка Старые проекты больше не нужна", CommandIntent.DELETE_FOLDER),
            Case("удали напоминание про оплату интернета", CommandIntent.DELETE_REMINDER),
            Case("измени последнее напоминание", CommandIntent.UNKNOWN),
            Case("переименуй папку Работа", CommandIntent.UNKNOWN),
            Case("удали заметку про пароль", CommandIntent.UNKNOWN),
        )
        try {
            cases.forEach { case ->
                val actual = client.understand(
                    case.text,
                    LocalDateTime.of(2026, 8, 8, 12, 0),
                    CommandContext(CommandScope.ASSISTANT, "ru"),
                )
                assertEquals(case.text, case.intent, actual.intent)
                val diagnostic = client.diagnostic.value
                assertEquals(true, diagnostic.requestId > 0)
                assertEquals(true, diagnostic.tokenPieces.isNotEmpty())
                assertEquals(true, diagnostic.intentScores.isNotEmpty())
            }
        } finally {
            client.release()
        }
    }

    private data class Case(val text: String, val intent: CommandIntent)
}
