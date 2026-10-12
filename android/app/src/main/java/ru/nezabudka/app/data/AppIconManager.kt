package ru.nezabudka.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.nezabudka.app.MainActivity
import ru.nezabudka.app.R

enum class AppIconOption(val title: String, val aliasName: String, val foreground: Int) {
    FLOWER("Незабудка", "LauncherFlower", R.drawable.ic_launcher_flower),
    CHECK("Заметка и галочка", "LauncherNoteCheck", R.drawable.ic_launcher_foreground),
    CLOCK("Заметка и часы", "LauncherNoteClock", R.drawable.ic_launcher_clock),
}

/** Android persists the component choice across app restarts and updates. */
class AppIconManager(private val context: Context) {
    private val manager = context.packageManager
    private val _selected = MutableStateFlow(readSelected())
    val selected: StateFlow<AppIconOption> = _selected

    fun component(option: AppIconOption): ComponentName = ComponentName(
        context.packageName, "${MainActivity::class.java.`package`!!.name}.${option.aliasName}",
    )

    fun refresh() { _selected.value = readSelected() }

    @Synchronized fun select(option: AppIconOption) {
        // Enable the requested launcher before disabling another on Android 10–12.
        // Android 13+ applies the whole set atomically; every flag must match.
        if (Build.VERSION.SDK_INT >= 33) {
            manager.setComponentEnabledSettings(AppIconOption.entries.map {
                PackageManager.ComponentEnabledSetting(component(it),
                    if (it == option) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
            })
        } else {
            manager.setComponentEnabledSetting(component(option), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            AppIconOption.entries.filterNot { it == option }.forEach {
                manager.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
        _selected.value = option
    }

    private fun readSelected(): AppIconOption = AppIconOption.entries.firstOrNull {
        manager.getComponentEnabledSetting(component(it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } ?: AppIconOption.CHECK
}
