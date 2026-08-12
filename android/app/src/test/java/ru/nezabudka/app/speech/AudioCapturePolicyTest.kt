package ru.nezabudka.app.speech

import android.media.AudioDeviceInfo
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AudioCapturePolicyTest {
    @Test fun `only the phone microphone is accepted for recording`() {
        assertTrue(isBuiltInMicrophoneType(AudioDeviceInfo.TYPE_BUILTIN_MIC))
        assertFalse(isBuiltInMicrophoneType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertFalse(isBuiltInMicrophoneType(AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertFalse(isBuiltInMicrophoneType(AudioDeviceInfo.TYPE_WIRED_HEADSET))
    }
}
