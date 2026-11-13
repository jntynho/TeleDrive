package com.infinity.drive.core.security

import android.content.Context
import android.util.AtomicFile
import com.infinity.drive.core.crypto.CredentialCipher
import kotlinx.serialization.json.Json
import java.io.File

/** Outside backup; the atomic record (including retry counters) is authenticated with Keystore AES-GCM. */
class AndroidHiddenLockStore(context:Context,private val cipher:CredentialCipher,recordName:String="hidden-lock.bin"):HiddenLockStore {
    private val file=AtomicFile(File(context.noBackupFilesDir,recordName))
    override suspend fun read():HiddenLockRecord? {
        val candidates=listOf(file.baseFile,File(file.baseFile.path+".bak"),File(file.baseFile.path+".new")).filter{it.exists()}
        if(candidates.isEmpty())return null
        require(candidates.all{it.length() in 1..16_384})
        val plain=cipher.decrypt(file.readFully())
        return try{Json.decodeFromString<HiddenLockRecord>(plain.decodeToString())}finally{plain.fill(0)}
    }
    override suspend fun write(record:HiddenLockRecord){
        val plain=Json.encodeToString(HiddenLockRecord.serializer(),record).encodeToByteArray()
        val bytes=try{cipher.encrypt(plain)}finally{plain.fill(0)}
        val stream=file.startWrite()
        try{stream.write(bytes);file.finishWrite(stream)}catch(error:Exception){file.failWrite(stream);throw error}
    }
    override suspend fun clear(){file.delete()}
}
