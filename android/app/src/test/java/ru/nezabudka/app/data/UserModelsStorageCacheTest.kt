package ru.nezabudka.app.data

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserModelsStorageCacheTest {
    @Test
    fun `legacy default model directory is renamed without copying files`() {
        val legacy = File(temporaryFolder.root, "LocalAssistant").apply { mkdirs() }
        val bytes = byteArrayOf(1, 2, 3)
        File(legacy, "models/whisper/model.bin").apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }

        val current = migratedDefaultModelRoot(temporaryFolder.root, "НеЗабудьКа", "LocalAssistant")

        assertFalse(legacy.exists())
        assertArrayEquals(bytes, File(current, "models/whisper/model.bin").readBytes())
    }
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun legacyCleanupDeletesOnlyRootDownloadParts() {
        val legacyModel = temporaryFolder.newFile("model.bin.download.part").apply { writeBytes(ByteArray(11)) }
        val legacyWhisper = temporaryFolder.newFile("whisper.bin.download.part").apply { writeBytes(ByteArray(13)) }
        val unrelated = temporaryFolder.newFile("keep.part").apply { writeBytes(ByteArray(17)) }
        val currentDownload = File(temporaryFolder.root, "model-downloads/whisper/model.part").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(19))
        }

        val removed = cleanupLegacyModelDownloads(temporaryFolder.root)

        assertEquals(24L, removed)
        assertFalse(legacyModel.exists())
        assertFalse(legacyWhisper.exists())
        assertTrue(unrelated.exists())
        assertTrue(currentDownload.exists())
    }

    @Test
    fun explicitCleanupDeletesNativeCopiesAndInactiveDownloads() {
        val cachedTiny = File(temporaryFolder.root, "native-models/whisper/tiny.bin").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(23))
        }
        val cachedBase = File(temporaryFolder.root, "native-models/whisper/base.bin").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(29))
        }
        val currentDownload = File(temporaryFolder.root, "model-downloads/whisper/model.part").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(31))
        }

        val removed = clearDisposableModelCache(temporaryFolder.root)

        assertEquals(83L, removed)
        assertFalse(cachedTiny.exists())
        assertFalse(cachedBase.exists())
        assertFalse(File(temporaryFolder.root, "native-models").exists())
        assertFalse(currentDownload.exists())
    }

    @Test
    fun cleanupPreservesDownloadsReportedAsActive() {
        val currentDownload = File(temporaryFolder.root, "model-downloads/whisper/model.part").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(31))
        }

        val removed = clearDisposableModelCache(temporaryFolder.root, includeIncompleteDownloads = false)

        assertEquals(0L, removed)
        assertTrue(currentDownload.exists())
    }

    @Test
    fun staleCleanupAndStatsAreScopedToDisposableCache() {
        val native = File(temporaryFolder.root, "native-models/whisper/model.bin").apply {
            parentFile!!.mkdirs(); writeBytes(ByteArray(23))
        }
        val oldPart = File(temporaryFolder.root, "model-downloads/whisper/old.part").apply {
            parentFile!!.mkdirs(); writeBytes(ByteArray(31)); setLastModified(1_000L)
        }
        val recentPart = File(temporaryFolder.root, "model-downloads/whisper/recent.part").apply {
            parentFile!!.mkdirs(); writeBytes(ByteArray(37)); setLastModified(9_000L)
        }
        val audio = File(temporaryFolder.root, "recordings/live.wav").apply {
            parentFile!!.mkdirs(); writeBytes(ByteArray(41))
        }

        val stats = modelCacheStats(temporaryFolder.root)
        val removed = cleanupStaleModelDownloadParts(
            temporaryFolder.root,
            maxAgeMillis = 5_000L,
            nowMillis = 10_000L,
        )

        assertEquals(23L, stats.nativeCopiesBytes)
        assertEquals(68L, stats.incompleteDownloadsBytes)
        assertEquals(41L, stats.temporaryAudioBytes)
        assertEquals(31L, removed)
        assertTrue(native.exists())
        assertFalse(oldPart.exists())
        assertTrue(recentPart.exists())
        assertTrue(audio.exists())
    }
}
