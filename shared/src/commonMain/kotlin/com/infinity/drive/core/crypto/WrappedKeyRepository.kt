package com.infinity.drive.core.crypto

/**
 * Persists random raw keys wrapped by a platform master key. Raw keys never
 * touch disk unencrypted; unwrapped copies are cached in memory only.
 */
interface WrappedKeyRepository {

    fun getOrCreate(name: String, sizeBytes: Int = 32): ByteArray

    fun wasRecreated(name: String): Boolean

    fun get(name: String): ByteArray?

    fun exists(name: String): Boolean

    fun store(name: String, key: ByteArray)

    /** Installs a recovered key only if it does not replace a readable, different key. */
    fun storeIfMissingOrMatching(name: String, key: ByteArray): Boolean
}
