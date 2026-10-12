package ru.nezabudka.app.ui.screens

internal enum class SettingsCategory(val title: String, val description: String) {
    SPEECH("Речь и команды", "Whisper, точность, потоки и пунктуация"),
    REMINDERS("Напоминания", "Будильник, разрешения и время суток"),
    NOTES("Заметки", "Интерфейс папок, корзина и хранение заметок"),
    APPEARANCE("Интерфейс", "Тема, оформление и производительность"),
    STORAGE("Данные и модели", "Каталог, резервные копии и очистка кэша"),
    ABOUT("О приложении", "Обновления, версия и автор"),
}

internal data class SettingsEntry(val key: String, val category: SettingsCategory, val title: String, val keywords: String) {
    fun matches(query: String): Boolean {
        val haystack = "${category.title} $title $keywords".lowercase().replace('ё', 'е')
        return query.trim().lowercase().replace('ё', 'е').split(Regex("\\s+")).all { it in haystack }
    }
}

internal val SETTINGS_CATALOG = listOf(
    SettingsEntry("Данные и хранилище", SettingsCategory.STORAGE, "Данные и хранилище", "каталог файлы модели резервная копия сохранение кэш память импорт"),
    SettingsEntry("Интерфейс", SettingsCategory.APPEARANCE, "Оформление", "тема светлая темная цвет тени навигация подписи значок иконка приложения незабудка галочка часы"),
    SettingsEntry("interface-behavior", SettingsCategory.APPEARANCE, "Поведение интерфейса", "производительность плавность скорость облегченный лог журнал локально облако время всплывающих уведомлений помощник начальные подсказки карточка скажите сохраните примеры команды"),
    SettingsEntry("Уведомления", SettingsCategory.REMINDERS, "Уведомления", "звук мелодия экран пробуждение"),
    SettingsEntry("folder-interface", SettingsCategory.NOTES, "Интерфейс папок", "заметки карусель боковая панель вид переключение папок"),
    SettingsEntry("alarm-mode", SettingsCategory.REMINDERS, "Будильник", "режим окно полноэкранный плавающие поверх заблокированный экран всплывающие"),
    SettingsEntry("alarm-sound", SettingsCategory.REMINDERS, "Мелодия будильника", "звук музыка громкость"),
    SettingsEntry("alarm-timing", SettingsCategory.REMINDERS, "Повторы будильника", "длительность пауза сигнал количество повторов"),
    SettingsEntry("notification-permissions", SettingsCategory.REMINDERS, "Разрешения и фоновая работа", "автозапуск батарея уведомления точные сигналы вибрация фон ограничения плавающие всплывающие окна"),
    SettingsEntry("Время суток", SettingsCategory.REMINDERS, "Время суток", "утром днем вечером ночью фраза час"),
    SettingsEntry("Корзина заметок", SettingsCategory.NOTES, "Корзина заметок", "удаление хранение очистка срок дней"),
    SettingsEntry("Распознавание речи", SettingsCategory.SPEECH, "Распознавание речи", "движок whisper виспер микрофон голос текст подтверждение выполнять сразу прослушивание аудио"),
    SettingsEntry("yandex", SettingsCategory.SPEECH, "Yandex SpeechKit", "яндекс облако ключ токен api"),
    SettingsEntry("google_cloud", SettingsCategory.SPEECH, "Google Cloud", "гугл облако ключ токен oauth"),
    SettingsEntry("vk_cloud", SettingsCategory.SPEECH, "VK Cloud", "вк облако ключ токен endpoint"),
    SettingsEntry("Пунктуация голосовых заметок", SettingsCategory.SPEECH, "Пунктуация голосовых заметок", "запятые знаки препинания rupunc модель скачать"),
    SettingsEntry("Модель Whisper.cpp", SettingsCategory.SPEECH, "Модель Whisper.cpp", "виспер потоки обработка точность точный режим скорость тест оптимальное количество скачать импорт память tiny base small ru"),
    SettingsEntry("Распознавание команд", SettingsCategory.SPEECH, "Распознавание команд", "nlu ntu модель расширенное скачать импорт"),
    SettingsEntry("О приложении", SettingsCategory.ABOUT, "О приложении", "версия автор связь сброс первый запуск обучение предложения"),
    SettingsEntry("github-updates", SettingsCategory.ABOUT, "Обновления", "github релиз скачать установить новая версия автоматически wifi wi-fi"),
)

internal fun availableSettingsEntries(engine: String, localOnly: Boolean, alarmMode: Boolean): List<SettingsEntry> =
    SETTINGS_CATALOG.filter {
        when (it.key) {
            "alarm-sound", "alarm-timing" -> alarmMode
            "Модель Whisper.cpp" -> engine == "whisper"
            "yandex", "google_cloud", "vk_cloud" -> !localOnly && engine == it.key
            else -> true
        }
    }
