package ru.nezabudka.app.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class AutoStartSettingsDestination(
    val title: String,
    val intent: Intent,
)

object DeviceAutoStartSettings {
    fun alarmPermissionDestination(context: Context): AutoStartSettingsDestination? {
        if (deviceFamily() != DeviceFamily.VIVO) return null
        val intent = Intent("permission.intent.action.softPermissionDetail")
            .setComponent(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
                ),
            )
            .putExtra("packagename", context.packageName)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }?.let {
            AutoStartSettingsDestination("Разрешения экрана будильника vivo/iQOO", it)
        }
    }

    fun destination(context: Context): AutoStartSettingsDestination? {
        if (deviceFamily() == DeviceFamily.VIVO) {
            return vivoAppPermissionDestination(context, "Автозапуск и фоновые разрешения vivo/iQOO")
        }
        val candidates = when (deviceFamily()) {
            DeviceFamily.VIVO -> emptyList()
            DeviceFamily.XIAOMI -> listOf(
                candidate("Фоновый автозапуск Xiaomi/HyperOS", "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            DeviceFamily.HUAWEI -> listOf(
                candidate("Запуск приложений Huawei", "com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            )
            DeviceFamily.HONOR -> listOf(
                candidate("Автозапуск Honor", "com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            )
            DeviceFamily.OPLUS -> listOf(
                candidate("Автозапуск OPPO/Realme", "com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
                candidate("Автозапуск OPPO/Realme", "com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                candidate("Фоновый запуск OnePlus", "com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            )
            DeviceFamily.ASUS -> listOf(
                candidate("Автозапуск ASUS", "com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
            )
            DeviceFamily.SAMSUNG,
            DeviceFamily.ANDROID,
            -> emptyList()
        }
        return candidates
            .map { it.forPackage(context.packageName) }
            .firstOrNull { it.intent.resolveActivity(context.packageManager) != null }
    }

    /** OEM power pages are used only when the exact component exists; app details is the fallback. */
    fun backgroundDestination(context: Context): AutoStartSettingsDestination {
        val family = deviceFamily()
        val title = when (family) {
            DeviceFamily.SAMSUNG -> "Батарея приложения Samsung"
            DeviceFamily.VIVO -> "Параметры приложения vivo/iQOO"
            DeviceFamily.XIAOMI -> "Батарея приложения Xiaomi/HyperOS"
            DeviceFamily.OPLUS -> "Батарея приложения OPPO/Realme"
            DeviceFamily.HUAWEI -> "Фоновая работа Huawei"
            DeviceFamily.HONOR -> "Фоновая работа Honor"
            DeviceFamily.ASUS -> "Батарея приложения ASUS"
            DeviceFamily.ANDROID -> "Батарея приложения"
        }
        return AutoStartSettingsDestination(
            title,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
    }

    private fun candidate(title: String, packageName: String, className: String, action: String? = null) =
        AutoStartSettingsDestination(
            title,
            Intent(action).setComponent(ComponentName(packageName, className)),
        )

    private fun vivoAppPermissionDestination(
        context: Context,
        title: String,
    ): AutoStartSettingsDestination? = Intent("permission.intent.action.softPermissionDetail")
        .setComponent(
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
            ),
        )
        .putExtra("packagename", context.packageName)
        .takeIf { it.resolveActivity(context.packageManager) != null }
        ?.let { AutoStartSettingsDestination(title, it) }

    private fun AutoStartSettingsDestination.forPackage(packageName: String) = copy(
        intent = Intent(intent)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            .putExtra("packageName", packageName)
            .putExtra("package_name", packageName)
            .putExtra("packagename", packageName)
            .putExtra("pkg", packageName),
    )

    internal fun deviceFamily(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND,
    ): DeviceFamily {
        val values = listOf(manufacturer, brand).map { it.trim().lowercase() }
        fun matches(vararg aliases: String) = values.any { value -> aliases.any { alias -> value == alias || value.startsWith("$alias ") } }
        return when {
            matches("vivo", "iqoo") -> DeviceFamily.VIVO
            matches("xiaomi", "redmi", "poco") -> DeviceFamily.XIAOMI
            matches("samsung") -> DeviceFamily.SAMSUNG
            matches("oppo", "realme", "oneplus", "oplus") -> DeviceFamily.OPLUS
            matches("huawei") -> DeviceFamily.HUAWEI
            matches("honor") -> DeviceFamily.HONOR
            matches("asus") -> DeviceFamily.ASUS
            else -> DeviceFamily.ANDROID
        }
    }
}

internal enum class DeviceFamily { ANDROID, SAMSUNG, VIVO, XIAOMI, OPLUS, HUAWEI, HONOR, ASUS }
