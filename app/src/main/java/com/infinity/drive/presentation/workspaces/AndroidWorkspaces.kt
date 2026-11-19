package com.infinity.drive.presentation.workspaces

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.crypto.CredentialCipher
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.repository.DriveContentReader
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.presentation.sharing.ReceivedFile
import com.infinity.drive.presentation.sharing.ReceivedRequestIntake
import com.infinity.drive.presentation.sharing.publicLinkOrigin
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
internal data class WorkspaceAccess(val origin: String, val workspace: String, val token: String) {
    override fun toString(): String = "WorkspaceAccess(origin=$origin, workspace=$workspace)"
}

@Serializable internal data class Workspace(val id: String, val name: String, val role: String = "owner")
@Serializable internal data class WorkspaceRow(
    val path: String, val kind: String, val size: Long = 0, val sha256: String? = null
) {
    val name: String get() = path.substringAfterLast('/')
}
@Serializable internal data class WorkspaceMember(val id: String, val name: String, val role: String, val token: String? = null)

internal class AndroidWorkspaces(
    private val context: Context,
    private val storage: AppStoragePaths,
    private val cipher: CredentialCipher,
    private val settings: SettingsRepository,
    private val lock: AppLockManager,
    private val fileDao: FileDao,
    private val hidden: HiddenContentAccess,
    private val reader: DriveContentReader,
    private val intake: ReceivedRequestIntake
) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val empty = ByteArray(0).toRequestBody()

    suspend fun connection(): WorkspaceAccess? = withContext(Dispatchers.IO) {
        val file = accessFile(drive())
        if (!file.baseFile.exists()) return@withContext null
        check(file.baseFile.length() in 1..12_000)
        val plain = cipher.decrypt(file.readFully())
        try { json.decodeFromString<WorkspaceAccess>(plain.decodeToString()) }
        finally { plain.fill(0) }
    }

    suspend fun configure(raw: WorkspaceAccess): List<Workspace> = withContext(Dispatchers.IO) {
        val drive = drive()
        val configured = raw.copy(origin = publicLinkOrigin(raw.origin).toString())
        require(configured.workspace.isEmpty() || idPattern.matches(configured.workspace))
        require(configured.token.length in 32..4096 && configured.token.all { it.code in 33..126 })
        val available = available(configured)
        check(drive() == drive)
        val file = accessFile(drive)
        val plain = json.encodeToString(WorkspaceAccess.serializer(), configured).encodeToByteArray()
        val data = try { cipher.encrypt(plain) } finally { plain.fill(0) }
        val stream = file.startWrite()
        try { stream.write(data); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        available
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) { accessFile(drive()).delete() }

    suspend fun available(access: WorkspaceAccess): List<Workspace> =
        if (access.workspace.isBlank()) json.decodeFromString<List<Workspace>>(exchange(
            request(access, null).get().build(), ::text
        )) else listOf(json.decodeFromString<Workspace>(exchange(
            request(access, access.workspace).get().build(), ::text
        )))

    suspend fun create(access: WorkspaceAccess, name: String): Workspace =
        json.decodeFromString(exchange(request(access, null)
            .post(buildJsonObject { put("name", name) }.toString().toRequestBody(JSON)).build(), ::text))

    suspend fun open(access: WorkspaceAccess, id: String): Workspace =
        json.decodeFromString(exchange(request(access, id).get().build(), ::text))

    suspend fun entries(access: WorkspaceAccess, id: String, folder: String): List<WorkspaceRow> =
        json.decodeFromString(exchange(request(access, id, "entries", folder).get().build(), ::text))

    suspend fun members(access: WorkspaceAccess, id: String): List<WorkspaceMember> =
        json.decodeFromString(exchange(request(access, id, "members").get().build(), ::text))

    suspend fun invite(access: WorkspaceAccess, id: String, name: String, role: String): WorkspaceMember {
        val body = buildJsonObject { put("name", name); put("role", role) }.toString().toRequestBody(JSON)
        return json.decodeFromString(exchange(request(access, id, "members").post(body).build(), ::text))
    }

    suspend fun revoke(access: WorkspaceAccess, id: String, member: String) {
        require(idPattern.matches(member))
        exchange(request(access, id, "members/$member").delete().build()) { }
    }

    suspend fun folder(access: WorkspaceAccess, id: String, path: String) {
        exchange(request(access, id, "folder", path).post(empty).build()) { }
    }

    suspend fun receive(access: WorkspaceAccess, id: String, row: WorkspaceRow) = withContext(Dispatchers.IO) {
        val active = drive()
        val checksum = requireNotNull(row.sha256).also { require(hashPattern.matches(it)) }
        val revisionId = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
            .digest((id + "\u0000" + row.path + "\u0000" + checksum).encodeToByteArray()))
        intake.save(revisionId, ReceivedFile(row.name, row.size, checksum), active, validate = {
            check(drive() == active && connection() == access)
        }) { output ->
            exchange(request(access, id, "file", row.path).get().build()) { reply ->
                val body = requireNotNull(reply.body)
                check(body.contentLength() == row.size)
                body.byteStream().use { src ->
                    var remaining = row.size
                    val buffer = ByteArray(128 * 1024)
                    while (remaining > 0) {
                        val amount = src.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                        check(amount > 0)
                        output.write(buffer, 0, amount)
                        remaining -= amount
                    }
                    check(src.read() == -1)
                }
            }
        }
    }

    suspend fun publish(access: WorkspaceAccess, id: String, folder: String, file: DriveFile) = withContext(Dispatchers.IO) {
        val active = drive()
        val row = requireNotNull(fileDao.byId(file.id))
        require(row.sizeBytes in 1..MAX_BYTES && row.backupState == BackupState.BACKED_UP && row.trashedAt == null)
        suspend fun validate() {
            ensureActive()
            check(drive() == active && row.chatId == active && fileDao.byId(file.id)?.messageId == row.messageId &&
                hidden.canRead(file.id) && !hidden.protectedFile(file) && connection() == access)
        }
        validate()
        val temporary = File.createTempFile("workspace-copy-", ".tmp", context.cacheDir)
        try {
            ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                check(temporary.delete())
                val expected = ParcelFileDescriptor.AutoCloseOutputStream(fd.dup()).buffered().use { output ->
                    reader.copyRemote(row, output, ::validate)
                }
                check(row.contentHash == null || row.contentHash.equals(expected, true))
                validate()
                val body = object : RequestBody() {
                    override fun contentType() = null
                    override fun contentLength() = row.sizeBytes
                    override fun isOneShot() = true
                    override fun writeTo(sink: BufferedSink) {
                        Os.lseek(fd.fileDescriptor, 0, OsConstants.SEEK_SET)
                        ParcelFileDescriptor.AutoCloseInputStream(fd.dup()).source().use { sink.writeAll(it) }
                    }
                }
                val name = com.infinity.drive.core.files.FileNameUtils.sanitize(row.name)
                val path = listOf(folder, name).filter(String::isNotEmpty).joinToString("/")
                exchange(request(access, id, "file", path).header("If-None-Match", "*")
                    .header("X-Content-SHA256", expected).put(body).build()) { }
            }
        } finally {
            temporary.delete()
        }
    }

    private suspend fun drive(): Long {
        val state = settings.preferences.first()
        check(state.onboardingComplete && !lock.locked.value)
        return requireNotNull(state.storageChatId)
    }

    private fun accessFile(drive: Long): android.util.AtomicFile {
        val directory = File(storage.filesDir, "workspaces").apply { mkdirs() }
        return android.util.AtomicFile(File(directory, "$drive.bin"))
    }

    private fun request(access: WorkspaceAccess, id: String?, suffix: String = "", path: String? = null): Request.Builder {
        require(id == null || idPattern.matches(id))
        require(access.workspace.isEmpty() || access.workspace == id)
        val url = publicLinkOrigin(access.origin).newBuilder().addPathSegments("api/workspaces")
        if (id != null) url.addPathSegment(id)
        if (suffix.isNotEmpty()) url.addPathSegments(suffix)
        if (path != null) url.addQueryParameter("path", path)
        return Request.Builder().url(url.build()).header("Authorization", "Bearer ${access.token}")
    }

    private fun text(reply: Response): String {
        val source = requireNotNull(reply.body).source()
        check(!source.request(4 * 1024 * 1024 + 1L))
        return source.readUtf8()
    }

    private suspend fun <T> exchange(request: Request, decode: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    continuation.resumeWithException(IOException("The workspace service is unavailable."))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            check(it.isSuccessful) { "The workspace declined the request (HTTP ${it.code})." }
                            decode(it)
                        }
                    }
                    result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }

    companion object {
        internal const val MAX_BYTES = 512L * 1024 * 1024
        private val JSON = "application/json".toMediaType()
        private val idPattern = Regex("[A-Za-z0-9_-]{43}")
        private val hashPattern = Regex("[a-f0-9]{64}")
    }
}
