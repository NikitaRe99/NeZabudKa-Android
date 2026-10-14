package ru.nezabudka.app.ui.screens

import org.junit.Assert.*
import org.junit.Test

class SettingsCatalogTest {
    @Test fun `search finds settings by their purpose and multiple words`() {
        val entries = availableSettingsEntries("whisper", false)
        assertTrue(entries.filter { it.matches("потоки") }.any { it.key == "Модель Whisper.cpp" })
        assertTrue(entries.filter { it.matches("чистый whisper") }.any { it.key == "Модель Whisper.cpp" })
        assertTrue(entries.filter { it.matches("автозапуск") }.any { it.key == "notification-permissions" })
        assertTrue(entries.filter { it.matches("плавающие окна") }.any { it.key == "notification-permissions" })
        assertTrue(entries.filter { it.matches("ТЁМНАЯ") }.any { it.key == "Интерфейс" })
        assertTrue(entries.filter { it.matches("первый запуск") }.any { it.key == "О приложении" })
        assertTrue(entries.none { it.matches("несуществующий параметр") })
    }

    @Test fun `hidden cloud and alarm options are not misleading search results`() {
        val local = availableSettingsEntries("whisper", false).map { it.key }
        assertFalse("yandex" in local)
        assertFalse("alarm-timing" in local)
        assertFalse("alarm-mode" in local)
        assertTrue("Уведомления" in local)
        assertTrue("reminder-mode" in local)
        val cloud = availableSettingsEntries("yandex", true).map { it.key }
        assertTrue("yandex" in cloud)
        assertTrue("alarm-timing" in cloud)
        assertTrue("alarm-mode" in cloud)
        assertFalse("Уведомления" in cloud)
        assertTrue("reminder-mode" in cloud)
        assertFalse("google_cloud" in cloud)
        assertFalse("Модель Whisper.cpp" in cloud)
        assertEquals(SETTINGS_CATALOG.size, SETTINGS_CATALOG.map { it.key }.distinct().size)
        assertEquals(SettingsCategory.entries.toSet(), SETTINGS_CATALOG.map { it.category }.toSet())
    }
    @Test fun `store builds hide updates in the catalog and search`() {
        val store = availableSettingsEntries("whisper", false, updatesEnabled = false)
        assertTrue(store.none { it.key == "github-updates" })
        assertTrue(store.none { it.matches("github релиз") })
        assertTrue(availableSettingsEntries("whisper", false, updatesEnabled = true).any { it.key == "github-updates" })
    }
}
