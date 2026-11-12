package com.infinity.drive.presentation.applock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks for the device owner before a sensitive change. Falls through when the
 * device has no secure lock, otherwise there would be no way to recover.
 */
fun requireDeviceOwner(
    activity: FragmentActivity?,
    title: String,
    subtitle: String,
    onDenied: (String?) -> Unit = {},
    allowUnsecured: Boolean = true,
    onConfirmed: () -> Unit
) {
    val host = activity ?: run {
        if(allowUnsecured)onConfirmed()else onDenied("Couldn't open device authentication. Please try again.")
        return
    }
    if(!allowUnsecured&&host.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure!=true){
        onDenied("Set up a fingerprint or screen lock in Android settings, then try again.")
        return
    }
    val biometric = if(android.os.Build.VERSION.SDK_INT<30)BiometricManager.Authenticators.BIOMETRIC_WEAK else BiometricManager.Authenticators.BIOMETRIC_STRONG
    val authenticators = biometric or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(host).canAuthenticate(authenticators) !=
        BiometricManager.BIOMETRIC_SUCCESS
    ) {
        if(allowUnsecured)onConfirmed()else onDenied("Set up a fingerprint or screen lock in Android settings, then try again.")
        return
    }

    val prompt = BiometricPrompt(
        host,
        ContextCompat.getMainExecutor(host),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onConfirmed()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val silent = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                onDenied(if (silent) null else errString.toString())
            }
        }
    )
    try { prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
    ) }catch(_:Exception){onDenied("Couldn't open device authentication. Please try again.")}
}
