package ru.nezabudka.app.application

import ru.nezabudka.app.domain.AssistantCommand
import ru.nezabudka.app.domain.CommandIntent
import ru.nezabudka.app.domain.CommandContext
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.domain.CommandValidationException
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.NoteFolder
import ru.nezabudka.app.domain.DeletedNoteFolder
import ru.nezabudka.app.domain.Reminder
import ru.nezabudka.app.ports.CommandUnderstandingPort
import ru.nezabudka.app.ports.NotesRepository
import ru.nezabudka.app.ports.ReminderRepository
import ru.nezabudka.app.ports.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class DispatcherTest {
    @Test fun `punctuated voice commands keep daily recurrence and calendar clarification`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher, fixedClock)
        val voice = ru.nezabudka.app.speech.restoreVoicePunctuation(
            ru.nezabudka.app.ports.SpeechRecognition("Напомни каждое утро осматривать аквариум"), "rupunct_small",
            { _, _ -> "Напомни, каждое утро, осматривать аквариум." }, { throw AssertionError(it) })
        val saved = useCase(voice.text).reminders.single()
        assertEquals("daily", saved.recurrence)
        assertEquals("осматривать аквариум", saved.text)
        val request = useCase("Напомни, в следующем году, заменить коврик.")
        val resolved = useCase("Пятого февраля, в восемь.", reminderDateClarification = request.reminderDateClarification)
        assertEquals(LocalDateTime.of(2027, 2, 5, 8, 0), resolved.reminders.single().remindAt)
        assertEquals("заменить коврик", resolved.reminders.single().text)
    }

    @Test fun `a calendar period supplied in clarification persists until a day is given`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher, fixedClock)
        val draft = useCase("Напомни заменить коврик")
        val scoped = useCase("в следующем году вечером", reminderDateClarification = draft.reminderDateClarification)
        assertEquals(2027, scoped.reminderDateClarification!!.dateRange!!.start.year)
        assertTrue(reminderRepo.values.isEmpty())
        val saved = useCase("5 февраля", reminderDateClarification = scoped.reminderDateClarification)
        assertEquals(LocalDateTime.of(2027, 2, 5, 19, 0), saved.reminders.single().remindAt)
    }

    @Test fun `repeat can be supplied in date reply and persists through another clarification`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher, fixedClock)
        val draft = useCase("Напомни осматривать аквариум")
        val saved = useCase("каждое утро", reminderDateClarification = draft.reminderDateClarification)
        assertEquals("daily", saved.reminders.single().recurrence)
        assertEquals(LocalDateTime.now(fixedClock).toLocalDate().plusDays(1).atTime(9, 0), saved.reminders.single().remindAt)
        val vague = useCase("Напомни в следующем году заменить коврик")
        val more = useCase("каждое утро", reminderDateClarification = vague.reminderDateClarification)
        assertEquals("daily", more.reminderDateClarification!!.recurrence)
        val resolved = useCase("5 февраля", reminderDateClarification = more.reminderDateClarification)
        assertEquals("daily", resolved.reminders.single().recurrence)
        assertEquals(LocalDateTime.of(2027, 2, 5, 9, 0), resolved.reminders.single().remindAt)
    }

    @Test fun `future year clarification retains recurrence time and scope before creating anything`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher, fixedClock)
        val draft = useCase("Напомни в следующем году каждое утро осматривать аквариум")
        val clarification = draft.reminderDateClarification!!
        assertTrue(reminderRepo.values.isEmpty())
        assertEquals("осматривать аквариум", clarification.text)
        assertEquals("daily", clarification.recurrence)
        val outside = useCase("завтра", reminderDateClarification = clarification)
        assertTrue(outside.reminderDateClarification != null)
        assertTrue(reminderRepo.values.isEmpty())
        val saved = useCase("5 февраля", reminderDateClarification = outside.reminderDateClarification)
        assertEquals(LocalDateTime.of(2027, 2, 5, 9, 0), saved.reminders.single().remindAt)
        assertEquals("daily", saved.reminders.single().recurrence)
        assertEquals("осматривать аквариум", saved.reminders.single().text)
    }

    @Test fun `month clarification accepts a bare day and implicit daily commands keep repetition`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher, fixedClock)
        val draft = useCase("Напомни в следующем месяце в восемь заменить коврик")
        val saved = useCase("18 числа", reminderDateClarification = draft.reminderDateClarification)
        assertEquals(LocalDateTime.of(2026, 8, 18, 8, 0), saved.reminders.single().remindAt)
        assertEquals("заменить коврик", saved.reminders.single().text)
        val implicit = useCase("Мне нужно по вечерам осматривать аквариум")
        assertEquals("daily", implicit.reminders.single().recurrence)
    }

    @Test fun `notes stay loading until both repository snapshots arrive and preserve state on return`() = kotlinx.coroutines.test.runTest {
        val note = notesRepo.create("Работа", "Существующая заметка")
        val folder = notesRepo.findFolder("Работа")!!
        val folderFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<NoteFolder>>(replay = 1)
        val noteFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<Note>>(replay = 1)
        val repository = object : NotesRepository by notesRepo {
            override fun observeFolders(): Flow<List<NoteFolder>> = folderFlow
            override fun observeAll(): Flow<List<Note>> = noteFlow
        }
        val feature = ru.nezabudka.app.ui.NotesFeature(repository, backgroundScope, { _, _ -> }, { throw it })
        val collector = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { feature.state.collect {} }
        assertTrue(feature.state.value.isLoading)
        folderFlow.emit(listOf(folder))
        runCurrent()
        assertTrue(feature.state.value.isLoading)
        noteFlow.emit(listOf(note))
        runCurrent()
        assertFalse(feature.state.value.isLoading)
        assertEquals(listOf(note), feature.state.value.allNotes)
        collector.cancel()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(listOf(note), feature.state.value.allNotes)
        assertFalse(feature.state.value.isLoading)
    }
    @Test fun `manual folder setup fires for new folders including implicit save but not reused folders`() = kotlinx.coroutines.test.runTest {
        val offered = mutableListOf<String>()
        val feature = ru.nezabudka.app.ui.NotesFeature(notesRepo, backgroundScope, { _, _ -> }, { throw it },
            onFolderCreated = { offered += it.name }, needsFolderSetup = { true })
        feature.createFolder("Работа", false) {}.join()
        feature.createFolder("работа", false) {}.join()
        assertEquals(listOf("Работа"), offered)
        feature.save(null, listOf("Личные"), "", "Текст", false, false, null) {}.join()
        feature.save(null, listOf("Личные"), "", "Другой текст", false, false, null) {}.join()
        assertEquals(listOf("Работа", "Личные"), offered)
    }

    @Test fun `first reminder setup is queued before permission callback and never on edit`() = kotlinx.coroutines.test.runTest {
        val order = mutableListOf<String>()
        val feature = ru.nezabudka.app.ui.ReminderFeature(reminderRepo, ReminderActions(reminderRepo, scheduler, fixedClock),
            backgroundScope, { throw it }, onReminderCreated = { order += "setup" })
        feature.create("Проверка", date(12), null, false) { order += "permission" }.join()
        assertEquals(listOf("setup", "permission"), order)
        feature.save(reminderRepo.values.single(), "Изменено", date(13), null, false, false) {}.join()
        assertEquals(listOf("setup", "permission"), order)
    }

    @Test fun `preview does not create a reminder and chosen named time applies to same command`() = runBlocking {
        var morning = java.time.LocalTime.of(9, 0)
        val temporal = ru.nezabudka.app.domain.RussianTemporalParser { mapOf("утром" to morning) }
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher, fixedClock, temporal)
        val text = "Напомни каждый день завтра утром проверить почту"
        assertEquals(CommandIntent.CREATE_REMINDER, useCase.preview(text, CommandContext()).intent)
        assertTrue(reminderRepo.values.isEmpty())
        assertEquals("утром", useCase.namedTimePhraseUsed(text))
        morning = java.time.LocalTime.of(7, 30)
        useCase(text)
        val reminder = reminderRepo.values.single()
        assertEquals(LocalDateTime.now(fixedClock).toLocalDate().plusDays(1).atTime(7, 30), reminder.remindAt)
        assertEquals("daily", reminder.recurrence)
    }
    @Test fun `monthly speech recurrence survives the requested start date`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher, fixedClock)
        val request = useCase("Напомни каждый месяц поливать цветы")
        assertEquals("monthly", request.reminderDateClarification?.recurrence)
        val saved = useCase("завтра в девять", reminderDateClarification = request.reminderDateClarification)
        assertEquals("monthly", saved.reminders.single().recurrence)
        assertEquals("поливать цветы", saved.reminders.single().text)
        assertTrue(saved.message.contains("Каждый месяц"))
        assertFalse(dispatcher.dispatch(AssistantCommand(CommandIntent.UNKNOWN)).commandRecognized)
    }
    @Test fun `repeat survives the direct command path and clarification`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher, fixedClock)
        useCase("Напомни каждый день завтра в десять двадцать пять полить рассаду")
        assertEquals("daily", reminderRepo.values.single().recurrence)
        assertEquals("полить рассаду", reminderRepo.values.single().text)
        assertEquals(LocalDateTime.now(fixedClock).toLocalDate().plusDays(1).atTime(10, 25), reminderRepo.values.single().remindAt)
        val request = useCase("Напомни каждый вторник проверить крепления двери")
        assertTrue(reminderRepo.values.size == 1)
        assertTrue(request.reminderDateClarification?.recurrence?.startsWith("custom:v1") == true)
        val retry = useCase("без даты", reminderDateClarification = request.reminderDateClarification)
        assertEquals(request.reminderDateClarification, retry.reminderDateClarification)
        useCase("завтра в девять", reminderDateClarification = retry.reminderDateClarification)
        val created = reminderRepo.values.last()
        val rule = ru.nezabudka.app.domain.CustomReminderRecurrence.fromStored(created.recurrence)!!
        assertEquals(created.remindAt, rule.start)
        assertEquals(java.time.DayOfWeek.TUESDAY, rule.start.dayOfWeek)
    }

    @Test fun `negated and unsupported changes never execute even if NLU predicts creation`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.CREATE_NOTE, noteText = "не должна создаться") }, dispatcher, fixedClock)
        listOf("Перекинь заметку с чертежом из одной папки в другую", "Отмени удаление папки Тестовый блокнот", "Не создавай напоминание про очистку принтера").forEach { useCase(it) }
        assertTrue(notesRepo.notes.isEmpty())
        assertTrue(reminderRepo.values.isEmpty())
    }
    @Test fun `folder insertion without note noun uses the existing folder and keeps the whole text`() = runBlocking {
        dispatcher.dispatch(AssistantCommand(CommandIntent.CREATE_FOLDER, noteFolder = "Работа"))
        var modelCalls = 0
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> modelCalls++; AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher)
        listOf("В папку работа добавь заметку сходить к врачу и купить бумаги",
            "В папку работа добавь, заметку сходить к врачу и купить бумаги",
            "В папку работа добавь сходить к врачу и купить бумаги").forEach { useCase(it) }
        assertEquals(1, notesRepo.folderNames.size)
        assertEquals(listOf("Работа"), notesRepo.folderNames.values.map { it.second })
        assertEquals(List(3) { "сходить к врачу и купить бумаги" }, notesRepo.notes.map { it.text })
        assertEquals(0, modelCalls)
    }
    @Test fun `folder before note action preserves a multiword name`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher)
        useCase("В папку Домашние рецепты добавь заметку испечь яблочный пирог")
        assertEquals(listOf("Домашние рецепты"), notesRepo.folderNames.values.map { it.second })
        assertEquals(listOf("испечь яблочный пирог"), notesRepo.notes.map { it.text })
    }
    @Test fun `unsupported media recording and completed statements never create notes`() = runBlocking {
        var calls = 0
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ ->
            calls++; AssistantCommand(CommandIntent.CREATE_NOTE, noteText = "не должна создаться")
        }, dispatcher)
        listOf("Запиши видео с камеры", "Сохрани мне голосовое сообщение", "Я уже записал номер договора").forEach { useCase(it) }
        assertTrue(notesRepo.notes.isEmpty())
        assertTrue(reminderRepo.values.isEmpty())
        assertEquals(0, calls)
    }

    @Test fun `a note may quote a media command or a completed statement`() = runBlocking {
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher)
        useCase("Создай заметку: Запиши видео с камеры")
        useCase("Создай заметку: Я уже записал номер договора")
        assertEquals(listOf("Запиши видео с камеры", "Я уже записал номер договора"), notesRepo.notes.map { it.text })
    }
    private val reminderRepo = FakeReminderRepository()
    private val notesRepo = FakeNotesRepository()
    private val scheduler = FakeScheduler()
    private val fixedClock = Clock.fixed(Instant.parse("2026-07-14T10:20:00Z"), ZoneOffset.UTC)
    private val dispatcher = Dispatcher(reminderRepo, notesRepo, scheduler, fixedClock)

    @Test fun `alternative command verb forms reach NLU and preserve the note payload`() = runBlocking {
        val inputs = mutableListOf<String>()
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { text, now, _ ->
            inputs += text
            ru.nezabudka.app.llm.ClassicNluCommandMapper().map(
                if (text.startsWith("покажи")) CommandIntent.LIST_REMINDERS else CommandIntent.CREATE_NOTE,
                emptyMap(), text, now)
        }, dispatcher, fixedClock)
        val list = useCase.preview("Отобразить дела на завтра", CommandContext())
        assertEquals(CommandIntent.LIST_REMINDERS, list.intent)
        assertEquals(date(0).plusDays(1), list.periodStart)
        val note = useCase.preview("Зафиксируем мысль завтра зафиксируем результат", CommandContext())
        assertEquals("завтра зафиксируем результат", note.noteText)
        assertEquals(listOf("покажи дела на завтра", "зафиксируй мысль завтра зафиксируем результат"), inputs)
        assertTrue(notesRepo.notes.isEmpty())
    }

    @Test fun `verb normalization does not bypass a rejected model intent`() = runBlocking {
        var calls = 0
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ ->
            calls++
            AssistantCommand(CommandIntent.UNKNOWN)
        }, dispatcher, fixedClock)
        for (input in listOf("Зафиксируем мысль проверить питание")) {
            assertEquals(CommandIntent.UNKNOWN, useCase.preview(input, CommandContext()).intent)
        }
        assertEquals(1, calls)
        assertTrue(notesRepo.notes.isEmpty())
    }

    @Test fun `future listing projects repetitions without changing stored date or scheduling`() = runBlocking {
        val daily = reminderRepo.create("ежедневное", date(11), "daily")
        reminderRepo.create("однократное", date(12), null)
        val completed = reminderRepo.create("выполненное", date(13), "daily")
        reminderRepo.setCompleted(completed.id, true)
        val start = LocalDateTime.of(2026, 8, 1, 0, 0)
        val result = dispatcher.dispatch(AssistantCommand(CommandIntent.LIST_REMINDERS,
            periodStart = start, periodEnd = start.plusMonths(1)))
        assertEquals(listOf(daily.id), result.reminders.map { it.id })
        assertEquals(start.plusHours(11), result.reminders.single().remindAt)
        assertEquals(date(11), reminderRepo.values.first { it.id == daily.id }.remindAt)
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test fun `future listing respects exclusive end and exhausted custom recurrence`() = runBlocking {
        val start = date(11).plusDays(1)
        reminderRepo.create("на границе", start.plusDays(1), null)
        reminderRepo.create("закончилось", date(11), ru.nezabudka.app.domain.CustomReminderRecurrence(
            1, ru.nezabudka.app.domain.ReminderRecurrenceUnit.DAY, emptySet(), date(11), repeatCount = 1).storedValue())
        reminderRepo.create("без даты", null, "daily")
        val result = dispatcher.dispatch(AssistantCommand(CommandIntent.LIST_REMINDERS,
            periodStart = start.plusDays(1), periodEnd = start.plusDays(2)))
        assertEquals(listOf("на границе"), result.reminders.map { it.text })
        val excluded = dispatcher.dispatch(AssistantCommand(CommandIntent.LIST_REMINDERS,
            periodStart = start, periodEnd = start.plusDays(1)))
        assertEquals(listOf("закончилось"), excluded.reminders.map { it.text })
        assertEquals(start, excluded.reminders.single().remindAt)
    }

    @Test fun `create and delete reminder schedules correctly`() = runBlocking {
        val creation = dispatcher.dispatch(AssistantCommand(CommandIntent.CREATE_REMINDER, text = "духовка", remindAt = date(12)))
        val created = creation.reminders.single()
        assertEquals(created, (creation.effect as ActionEffect.ReminderCreated).reminder)
        assertEquals(listOf(created.id), scheduler.scheduled)

        val deletion = dispatcher.dispatch(AssistantCommand(CommandIntent.DELETE_REMINDER, targetId = created.id))
        assertTrue(reminderRepo.values.isEmpty())
        assertEquals(listOf(created.id), scheduler.cancelled)
        val deleted = (deletion.effect as ActionEffect.ReminderDeleted).reminder
        assertEquals("духовка", deleted.text)
        assertEquals(date(12), deleted.remindAt)
    }

    @Test fun `dated reminders sort first and null dates last`() = runBlocking {
        reminderRepo.create("без даты", null, null)
        reminderRepo.create("позже", date(15), null)
        reminderRepo.create("раньше", date(11), null)
        reminderRepo.create("просрочено", date(9), null)
        val result = dispatcher.dispatch(AssistantCommand(CommandIntent.LIST_REMINDERS))
        assertEquals(listOf("раньше", "позже", "без даты"), result.reminders.map { it.text })
        assertFalse(result.message.contains(Regex("(?m)^\\d+\\.")))
        assertTrue(result.message.lines().all { it.startsWith("• ") })
        assertTrue(result.effect is ActionEffect.RemindersListed)
    }

    @Test fun `explicit note folder is created and reused case insensitively`() = runBlocking {
        dispatcher.dispatch(AssistantCommand(CommandIntent.CREATE_NOTE, noteText = "проверить API", noteFolder = "Проект X"))
        dispatcher.dispatch(AssistantCommand(CommandIntent.CREATE_NOTE, noteText = "написать тест", noteFolder = "проект x"))
        assertEquals(1, notesRepo.folderNames.size)
        assertEquals(listOf("проверить API", "написать тест"), notesRepo.notes.map { it.text })
    }

    @Test fun `folder can be created without note`() = runBlocking {
        val result = dispatcher.dispatch(AssistantCommand(CommandIntent.CREATE_FOLDER, noteFolder = "Поездки"))
        assertEquals(listOf("Поездки"), notesRepo.folderNames.values.map { it.second })
        assertTrue(notesRepo.notes.isEmpty())
        assertTrue(result.effect is ActionEffect.FolderCreated)
    }

    @Test fun `folder voice deletion returns restorable undo snapshot`() = runBlocking {
        var llmCalls = 0
        val useCase = ExecuteTextCommand(CommandUnderstandingPort { _, _, _ -> llmCalls++; AssistantCommand(CommandIntent.UNKNOWN) }, dispatcher)
        useCase("Создай папку Поездки")
        val deleted = useCase("Удали папку Поездки")
        assertEquals(0, llmCalls)
        assertTrue(notesRepo.folderNames.isEmpty())
        val effect = deleted.effect as ActionEffect.FolderDeleted
        notesRepo.restoreFolder(effect.snapshot)
        assertEquals(listOf("Поездки"), notesRepo.folderNames.values.map { it.second })
    }

    @Test fun `undated reminder bypasses understanding and waits for date clarification`() = runBlocking {
        var calls = 0
        val llm = CommandUnderstandingPort { _, _, _ ->
            calls++
            AssistantCommand(CommandIntent.CREATE_REMINDER, text = "проверка")
        }
        val useCase = ExecuteTextCommand(llm, dispatcher, Clock.fixed(Instant.parse("2026-07-14T02:20:00Z"), ZoneOffset.UTC))
        assertTrue(reminderRepo.values.isEmpty())
        val request = useCase("Напомни проверка")
        assertEquals(0, calls)
        assertTrue(reminderRepo.values.isEmpty())
        assertEquals("проверка", request.reminderDateClarification?.text)
        assertTrue(request.effect is ActionEffect.ReminderDateRequested)

        useCase(
            "завтра в восемь",
            reminderDateClarification = request.reminderDateClarification,
        )
        assertEquals(1, reminderRepo.values.size)
        assertEquals(LocalDateTime.of(2026, 7, 15, 8, 0), reminderRepo.values.single().remindAt)
    }

    @Test fun `rejected speech result falls back to a recognized alternative`() = runBlocking {
        val inputs = mutableListOf<String>()
        val understanding = CommandUnderstandingPort { text, _, _ ->
            inputs += text
            if (text == "сводка дел") AssistantCommand(CommandIntent.LIST_REMINDERS)
            else AssistantCommand(CommandIntent.UNKNOWN)
        }
        val useCase = ExecuteTextCommand(understanding, dispatcher)

        val result = useCase(
            userText = "непонятная фраза",
            alternatives = listOf("сводка дел"),
        )

        assertEquals(listOf("непонятная фраза", "сводка дел"), inputs)
        assertTrue(result.effect is ActionEffect.RemindersListed)
    }

    @Test fun `date clarification can be retried cancelled or explicitly omitted`() = runBlocking {
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) },
            dispatcher,
            Clock.fixed(Instant.parse("2026-07-14T02:20:00Z"), ZoneOffset.UTC),
        )
        val request = useCase("Напомни выбросить мусор")
        val retry = useCase("когда будет удобно", reminderDateClarification = request.reminderDateClarification)
        assertTrue(reminderRepo.values.isEmpty())
        assertEquals(request.reminderDateClarification, retry.reminderDateClarification)

        val cancelled = useCase("отмена", reminderDateClarification = retry.reminderDateClarification)
        assertEquals("Создание напоминания отменено.", cancelled.message)
        assertTrue(reminderRepo.values.isEmpty())

        val secondRequest = useCase("Напомни выбросить мусор")
        useCase("создай без времени", reminderDateClarification = secondRequest.reminderDateClarification)
        assertEquals(null, reminderRepo.values.single().remindAt)
    }

    @Test fun `optional understanding never calls llm while disabled`() = runBlocking {
        var enabled = false
        var calls = 0
        val optional = OptionalCommandUnderstanding(
            delegate = CommandUnderstandingPort { _, _, _ ->
                calls++
                AssistantCommand(CommandIntent.CREATE_REMINDER, text = "купить хлеб", remindAt = date(12))
            },
            enabled = { enabled },
        )
        val useCase = ExecuteTextCommand(optional, dispatcher)

        useCase("Напомни завтра позвонить")
        val unknown = useCase("Сохрани в памяти купить хлеб")
        assertEquals(0, calls)
        assertEquals("Не удалось распознать команду.", unknown.message)

        enabled = true
        useCase("Сохрани в памяти купить хлеб")
        assertEquals(1, calls)
        assertEquals(listOf("позвонить", "купить хлеб"), reminderRepo.values.map { it.text })
    }

    @Test fun `reminder marker after date and time bypasses understanding layer`() = runBlocking {
        var calls = 0
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ ->
                calls++
                AssistantCommand(CommandIntent.UNKNOWN)
            },
            dispatcher,
            Clock.fixed(Instant.parse("2026-07-14T10:20:00Z"), ZoneOffset.UTC),
        )

        val reminder = useCase("Завтра в 8 напомни позвонить").reminders.single()

        assertEquals("позвонить", reminder.text)
        assertEquals(LocalDateTime.of(2026, 7, 15, 8, 0), reminder.remindAt)
        assertEquals(0, calls)
    }

    @Test fun `different undated reminder phrases request a date without llm`() = runBlocking {
        var calls = 0
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ ->
                calls++
                AssistantCommand(CommandIntent.UNKNOWN)
            },
            dispatcher,
            Clock.fixed(Instant.parse("2026-07-14T10:20:00Z"), ZoneOffset.UTC),
        )
        listOf(
            "Напомни купить молоко" to "купить молоко",
            "Создай напоминание позвонить Кате" to "позвонить Кате",
            "Создай мне напоминание проверить отчёт" to "проверить отчёт",
            "Сделай напоминание оплатить интернет" to "оплатить интернет",
            "Установи напоминание забрать заказ" to "забрать заказ",
            "Запланируй напоминание встреча" to "встреча",
        ).forEach { (phrase, expected) ->
            assertEquals(expected, useCase(phrase).reminderDateClarification?.text)
        }
        assertTrue(reminderRepo.values.isEmpty())
        assertEquals(0, calls)
    }

    @Test fun `implicit task wording falls back to reminder when classifier rejects it`() = runBlocking {
        var calls = 0
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ ->
                calls++
                AssistantCommand(CommandIntent.UNKNOWN)
            },
            dispatcher,
            Clock.fixed(Instant.parse("2026-07-14T10:20:00Z"), ZoneOffset.UTC),
        )

        listOf(
            "Надо сходить" to "сходить",
            "Нужно позвонить врачу" to "позвонить врачу",
            "Мне необходимо не забыть купить хлеб" to "купить хлеб",
        ).forEach { (phrase, expected) ->
            val result = useCase(phrase)
            assertEquals(expected, result.reminderDateClarification?.text)
            assertTrue(result.effect is ActionEffect.ReminderDateRequested)
        }
        val dated = useCase("Мне завтра надо забрать заказ").reminders.single()
        assertEquals("забрать заказ", dated.text)
        assertEquals(LocalDateTime.of(2026, 7, 15, 10, 20), dated.remindAt)
        assertEquals(4, calls)
    }

    @Test fun `implicit task fallback does not turn questions or meta commands into reminders`() = runBlocking {
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) },
            dispatcher,
        )

        assertEquals("Не удалось распознать команду.", useCase("Нужно ли сходить").message)
        assertEquals("Не удалось распознать команду.", useCase("Надо удалить последнее напоминание").message)
        assertTrue(reminderRepo.values.isEmpty())
    }

    @Test fun `day of month reminder uses date instead of reminder text`() = runBlocking {
        var calls = 0
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ ->
                calls++
                AssistantCommand(CommandIntent.UNKNOWN)
            },
            dispatcher,
            Clock.fixed(Instant.parse("2026-07-14T10:20:00Z"), ZoneOffset.UTC),
        )
        val reminder = useCase("Напомни 28 числа оплатить интернет").reminders.single()
        assertEquals("оплатить интернет", reminder.text)
        assertEquals(LocalDateTime.of(2026, 7, 28, 10, 20), reminder.remindAt)
        assertEquals(0, calls)
    }

    @Test fun `explicit reminder deletion bypasses understanding layer`() = runBlocking {
        var calls = 0
        reminderRepo.create("позвонить Кате", date(12), null)
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ ->
                calls++
                AssistantCommand(CommandIntent.UNKNOWN)
            },
            dispatcher,
        )

        val deletion = useCase("Убери напоминание позвонить Кате")

        assertEquals(0, calls)
        assertTrue(reminderRepo.values.isEmpty())
        assertEquals("позвонить Кате", (deletion.effect as ActionEffect.ReminderDeleted).reminder.text)
    }

    @Test fun `cancel last reminder returns an undo snapshot`() = runBlocking {
        reminderRepo.create("первое", date(11), null)
        reminderRepo.create("последнее", date(12), null)
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.UNKNOWN) },
            dispatcher,
        )

        val deletion = useCase("Отмени последнее напоминание")

        assertEquals(listOf("первое"), reminderRepo.values.map { it.text })
        assertEquals("последнее", (deletion.effect as ActionEffect.ReminderDeleted).reminder.text)
    }

    @Test fun `last reminder not needed formulation bypasses understanding layer`() = runBlocking {
        reminderRepo.create("первое", date(11), null)
        reminderRepo.create("последнее", date(12), null)
        var calls = 0
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ ->
                calls++
                AssistantCommand(CommandIntent.UNKNOWN)
            },
            dispatcher,
        )

        val deletion = useCase("Мне не нужно последнее напоминание")

        assertEquals(0, calls)
        assertEquals(listOf("первое"), reminderRepo.values.map { it.text })
        assertEquals("последнее", (deletion.effect as ActionEffect.ReminderDeleted).reminder.text)
    }

    @Test fun `clear list questions bypass llm and are filtered in Kotlin`() = runBlocking {
        reminderRepo.create("через четыре дня", LocalDateTime.of(2026, 7, 18, 9, 0), null)
        reminderRepo.create("на 28 июля", LocalDateTime.of(2026, 7, 28, 9, 0), null)
        reminderRepo.create("через пять дней", LocalDateTime.of(2026, 7, 19, 9, 0), null)
        reminderRepo.create("без даты", null, null)
        var calls = 0
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ ->
                calls++
                AssistantCommand(CommandIntent.LIST_REMINDERS)
            },
            dispatcher,
            Clock.fixed(Instant.parse("2026-07-14T10:20:00Z"), ZoneOffset.UTC),
        )

        val relative = useCase("Что у меня через четыре дня?")
        val calendar = useCase("Какие планы на 28 июля?")
        val all = useCase("Покажи все напоминания")

        assertEquals(listOf("через четыре дня"), relative.reminders.map { it.text })
        assertEquals(listOf("на 28 июля"), calendar.reminders.map { it.text })
        assertEquals(4, all.reminders.size)
        assertEquals(0, calls)
    }

    @Test fun `month part list uses kotlin range without llm`() = runBlocking {
        var calls = 0
        reminderRepo.create("начало", LocalDateTime.of(2026, 7, 5, 9, 0), null)
        reminderRepo.create("середина", LocalDateTime.of(2026, 7, 15, 9, 0), null)
        reminderRepo.create("конец", LocalDateTime.of(2026, 7, 25, 9, 0), null)
        reminderRepo.create("следующий месяц", LocalDateTime.of(2026, 8, 1, 9, 0), null)
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ ->
                calls++
                AssistantCommand(CommandIntent.UNKNOWN)
            },
            dispatcher,
            Clock.fixed(Instant.parse("2026-07-14T10:20:00Z"), ZoneOffset.UTC),
        )

        val result = useCase("Какие планы ближе к концу месяца?")

        assertEquals(0, calls)
        assertEquals(listOf("конец"), result.reminders.map { it.text })
    }

    @Test fun `soft reminder wording uses assistant screen context`() = runBlocking {
        val receivedScopes = mutableListOf<CommandScope>()
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, context ->
                receivedScopes += context.scope
                AssistantCommand(
                    CommandIntent.CREATE_REMINDER,
                    text = "позвонить Свете",
                    remindAt = LocalDateTime.of(2026, 7, 15, 8, 0),
                )
            },
            dispatcher,
        )

        listOf(
            "Мне нужно не забыть завтра к восьми позвонить Свете",
            "Не забудь завтра позвонить Свете",
        ).forEach { useCase(it) }

        assertEquals(listOf(CommandScope.ASSISTANT, CommandScope.ASSISTANT), receivedScopes)
        assertEquals(listOf("позвонить Свете", "позвонить Свете"), reminderRepo.values.map { it.text })
    }

    @Test fun `selected notes folder is used only on notes screen`() = runBlocking {
        val llm = CommandUnderstandingPort { _, _, _ -> AssistantCommand(CommandIntent.CREATE_NOTE, noteText = "проверить отчёт") }
        val useCase = ExecuteTextCommand(llm, dispatcher)
        val inFolder = useCase(
            "Создай заметку проверить отчёт",
            "Проекты",
            CommandContext(CommandScope.NOTES),
        ).notes.single()
        val withoutFolder = useCase(
            "Создай заметку позвонить Свете",
            "Проекты",
            CommandContext(CommandScope.ASSISTANT),
        ).notes.single()
        assertEquals("Проекты", inFolder.folderName)
        assertEquals(null, withoutFolder.folderName)
        assertEquals(listOf("Проекты"), notesRepo.folderNames.values.map { it.second })
    }

    @Test fun `single word folder without separator is split from note body`() = runBlocking {
        var llmCalls = 0
        val useCase = ExecuteTextCommand(
            CommandUnderstandingPort { _, _, _ -> llmCalls++; AssistantCommand(CommandIntent.UNKNOWN) },
            dispatcher,
        )

        val created = useCase("Создай заметку в папку Тест Подготовить отчёт").notes.single()
        val added = useCase("Добавь заметку в папку Тест Проверить цифры").notes.single()
        val folderFirst = useCase("Добавь в папку Тест заметку Проверить бюджет").notes.single()
        val folderLast = useCase("Создай заметку Купить билеты в папку Тест").notes.single()
        val createThenAdd = useCase("Создай заметку Купить билеты и добавь в папку Тест").notes.single()
        val addThenBody = useCase("Создай заметку и добавь в папку Тест Сверить договор").notes.single()

        assertEquals(0, llmCalls)
        assertEquals(
            List(6) { "Тест" },
            listOf(
                created.folderName,
                added.folderName,
                folderFirst.folderName,
                folderLast.folderName,
                createThenAdd.folderName,
                addThenBody.folderName,
            ),
        )
        assertEquals(
            listOf(
                "Подготовить отчёт",
                "Проверить цифры",
                "Проверить бюджет",
                "Купить билеты",
                "Купить билеты",
                "Сверить договор",
            ),
            listOf(created.text, added.text, folderFirst.text, folderLast.text, createThenAdd.text, addThenBody.text),
        )
    }

    @Test fun `explicit long note bypasses llm and preserves every sentence`() = runBlocking {
        var llmCalls = 0
        val llm = CommandUnderstandingPort { _, _, _ ->
            llmCalls++
            AssistantCommand(CommandIntent.UNKNOWN)
        }
        val useCase = ExecuteTextCommand(llm, dispatcher)
        val expected = "Купить молоко. Затем зайти в аптеку. Позвонить Кате вечером! Не забыть документы?"
        val result = useCase("Создай заметку $expected", context = CommandContext(CommandScope.NOTES))
        assertEquals(0, llmCalls)
        assertEquals(expected, result.notes.single().text)
    }

    @Test fun `long note folder instruction is removed without changing note body`() = runBlocking {
        var llmCalls = 0
        val llm = CommandUnderstandingPort { _, _, _ ->
            llmCalls++
            AssistantCommand(CommandIntent.UNKNOWN)
        }
        val useCase = ExecuteTextCommand(llm, dispatcher)
        val expected = "Подготовить отчёт. Проверить цифры. Отправить документ руководителю."
        val result = useCase(
            "Добавь в папку Работа заметку $expected",
            context = CommandContext(CommandScope.NOTES),
        )
        assertEquals(0, llmCalls)
        assertEquals(expected, result.notes.single().text)
        assertEquals("Работа", result.notes.single().folderName)
    }

    @Test fun `folder before period is separated from long note body`() = runBlocking {
        var llmCalls = 0
        val llm = CommandUnderstandingPort { _, _, _ ->
            llmCalls++
            AssistantCommand(CommandIntent.UNKNOWN)
        }
        val useCase = ExecuteTextCommand(llm, dispatcher)
        val body = "В земле была нора. А в норе жил хоббит. Нора была не противная и не грязная."
        val result = useCase(
            "Добавь заметку в папку О хоббитах. $body",
            context = CommandContext(CommandScope.ASSISTANT),
        )
        assertEquals(0, llmCalls)
        assertEquals("О хоббитах", result.notes.single().folderName)
        assertEquals(body, result.notes.single().text)
    }

    @Test fun `reminders scope rejects note returned by model`() = runBlocking {
        var receivedContext: CommandContext? = null
        val llm = CommandUnderstandingPort { _, _, context ->
            receivedContext = context
            AssistantCommand(CommandIntent.CREATE_NOTE, noteText = "не должна создаться")
        }
        val useCase = ExecuteTextCommand(llm, dispatcher)
        val failure = runCatching {
            useCase("Создай заметку", context = CommandContext(CommandScope.REMINDERS))
        }.exceptionOrNull()
        assertTrue(failure is CommandValidationException)
        assertEquals(CommandScope.REMINDERS, receivedContext?.scope)
        assertTrue(notesRepo.notes.isEmpty())
    }

    @Test fun `notes scope rejects reminder returned by model`() = runBlocking {
        val llm = CommandUnderstandingPort { _, _, _ ->
            AssistantCommand(CommandIntent.CREATE_REMINDER, text = "не должно создаться")
        }
        val useCase = ExecuteTextCommand(llm, dispatcher)
        val failure = runCatching {
            useCase("Напомни проверить", context = CommandContext(CommandScope.NOTES))
        }.exceptionOrNull()
        assertTrue(failure is CommandValidationException)
        assertTrue(reminderRepo.values.isEmpty())
    }

    @Test fun `folder shortcut is not executed in reminders scope`() = runBlocking {
        var calls = 0
        val llm = CommandUnderstandingPort { _, _, context ->
            calls++
            assertEquals(CommandScope.REMINDERS, context.scope)
            AssistantCommand(CommandIntent.UNKNOWN)
        }
        val useCase = ExecuteTextCommand(llm, dispatcher)
        useCase("Создай папку Личное", context = CommandContext(CommandScope.REMINDERS))
        assertEquals(1, calls)
        assertTrue(notesRepo.folderNames.isEmpty())
    }

    private fun date(hour: Int) = LocalDateTime.of(2026, 7, 14, hour, 0)
}

