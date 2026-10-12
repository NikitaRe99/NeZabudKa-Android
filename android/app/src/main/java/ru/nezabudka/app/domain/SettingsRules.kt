package ru.nezabudka.app.domain

private val THEMES = setOf("light", "dark", "custom")
private val STT_ENGINES = setOf("android", "whisper", "yandex", "google_cloud", "vk_cloud")
private val REMINDER_SORTS = setOf("my_order", "date", "recently_marked", "title")
private val NOTE_SORTS = setOf("created", "updated", "manual")
private val FOLDER_INTERFACES = setOf("carousel", "drawer")
private val ALARM_PRESENTATIONS = setOf(ALARM_PRESENTATION_SCREEN, ALARM_PRESENTATION_NOTIFICATION)
private val UI_PERFORMANCE_MODES = setOf(UI_PERFORMANCE_AUTO, UI_PERFORMANCE_REDUCED, UI_PERFORMANCE_FULL)
val NOTE_TRASH_RETENTION_OPTIONS = listOf(0, 1, 7, 30, 90)
const val AUTOMATIC_WHISPER_THREAD_COUNT = 0

fun normalizeTheme(value: String): String = value.takeIf(THEMES::contains) ?: "light"
fun normalizeSttEngine(value: String): String = value.takeIf(STT_ENGINES::contains) ?: "whisper"
fun normalizeReminderSort(value: String): String = value.takeIf(REMINDER_SORTS::contains) ?: "my_order"
fun normalizeNoteSort(value: String): String = value.takeIf(NOTE_SORTS::contains) ?: "updated"
fun normalizeFolderInterface(value: String): String = value.takeIf(FOLDER_INTERFACES::contains) ?: "carousel"
fun normalizeAlarmPresentation(value: String): String =
    value.takeIf(ALARM_PRESENTATIONS::contains) ?: ALARM_PRESENTATION_SCREEN
fun normalizeUiPerformanceMode(value: String): String =
    value.takeIf(UI_PERFORMANCE_MODES::contains) ?: UI_PERFORMANCE_AUTO
fun normalizeNoteTrashRetentionDays(value: Int): Int =
    value.takeIf(NOTE_TRASH_RETENTION_OPTIONS::contains) ?: DEFAULT_NOTE_TRASH_RETENTION_DAYS
fun normalizeWhisperThreadCount(value: Int): Int =
    value.coerceAtLeast(AUTOMATIC_WHISPER_THREAD_COUNT)

fun maximumWhisperThreadCount(cpuCores: Int): Int = cpuCores.coerceAtLeast(1)

fun automaticWhisperThreadCount(cpuCores: Int, lowRamDevice: Boolean, memoryClassMb: Int): Int {
    val cores = cpuCores.coerceAtLeast(1)
    return when {
        cores <= 2 -> cores
        lowRamDevice || memoryClassMb <= 192 -> minOf(2, cores)
        cores <= 4 -> cores - 1
        else -> minOf(4, cores)
    }
}

fun effectiveWhisperThreadCount(
    configured: Int,
    cpuCores: Int,
    lowRamDevice: Boolean,
    memoryClassMb: Int,
): Int {
    val available = maximumWhisperThreadCount(cpuCores)
    val normalized = normalizeWhisperThreadCount(configured)
    return if (normalized == AUTOMATIC_WHISPER_THREAD_COUNT) {
        automaticWhisperThreadCount(cpuCores, lowRamDevice, memoryClassMb)
    } else {
        normalized.coerceAtMost(available)
    }
}

fun normalizeNotificationSound(value: String, fallback: String): String {
    val clean = value.trim()
    if (clean.isEmpty()) return ""
    return clean.takeIf {
        it.startsWith("content://", ignoreCase = true) ||
            it.startsWith("android.resource://", ignoreCase = true)
    } ?: fallback
}
