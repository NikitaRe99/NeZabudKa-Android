package ru.nezabudka.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Read-only render samples use the production notice component. */
@RunWith(AndroidJUnit4::class)
class NotificationGalleryCaptureTest {
    @Test fun captureAllCurrentNoticeVariants() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samples = JSONArray(instrumentation.context.assets.open("notification-gallery.json").bufferedReader().use { it.readText() })
        val directory = File(context.filesDir, "notification-gallery").apply { mkdirs() }
        for (index in 0 until samples.length()) {
            val sample = samples.getJSONObject(index)
            val actions = sample.getJSONArray("actions")
            val intent = Intent(context, InterfaceReviewActivity::class.java)
                .putExtra("gallery_message", sample.getString("message"))
                .putStringArrayListExtra("gallery_actions", ArrayList((0 until actions.length()).map { actions.getString(it) }))
            ActivityScenario.launch<InterfaceReviewActivity>(intent).use { scenario ->
                Thread.sleep(700)
                instrumentation.waitForIdleSync()
                var bounds = Rect()
                scenario.onActivity { bounds = Rect(it.galleryBounds) }
                assertTrue("Sample ${index + 1} laid out", bounds.width() > 0 && bounds.height() > 0)
                instrumentation.uiAutomation.clearCache()
                val root = instrumentation.uiAutomation.rootInActiveWindow!!
                val message = findBounds(root, sample.getString("message"))!!
                val close = findBounds(root, "Закрыть")!!
                assertTrue("Sample ${index + 1}: close above message", close.bottom <= message.top)
                assertTrue("Sample ${index + 1}: no width reserved for close", message.right >= close.centerX())
                assertTrue("Sample ${index + 1}: equal message side margins", kotlin.math.abs(message.centerX() - bounds.centerX()) <= 1)
                val screen = instrumentation.uiAutomation.takeScreenshot()!!
                assertTrue("Sample fits screen", bounds.bottom <= screen.height && bounds.right <= screen.width)
                val card = Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())
                File(directory, "notice-${sample.getInt("id")}.png").outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (card !== screen) card.recycle(); screen.recycle()
            }
        }
    }

    private fun findBounds(node: AccessibilityNodeInfo, label: String): Rect? {
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findBounds(child, label)?.let { return it }
        }
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
            return Rect().also(node::getBoundsInScreen)
        }
        return null
    }
}
