package ru.nezabudka.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndPrimaryNavigation() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
        maxIterations = 5,
        stableIterations = 2,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Navigation is deliberately best-effort: the first-run guide can be visible
        // on a clean benchmark install, while existing benchmark data opens Assistant.
        listOf("Помощник", "Напоминания", "Заметки", "Настройки").forEach { label ->
            (device.findObject(androidx.test.uiautomator.By.text(label))
                ?: device.findObject(androidx.test.uiautomator.By.desc(label)))?.let { item ->
                item.click()
                device.waitForIdle()
            }
        }

        (device.findObject(androidx.test.uiautomator.By.text("Заметки"))
            ?: device.findObject(androidx.test.uiautomator.By.desc("Заметки")))?.let { notes ->
            notes.click()
            device.waitForIdle()
            device.findObject(androidx.test.uiautomator.By.desc("Добавить"))?.let { add ->
                add.click()
                device.waitForIdle()
                device.findObject(androidx.test.uiautomator.By.text("Заметка"))?.let { newNote ->
                    newNote.click()
                    device.wait(
                        androidx.test.uiautomator.Until.hasObject(
                            androidx.test.uiautomator.By.text("Название"),
                        ),
                        3_000,
                    )
                    device.pressBack()
                    device.pressBack()
                    device.waitForIdle()
                }
            }
        }
    }

    private companion object {
        const val PACKAGE_NAME = "ru.nezabudka.app"
    }
}
