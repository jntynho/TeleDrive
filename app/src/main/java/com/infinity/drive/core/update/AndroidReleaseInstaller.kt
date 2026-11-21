package com.infinity.drive.core.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.infinity.drive.presentation.platform.AppUpdateInstallResult
import com.infinity.drive.presentation.platform.AppUpdateInstaller
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal class AndroidReleaseInstaller(private val activity: Activity) : AppUpdateInstaller {
    private val packageManager get() = activity.packageManager

    override fun supports(release: AppRelease): Boolean =
        release.androidAssetFor(Build.SUPPORTED_ABIS.toList()) != null

    override suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): AppUpdateInstallResult =
        installation.withLock {
            val asset = release.androidAssetFor(Build.SUPPORTED_ABIS.toList())
                ?: return@withLock AppUpdateInstallResult.InstallerUnavailable
            if (!packageManager.canRequestPackageInstalls()) return@withLock requestInstallPermission()

            val apk = try {
                withContext(Dispatchers.IO) { prepareApk(release.version, asset, onProgress) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: InvalidUpdateException) {
                return@withLock AppUpdateInstallResult.VerificationFailed
            } catch (_: IOException) {
                return@withLock AppUpdateInstallResult.DownloadFailed
            } catch (_: SecurityException) {
                return@withLock AppUpdateInstallResult.VerificationFailed
            }

            if (!packageManager.canRequestPackageInstalls()) return@withLock requestInstallPermission()
            try {
                openInstaller(apk)
            } catch (_: IllegalArgumentException) {
                AppUpdateInstallResult.InstallerUnavailable
            } catch (_: SecurityException) {
                AppUpdateInstallResult.InstallerUnavailable
            }
        }

    private fun requestInstallPermission(): AppUpdateInstallResult {
        val permission = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
        return if (startSystemActivity(permission)) AppUpdateInstallResult.PermissionRequired
        else AppUpdateInstallResult.InstallerUnavailable
    }

    @Suppress("DEPRECATION") // Android displays its native consent UI for this system-only intent.
    private fun openInstaller(apk: File): AppUpdateInstallResult {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("TeleDrive APK", uri)
        }
        if (startSystemActivity(intent)) return AppUpdateInstallResult.InstallerOpened
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        return if (startSystemActivity(intent)) AppUpdateInstallResult.InstallerOpened
        else AppUpdateInstallResult.InstallerUnavailable
    }

    private fun startSystemActivity(intent: Intent): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val flags = PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_DEFAULT_ONLY
        val handler = packageManager.resolveActivity(intent, flags)?.activityInfo ?: return false
        intent.component = ComponentName(handler.packageName, handler.name)
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            intent.component = null
            false
        } catch (_: SecurityException) {
            intent.component = null
            false
        }
    }

    private suspend fun prepareApk(version: String, asset: AppUpdateAsset, onProgress: (Float) -> Unit): File {
        val directory = File(activity.cacheDir, "app-updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Update directory unavailable")
        val expired = System.currentTimeMillis() - APK_CACHE_DURATION_MS
        directory.listFiles()?.filter { it.lastModified() < expired }?.forEach { it.delete() }
        val cached = File(directory, "${asset.sha256}.apk")
        if (cached.isFile && cached.matches(asset)) {
            verifyPackage(cached, version)
            cached.setLastModified(System.currentTimeMillis())
            return cached
        }

        val partial = File.createTempFile("download-", ".apk", directory)
        try {
            download(asset, partial, onProgress)
            verifyPackage(partial, version)
            coroutineContext.ensureActive()
            if (!partial.renameTo(cached)) throw IOException("Unable to stage update")
            return cached
        } finally {
            partial.delete()
        }
    }

    private fun verifyPackage(file: File, releaseVersion: String) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val installed = try {
            packageManager.getPackageInfo(activity.packageName, flags).updateIdentity()
        } catch (_: PackageManager.NameNotFoundException) {
            throw InvalidUpdateException()
        }
        @Suppress("DEPRECATION")
        val download = packageManager.getPackageArchiveInfo(file.path, flags)?.updateIdentity()
        if (download == null || !installed.accepts(download, releaseVersion)) throw InvalidUpdateException()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.updateIdentity() = UpdateApkIdentity(
        packageName = packageName.orEmpty(),
        versionName = versionName.orEmpty(),
        versionCode = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong(),
        signers = (if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures)
            .orEmpty().mapTo(mutableSetOf()) { it.toCharsString() }
    )

    private suspend fun download(asset: AppUpdateAsset, destination: File, onProgress: (Float) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = openAssetConnection(URL(asset.url))
        try {
            val declaredLength = connection.contentLengthLong
            if (declaredLength >= 0 && declaredLength != asset.sizeBytes) throw InvalidUpdateException()
            var downloaded = 0L
            var lastPercent = -1
            val buffer = ByteArray(64 * 1024)
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > asset.sizeBytes) throw InvalidUpdateException()
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val percent = (downloaded * 100 / asset.sizeBytes).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            withContext(Dispatchers.Main.immediate) { onProgress(percent / 100f) }
                        }
                    }
                }
            }
            if (downloaded != asset.sizeBytes || !asset.matches(digest.digest())) throw InvalidUpdateException()
        } finally {
            connection.disconnect()
        }
    }

    private fun openAssetConnection(initial: URL): HttpsURLConnection {
        var target = initial
        repeat(5) {
            if (target.protocol != "https" || target.userInfo != null ||
                target.port !in setOf(-1, 443) || target.host !in githubDownloadHosts
            ) throw InvalidUpdateException()

            val connection = (target.openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "TeleDrive")
            }
            val status = try {
                connection.responseCode
            } catch (failure: IOException) {
                connection.disconnect()
                throw failure
            }
            if (status == HttpURLConnection.HTTP_OK) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (status !in redirectCodes || location.isNullOrBlank()) throw IOException("Update download failed")
            target = URL(target, location)
        }
        throw IOException("Update has too many redirects")
    }

    private suspend fun File.matches(asset: AppUpdateAsset): Boolean {
        if (length() != asset.sizeBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return asset.matches(digest.digest())
    }

    private fun AppUpdateAsset.matches(digest: ByteArray): Boolean {
        val expected = ByteArray(digest.size) { index ->
            sha256.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return MessageDigest.isEqual(expected, digest)
    }

    private class InvalidUpdateException : IOException()

    private companion object {
        val installation = Mutex()
        val redirectCodes = setOf(301, 302, 303, 307, 308)
        val githubDownloadHosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
        const val APK_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
    }
}

internal data class UpdateApkIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signers: Set<String>
) {
    fun accepts(candidate: UpdateApkIdentity, releaseVersion: String): Boolean {
        val buildSuffix = "-build."
        val expectedCode = releaseVersion.substringAfterLast(buildSuffix, "").toLongOrNull()
        val expectedName = if (expectedCode == null) releaseVersion else releaseVersion.substringBeforeLast(buildSuffix)
        return packageName == candidate.packageName && candidate.versionName == expectedName &&
            candidate.versionCode > versionCode && (expectedCode == null || expectedCode == candidate.versionCode) &&
            signers.isNotEmpty() && signers == candidate.signers
    }
}
