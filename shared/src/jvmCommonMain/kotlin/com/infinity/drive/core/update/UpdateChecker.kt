// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.update

import com.infinity.drive.core.common.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the latest GitHub release and compares its tag with the running build.
 * Nothing is sent along with the request: it is an anonymous read of a public
 * endpoint, so a check reveals no more than visiting the releases page.
 */
class UpdateChecker(
    private val currentVersion: String,
    private val currentBuild: Int = 0
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun newerRelease(strict:Boolean=false): AppRelease? = withContext(Dispatchers.IO) {
        val payload=try{fetchLatest()}catch(e:Exception){
            if(e is kotlinx.coroutines.CancellationException || strict)throw e
            SafeLog.w(TAG,"Update check failed",e);return@withContext null
        }

        runCatching { decodeLatest(payload) }.getOrElse { if (strict) throw it; null }
    }

    internal fun decodeLatest(payload: String): AppRelease? {
        val release = json.decodeFromString(GitHubRelease.serializer(), payload)
        val latest = release.tag.removePrefix("v").trim()
        if (release.draft || release.preRelease || !AppUpdateAsset.releaseVersion.matches(latest)) return null
        if (!isNewer(latest, if (currentBuild > 0) "$currentVersion-build.$currentBuild" else currentVersion)) return null

        return AppRelease(
            version = latest,
            notes = release.body.orEmpty().trim(),
            pageUrl = "${AppLinks.REPOSITORY}/releases/tag/v$latest",
            androidAssets = release.assets.mapNotNull { asset ->
                val abi = AppUpdateAsset.supportedAbis.firstOrNull { asset.name == "tg-drive-$it.apk" }
                    ?: return@mapNotNull null
                val digest = asset.digest?.takeIf { it.startsWith("sha256:") } ?: return@mapNotNull null
                AppUpdateAsset(abi, asset.url, asset.size, digest.removePrefix("sha256:"))
                    .takeIf { asset.state == "uploaded" && it.belongsTo(latest) }
            }
        )
    }

    private fun fetchLatest(): String {
        val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Compares dotted numbers so 1.10 counts as newer than 1.9. */
    private fun isNewer(candidate: String, current: String): Boolean {
        val left = candidate.numbers()
        val right = current.removePrefix("v").numbers()
        if (left.isEmpty()) return false
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun String.numbers(): List<Int> = split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tag: String,
        val body: String? = null,
        val draft: Boolean = false,
        @SerialName("prerelease") val preRelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList()
    )

    @Serializable
    private data class GitHubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        val size: Long = 0,
        val digest: String? = null,
        val state: String = ""
    )

    private companion object {
        const val TAG = "UpdateChecker"
        const val RELEASES_URL =
            "https://api.github.com/repos/jntynho/TeleDrive/releases/latest"
        const val USER_AGENT = "TGDrive"
        const val TIMEOUT_MS = 15_000
    }
}
