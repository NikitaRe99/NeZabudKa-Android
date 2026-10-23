package ru.nezabudka.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class GithubReleaseTest {
    @Test fun startupChecksRespectSuccessfulCheckAgeAndClockRollback() {
        val now = 100_000_000L
        assertTrue(shouldCheckOnStartup(now, 0))
        assertFalse(shouldCheckOnStartup(now, now - 1))
        assertFalse(shouldCheckOnStartup(now, now - 12 * 60 * 60 * 1000L + 1))
        assertTrue(shouldCheckOnStartup(now, now - 12 * 60 * 60 * 1000L))
        assertTrue(shouldCheckOnStartup(now, now + 1))
    }

    @Test fun historyIsFetchedForMissedReleasesButNotForAnUpToDateInstallation() {
        val installed = release("v0.2.86", "app-arm64-v8a-release.apk")
        assertFalse(needsOlderReleasePages("[$installed]", "0.2.86", listOf("arm64-v8a")))
        assertTrue(needsOlderReleasePages("[${release("v0.2.87", "app-arm64-v8a-release.apk")},$installed]", "0.2.86", listOf("arm64-v8a")))
        assertTrue(needsOlderReleasePages("[${release("models-v3", "model.onnx")}]", "0.2.86", listOf("arm64-v8a")))
    }
    @Test fun includesEveryMissedAppReleaseButNotInstalledOrModelReleases() {
        fun withBody(tag: String, body: String) = release(tag, "app-arm64-v8a-release.apk").dropLast(1) + ",\"body\":\"$body\"}"
        val json = "[${withBody("v0.2.85", "New fix")},${withBody("v0.2.84", "Previous fix")},${withBody("v0.2.83", "Installed fix")},${withBody("models-v3", "Model")}]"
        val changes = findAppRelease(json, "0.2.83", listOf("arm64-v8a"))!!.changes
        assertTrue(changes.contains("New fix")); assertTrue(changes.contains("Previous fix"))
        assertFalse(changes.contains("Installed fix")); assertFalse(changes.contains("Model"))
    }
    private fun release(tag: String, name: String, digest: String = "sha256:${"a".repeat(64)}", draft: Boolean = false) =
        """{"tag_name":"$tag","draft":$draft,"prerelease":false,"assets":[{"name":"$name","size":84000000,"digest":"$digest","browser_download_url":"https://github.com/NikitaRe99/NeZabudKa-Android/releases/download/$tag/$name"}]}"""

    @Test fun choosesNewestReleaseForSupportedAbi() {
        val json = "[${release("v0.2.54", "app-arm64-v8a-release.apk")},${release("v0.2.55", "app-arm64-v8a-release.apk")}]"
        assertEquals("0.2.55", findAppRelease(json, "0.2.53", listOf("arm64-v8a"))?.version)
    }

    @Test fun ignoresModelDraftDebugAndUnverifiedApk() {
        val json = "[${release("models-v2", "app-arm64-v8a-release.apk")},${release("v0.2.55", "app-arm64-v8a-debug.apk")},${release("v0.2.56", "app-arm64-v8a-release.apk", "")},${release("v0.2.57", "app-arm64-v8a-release.apk", draft = true)}]"
        assertNull(findAppRelease(json, "0.2.53", listOf("arm64-v8a")))
    }

    @Test fun ignoresAlreadyInstalledRelease() {
        assertNull(findAppRelease("[${release("v0.2.54", "app-arm64-v8a-release.apk")}]", "0.2.54", listOf("arm64-v8a")))
    }

    @Test fun rejectsOlderReleasesAndComparesVersionPartsNumerically() {
        assertNull(findAppRelease("[${release("v0.2.57", "app-arm64-v8a-release.apk")}]", "0.2.68", listOf("arm64-v8a")))
        assertFalse(isNewerAppVersion("0.2.68", "0.2.68"))
        assertFalse(isNewerAppVersion("0.2.9", "0.2.68"))
        assertTrue(isNewerAppVersion("0.2.100", "0.2.68"))
        assertTrue(isNewerAppVersion("0.3.0", "0.2.68"))
        assertFalse(isNewerAppVersion("0.2.69-beta", "0.2.68"))
        assertFalse(isNewerAppVersion(null, "0.2.68"))
    }
}