internal class FakeReminderRepository : ReminderRepository {
    val values = mutableListOf<Reminder>()
    private val flow = MutableStateFlow<List<Reminder>>(emptyList())
    private var nextId = 1L
    override fun observeAll(): Flow<List<Reminder>> = flow
    override suspend fun create(text: String, remindAt: LocalDateTime?, recurrence: String?): Reminder =
        Reminder(nextId++, text, remindAt, recurrence, "active", LocalDateTime.of(2026, 7, 14, 10, 0)).also { values += it; emit() }
    override suspend fun list(start: LocalDateTime?, end: LocalDateTime?): List<Reminder> = values.filter {
        (start == null || (it.remindAt != null && !it.remindAt.isBefore(start))) && (end == null || (it.remindAt != null && it.remindAt.isBefore(end)))
    }.sortedWith(compareBy<Reminder> { it.remindAt == null }.thenBy { it.remindAt }.thenBy { it.id })
    override suspend fun find(id: Long?, text: String?, last: Boolean): Reminder? = when {
        id != null -> values.find { it.id == id }; text != null -> values.lastOrNull { it.text.contains(text, true) }; last -> values.lastOrNull(); else -> null
    }
    override suspend fun update(id: Long, text: String?, remindAt: LocalDateTime?): Reminder {
        val index = values.indexOfFirst { it.id == id }
        values[index] = values[index].copy(text = text ?: values[index].text, remindAt = remindAt ?: values[index].remindAt)
        emit(); return values[index]
    }
    override suspend fun replace(id: Long, text: String, remindAt: LocalDateTime?): Reminder = mutate(id) {
        it.copy(text = text, remindAt = remindAt)
    }
    override suspend fun setRecurrence(id: Long, recurrence: String?): Reminder = mutate(id) {
        it.copy(recurrence = recurrence)
    }
    override suspend fun delete(id: Long) { values.removeAll { it.id == id }; emit() }
    override suspend fun setFavorite(id: Long, favorite: Boolean) { mutate(id) { it.copy(favorite = favorite) } }
    override suspend fun setCompleted(id: Long, completed: Boolean): Reminder = mutate(id) { it.copy(status = if (completed) "completed" else "active") }
    override suspend fun move(id: Long, beforeId: Long?) = Unit
    override suspend fun deleteCompleted() { values.removeAll { it.status == "completed" }; emit() }
    private fun mutate(id: Long, transform: (Reminder) -> Reminder): Reminder {
        val index = values.indexOfFirst { it.id == id }
        values[index] = transform(values[index]); emit(); return values[index]
    }
    private fun emit() { flow.value = values.toList() }
}

