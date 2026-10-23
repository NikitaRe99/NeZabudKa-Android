package ru.nezabudka.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AssistantSettingsTarget(val query: String, val path: String) {
    SCREENS("экраны приложения", "Настройки → Интерфейс → Экраны приложения"),
    SPEECH("", "Настройки → Речь и команды → Распознавание речи"),
    WHISPER("потоки", "Настройки → Речь и команды → Whisper → Проверить оптимальное количество потоков"),
    GIGAAM("потоки", "Настройки → Речь и команды → GigaAM → Проверить оптимальное количество потоков"),
    LIVE_TEXT("текст во время записи", "Настройки → Речь и команды → GigaAM → Текст во время записи"),
    EDITOR_PUNCTUATION("пунктуация", "Настройки → Речь и команды → Пунктуация голосового ввода → Другие знаки в редакторе заметок"),
    IMMEDIATE_COMMANDS("выполнять команды сразу", "Настройки → Речь и команды → Распознавание речи → Выполнять команды сразу"),
    TIMES("время суток", "Настройки → Напоминания → Время суток"),
    TRASH("корзина заметок", "Настройки → Заметки → Корзина заметок"),
    FOLDERS("интерфейс папок", "Настройки → Заметки → Интерфейс папок"),
    NLU("распознавание команд", "Настройки → Речь и команды → Распознавание команд"),
    PUNCTUATION("пунктуация", "Настройки → Речь и команды → Пунктуация голосового ввода"),
    REMINDER_MODE("режим напоминаний", "Настройки → Напоминания → Режим напоминаний"),
    REMINDER_ACCESS("разрешения", "Настройки → Напоминания → Разрешения и работа в фоне"),
    ALARM("будильник", "Настройки → Напоминания → Будильник"),
    WELCOME("начальные подсказки", "Настройки → Интерфейс → Поведение интерфейса → Начальная подсказка"),
    CLOUD("", "Настройки → Речь и команды → Выбранный облачный сервис"),
    MICROPHONE("", "Настройки телефона → Приложения → НеЗабудьКа → Разрешения → Микрофон"),
}

enum class AssistantTipKind(val title: String, val description: String, val button: String, val target: AssistantSettingsTarget) {
    RECOGNITION("Выберите способ распознавания",
        "GigaAM и Whisper распознают речь на телефоне без интернета. Также доступны распознавание Android и облачные сервисы. Для облачных сервисов нужны интернет и собственные ключи доступа.",
        "Настроить распознавание", AssistantSettingsTarget.SPEECH),
    WHISPER_SPEED("Можно ускорить распознавание",
        "Проверьте, сколько потоков лучше использовать на вашем телефоне. Сравнение выполняется на последней голосовой записи.",
        "Проверить потоки", AssistantSettingsTarget.WHISPER),
    GIGAAM_SPEED("Можно ускорить распознавание",
        "Проверьте, сколько потоков лучше использовать на вашем телефоне. Сравнение выполняется на последней голосовой записи.",
        "Проверить потоки", AssistantSettingsTarget.GIGAAM),
    LIVE_TEXT("Текст во время речи",
        "Текст может появляться ещё во время записи. Последние слова могут меняться; окончательный результат появится после отпускания кнопки.",
        "Настроить предварительный текст", AssistantSettingsTarget.LIVE_TEXT),
    EDITOR_PUNCTUATION("Больше знаков в заметках",
        "В редакторе заметок модель пунктуации может добавлять точки, вопросительные и другие знаки. Перед сохранением проверьте результат.",
        "Настроить знаки препинания", AssistantSettingsTarget.EDITOR_PUNCTUATION),
    IMMEDIATE_COMMANDS("Команды можно выполнять сразу",
        "Можно выполнять голосовые команды сразу после распознавания, без подтверждения текста. Включите эту возможность, если результат обычно не требует исправлений.",
        "Настроить выполнение команд", AssistantSettingsTarget.IMMEDIATE_COMMANDS),
    TIME_OF_DAY("Во сколько — утром или вечером?",
        "Для слов о времени суток используются сохранённые значения. Их можно изменить для следующих напоминаний.",
        "Настроить время суток", AssistantSettingsTarget.TIMES),
    FOLDERS("Выберите удобный вид папок",
        "Карусель показывает папки над заметками, боковая панель — списком сбоку. Способ переключения можно изменить в любое время.",
        "Интерфейс папок", AssistantSettingsTarget.FOLDERS),
    NLU_ENABLE("Попробуйте расширенное распознавание команд",
        "NLU-классификатор помогает понимать более свободные формулировки команд. Выберите или скачайте модель и включите «Расширенное распознавание команд».",
        "Настроить распознавание команд", AssistantSettingsTarget.NLU),
    PUNCTUATION("Добавляйте знаки препинания",
        "Whisper уже может добавлять знаки препинания. При желании можно включить дополнительную модель пунктуации.",
        "Настроить пунктуацию", AssistantSettingsTarget.PUNCTUATION),
    REMINDER_MODES("Как напоминать о делах",
        "Уведомление даёт короткий сигнал, будильник — длительный сигнал с возможностью повторов. Выбранный режим можно дополнительно проверить.",
        "Выбрать режим и проверить", AssistantSettingsTarget.REMINDER_MODE),
    REMINDER_DELIVERY("Чтобы уведомления приходили вовремя",
        "Проверьте уведомления, точное время срабатывания и ограничения работы в фоне. На некоторых телефонах также нужен автозапуск.",
        "Проверить разрешения", AssistantSettingsTarget.REMINDER_ACCESS),
    ALARM_WINDOW("Будильник отдельным окном",
        "Будильник можно показывать уведомлением или отдельным окном. Для окна поверх приложений нужен специальный доступ, а на заблокированном экране — полноэкранный доступ и разрешения конкретного телефона.",
        "Настроить будильник", AssistantSettingsTarget.ALARM),
    WELCOME_RESTORE("Начальную подсказку можно вернуть",
        "Примеры голосовых команд можно вернуть, включив «Начальная подсказка» в настройках интерфейса.",
        "Начальная подсказка", AssistantSettingsTarget.WELCOME),
}

