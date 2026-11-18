package com.infinity.drive.presentation.sharing

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.repository.DriveContentReader
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.PublicLinkConnection
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
internal data class PublicLink(
    val id: String,
    val name: String,
    val expiresAt: Long,
    val size: Long
)

@Serializable
internal data class UploadRequest(val id: String, val url: String, val expiresAt: Long, val incoming: ReceivedFile? = null)

@Serializable
internal data class ReceivedFile(val name: String, val size: Long, val sha256: String)

@Serializable
private data class CreatedPublicLink(val id: String, val url: String, val expiresAt: Long)

internal class AndroidPublicLinks(
    private val context: Context,
    private val settings: SettingsRepository,
    private val files: FileDao,
    private val reader: DriveContentReader,
    private val access: HiddenContentAccess,
    private val lock: AppLockManager,
    private val telegram: TelegramClient,
    private val received: ReceivedRequestIntake
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS).writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES).build()

    suspend fun connection(): PublicLinkConnection? {
        val drive = activeDrive()
        return settings.publicLinkConnection(drive)
    }

    suspend fun configure(origin: String, token: String) {
        val drive = activeDrive()
        val url = publicLinkOrigin(origin)
        require(token.length in 32..4096 && token.all { it.code in 33..126 })
        val connection = PublicLinkConnection(url.toString(), token)
        fetchLinks(connection)
        check(activeDrive() == drive)
        settings.setPublicLinkConnection(drive, connection)
    }

    suspend fun disconnect() {
        settings.setPublicLinkConnection(activeDrive(), null)
    }

    suspend fun links(): List<PublicLink> {
        val drive = activeDrive()
        val connection = requireNotNull(settings.publicLinkConnection(drive))
        val links = fetchLinks(connection)
        check(activeDrive() == drive)
        return links.sortedBy(PublicLink::expiresAt)
    }

    fun linkUrl(connection: PublicLinkConnection, id: String): String {
        require(linkId.matches(id))
        return publicLinkOrigin(connection.origin).newBuilder().addPathSegment("s").addPathSegment(id).build().toString()
    }

    suspend fun revoke(id: String) {
        val drive = activeDrive()
        val connection = requireNotNull(settings.publicLinkConnection(drive))
        require(linkId.matches(id))
        http.request(ownerRequest(connection, id).delete().build())
    }

    suspend fun requests(): List<UploadRequest> {
        val drive = activeDrive()
        val connection = requireNotNull(settings.publicLinkConnection(drive))
        val entries = json.decodeFromString<List<UploadRequest>>(
            http.request(ownerRequest(connection, collection = "requests").get().build())
        ).filter { it.validFor(connection) }
        check(activeDrive() == drive)
        return entries.sortedBy(UploadRequest::expiresAt)
    }

    suspend fun newRequest(hours: Int, megabytes: Int) {
        require(hours in 1..168 && megabytes in 1..512)
        val connection = requireNotNull(connection())
        val value = buildJsonObject {
            put("expiresIn", hours * 3600)
            put("maxUploadBytes", megabytes * 1024L * 1024)
        }.toString().toRequestBody("application/json".toMediaType())
        val response = http.request(ownerRequest(connection, collection = "requests").post(value).build())
        check(json.decodeFromString<UploadRequest>(response).validFor(connection))
    }

    suspend fun closeRequest(id: String) {
        require(linkId.matches(id))
        val connection = requireNotNull(connection())
        http.request(ownerRequest(connection, id, collection = "requests").delete().build())
    }

    suspend fun accept(request: UploadRequest) = withContext(Dispatchers.IO) {
        val drive = activeDrive()
        val connection = requireNotNull(settings.publicLinkConnection(drive))
        check(request.validFor(connection))
        val file = requireNotNull(request.incoming)
        val call = ownerRequest(connection, request.id, collection = "requests", receivedFile = true).get().build()
        suspend fun verify() {
            ensureActive()
            check(activeDrive() == drive && settings.publicLinkConnection(drive) == connection)
        }
        received.save(request, drive, ::verify) { output ->
            http.exchange(call) { reply ->
                val body = requireNotNull(reply.body)
                check(body.contentLength() == file.size)
                body.byteStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var copied = 0L
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        copied += size
                        check(copied <= file.size)
                        output.write(buffer, 0, size)
                    }
                }
            }
        }
    }

    private fun UploadRequest.validFor(connection: PublicLinkConnection): Boolean {
        if (!linkId.matches(id)) return false
        val exact = publicLinkOrigin(connection.origin).newBuilder().addPathSegment("u").addPathSegment(id).build()
        return url == exact.toString() &&
            (incoming == null || incoming.size in 1..MAX_SHARE_BYTES && incoming.sha256.matches(hashPattern))
    }

    suspend fun create(file: DriveFile, seconds: Int, password: String, attempt: String): String =
        withContext(Dispatchers.IO) {
            require(seconds in 300..604_800 && password.toByteArray().size <= 256)
            require(file.sizeBytes in 0..MAX_SHARE_BYTES && file.backupState == BackupState.BACKED_UP && file.hasRemoteCopy)
            val drive = activeDrive()
            val connection = requireNotNull(settings.publicLinkConnection(drive))
            val ownerRequest = ownerRequest(connection)
            suspend fun validate() {
                ensureActive()
                check(activeDrive() == drive && file.chatId == drive && access.canRead(file.id) && !access.protectedFile(file))
            }
            validate()
            val row = requireNotNull(files.byId(file.id))
            require(row.chatId == drive && row.trashedAt == null && row.modifiedAt == file.modifiedAt &&
                !row.isHidden && !access.protectedFolder(row.folderId))

            val spool = File.createTempFile("share-copy-", ".tmp", context.cacheDir)
            try {
                ParcelFileDescriptor.open(spool, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                    check(spool.delete())
                    val checksum = ParcelFileDescriptor.AutoCloseOutputStream(fd.dup()).buffered().use { output ->
                        reader.copyRemote(row, output, ::validate)
                    }
                    check(row.contentHash == null || row.contentHash.equals(checksum, ignoreCase = true))
                    validate()
                    val body = object : RequestBody() {
                        override fun contentType() = null
                        override fun contentLength() = file.sizeBytes
                        override fun isOneShot() = true
                        override fun writeTo(sink: BufferedSink) {
                            Os.lseek(fd.fileDescriptor, 0, OsConstants.SEEK_SET)
                            ParcelFileDescriptor.AutoCloseInputStream(fd.dup()).source().use { sink.writeAll(it) }
                        }
                    }
                    val response = http.request(ownerRequest
                        .header("X-File-Name", encode(FileNameUtils.sanitize(file.name)))
                        .header("X-Share-Expires-In", seconds.toString())
                        .header("X-Content-SHA256", checksum)
                        .header("X-Share-Password", encode(password))
                        .header("X-Upload-Id", UUID.fromString(attempt).toString())
                        .post(body).build())
                    val result = json.decodeFromString<CreatedPublicLink>(response)
                    val expected = linkUrl(connection, result.id)
                    check(result.url == expected)
                    validate()
                    expected
                }
            } finally {
                spool.delete()
            }
        }

    private suspend fun activeDrive(): Long {
        val prefs = settings.preferences.first()
        check(prefs.onboardingComplete && !lock.locked.value && telegram.authState.value == TelegramAuthState.Ready)
        return requireNotNull(prefs.storageChatId)
    }

    private suspend fun fetchLinks(connection: PublicLinkConnection): List<PublicLink> =
        json.decodeFromString<List<PublicLink>>(http.request(ownerRequest(connection).get().build()))
            .filter { linkId.matches(it.id) }

    private fun ownerRequest(
        connection: PublicLinkConnection,
        id: String? = null,
        collection: String = "shares",
        receivedFile: Boolean = false
    ): Request.Builder {
        val path = publicLinkOrigin(connection.origin).newBuilder().addPathSegment("api").addPathSegment(collection)
        if (id != null) path.addPathSegment(id)
        if (receivedFile) path.addPathSegment("file")
        return Request.Builder().url(path.build()).header("Authorization", "Bearer ${connection.ownerToken}")
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    private suspend fun OkHttpClient.request(request: Request): String = exchange(request) { reply ->
        val source = requireNotNull(reply.body).source()
        check(!source.request(MAX_RESPONSE + 1)) { "Sharing response is too large" }
        source.readUtf8()
    }

    private suspend fun <T> OkHttpClient.exchange(request: Request, consume: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(IOException("Sharing service unavailable"))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use { reply ->
                        check(reply.isSuccessful) { "Sharing service declined the request" }
                        consume(reply)
                    }
                    continuation.resume(value)
                } catch (_: Exception) {
                    continuation.resumeWithException(IOException("Sharing service returned an invalid response"))
                }
            }
        })
    }

    companion object {
        const val MAX_SHARE_BYTES = 512L * 1024 * 1024
        private const val MAX_RESPONSE = 1L * 1024 * 1024
        private val hashPattern = Regex("[a-f0-9]{64}")
        private val linkId = Regex("[A-Za-z0-9_-]{43}")
    }
}

internal fun publicLinkOrigin(raw: String): HttpUrl =
    raw.trim().toHttpUrl().also { url ->
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
            url.query == null && url.fragment == null && url.encodedPath == "/")
    }
