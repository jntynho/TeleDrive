// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.crypto

import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.telegram.TdlibDatabaseKeyProvider
import java.io.File

/** Existing session databases must never be paired with a newly minted key. */
class TdlibDatabaseKeyProviderImpl(
    private val storagePaths: AppStoragePaths,
    private val wrappedKeyRepository: WrappedKeyRepository
) : TdlibDatabaseKeyProvider {
    override fun databaseKey(): ByteArray {
        val database = File(storagePaths.filesDir, "tdlib/db")
        if(database.listFiles()?.isNotEmpty()==true) {
            return wrappedKeyRepository.get(CryptoKeys.TDLIB_DATABASE)
                ?: throw KeyUnavailableException(CryptoKeys.TDLIB_DATABASE)
        }
        return wrappedKeyRepository.getOrCreate(CryptoKeys.TDLIB_DATABASE)
    }
}