data class AssistantTipHistory(val offered: Set<String> = emptySet(), val pending: List<String> = emptyList(), val assistantVoiceCommands: Int = 0)
interface AssistantTipStore { fun read(): AssistantTipHistory; fun write(history: AssistantTipHistory) }

class AssistantTipController(private val store: AssistantTipStore) {
    private fun canonical(name: String) = when (name) {
        "NLU_USAGE" -> AssistantTipKind.NLU_ENABLE.name
        "DEVICE_BACKGROUND" -> AssistantTipKind.REMINDER_DELIVERY.name
        else -> name
    }
    private var history = store.read().let { saved ->
        val pending = saved.pending.map(::canonical).distinct()
        AssistantTipHistory(saved.offered.map(::canonical).toSet() + pending, pending, saved.assistantVoiceCommands.coerceIn(0, 3))
    }.also(store::write)
    private val _pending = MutableStateFlow(active())
    val pending: StateFlow<List<AssistantTipKind>> = _pending
    private fun active() = history.pending.mapNotNull { name -> AssistantTipKind.entries.firstOrNull { it.name == name } }.distinct()
    @Synchronized fun wasOffered(kind: AssistantTipKind) = kind.name in history.offered
    @Synchronized fun offer(kind: AssistantTipKind) {
        if (wasOffered(kind)) return
        history = history.copy(offered = history.offered + kind.name, pending = history.pending + kind.name)
        store.write(history)
        _pending.value = active()
    }
    @Synchronized fun acknowledge(kind: AssistantTipKind) {
        history = history.copy(pending = history.pending.filterNot { it == kind.name })
        store.write(history)
        _pending.value = active()
    }
    @Synchronized fun assistantVoiceCommandCompleted(immediateEnabled: Boolean) {
        history = history.copy(assistantVoiceCommands = (history.assistantVoiceCommands + 1).coerceAtMost(3))
        store.write(history)
        if (history.assistantVoiceCommands >= 3 && !immediateEnabled) offer(AssistantTipKind.IMMEDIATE_COMMANDS)
    }
    @Synchronized fun reset() {
        history = AssistantTipHistory()
        store.write(history)
        _pending.value = emptyList()
    }
}

/** Permission cards precede setup suggestions; equal priorities keep event order. */
fun AssistantTipKind.priority(): Int = when (this) {
    AssistantTipKind.REMINDER_DELIVERY, AssistantTipKind.ALARM_WINDOW -> 0
    AssistantTipKind.RECOGNITION, AssistantTipKind.NLU_ENABLE -> 1
    AssistantTipKind.REMINDER_MODES, AssistantTipKind.TIME_OF_DAY -> 2
    else -> 3
}
fun prioritizedAssistantTips(tips: List<AssistantTipKind>) = tips.sortedBy { it.priority() }

data class ReminderPermissionStatus(
    val notifications: Boolean, val exactTime: Boolean, val background: Boolean,
    val fullScreen: Boolean, val overlay: Boolean,
    val backgroundVerifiable: Boolean = true,
) {
    fun deliveryReady() = notifications && exactTime && (background || !backgroundVerifiable)
    fun alarmWindowReady() = deliveryReady() && fullScreen && overlay
}
