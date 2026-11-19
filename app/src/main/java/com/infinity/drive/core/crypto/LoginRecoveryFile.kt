package com.infinity.drive.core.crypto

import com.infinity.drive.core.telegram.TelegramLoginRecovery
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Version 1: authenticated magic/version, 16-byte salt, 12-byte nonce, ciphertext and GCM tag. */
internal object LoginRecoveryFile {
    const val MIN_PASSPHRASE_LENGTH = 4
    const val MAX_PASSPHRASE_LENGTH = 256
    const val FILE_NAME = "TeleDrive-recovery.tgdr"

    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val TAG_SIZE = 16
    private const val MAX_PHONE_BYTES = 32
    private const val MAX_TOKEN_BYTES = 4096
    private const val PAYLOAD_PREFIX_SIZE = 12
    const val MAX_FILE_SIZE = 5 + SALT_SIZE + NONCE_SIZE + PAYLOAD_PREFIX_SIZE + MAX_PHONE_BYTES + MAX_TOKEN_BYTES + TAG_SIZE

    private val random = SecureRandom()
    private val magic = byteArrayOf('T'.code.toByte(), 'G'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(), 1)

    fun isValidPassphrase(passphrase: CharSequence): Boolean =
        passphrase.length in MIN_PASSPHRASE_LENGTH..MAX_PASSPHRASE_LENGTH &&
            Character.codePointCount(passphrase, 0, passphrase.length) >= MIN_PASSPHRASE_LENGTH &&
            passphrase.isNotBlank()

    fun encrypt(recovery: TelegramLoginRecovery, passphrase: CharArray): ByteArray {
        require(isValidPassphrase(CharBuffer.wrap(passphrase)))
        val phone = recovery.phoneNumber.encodeToByteArray()
        val token = recovery.token.encodeToByteArray()
        val key = ByteArray(32)
        var payload: ByteArray? = null
        try {
            require(recovery.accountId > 0 && phone.isNotEmpty() && phone.size <= MAX_PHONE_BYTES)
            require(token.isNotEmpty() && token.size <= MAX_TOKEN_BYTES)

            val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
            val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
            val header = magic + salt + nonce
            deriveKey(passphrase, salt, key)

            val plaintext = ByteBuffer.allocate(PAYLOAD_PREFIX_SIZE + phone.size + token.size)
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

    fun decrypt(file: ByteArray, passphrase: CharArray): TelegramLoginRecovery {
        require(isValidPassphrase(CharBuffer.wrap(passphrase)))
        val saltEnd = magic.size + SALT_SIZE
        val headerEnd = saltEnd + NONCE_SIZE
        require(file.size in (headerEnd + PAYLOAD_PREFIX_SIZE + 2 + TAG_SIZE)..MAX_FILE_SIZE)
        require(file.copyOfRange(0, magic.size).contentEquals(magic))

        val key = ByteArray(32)
        var payload: ByteArray? = null
        try {
            deriveKey(passphrase, file.copyOfRange(magic.size, saltEnd), key)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, file, saltEnd, NONCE_SIZE))
            cipher.updateAAD(file, 0, headerEnd)
            val plaintext = cipher.doFinal(file, headerEnd, file.size - headerEnd)
            payload = plaintext
            val reader = ByteBuffer.wrap(plaintext)
            val accountId = reader.long
            val phone = reader.readString(MAX_PHONE_BYTES)
            val token = reader.readString(MAX_TOKEN_BYTES)
            require(accountId > 0 && !reader.hasRemaining())
            val digits = phone.removePrefix("+")
            require(digits.length in 1..15 && digits.all { it in '0'..'9' } && token.isNotBlank())
            return TelegramLoginRecovery(accountId, "+$digits", token)
        } finally {
            payload?.fill(0)
            key.fill(0)
        }
    }

    private fun ByteBuffer.readString(maxBytes: Int): String {
        require(remaining() >= 2)
        val size = short.toInt()
        require(size in 1..maxBytes && size <= remaining())
        val bytes = ByteArray(size)
        return try {
            get(bytes)
            bytes.decodeToString(throwOnInvalidSequence = true)
        } finally {
            bytes.fill(0)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, key: ByteArray) {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withParallelism(4)
            .withMemoryAsKB(64 * 1024)
            .withIterations(3)
            .withSalt(salt)
            .build()
        Argon2BytesGenerator().apply { init(parameters) }.generateBytes(passphrase, key)
    }
}
