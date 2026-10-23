package ru.nezabudka.app.domain

val DEFAULT_APP_SCREENS = listOf("assistant", "reminders", "notes")

/** At least one collection must remain accessible; the assistant alone is not useful. */
fun validAppScreens(screens: Collection<String>): Boolean =
    screens.isNotEmpty() && screens.all { it in DEFAULT_APP_SCREENS } &&
        screens.any { it == "notes" || it == "reminders" }

fun normalizeAppScreens(screens: Collection<String>): List<String> {
    val ordered = DEFAULT_APP_SCREENS.filter { it in screens }
    return if (validAppScreens(ordered)) ordered else listOf("notes")
}
