package ru.nezabudka.app.speech

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WhisperVadModelProviderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun modelValidationRejectsPartialDownloads() {
        val model = temporaryFolder.newFile("vad.bin")
        RandomAccessFile(model, "rw").use { it.setLength(WHISPER_VAD_MODEL_BYTES - 1L) }

        assertFalse(isValidWhisperVadModel(model))
    }

    @Test
    fun modelValidationAcceptsBundledSize() {
        val model = temporaryFolder.newFile("vad.bin")
        RandomAccessFile(model, "rw").use { it.setLength(WHISPER_VAD_MODEL_BYTES) }

        assertTrue(isValidWhisperVadModel(model))
    }

    @Test
    fun missingModelIsRejected() {
        assertFalse(isValidWhisperVadModel(File(temporaryFolder.root, "missing.bin")))
    }
}
