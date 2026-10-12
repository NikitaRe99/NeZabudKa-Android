package ru.nezabudka.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.Bitmap
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
import java.io.File

@RunWith(AndroidJUnit4::class)
class InterfaceRegressionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun node(text: String, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        val child = (0 until root.childCount).firstNotNullOfOrNull { node(text, root.getChild(it)) }
        if (child != null) return child
        return root.takeIf { it.text?.toString()?.contains(text) == true || it.contentDescription?.toString() == text }
    }
    @Test fun shortFastSwipeDoesNotDeleteButMediumSwipeDoes() {
        ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java).putExtra("scenario", "swipe")).use { scenario ->
            Thread.sleep(1_200)
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.clearCache()
            screenshot("swipe-before")
            File(context.filesDir, "swipe-tree.txt").writeText(dump(instrumentation.uiAutomation.rootInActiveWindow))
            val bounds = Rect(); requireNotNull(node("Проверка")).getBoundsInScreen(bounds)
            val width = context.resources.displayMetrics.widthPixels.toFloat()
            swipe(width * .85f, width * .65f, bounds.centerY().toFloat(), 110)
            Thread.sleep(600)
            scenario.onActivity { assertEquals("Short fling must not delete", 0, it.deletions) }
            swipe(width * .85f, width * .27f, bounds.centerY().toFloat(), 800)
            Thread.sleep(700)
            screenshot("swipe-after")
            scenario.onActivity { assertEquals("Medium swipe deletes once", 1, it.deletions) }
        }
    }
    @Test fun welcomeRemainsBelowResultAndCanBeHidden() {
        ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java).putExtra("scenario", "welcome")).use {
            Thread.sleep(1_200)
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.clearCache()
            val result = Rect(); requireNotNull(node("Результат команды")).getBoundsInScreen(result)
            val welcome = Rect(); requireNotNull(node("Скажите — и сохраните")).getBoundsInScreen(welcome)
            assertTrue("Welcome follows result", welcome.top > result.top)
            val hide = Rect(); requireNotNull(node("Больше не показывать")).getBoundsInScreen(hide)
            val example = Rect(); requireNotNull(node("Создай папку Путешествия")).getBoundsInScreen(example)
            assertTrue("Hide control follows examples", hide.top > example.top)
            screenshot("welcome-inline-mic")
            click(requireNotNull(node("Больше не показывать")))
            Thread.sleep(500)
            instrumentation.uiAutomation.clearCache()
            assertNull(node("Скажите — и сохраните"))
        }
    }
    @Test fun noticeActionsAlignRightAndCloseStaysAbove() {
        for (actions in listOf(arrayListOf("edit", "folder", "undo"), arrayListOf("cancel", "no-time"))) {
            ActivityScenario.launch<InterfaceReviewActivity>(Intent(context, InterfaceReviewActivity::class.java)
                .putStringArrayListExtra("gallery_actions", actions)).use {
                Thread.sleep(1_200)
                instrumentation.waitForIdleSync(); instrumentation.uiAutomation.clearCache()
                val labels = if (actions.first() == "edit") listOf("Редактировать", "Сменить папку", "Отмена") else listOf("Без времени", "Отмена")
                val bounds = labels.map { label -> Rect().also { requireNotNull(node(label)).getBoundsInScreen(it) } }
                val close = Rect(); requireNotNull(node("Закрыть")).getBoundsInScreen(close)
                val message = Rect(); requireNotNull(node("Заметка добавлена.")).getBoundsInScreen(message)
                assertTrue("Close above full-width message", close.bottom <= message.top)
                assertTrue("Cancel is rightmost", bounds.last().left > bounds.first().left)
                assertTrue("Close is above actions", close.bottom <= bounds.first().top)
                assertTrue("Actions share one row", bounds.all { kotlin.math.abs(it.centerY() - bounds.first().centerY()) <= 1 })
                assertTrue("Actions ordered to the right", bounds.zipWithNext().all { (left, right) -> left.right <= right.left })
                screenshot(if (actions.first() == "edit") "notice-actions-right" else "notice-date-one-row")
            }
        }
    }
    private fun screenshot(name: String) {
        val image = instrumentation.uiAutomation.takeScreenshot()!!
        File(context.filesDir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }
    private fun dump(node: AccessibilityNodeInfo?): String = if (node == null) "null" else "${node.text} | ${node.contentDescription} | ${node.isClickable}\n" + (0 until node.childCount).joinToString("\n") { dump(node.getChild(it)) }
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null) {
            if (target.isClickable) return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            target = target.parent
        }
        return false
    }
    private fun swipe(from: Float, to: Float, y: Float, duration: Long) {
        val start = SystemClock.uptimeMillis()
        for (step in 0..20) {
            val time = start + duration * step / 20
            val wait = time - SystemClock.uptimeMillis(); if (wait > 0) SystemClock.sleep(wait)
            val action = if (step == 0) MotionEvent.ACTION_DOWN else if (step == 20) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE
            val event = MotionEvent.obtain(start, time, action, from + (to - from) * step / 20f, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
        }
    }
}
