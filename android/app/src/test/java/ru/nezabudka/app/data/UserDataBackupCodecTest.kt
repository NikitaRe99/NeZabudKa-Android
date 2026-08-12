package ru.nezabudka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataBackupCodecTest {
    @Test
    fun `version one backup remains readable`() {
        val decoded = UserDataBackupCodec.decode(VERSION_ONE_FIXTURE)

        assertEquals(1, decoded.version)
        assertEquals("Позвонить", decoded.reminders.single().text)
        assertEquals(listOf(7L), decoded.notes.single().folderIds)
        assertEquals("", decoded.notes.single().title)
        assertEquals(9L, decoded.notes.single().manualOrder)
        assertNull(decoded.settings)
    }

    @Test
    fun `current backup round trip preserves data and portable settings`() {
        val original = UserDataBackupDto(
            reminders = listOf(
                BackupReminderDto(
                    id = 3,
                    text = "Встреча",
                    remindAt = "2026-07-30T18:00:00",
                    recurrence = null,
                    status = "active",
                    createdAt = "2026-07-26T10:00:00",
                    favorite = true,
                    manualOrder = 3,
                    completedAt = null,
                ),
            ),
            folders = emptyList(),
            notes = listOf(
                BackupNoteDto(
                    id = 4,
                    primaryFolderId = null,
                    folderIds = emptyList(),
                    text = "Основной текст",
                    createdAt = "2026-07-26T10:00:00",
                    updatedAt = "2026-07-26T10:00:00",
                    pinned = false,
                    archived = false,
                    trashed = false,
                    color = "theme-note:2",
                    title = "Название",
                    manualOrder = 12,
                ),
            ),
            settings = PortableAppSettings(theme = "dark", selectedWhisperModel = "ggml-base.bin"),
        )

        val encoded = UserDataBackupCodec.encode(original)

        assertEquals(original, UserDataBackupCodec.decode(encoded))
        assertEquals("Название", UserDataBackupCodec.decode(encoded).notes.single().title)
        assertEquals(12L, UserDataBackupCodec.decode(encoded).notes.single().manualOrder)
        val normalized = encoded.lowercase()
        assertFalse("API keys must not be exported", "apikey" in normalized)
        assertFalse("OAuth and service tokens must not be exported", "token" in normalized)
        assertFalse("credential containers must not be exported", "credential" in normalized)
    }

    @Test
    fun `unsupported and malformed backups are rejected before restore`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(VERSION_ONE_FIXTURE.replace("\"version\": 1", "\"version\": 0"))
        }
        assertThrows(Exception::class.java) { UserDataBackupCodec.decode("{not-json") }
    }

    @Test
    fun `unknown fields from newer writers do not break supported backup`() {
        val decoded = UserDataBackupCodec.decode(
            VERSION_ONE_FIXTURE.replace("\"version\": 1", "\"version\": 1, \"futureField\": true"),
        )
        assertTrue(decoded.reminders.isNotEmpty())
    }

    @Test
    fun `restore plan validates every timestamp and id before database writes`() {
        val valid = UserDataBackupCodec.decode(VERSION_ONE_FIXTURE)
        assertEquals(1, BackupRestorePlan.from(valid).notes.size)

        assertThrows(IllegalArgumentException::class.java) {
            BackupRestorePlan.from(
                valid.copy(notes = valid.notes.map { it.copy(updatedAt = "not-a-date") }),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestorePlan.from(
                valid.copy(reminders = valid.reminders + valid.reminders.single()),
            )
        }
    }

    @Test
    fun `restore plan ignores dangling folder references but preserves note`() {
        val valid = UserDataBackupCodec.decode(VERSION_ONE_FIXTURE)
        val plan = BackupRestorePlan.from(
            valid.copy(notes = valid.notes.map { it.copy(primaryFolderId = 100, folderIds = listOf(7, 100)) }),
        )

        assertNull(plan.notes.single().entity.folderId)
        assertEquals(listOf(7L), plan.notes.single().folderIds)
    }

    private companion object {
        val VERSION_ONE_FIXTURE = """
            {
              "version": 1,
              "reminders": [{
                "id": 1,
                "text": "Позвонить",
                "remindAt": "2026-07-27T09:30:00",
                "recurrence": null,
                "status": "active",
                "createdAt": "2026-07-26T08:00:00",
                "favorite": false,
                "manualOrder": 1,
                "completedAt": null
              }],
              "folders": [{
                "id": 7,
                "name": "Работа",
                "createdAt": "2026-07-20T12:00:00",
                "manualOrder": 7
              }],
              "notes": [{
                "id": 9,
                "primaryFolderId": 7,
                "folderIds": [7],
                "text": "Подготовить отчёт",
                "createdAt": "2026-07-21T10:00:00",
                "updatedAt": "2026-07-22T11:00:00",
                "pinned": true,
                "archived": false,
                "trashed": false,
                "color": null
              }]
            }
        """.trimIndent()
    }
}
