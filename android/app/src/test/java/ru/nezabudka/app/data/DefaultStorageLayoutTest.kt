package ru.nezabudka.app.data

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultStorageLayoutTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `flattening preserves existing and legacy models without overwriting`() {
        val parent = temporary.root
        File(parent, "НеЗабудьКа/models/gigaam").mkdirs()
        File(parent, "НеЗабудьКа/models/gigaam/one.onnx").writeText("model")
        File(parent, "НеЗабудьКа/data").mkdirs()
        File(parent, "НеЗабудьКа/data/assistant-backup.json").writeText("backup")
        assertEquals(parent, migrateDefaultStorageLayout(parent))
        assertEquals("model", File(parent, "models/gigaam/one.onnx").readText())
        assertEquals("backup", File(parent, "data/assistant-backup.json").readText())
        assertFalse(File(parent, "НеЗабудьКа").exists())
        File(parent, "LocalAssistant/models/gigaam").mkdirs()
        File(parent, "LocalAssistant/models/gigaam/one.onnx").writeText("different")
        migrateDefaultStorageLayout(parent)
        assertEquals("model", File(parent, "models/gigaam/one.onnx").readText())
        assertEquals("different", File(parent, "LocalAssistant/models/gigaam/one.onnx").readText())
    }
}
