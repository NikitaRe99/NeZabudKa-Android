package ru.nezabudka.app.data

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.nezabudka.app.AssistantApplication
import ru.nezabudka.app.MainActivity
import java.io.File

@RunWith(AndroidJUnit4::class)
class LauncherAppearanceTest {
    @Test fun iconChoicePersistsAndKeepsExactlyOneLauncher() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icons = (context as AssistantApplication).container.appIcons
        val original = icons.selected.value
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        try {
            AppIconOption.entries.forEach { option ->
                icons.select(option)
                @Suppress("DEPRECATION")
                val matches = context.packageManager.queryIntentActivities(intent, 0)
                assertEquals("A single visible launcher for $option", 1, matches.size)
                assertEquals(icons.component(option).className, matches.single().activityInfo.name)
                assertEquals(option, AppIconManager(context).selected.value)
                val drawable = matches.single().loadIcon(context.packageManager)
                val image = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 384, 384)
                drawable.draw(Canvas(image))
                File(context.filesDir, "launcher-preview-${option.name.lowercase()}.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
        } finally { icons.select(original) }
    }

    @Test fun oldWidgetIsNoLongerRegistered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(AppWidgetManager.getInstance(context).installedProviders.none {
            it.provider.packageName == context.packageName && it.provider.className.endsWith("VoiceNoteWidgetProvider")
        })
    }

    @Test fun captureThemesForReview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("render_design_preview") == "true")
        val context = ApplicationProvider.getApplicationContext<AssistantApplication>()
        val settings = context.container.settings
        val original = settings.state.value.theme
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            try {
                for (theme in listOf("light", "dark")) {
                    instrumentation.runOnMainSync { settings.setTheme(theme) }
                    instrumentation.waitForIdleSync()
                    Thread.sleep(750)
                    val image = instrumentation.uiAutomation.takeScreenshot()
                    assertNotNull(image)
                    File(context.filesDir, "design-preview-$theme.png").outputStream().use { output ->
                        image!!.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    image?.recycle()
                }
            } finally { instrumentation.runOnMainSync { settings.setTheme(original) } }
        }
    }
}
