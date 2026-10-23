package ru.nezabudka.app.performance

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.MainActivity

/** Real scrolling frames on a disposable debug database, never production data. */
@RunWith(AndroidJUnit4::class)
class ScreenSmoothnessTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun measureNotesAndRemindersScrolling() {
        check(context.packageName.endsWith(".debug"))
        val data = PerformanceDataInstrumentedTest()
        val worker = HandlerThread("frame-measurement").apply { start() }
        val results = JSONArray()
        try {
            data.seedPerformanceData()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                Thread.sleep(2_000)
                for (screen in listOf("Заметки", "Напоминания")) {
                    val root = instrumentation.uiAutomation.rootInActiveWindow
                    val node = requireNotNull(find(screen, root)) { "Navigation missing: $screen" }
                    val bounds = Rect().also(node::getBoundsInScreen)
                    tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                    Thread.sleep(1_200)
                    val frames = java.util.Collections.synchronizedList(mutableListOf<Long>())
                    val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                        frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                    }
                    scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
                    val width = context.resources.displayMetrics.widthPixels.toFloat()
                    val height = context.resources.displayMetrics.heightPixels.toFloat()
                    repeat(4) { swipe(width * .5f, height * .72f, height * .3f) }
                    repeat(2) { swipe(width * .5f, height * .3f, height * .72f) }
                    Thread.sleep(400)
                    scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                    val values = synchronized(frames) { frames.filter { it > 0 }.sorted() }
                    assertTrue("No scrolling frames for $screen", values.size > 20)
                    results.put(JSONObject().put("screen", screen).put("frames", values.size)
                        .put("p50Ms", values[values.size / 2] / 1_000_000.0)
                        .put("p95Ms", values[(values.size * .95).toInt().coerceAtMost(values.lastIndex)] / 1_000_000.0)
                        .put("over16_67Ms", values.count { it > 16_666_667 }))
                }
            }
            File(context.filesDir, "scroll-frame-results.json").writeText(results.toString(2))
        } finally {
            worker.quitSafely()
            data.cleanupPerformanceData()
        }
    }

    private fun find(text: String, node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        return (0 until node.childCount).reversed().firstNotNullOfOrNull { find(text, node.getChild(it)) }
            ?: node.takeIf { it.text?.toString() == text || it.contentDescription?.toString() == text }
    }
    private fun event(down: Long, time: Long, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(down, time, action, x, y, 0).also {
            it.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(it, true); it.recycle()
        }
    }
    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        event(down, down, MotionEvent.ACTION_DOWN, x, y)
        event(down, down + 30, MotionEvent.ACTION_UP, x, y)
    }
    private fun swipe(x: Float, start: Float, end: Float) {
        val down = SystemClock.uptimeMillis()
        event(down, down, MotionEvent.ACTION_DOWN, x, start)
        for (step in 1..24) {
            Thread.sleep(16)
            event(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, start + (end - start) * step / 24)
        }
        event(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, end)
        Thread.sleep(250)
    }
}
