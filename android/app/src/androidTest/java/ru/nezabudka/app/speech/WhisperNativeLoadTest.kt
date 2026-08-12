package ru.nezabudka.app.speech

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperNativeLoadTest {
    @Test fun nativeRuntimeLoadsAndReleasesWithoutAModel() {
        assertTrue(WhisperNative.systemInfo().isNotBlank())
        WhisperNative.release()
    }

}
