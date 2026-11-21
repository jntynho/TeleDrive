package com.infinity.drive.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerAssetTest {
    private val checker = UpdateChecker("1.5.5", 91)
    private val digest = "0123456789abcdef".repeat(4)

    @Test fun selectsTheFirstCompatibleVerifiedArmAssetInDeviceOrder() {
        val release = requireNotNull(checker.decodeLatest(release(
            asset("arm64-v8a"),
            asset("armeabi-v7a"),
            asset("x86_64")
        )))

        assertEquals("1.5.6", release.version)
        assertEquals("${AppLinks.REPOSITORY}/releases/tag/v1.5.6", release.pageUrl)
        assertEquals(digest, release.androidAssetFor(listOf("arm64-v8a", "armeabi-v7a"))?.sha256)
        assertEquals("armeabi-v7a", release.androidAssetFor(listOf("x86", "armeabi-v7a"))?.abi)
        assertNull(release.androidAssetFor(listOf("x86_64")))
    }

    @Test fun rejectsWrongReposTagsMissingDigestsAndUnpublishedAssets() {
        val release = requireNotNull(checker.decodeLatest(release(
            asset("arm64-v8a", origin = "https://github.com/another/TeleDrive"),
            asset("arm64-v8a", version = "1.5.7"),
            asset("arm64-v8a", digest = ""),
            asset("arm64-v8a", size = 0),
            asset("arm64-v8a", size = 200_000_000),
            asset("arm64-v8a", state = "new"),
            asset("armeabi-v7a")
        )))

        assertNull(release.androidAssetFor(listOf("arm64-v8a")))
        assertEquals("armeabi-v7a", release.androidAssetFor(listOf("arm64-v8a", "armeabi-v7a"))?.abi)
    }

    private fun release(vararg assets: String) =
        """{"tag_name":"v1.5.6","html_url":"https://other.example/download","assets":[${assets.joinToString(",")}]}"""

    private fun asset(
        abi: String,
        origin: String = AppLinks.REPOSITORY,
        version: String = "1.5.6",
        digest: String = this.digest,
        size: Long = 10_000,
        state: String = "uploaded"
    ): String {
        val name = "tg-drive-$abi.apk"
        return """{"name":"$name","browser_download_url":"$origin/releases/download/v$version/$name","size":$size,"digest":"sha256:$digest","state":"$state"}"""
    }
}
