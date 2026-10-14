package ru.nezabudka.app

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.nezabudka.app.data.AssistantTipPreferences
import ru.nezabudka.app.data.FirstUseSetupPreferences
import ru.nezabudka.app.data.ReminderEntity
import ru.nezabudka.app.domain.AssistantTipHistory
import ru.nezabudka.app.domain.AssistantTipKind
import ru.nezabudka.app.domain.FirstUseSetupKind
import java.time.LocalDate
import java.time.LocalDateTime

/** Screenshot fixtures exist only in the separate demo APK and its private database. */
class DemoApplication : AssistantApplication() {
    override fun onCreate() {
        super.onCreate()
        val preferences = getSharedPreferences("screenshot_demo", MODE_PRIVATE)
        if (preferences.getBoolean("seeded", false)) return
        runBlocking(Dispatchers.IO) {
            container.database.withTransaction {
                // Never duplicate or replace edits if a previous seed already committed.
                if (container.database.notesDao().noteCount() == 0 && container.database.reminderDao().count() == 0) {
                    seedNotes()
                    seedReminders()
                }
            }
        }
        container.settings.apply {
            setTheme("light")
            setNoteColumns(2)
            setNoteFolderInterface("drawer")
            setReminderSort("date")
            setShowTechnicalLog(false)
            setShowAssistantWelcomeCard(true)
            setReminderAlarmMode(true)
        }
        AssistantTipPreferences(this).write(AssistantTipHistory(AssistantTipKind.entries.map { it.name }.toSet()))
        FirstUseSetupKind.entries.forEach { FirstUseSetupPreferences(this).markOffered(it) }
        preferences.edit().putBoolean("seeded", true).commit()
    }

    // Sample dates are for browsing/screenshots. Only explicit user actions (including
    // the alarm test in Settings) should start delivery, not opening the demo app.
    override fun startUiServices() = Unit

    private suspend fun seedNotes() {
        val notes = container.notes
        listOf("Личное", "Работа", "Путешествия", "Идеи").forEach { notes.createFolder(it) }
        suspend fun note(folder: String, title: String, text: String, color: Int? = null, pinned: Boolean = false) {
            notes.save(null, listOf(folder), title, text, pinned, false, color?.let { "theme-note:$it" })
        }
        note("Личное", "Купить к выходным", "Овощи для салата\nСвежий хлеб\nКофе в зёрнах\nКорм для кота", 2, true)
        note("Личное", "Книги на осень", "Михаил Булгаков — «Мастер и Маргарита»\nРэй Брэдбери — «Вино из одуванчиков»\nНачать с двадцати минут чтения перед сном.", 6)
        note("Личное", "Уютный вечер", "Приготовить чай с мятой, выбрать фильм и отложить телефон. Иногда лучший план — никуда не спешить.")
        note("Работа", "Встреча с командой", "Обсудить новый экран приложения.\nПроверить крупный шрифт и тёмную тему.\nСобрать обратную связь к пятнице.", 7, true)
        note("Работа", "План на неделю", "Завершить макеты\nПодготовить презентацию\nСогласовать сроки следующего этапа", 4)
        note("Путешествия", "Выходные на Байкале", "Погулять по набережной в Листвянке. Выпить горячий чай с видом на озеро. На обратном пути остановиться у смотровой площадки.", 5, true)
        note("Путешествия", "Взять с собой", "Тёплую куртку и удобную обувь\nТермос и перекус\nЗарядку и внешний аккумулятор\nФотоаппарат", 1)
        note("Идеи", "Маленькие привычки", "Каждый день записывать одну хорошую мысль. Раз в неделю перечитывать заметки и выбирать идею, которую хочется воплотить.", 3)
        note("Идеи", "Подарок для мамы", "Собрать альбом с семейными фотографиями и добавить к каждому снимку короткую историю.", 8)
    }

    private suspend fun seedReminders() {
        val today = LocalDate.now()
        val created = today.atTime(8, 0)
        suspend fun reminder(text: String, date: LocalDateTime?, recurrence: String? = null,
            favorite: Boolean = false, completed: Boolean = false) {
            container.database.reminderDao().insert(ReminderEntity(text = text, remindAt = date,
                recurrence = recurrence, createdAt = created, favorite = favorite,
                status = if (completed) "completed" else "active",
                completedAt = if (completed) today.atTime(10, 30) else null))
        }
        reminder("Забрать заказ из пункта выдачи", today.atTime(18, 30), favorite = true)
        reminder("Выйти на прогулку", today.atTime(19, 0))
        reminder("Полить растения", today.atTime(20, 0), "daily")
        reminder("Позвонить родителям", today.atTime(20, 30))
        reminder("Встреча с командой", today.plusDays(1).atTime(10, 0), favorite = true)
        reminder("Купить продукты к выходным", today.plusDays(1).atTime(18, 0))
        reminder("Записаться на стрижку", null)
        reminder("Оплатить интернет", today.minusDays(1).atTime(19, 0))
        reminder("Забрать книгу из библиотеки", today.atTime(10, 0), completed = true)
        reminder("Отправить документы", today.atTime(9, 30), completed = true)
    }
}
