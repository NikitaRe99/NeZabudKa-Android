package ru.nezabudka.app.ui.screens

internal enum class SettingsCategory(val title: String, val description: String) {
    SPEECH("Речь и команды", "GigaAM, Whisper, команды и пунктуация"),
    REMINDERS("Напоминания", "Будильник, разрешения и время суток"),
    NOTES("Заметки", "Интерфейс папок, корзина и хранение заметок"),
    APPEARANCE("Интерфейс", "Тема, оформление и производительность"),
    STORAGE("Данные и модели", "Каталог, резервные копии и очистка кэша"),
    ABOUT("О приложении", if (ru.nezabudka.app.BuildConfig.GITHUB_UPDATES_ENABLED) "Обновления, версия и автор" else "Версия и автор"),
}

internal data class SettingsEntry(val key: String, val category: SettingsCategory, val title: String, val keywords: String) {
    fun matches(query: String): Boolean {
        val haystack = "${category.title} $title $keywords".lowercase().replace('ё', 'е')
        return query.trim().lowercase().replace('ё', 'е').split(Regex("\\s+")).all { it in haystack }
    }
}

internal val SETTINGS_CATALOG = listOf(
    SettingsEntry("data-files", SettingsCategory.STORAGE, "Заметки и напоминания", "файл экспорт импорт сохранить восстановить json резервная копия папки"),
    SettingsEntry("Данные и хранилище", SettingsCategory.STORAGE, "Каталог данных и моделей", "каталог папка файлы модели данные переустановка хранение whisper nlu пунктуация"),
    SettingsEntry("storage-cache", SettingsCategory.STORAGE, "Временные файлы", "кэш кеш память очистить удалить загрузки аудио"),
    SettingsEntry("Интерфейс", SettingsCategory.APPEARANCE, "Оформление", "тема светлая темная цвет тени навигация подписи значок иконка приложения незабудка галочка часы"),
    SettingsEntry("interface-behavior", SettingsCategory.APPEARANCE, "Поведение интерфейса", "производительность плавность скорость облегченный лог журнал время всплывающих уведомлений помощник начальная подсказка карточка запомните голосом примеры примеры команды"),
    SettingsEntry("Уведомления", SettingsCategory.REMINDERS, "Уведомления", "звук мелодия экран пробуждение"),
    SettingsEntry("folder-interface", SettingsCategory.NOTES, "Интерфейс папок", "заметки карусель боковая панель вид переключение папок"),
    SettingsEntry("alarm-mode", SettingsCategory.REMINDERS, "Будильник", "мелодия звук музыка громкость окно способ показа"),
    SettingsEntry("reminder-mode", SettingsCategory.REMINDERS, "Режим напоминаний", "обычные уведомления будильник переключатель вибрация"),
    SettingsEntry("alarm-timing", SettingsCategory.REMINDERS, "Повторы будильника", "длительность пауза сигнал количество повторов"),
    SettingsEntry("notification-permissions", SettingsCategory.REMINDERS, "Разрешения и работа в фоне", "автозапуск батарея уведомления точные сигналы фон ограничения плавающие всплывающие окна экран блокировки"),
    SettingsEntry("Время суток", SettingsCategory.REMINDERS, "Время суток", "утром днем вечером ночью фраза час"),
    SettingsEntry("Корзина заметок", SettingsCategory.NOTES, "Корзина заметок", "удаление хранение очистка срок дней"),
    SettingsEntry("Распознавание речи", SettingsCategory.SPEECH, "Распознавание речи", "движок whisper виспер микрофон голос текст подтверждение выполнять сразу прослушивание аудио"),
    SettingsEntry("yandex", SettingsCategory.SPEECH, "Yandex SpeechKit", "яндекс облако ключ токен api"),
    SettingsEntry("google_cloud", SettingsCategory.SPEECH, "Google Cloud", "гугл облако ключ токен oauth"),
    SettingsEntry("vk_cloud", SettingsCategory.SPEECH, "VK Cloud", "вк облако ключ токен endpoint"),
    SettingsEntry("Пунктуация голосового ввода", SettingsCategory.SPEECH, "Пунктуация голосового ввода", "запятые знаки препинания rupunc модель скачать помощник напоминания заметки редактор голос"),
    SettingsEntry("Модель Whisper.cpp", SettingsCategory.SPEECH, "Whisper", "виспер чистый без словарей подсказок исправлений потоки обработка точность точный режим скорость тест оптимальное количество скачать импорт память base q5 small ru"),
    SettingsEntry("Модель GigaAM", SettingsCategory.SPEECH, "GigaAM", "гигаам модель v1 v2 v3 int8 потоки память оптимальное количество скачать импорт офлайн"),
    SettingsEntry("Распознавание команд", SettingsCategory.SPEECH, "Распознавание команд", "nlu ntu модель расширенное скачать импорт"),
    SettingsEntry("О приложении", SettingsCategory.ABOUT, "О приложении", "версия автор связь сброс первый запуск обучение предложения"),
    SettingsEntry("github-updates", SettingsCategory.ABOUT, "Обновления", "github релиз скачать установить новая версия автоматически поиск запуск wifi wi-fi"),
)

internal fun availableSettingsEntries(engine: String, alarmMode: Boolean, updatesEnabled: Boolean = ru.nezabudka.app.BuildConfig.GITHUB_UPDATES_ENABLED): List<SettingsEntry> =
    SETTINGS_CATALOG.filter {
        when (it.key) {
            "github-updates" -> updatesEnabled
            "alarm-mode", "alarm-timing" -> alarmMode
            "Уведомления" -> !alarmMode
            "Модель Whisper.cpp" -> engine == "whisper"
            "Модель GigaAM" -> engine == "gigaam"
            "yandex", "google_cloud", "vk_cloud" -> engine == it.key
            else -> true
        }
    }
