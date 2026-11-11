package com.infinity.drive.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.infinity.drive.presentation.platform.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Every successful unlock decrypts a challenge with an authentication-bound Keystore key. */
class AndroidHiddenBiometrics(private val activity:FragmentActivity):HiddenBiometrics {
    override val available:Boolean get()=BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(ALIAS,null) as? SecretKey)?.let{return it}
        val spec=KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
        if(Build.VERSION.SDK_INT>=30)spec.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG)
        else @Suppress("DEPRECATION") spec.setUserAuthenticationValidityDurationSeconds(-1)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(spec.build())}.generateKey()
    }
    private suspend fun authenticate(cipher:Cipher):Cipher?=suspendCancellableCoroutine{continuation->
        if(!available){continuation.resume(null);return@suspendCancellableCoroutine}
        val prompt=BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),object:BiometricPrompt.AuthenticationCallback(){
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){
                if(continuation.isActive)continuation.resume(result.cryptoObject?.cipher)
            }
            override fun onAuthenticationError(code:Int,message:CharSequence){if(continuation.isActive)continuation.resume(null)}
        })
        continuation.invokeOnCancellation{prompt.cancelAuthentication()}
        try{prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Hidden")
            .setSubtitle("Verify your fingerprint or supported biometric")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel").build(),BiometricPrompt.CryptoObject(cipher))}
        catch(error:Exception){if(continuation.isActive)continuation.resumeWithException(error)}
    }
    override suspend fun enroll():HiddenBiometricEnrollment? {
        // Enrollment is requested only after PIN/key verification. Replace invalidated/old enrollment keys.
        KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(ALIAS)}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
        val authenticated=authenticate(cipher)?:return null
        val secret=ByteArray(32).also(SecureRandom()::nextBytes)
        return try{HiddenBiometricEnrollment(authenticated.iv,authenticated.doFinal(secret),secret)}catch(error:Exception){secret.fill(0);throw error}
    }
    override suspend fun authenticate(iv:ByteArray,ciphertext:ByteArray):ByteArray? {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))}
        return authenticate(cipher)?.doFinal(ciphertext)
    }
    companion object {private const val ALIAS="teledrive_hidden_biometric_v1"}
}
