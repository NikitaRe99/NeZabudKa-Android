package ru.nezabudka.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidSpeechRecognizerCompatibilityTest {
    @Test
    fun `android 10 and 11 use the installed system service`() {
        assertEquals(AndroidRecognizerMode.SYSTEM, androidRecognizerMode(29, true, false))
        assertEquals(AndroidRecognizerMode.SYSTEM, androidRecognizerMode(30, true, false))
    }

    @Test
    fun `android 12 and newer prefer on-device recognition`() {
        assertEquals(AndroidRecognizerMode.ON_DEVICE, androidRecognizerMode(31, true, true))
        assertEquals(AndroidRecognizerMode.ON_DEVICE, androidRecognizerMode(36, true, true))
    }


    @Test
    fun `missing recognition services are reported as unavailable`() {
        assertEquals(AndroidRecognizerMode.UNAVAILABLE, androidRecognizerMode(29, false, false))
        assertEquals(AndroidRecognizerMode.UNAVAILABLE, androidRecognizerMode(36, false, false))
    }
}
