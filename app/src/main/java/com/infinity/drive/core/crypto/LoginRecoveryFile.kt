package com.infinity.drive.core.crypto

import com.infinity.drive.core.telegram.TelegramLoginRecovery
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Version 1: authenticated magic/version, 16-byte salt, 12-byte nonce, ciphertext and GCM tag. */
internal object LoginRecoveryFile {
    const val MIN_PASSPHRASE_LENGTH = 16
    const val MAX_PASSPHRASE_LENGTH = 256
    const val FILE_NAME = "TeleDrive-recovery.tgdr"

    private val random = SecureRandom()
    private val magic = byteArrayOf('T'.code.toByte(), 'G'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(), 1)

    fun encrypt(recovery: TelegramLoginRecovery, passphrase: CharArray): ByteArray {
        require(passphrase.size in MIN_PASSPHRASE_LENGTH..MAX_PASSPHRASE_LENGTH && passphrase.any { !it.isWhitespace() })
        val phone = recovery.phoneNumber.encodeToByteArray()
        val token = recovery.token.encodeToByteArray()
        val key = ByteArray(32)
        var payload: ByteArray? = null
        try {
            require(recovery.accountId > 0 && phone.isNotEmpty() && phone.size <= 32)
            require(token.isNotEmpty() && token.size <= 4096)

            val salt = ByteArray(16).also(random::nextBytes)
            val nonce = ByteArray(12).also(random::nextBytes)
            val header = magic + salt + nonce
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withParallelism(4)
                .withMemoryAsKB(64 * 1024)
                .withIterations(3)
                .withSalt(salt)
                .build()
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(passphrase, key)

            val plaintext = ByteBuffer.allocate(8 + 2 + phone.size + 2 + token.size)
                .putLong(recovery.accountId)
                .putShort(phone.size.toShort()).put(phone)
                .putShort(token.size.toShort()).put(token)
                .array()
            payload = plaintext
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plaintext)
        } finally {
            phone.fill(0)
            token.fill(0)
            payload?.fill(0)
            key.fill(0)
        }
    }
}
