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
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginRecoveryFileTest {
    private val recovery = TelegramLoginRecovery(1234, "+00000000000", "synthetic-test-token")
    private val passphrase = "rain river cedar morning".toCharArray()

    @Test
    fun acceptsFourDigitsAndRejectsShortOrBlankPasswords() {
        val pin = "1234".toCharArray()
        val file = LoginRecoveryFile.encrypt(recovery, pin)
        val restored = LoginRecoveryFile.decrypt(file, pin)
        assertEquals(recovery.accountId, restored.accountId)
        assertEquals(recovery.phoneNumber, restored.phoneNumber)
        assertEquals(recovery.token, restored.token)
        assertTrue(LoginRecoveryFile.isValidPassphrase("١٢٣٤"))

        for (invalid in listOf("123", "    ", "🔐🔐")) {
            assertFalse(LoginRecoveryFile.isValidPassphrase(invalid))
            assertThrows(IllegalArgumentException::class.java) {
                LoginRecoveryFile.encrypt(recovery, invalid.toCharArray())
            }
            assertThrows(IllegalArgumentException::class.java) {
                LoginRecoveryFile.decrypt(file, invalid.toCharArray())
            }
        }
        assertThrows(AEADBadTagException::class.java) {
            LoginRecoveryFile.decrypt(file, "4321".toCharArray())
        }
    }

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
    fun importsExistingVersionOneFilesAndRejectsWrongPasswordsAndDamagedData() {
        val file = LoginRecoveryFile.encrypt(recovery, passphrase)
        val imported = LoginRecoveryFile.decrypt(file, passphrase)
        assertEquals(recovery.accountId, imported.accountId)
        assertEquals(recovery.phoneNumber, imported.phoneNumber)
        assertEquals(recovery.token, imported.token)
        assertThrows(AEADBadTagException::class.java) {
            LoginRecoveryFile.decrypt(file, "incorrect passphrase".toCharArray())
        }
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { LoginRecoveryFile.decrypt(file, passphrase) }
        assertThrows(IllegalArgumentException::class.java) {
            LoginRecoveryFile.decrypt(ByteArray(LoginRecoveryFile.MAX_FILE_SIZE + 1), passphrase)
        }
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
