package ru.nezabudka.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.domain.FirstUseSetupController
import ru.nezabudka.app.domain.FirstUseSetupKind

@RunWith(AndroidJUnit4::class)
class FirstUseSetupPreferencesTest {
    @Test fun eventsPersistAndResetDoesNotTouchOtherPreferences() {
        // Test APK context: the user's application preferences and data are untouched.
        val context = InstrumentationRegistry.getInstrumentation().context
        val other = context.getSharedPreferences("first_use_preservation_test", Context.MODE_PRIVATE)
        other.edit().putString("selected_model", "small_ru").commit()
        val store = FirstUseSetupPreferences(context)
        store.reset()
        try {
            val controller = FirstUseSetupController(store)
            assertTrue(controller.pending.value.isEmpty())
            FirstUseSetupKind.entries.forEach { assertTrue(controller.offer(it)) }
            val reopened = FirstUseSetupController(FirstUseSetupPreferences(context))
            FirstUseSetupKind.entries.forEach { assertFalse(reopened.offer(it)) }
            reopened.reset()
            assertEquals("small_ru", other.getString("selected_model", null))
            FirstUseSetupKind.entries.forEach { assertTrue(reopened.offer(it)) }
        } finally {
            store.reset()
            other.edit().clear().commit()
        }
    }
}
