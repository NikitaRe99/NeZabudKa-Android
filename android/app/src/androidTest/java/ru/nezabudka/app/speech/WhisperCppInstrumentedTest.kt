package ru.nezabudka.app.speech

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ru.nezabudka.app.data.UserModelsStorage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperCppInstrumentedTest {
    @Test fun native_whisper_library_loads() {
        val info = WhisperNative.systemInfo()
        assertTrue(info.isNotBlank())
        assertTrue(info.contains("WHISPER", ignoreCase = true) || info.contains("AVX", ignoreCase = true) || info.contains("NEON", ignoreCase = true))
    }

    @Test fun model_manager_waits_for_user_owned_directory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = UserModelsStorage(context)
        val manager = WhisperModelManager(context, storage)
        manager.refresh()
        assertTrue(manager.state.value.installed.isEmpty() || storage.state.value.configured)
    }
}
