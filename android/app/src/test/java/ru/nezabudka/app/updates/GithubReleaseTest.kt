package ru.nezabudka.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubReleaseTest {
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
}
