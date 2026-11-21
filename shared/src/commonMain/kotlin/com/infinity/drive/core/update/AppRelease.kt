package com.infinity.drive.core.update

data class AppRelease(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val androidAssets: List<AppUpdateAsset> = emptyList()
) {
    fun androidAssetFor(supportedAbis: List<String>): AppUpdateAsset? =
        supportedAbis.firstNotNullOfOrNull { abi -> androidAssets.singleOrNull { it.abi == abi && it.belongsTo(version) } }
}

data class AppUpdateAsset(val abi: String, val url: String, val sizeBytes: Long, val sha256: String) {
    fun belongsTo(version: String): Boolean =
        abi in supportedAbis && releaseVersion.matches(version) && sizeBytes in 1..maxSizeBytes &&
            digestPattern.matches(sha256) && url == "${AppLinks.REPOSITORY}/releases/download/v$version/tg-drive-$abi.apk"

    companion object {
        internal val supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        internal val releaseVersion = Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[.-][A-Za-z0-9]+)*")
        private val digestPattern = Regex("[0-9a-f]{64}")
        private const val maxSizeBytes = 128L * 1024 * 1024
    }
}
