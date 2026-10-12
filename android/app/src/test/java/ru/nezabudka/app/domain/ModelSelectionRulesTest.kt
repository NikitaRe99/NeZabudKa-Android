package ru.nezabudka.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelSelectionRulesTest {
    @Test
    fun `persisted model is restored only when selected file still exists`() {
        assertEquals("chosen.bin", restoredModelSelection(listOf("other.bin", "chosen.bin"), "chosen.bin"))
        assertNull(restoredModelSelection(listOf("other.bin", "second.bin"), "missing.bin"))
    }

    @Test
    fun `single available model is selected after directory is chosen again`() {
        assertEquals("only.bin", restoredModelSelection(listOf("only.bin"), "missing.bin"))
        assertNull(restoredModelSelection(emptyList(), "missing.bin"))
    }

    @Test
    fun `native memory guard reserves space for Android and the UI`() {
        val mib = 1024L * 1024L
        val whisperPeak = estimatedWhisperPeakBytes(181L * mib, 2L * mib)

        assertEquals(true, hasNativeMemoryHeadroom(whisperPeak, 2_000L * mib, 4_000L * mib))
        assertEquals(false, hasNativeMemoryHeadroom(whisperPeak, 200L * mib, 1_000L * mib))
    }

    @Test
    fun `retaining weights still reserves an inference workspace`() {
        val mib = 1024L * 1024L
        val modelBytes = 190L * mib
        val cold = estimatedWhisperPeakBytes(modelBytes, 2L * mib)
        val retained = estimatedWhisperPeakBytes(modelBytes, 2L * mib, modelWeightsRetained = true)
        assertEquals(modelBytes, cold - retained)
        assertEquals(true, retained > estimatedWhisperPeakBytes(0, 2L * mib))
        assertEquals(false, hasNativeMemoryHeadroom(retained, 200L * mib, 1_000L * mib))
    }
}
