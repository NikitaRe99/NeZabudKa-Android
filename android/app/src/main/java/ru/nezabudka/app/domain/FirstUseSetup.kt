package ru.nezabudka.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime

enum class FirstUseSetupKind { TIME_OF_DAY, FOLDER_INTERFACE, REMINDER_DELIVERY, COMMAND_UNDERSTANDING }

fun shouldOfferNluSetup(commandRecognized: Boolean, enabled: Boolean, modelInstalled: Boolean): Boolean =
    !commandRecognized && (!enabled || !modelInstalled)

interface FirstUseSetupStore {
    fun wasOffered(kind: FirstUseSetupKind): Boolean
    fun markOffered(kind: FirstUseSetupKind)
    fun reset()
}

data class FirstUseSetupPrompt(
    val id: Long,
    val kind: FirstUseSetupKind,
    val phrase: String? = null,
    val folderName: String? = null,
    val requiresExactTime: Boolean = false,
)

/** Only an actual feature event can enqueue a prompt; construction never starts a tour. */
class FirstUseSetupController(private val store: FirstUseSetupStore) {
    private val _pending = MutableStateFlow<List<FirstUseSetupPrompt>>(emptyList())
    val pending: StateFlow<List<FirstUseSetupPrompt>> = _pending
    private var nextId = 0L

    fun wasOffered(kind: FirstUseSetupKind): Boolean = store.wasOffered(kind)

    @Synchronized fun offer(
        kind: FirstUseSetupKind,
        phrase: String? = null,
        folderName: String? = null,
        requiresExactTime: Boolean = false,
    ): Boolean {
        if (store.wasOffered(kind) || _pending.value.any { it.kind == kind }) return false
        store.markOffered(kind)
        _pending.value += FirstUseSetupPrompt(++nextId, kind, phrase, folderName, requiresExactTime)
        return true
    }

    @Synchronized fun finish(id: Long): FirstUseSetupPrompt? {
        val current = _pending.value.firstOrNull()?.takeIf { it.id == id } ?: return null
        _pending.value = _pending.value.drop(1)
        return current
    }

    @Synchronized fun reset() {
        _pending.value = emptyList()
        store.reset()
    }
}

fun parseFirstUseTime(value: String): LocalTime? {
    val match = Regex("(\\d{1,2}):(\\d{2})").matchEntire(value.trim()) ?: return null
    return runCatching { LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt()) }.getOrNull()
}
