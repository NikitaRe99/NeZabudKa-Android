package ru.nezabudka.app.updates

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class GithubRelease(
    val version: String,
    val assetName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
)

/** Only versioned app releases with a digest and an APK for this device are installable. */
internal fun findAppRelease(json: String, currentVersion: String, supportedAbis: List<String>): GithubRelease? {
    val current = parseVersion(currentVersion) ?: return null
    val releases = Json.parseToJsonElement(json) as? JsonArray ?: return null
    return releases.mapNotNull { element ->
        val release = element as? JsonObject ?: return@mapNotNull null
        if (release["draft"]?.jsonPrimitive?.booleanOrNull != false ||
            release["prerelease"]?.jsonPrimitive?.booleanOrNull != false
        ) return@mapNotNull null
        val tag = release.string("tag_name") ?: return@mapNotNull null
        val version = tag.removePrefix("v")
        val parsed = parseVersion(version) ?: return@mapNotNull null
        if (compareVersions(parsed, current) <= 0) return@mapNotNull null
        val assets = release["assets"] as? JsonArray ?: return@mapNotNull null
        val matchingAsset = supportedAbis.firstNotNullOfOrNull { abi ->
            val expectedName = "app-$abi-release.apk"
            assets.filterIsInstance<JsonObject>().firstOrNull { it.string("name") == expectedName }
        } ?: return@mapNotNull null
        val url = matchingAsset.string("browser_download_url") ?: return@mapNotNull null
        if (!url.startsWith("https://github.com/NikitaRe99/NeZabudKa-Android/releases/download/")) return@mapNotNull null
        val digest = matchingAsset.string("digest")?.removePrefix("sha256:") ?: return@mapNotNull null
        if (!digest.matches(Regex("[0-9a-fA-F]{64}"))) return@mapNotNull null
        val size = matchingAsset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
        if (size !in 1_000_000L..500_000_000L) return@mapNotNull null
        GithubRelease(version, matchingAsset.string("name")!!, url, size, digest.lowercase())
    }.maxWithOrNull { left, right ->
        compareVersions(parseVersion(left.version)!!, parseVersion(right.version)!!)
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

private fun parseVersion(version: String): List<Int>? {
    if (!version.matches(Regex("\\d+\\.\\d+\\.\\d+"))) return null
    return version.split('.').map { it.toIntOrNull() ?: return null }
}

internal fun isNewerAppVersion(candidate: String?, current: String): Boolean {
    val next = candidate?.let(::parseVersion) ?: return false
    val installed = parseVersion(current) ?: return false
    return compareVersions(next, installed) > 0
}

private fun compareVersions(left: List<Int>, right: List<Int>): Int {
    for (index in 0..2) {
        val comparison = left[index].compareTo(right[index])
        if (comparison != 0) return comparison
    }
    return 0
}
