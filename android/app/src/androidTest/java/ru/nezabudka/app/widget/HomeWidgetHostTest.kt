package ru.nezabudka.app.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.MainActivity
import ru.nezabudka.app.data.AppDatabase
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.R
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeWidgetHostTest {
    @Test fun nativeCollectionsRenderResizeAndKeepInstanceFoldersIndependent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, WidgetPreviewActivity.HOST_ID)
        val preferences = WidgetPreferences(context)
        val originalTheme = SettingsStore(context).state.value.theme
        val ids = mutableListOf<Int>()
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            try {
                for (provider in listOf(RemindersWidgetProvider::class.java, NotesWidgetProvider::class.java, NotesWidgetProvider::class.java)) {
                    val id = host.allocateAppWidgetId(); ids += id
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 330)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 230)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 230)
                    }
                    assertTrue("Bind ${provider.simpleName}", manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider), options))
                }
            } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
            val folder = AppDatabase.get(context).notesDao().allFolders().firstOrNull()
            preferences.setFolder(ids[1], null)
            preferences.setFolder(ids[2], folder?.id)
            assertNull(preferences.folder(ids[1]))
            assertEquals(folder?.id, WidgetPreferences(context).folder(ids[2]))
            HomeWidgets.refresh(context)
            ActivityScenario.launch<WidgetPreviewActivity>(Intent(context, WidgetPreviewActivity::class.java)
                .putExtra("widget_ids", intArrayOf(ids[0], ids[1]))).use { scenario ->
                Thread.sleep(1_500)
                scenario.onActivity { activity -> allViews(activity.root).filter { it.id == R.id.widget_folder_chevron }.last().performClick() }
                Thread.sleep(500)
                assertTrue("Folder chooser opened", accessibleText(instrumentation.uiAutomation.rootInActiveWindow).contains("Папка виджета"))
                clickText(instrumentation.uiAutomation.rootInActiveWindow, "Все заметки")
                Thread.sleep(500)
                assertNull(preferences.folder(ids[1]))
                assertEquals(folder?.id, preferences.folder(ids[2]))
                // Header clicks open the app; the separate chevron keeps folder selection.
                for ((headerIndex, searchText) in listOf(0 to "Поиск напоминаний", 1 to "Поиск заметок")) {
                    scenario.onActivity { activity -> allViews(activity.root).filter { it.id == R.id.widget_folder_picker }[headerIndex].performClick() }
                    Thread.sleep(1_000)
                    runCatching { instrumentation.uiAutomation.clearCache() } // Unavailable on Android 10.
                    assertTrue("Header opens correct tab", accessibleText(instrumentation.uiAutomation.rootInActiveWindow).contains(searchText))
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    Thread.sleep(700)
                }
                // A collection check is routed without opening MainActivity. Use a
                // nonexistent ID so this route check never changes the user's tasks.
                scenario.onActivity { activity -> activity.startActivity(Intent(activity, WidgetEntryActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ids[0]).putExtra(EXTRA_ROW_ACTION, ROW_COMPLETE)
                    .putExtra(EXTRA_ITEM_ID, Long.MAX_VALUE).putExtra(EXTRA_EXPECTED_AT, "2026-10-12T12:00")
                    .putExtra(EXTRA_CREATED_AT, "2026-10-12T08:00")) }
                Thread.sleep(500)
                scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
                val headerUndo = WidgetUndo(Long.MAX_VALUE, "2026-10-12T12:00", null, "completed", "2026-10-12T08:00", "Тест", null,
                    System.currentTimeMillis() + 10_000, "header-test")
                preferences.setUndo(ids[0], headerUndo)
                HomeWidgets.refresh(context, setOf(WidgetKind.REMINDERS))
                Thread.sleep(500)
                scenario.onActivity { activity ->
                    val button = allViews(activity.root).first { it.id == R.id.widget_undo }
                    assertEquals("Undo shown in header", View.VISIBLE, button.visibility)
                    button.performClick()
                }
                Thread.sleep(500)
                assertNull("Undo consumed", preferences.undo(ids[0], includeExpired = true))
                for (theme in listOf("light", "dark")) {
                    instrumentation.runOnMainSync { SettingsStore(context).setTheme(theme) }
                    HomeWidgets.refresh(context)
                    Thread.sleep(1_500)
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        activity.setPreviewTheme(theme)
                        assertTrue("Native widget title", allViews(activity.root).filterIsInstance<TextView>().any { it.text.toString() == "Сегодня" })
                        assertTrue("Native collection", allViews(activity.root).filterIsInstance<ListView>().size >= 2)
                        assertTrue("Notes header", allViews(activity.root).filterIsInstance<TextView>().any { it.text.toString() == "Все заметки" })
                    }
                    instrumentation.waitForIdleSync()
                    Thread.sleep(200)
                    val image = instrumentation.uiAutomation.takeScreenshot()
                    assertNotNull(image)
                    File(context.filesDir, "native-widgets-$theme.png").outputStream().use { image!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    image?.recycle()
                }
                val reminderRows = widgetReminderRows(AppDatabase.get(context), preferences, java.time.LocalDate.now())
                if (reminderRows.completedCount > 0) {
                    scenario.onActivity { activity -> allViews(activity.root).filterIsInstance<ListView>().first().setSelection(reminderRows.rows.indexOfFirst { it.completed }.coerceAtLeast(0)) }
                    Thread.sleep(300)
                    val completedImage = instrumentation.uiAutomation.takeScreenshot()!!
                    File(context.filesDir, "native-widgets-completed.png").outputStream().use { completedImage.compress(Bitmap.CompressFormat.PNG, 100, it) }; completedImage.recycle()
                }
                val notesCount = AppDatabase.get(context).notesDao().widgetCount(null).coerceAtMost(500)
                scenario.onActivity { activity ->
                    val list = allViews(activity.root).filterIsInstance<ListView>().last()
                    assertEquals("Notes collection loaded", notesCount, list.adapter.count)
                    if (notesCount > 3) list.setSelection(notesCount - 1)
                }
                Thread.sleep(300)
                scenario.onActivity { activity ->
                    val list = allViews(activity.root).filterIsInstance<ListView>().last()
                    if (notesCount > 3) assertTrue("Native scrolling", list.firstVisiblePosition > 0)
                    list.setSelection(0)
                }
                manager.updateAppWidgetOptions(ids[1], manager.getAppWidgetOptions(ids[1]).apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                })
                HomeWidgets.refresh(context, setOf(WidgetKind.NOTES))
            }
            val request = WidgetLaunch(WidgetDestination.NOTES, folder?.id)
            val intent = WidgetLaunchIntent.create(context, request, "test-launch")
            assertEquals(folder?.id, WidgetLaunchIntent.consume(Intent(intent))?.folderId)
            val consumed = Intent(intent)
            assertNotNull(WidgetLaunchIntent.consume(consumed))
            assertNull(WidgetLaunchIntent.consume(consumed))
            ActivityScenario.launch<MainActivity>(WidgetLaunchIntent.create(context, WidgetLaunch(WidgetDestination.REMINDERS), "test-reminders")).use {
                Thread.sleep(750)
                instrumentation.waitForIdleSync()
                assertTrue("Reminders tab opened", accessibleText(instrumentation.uiAutomation.rootInActiveWindow).contains("Поиск напоминаний"))
            }
            ActivityScenario.launch<MainActivity>(WidgetLaunchIntent.create(context, request, "test-notes")).use {
                Thread.sleep(750)
                instrumentation.waitForIdleSync()
                assertTrue("Notes tab opened", accessibleText(instrumentation.uiAutomation.rootInActiveWindow).contains("Поиск заметок"))
            }
            // Opening an individual record from a widget must return to the host
            // on Back. Existing records are read only; no edits are made here.
            val note = AppDatabase.get(context).notesDao().widgetRows(null, 1).firstOrNull()
            val reminder = AppDatabase.get(context).reminderDao().widgetRows(java.time.LocalDate.now().plusDays(1).atStartOfDay(), 1).firstOrNull()
            for ((destination, itemId) in listOf(WidgetDestination.NOTE to note?.id, WidgetDestination.REMINDER to reminder?.id)) {
                if (itemId == null) continue
                ActivityScenario.launch<MainActivity>(WidgetLaunchIntent.create(context, WidgetLaunch(destination, itemId = itemId), "test-back-${destination.name}")).use { scenario ->
                    Thread.sleep(1_000)
                    if (destination == WidgetDestination.REMINDER) repeat(20) {
                        if (!accessibleText(instrumentation.uiAutomation.rootInActiveWindow).contains("Выполнено")) Thread.sleep(200)
                    }
                    instrumentation.waitForIdleSync()
                    val image = instrumentation.uiAutomation.takeScreenshot()!!
                    File(context.filesDir, "editor-before-back-${destination.name}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
                    val text = accessibleText(instrumentation.uiAutomation.rootInActiveWindow)
                    assertFalse("Individual editor opened", text.contains(if (destination == WidgetDestination.NOTE) "Поиск заметок" else "Поиск напоминаний"))
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    repeat(20) { if (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED) Thread.sleep(200) }
                    assertEquals("Back finishes widget entry: ${destination.name}", androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
                }
            }
        } finally {
            instrumentation.runOnMainSync { SettingsStore(context).setTheme(originalTheme) }
            ids.forEach(host::deleteAppWidgetId)
            preferences.delete(ids.toIntArray())
            HomeWidgets.start(context)
        }
    }
    private fun allViews(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { allViews(view.getChildAt(it)) } else emptyList()
    private fun accessibleText(node: android.view.accessibility.AccessibilityNodeInfo?): String {
        if (node == null) return ""
        return node.text.toString() + " " + (0 until node.childCount).joinToString(" ") { accessibleText(node.getChild(it)) }
    }
    private fun clickText(node: android.view.accessibility.AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        if (node.text?.toString() == text) {
            var target: android.view.accessibility.AccessibilityNodeInfo? = node
            while (target != null) {
                if (target.isClickable) return target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                target = target.parent
            }
        }
        return (0 until node.childCount).any { clickText(node.getChild(it), text) }
    }
}
