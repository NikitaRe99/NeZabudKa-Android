package ru.nezabudka.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process

data class DeviceCapabilities(
    val abi: String,
    val cpuCores: Int,
    val memoryClassMb: Int,
    val totalRamBytes: Long,
    val lowRamDevice: Boolean,
    val androidVersion: String,
    val hardware: String,
) {
    val reducedUiEffects: Boolean
        get() = lowRamDevice || memoryClassMb <= 192 || totalRamBytes < 3L * 1024L * 1024L * 1024L

    val summary: String
        get() = "$abi · $cpuCores ядер · RAM ${totalRamBytes / (1024L * 1024L)} МБ · " +
            "heap $memoryClassMb МБ${if (lowRamDevice) " · Android Low RAM" else ""}"
}

fun detectDeviceCapabilities(context: Context): DeviceCapabilities {
    val manager = context.getSystemService(ActivityManager::class.java)
    val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    return DeviceCapabilities(
        abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty() + if (Process.is64Bit()) " 64-bit" else " 32-bit",
        cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
        memoryClassMb = manager.memoryClass,
        totalRamBytes = memory.totalMem,
        lowRamDevice = manager.isLowRamDevice,
        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        hardware = Build.HARDWARE,
    )
}
