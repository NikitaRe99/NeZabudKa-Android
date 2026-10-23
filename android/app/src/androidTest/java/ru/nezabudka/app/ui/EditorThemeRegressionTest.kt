package ru.nezabudka.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.data.SettingsStore
import java.io.File

@RunWith(AndroidJUnit4::class)
class EditorThemeRegressionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun launch(scenario: String) = ActivityScenario.launch<InterfaceReviewActivity>(
        Intent(context, InterfaceReviewActivity::class.java).putExtra("scenario", scenario))

    @Test fun saveDoesNotCompleteAndCompletionRequiresConfirmation() {
        launch("reminder-editor").use { activity ->
            Thread.sleep(700)
            val editor = requireNotNull(find { it.className == "android.widget.EditText" })
            assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Изменённый текст")
            }))
            Thread.sleep(1_000)
            assertTrue(click("Сохранить"))
            activity.onActivity { assertEquals(false, it.lastReminderCompleted); assertTrue(it.editorClosed) }
        }
        launch("reminder-editor").use { activity ->
            Thread.sleep(700)
            assertTrue(click("Выполнено")); Thread.sleep(250)
            activity.onActivity { assertNull(it.lastReminderCompleted) }
            assertTrue(click("Отмена")); Thread.sleep(250)
            activity.onActivity { assertNull(it.lastReminderCompleted) }
            assertTrue(click("Выполнено")); Thread.sleep(250)
            assertTrue(click("Отметить выполненным")); Thread.sleep(250)
            activity.onActivity { assertEquals(true, it.lastReminderCompleted); assertTrue(it.editorClosed) }
        }
    }

    @Test fun customColorCanBeAddedPersistedAndSelectedInTheNotePicker() {
        val original = SettingsStore(context).state.value.customTheme
        try {
            launch("theme-editor").use { activity ->
                Thread.sleep(700)
                var attempts = 0
                while (find { it.contentDescription?.toString() == "Добавить цвет заметки" } == null && attempts++ < 16) {
                    swipeUp(); Thread.sleep(150)
                }
                screenshot("custom-note-colors-round")
                assertTrue(click("Добавить цвет заметки")); Thread.sleep(350)
                assertTrue(click("Готово")); Thread.sleep(250)
                assertTrue(click("Сохранить")); Thread.sleep(250)
                activity.onActivity { assertEquals(14, it.savedTheme!!.notePalette.size) }
                assertEquals(14, SettingsStore(context).state.value.customTheme.notePalette.size)
            }
            launch("note-colors").use { activity ->
                Thread.sleep(600)
                assertTrue(click("Цвет заметки 13"))
                activity.onActivity { assertEquals("theme-note:12", it.selectedNoteColor) }
                screenshot("note-picker-extra-colors")
            }
        } finally { SettingsStore(context).setCustomTheme(original) }
    }

    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? = node?.let {
            (0 until it.childCount).reversed().firstNotNullOfOrNull { index -> walk(it.getChild(index)) } ?: it.takeIf(predicate)
        }
        return walk(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun click(text: String): Boolean {
        var node = find { it.text?.toString() == text || it.contentDescription?.toString() == text }
        while (node != null) {
            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node = node.parent
        }
        return false
    }
    private fun screenshot(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun swipeUp() {
        val metrics = context.resources.displayMetrics
        val x = metrics.widthPixels * .5f
        val start = metrics.heightPixels * .78f
        val end = metrics.heightPixels * .28f
        val down = SystemClock.uptimeMillis()
        for (step in 0..20) {
            val action = if (step == 0) MotionEvent.ACTION_DOWN else if (step == 20) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE
            MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, start + (end - start) * step / 20, 0).also {
                it.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.uiAutomation.injectInputEvent(it, true); it.recycle()
            }
            Thread.sleep(16)
        }
    }
}
