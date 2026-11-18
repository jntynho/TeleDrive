package com.infinity.drive.core.crypto

import com.infinity.drive.core.telegram.TelegramLoginRecovery
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LoginRecoveryFileTest {
    private val recovery = TelegramLoginRecovery(1234, "+00000000000", "synthetic-test-token")
    private val passphrase = "rain river cedar morning".toCharArray()

    @Test
    fun restoresOnlyTheEncryptedAccountAndToken() {
        val file = LoginRecoveryFile.encrypt(recovery, passphrase)
        val bytes = decrypt(file, passphrase)
        assertEquals(recovery.accountId, bytes.long)
        assertEquals(recovery.phoneNumber, bytes.readString())
        assertEquals(recovery.token, bytes.readString())
        assertFalse(bytes.hasRemaining())
        assertFalse(file.decodeToString().contains(recovery.token))
        assertFalse(file.decodeToString().contains(recovery.phoneNumber))
    }

    @Test
    fun authenticatesTheHeaderAndRequiresTheCorrectPassphrase() {
        val file = LoginRecoveryFile.encrypt(recovery, passphrase)
        val otherFile = LoginRecoveryFile.encrypt(recovery, passphrase)
        assertFalse(file.copyOfRange(5, 33).contentEquals(otherFile.copyOfRange(5, 33)))
        assertThrows(AEADBadTagException::class.java) { decrypt(file, "incorrect passphrase".toCharArray()) }

        file[4] = 2
        assertThrows(AEADBadTagException::class.java) { decrypt(file, passphrase) }
    }

    private fun decrypt(file: ByteArray, secret: CharArray): ByteBuffer {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withParallelism(4)
            .withMemoryAsKB(65_536)
            .withIterations(3)
            .withSalt(file.copyOfRange(5, 21))
            .build()
        val key = ByteArray(32)
        Argon2BytesGenerator().apply { init(parameters) }.generateBytes(secret, key)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, file, 21, 12))
            cipher.updateAAD(file, 0, 33)
            ByteBuffer.wrap(cipher.doFinal(file, 33, file.size - 33))
        } finally {
            key.fill(0)
        }
    }

    private fun ByteBuffer.readString() = ByteArray(short.toInt()).also { get(it) }.decodeToString()
}
