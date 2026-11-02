package app.tgdrive.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.tgdrive.data.IndexStore
import app.tgdrive.library.*
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramDownloadEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Compatibility reader. Opens the old database read-only and never publishes messages. */
class PreviousLibraryReader(private val context: Context, private val telegram: TelegramClient) {
    data class Entry(val item: Item, val version: Version, val vaultId: String = "")
    data class Listing(val files: List<Entry>, val locked: List<VaultDescriptor>)
    private val vaults = VaultManager()
    private var accountId = 0L

    suspend fun list(): Listing = withContext(Dispatchers.IO) {
        accountId = telegram.getCurrentUser().id
        val path = context.getDatabasePath("library-v2-$accountId.db")
        if (!path.exists()) {
            return@withContext Listing(IndexStore(context, accountId).read().first.map { old ->
                val versionId = "legacy-${old.messageId}"
                Entry(Item(id=old.metadata.id,name=old.name,mime=old.mimeType,size=old.size,versionId=versionId),
                    Version(id=versionId,itemId=old.metadata.id,size=old.size,sha256="",imported=true,
                        parts=listOf(Part(old.messageId,0,old.size,old.size,""))))
            }, emptyList())
        }
        val codec = LocalRowCipher(accountId, existingOnly=true)
        val envelopes = SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.query("entities",arrayOf("id","payload"),null,null,null,null,null).use { cursor ->
                buildList {
                    while(cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        add(codec.decode("entities",cursor.getString(0),cursor.getString(1)).model<Envelope>())
                    }
                }
            }
        }
        val descriptors = envelopes.filter { it.kind==EntityKind.VAULT && !it.tombstone }
            .map { vaults.body(it).model<VaultDescriptor>() }
        val readable = envelopes.filter { !it.tombstone && vaults.isUnlocked(it.vaultId) }
        val versions = readable.filter { it.kind==EntityKind.VERSION }.associate { it.entityId to vaults.body(it).model<Version>() }
        val entries = readable.filter { it.kind==EntityKind.ITEM }.mapNotNull { envelope ->
            val item = vaults.body(envelope).model<Item>()
            val version = versions[item.versionId] ?: return@mapNotNull null
            if(item.deletedAt!=0L || version.itemId!=item.id) return@mapNotNull null
            Entry(item,version,envelope.vaultId)
        }
        Listing(entries.sortedByDescending { it.item.modifiedAt },descriptors.filterNot { vaults.isUnlocked(it.id) })
    }

    suspend fun unlock(descriptor:VaultDescriptor, password:CharArray, recovery:Boolean) = withContext(Dispatchers.Default) {
        vaults.unlock(descriptor,password,recovery)
    }

    /** Verifies each stored part, decrypted size, and whole-file hash before offering an export. */
    suspend fun verifiedCopy(entry:Entry,onProgress:(String)->Unit):File = withContext(Dispatchers.IO) {
        require(telegram.getCurrentUser().id==accountId) { "Switch back to the original Telegram account." }
        val version=entry.version
        val parts=version.parts.sortedBy { it.index }
        require(parts.isNotEmpty() && parts.map { it.index }==parts.indices.toList()) { "The previous file has missing parts." }
        require(parts.sumOf { it.plainSize }==version.size && version.size>=0) { "The previous file size is inconsistent." }
        val folder=File(context.cacheDir,"previous-library-exports").apply { mkdirs() }
        require(folder.usableSpace>version.size+16L*1024*1024) { "Not enough space to verify this file." }
        val target=File.createTempFile("verified-",".part",folder)
        try {
            val chatId=telegram.previousSavedMessagesChatId()
            target.outputStream().use { output ->
                parts.forEachIndexed { index,part ->
                    currentCoroutineContext().ensureActive()
                    onProgress("Downloading part ${index+1} of ${parts.size}")
                    val document=telegram.getDocument(chatId,part.messageId) ?: error("A previous file part is unavailable in Telegram.")
                    val result=telegram.downloadDocument(document.remoteFileId)
                        .filterIsInstance<TelegramDownloadEvent.Completed>().first()
                    val stored=File(result.localPath)
                    require(stored.length()==part.storedSize) { "A downloaded part has the wrong size." }
                    if(part.storedSha256.isNotEmpty())require(hash(stored)==part.storedSha256) { "A downloaded part failed verification." }
                    onProgress("Verifying part ${index+1} of ${parts.size}")
                    stored.inputStream().use { input ->
                        if(part.encrypted) {
                            require(entry.vaultId.isNotEmpty()) { "The previous encryption key is required." }
                            vaults.decrypt(entry.vaultId,input,output,"${entry.item.id}/${version.id}/${part.index}")
                        } else copyCancellable(input,output)
                    }
                }
            }
            require(target.length()==version.size) { "The restored file size is incorrect." }
            if(version.sha256.isNotEmpty())require(hash(target)==version.sha256) { "The restored file failed verification." }
            target
        } catch(t:Throwable) { target.delete(); throw t }
    }

    fun close() = vaults.lock()

    private suspend fun hash(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer=ByteArray(128*1024)
            while(true) { currentCoroutineContext().ensureActive(); val n=input.read(buffer); if(n<0)break; digest.update(buffer,0,n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal suspend fun copyCancellable(input:java.io.InputStream,output:java.io.OutputStream) {
    val buffer=ByteArray(128*1024)
    while(true) { currentCoroutineContext().ensureActive(); val n=input.read(buffer); if(n<0)break; output.write(buffer,0,n) }
}
