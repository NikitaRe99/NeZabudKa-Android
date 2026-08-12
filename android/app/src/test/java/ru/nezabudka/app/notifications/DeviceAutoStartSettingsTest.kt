package ru.nezabudka.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceAutoStartSettingsTest {
    @Test
    fun `device families do not fall through to another vendor`() {
        assertEquals(DeviceFamily.SAMSUNG, DeviceAutoStartSettings.deviceFamily("samsung", "samsung"))
        assertEquals(DeviceFamily.VIVO, DeviceAutoStartSettings.deviceFamily("vivo", "iQOO"))
        assertEquals(DeviceFamily.XIAOMI, DeviceAutoStartSettings.deviceFamily("Xiaomi", "Redmi"))
        assertEquals(DeviceFamily.OPLUS, DeviceAutoStartSettings.deviceFamily("OPPO", "realme"))
        assertEquals(DeviceFamily.HUAWEI, DeviceAutoStartSettings.deviceFamily("HUAWEI", "HUAWEI"))
        assertEquals(DeviceFamily.HONOR, DeviceAutoStartSettings.deviceFamily("HONOR", "HONOR"))
        assertEquals(DeviceFamily.ANDROID, DeviceAutoStartSettings.deviceFamily("Google", "google"))
    }

    @Test
    fun `partial vendor names are not treated as a supported shell`() {
        assertEquals(DeviceFamily.ANDROID, DeviceAutoStartSettings.deviceFamily("samsung-vivo", "generic"))
        assertEquals(DeviceFamily.ANDROID, DeviceAutoStartSettings.deviceFamily("notxiaomi", "generic"))
    }
}