private class FakeNotesRepository : NotesRepository {
    val notes = mutableListOf<Note>()
    val folderNames = linkedMapOf<String, Pair<Long, String>>()
    override fun observeFolders(): Flow<List<NoteFolder>> = MutableStateFlow(folderNames.values.map { NoteFolder(it.first, it.second, LocalDateTime.now()) })
    override fun observeNotes(folderId: Long): Flow<List<Note>> = MutableStateFlow(notes.filter { it.folderId == folderId })
    override fun observeAll(): Flow<List<Note>> = MutableStateFlow(notes)
    override suspend fun createFolder(folderName: String): NoteFolder {
        val folder = folderNames.getOrPut(folderName.lowercase()) { folderNames.size.inc().toLong() to folderName }
        return NoteFolder(folder.first, folder.second, LocalDateTime.now())
    }
    override suspend fun findFolder(name: String): NoteFolder? = folderNames.values.firstOrNull { it.second.equals(name, true) }
        ?.let { NoteFolder(it.first, it.second, LocalDateTime.now()) }
    override suspend fun renameFolder(id: Long, name: String) = Unit
    override suspend fun moveFolder(id: Long, beforeId: Long?) = Unit
    override suspend fun moveNote(id: Long, beforeId: Long?) = Unit
    override suspend fun deleteFolder(id: Long): DeletedNoteFolder? {
        val entry = folderNames.entries.firstOrNull { it.value.first == id } ?: return null
        folderNames.remove(entry.key)
        return DeletedNoteFolder(NoteFolder(id, entry.value.second, LocalDateTime.now()), notes.filter { it.folderId == id }.map { it.id })
    }
    override suspend fun restoreFolder(snapshot: DeletedNoteFolder): NoteFolder = snapshot.folder.also {
        folderNames[it.name.lowercase()] = it.id to it.name
    }
    override suspend fun deleteNote(id: Long) { notes.removeAll { it.id == id } }
    override suspend fun deleteTrashed() { notes.removeAll { it.trashed } }
    override suspend fun deleteTrashedBefore(cutoff: LocalDateTime) {
        notes.removeAll { it.trashed && it.updatedAt <= cutoff }
    }
    override suspend fun create(folderName: String?, text: String): Note {
        if (folderName == null) return Note(notes.size.inc().toLong(), null, null, text, LocalDateTime.now()).also(notes::add)
        val actual = folderName
        val folder = folderNames.getOrPut(actual.lowercase()) { folderNames.size.inc().toLong() to actual }
        return Note(notes.size.inc().toLong(), folder.first, folder.second, text, LocalDateTime.now()).also(notes::add)
    }
    override suspend fun save(id: Long?, folderNames: List<String>, title: String, text: String, pinned: Boolean, archived: Boolean, color: String?): Note =
        create(folderNames.firstOrNull(), text)
    override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
    override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    override suspend fun setTrashed(id: Long, trashed: Boolean) = Unit
    override suspend fun setPinned(ids: List<Long>, pinned: Boolean) = Unit
    override suspend fun setArchived(ids: List<Long>, archived: Boolean) = Unit
    override suspend fun setTrashed(ids: List<Long>, trashed: Boolean) = Unit
    override suspend fun setColor(ids: List<Long>, color: String?) = Unit
    override suspend fun setFolderMembership(ids: List<Long>, folderName: String, included: Boolean) = Unit
}

internal class FakeScheduler : ReminderScheduler {
    var enabled = true
    override fun isEnabled() = enabled
    val scheduled = mutableListOf<Long>()
    val scheduledReminders = mutableListOf<Reminder>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(reminder: Reminder) {
        if (enabled && reminder.remindAt != null) { scheduled += reminder.id; scheduledReminders += reminder }
    }
    override fun cancel(reminderId: Long) { cancelled += reminderId }
    override fun isScheduled(reminderId: Long): Boolean = reminderId in scheduled
}
