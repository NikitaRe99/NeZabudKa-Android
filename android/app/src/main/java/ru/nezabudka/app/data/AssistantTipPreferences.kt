package ru.nezabudka.app.data

import android.content.Context
import org.json.JSONArray
import ru.nezabudka.app.domain.AssistantTipHistory
import ru.nezabudka.app.domain.AssistantTipStore

class AssistantTipPreferences(context: Context) : AssistantTipStore {
    private val prefs = context.getSharedPreferences("assistant_tips", Context.MODE_PRIVATE)
    override fun read(): AssistantTipHistory {
        val pending = runCatching {
            val names = JSONArray(prefs.getString("pending", "[]"))
            List(names.length()) { names.getString(it) }
        }.getOrDefault(emptyList())
        return AssistantTipHistory(prefs.getStringSet("offered", emptySet()).orEmpty().toSet(), pending,
            prefs.getInt("assistant_voice_commands", 0))
    }
    override fun write(history: AssistantTipHistory) {
        prefs.edit().putStringSet("offered", history.offered).putString("pending", JSONArray(history.pending).toString())
            .putInt("assistant_voice_commands", history.assistantVoiceCommands).apply()
    }
}
