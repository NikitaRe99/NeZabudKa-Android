package ru.nezabudka.app.data

import android.content.Context
import ru.nezabudka.app.domain.FirstUseSetupKind
import ru.nezabudka.app.domain.FirstUseSetupStore

class FirstUseSetupPreferences(context: Context) : FirstUseSetupStore {
    private val preferences = context.getSharedPreferences("first_use_setup", Context.MODE_PRIVATE)
    override fun wasOffered(kind: FirstUseSetupKind): Boolean = preferences.getBoolean(kind.name, false)
    override fun markOffered(kind: FirstUseSetupKind) { preferences.edit().putBoolean(kind.name, true).apply() }
    override fun reset() { preferences.edit().clear().apply() }
}
