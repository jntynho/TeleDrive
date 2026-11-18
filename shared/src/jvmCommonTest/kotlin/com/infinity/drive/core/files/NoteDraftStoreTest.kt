package com.infinity.drive.core.files

import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.crypto.WrappedKeyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteDraftStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun draftsAreEncryptedDurableAndIsolatedBySlot() = runBlocking {
        val storage = object : AppStoragePaths {
            override val filesDir = temp.root
            override val cacheDir = temp.root
            override val externalCacheDir = null
            override val externalStorageRoot = null
        }
        val keys = object : WrappedKeyRepository {
            private val value = ByteArray(32) { it.toByte() }
            override fun getOrCreate(name: String, sizeBytes: Int) = value
            override fun get(name: String) = value
            override fun exists(name: String) = true
            override fun wasRecreated(name: String) = false
            override fun store(name: String, key: ByteArray) = Unit
            override fun storeIfMissingOrMatching(name: String, key: ByteArray) = false
        }
        val draft = NoteDraft("خاص", "Draft contents")
        NoteDraftStore(storage, keys, StreamCrypto()).write("-1:note:id", draft)
        val reopened = NoteDraftStore(storage, keys, StreamCrypto())
        assertEquals(draft, reopened.read("-1:note:id"))
        assertNull(reopened.read("-2:note:id"))
        assertFalse(temp.root.resolve("note-drafts").listFiles()!!.single().readBytes().decodeToString().contains(draft.body))
        reopened.clear("-1:note:id")
        assertNull(reopened.read("-1:note:id"))
    }
}
