package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

data class HiddenBiometricEnrollment(val iv:ByteArray,val ciphertext:ByteArray,val secret:ByteArray)
interface HiddenBiometrics {
    val available:Boolean
    suspend fun enroll():HiddenBiometricEnrollment?
    suspend fun authenticate(iv:ByteArray,ciphertext:ByteArray):ByteArray?
}
val LocalHiddenBiometrics=staticCompositionLocalOf<HiddenBiometrics?>{null}

val LocalHiddenContentAccess=staticCompositionLocalOf<com.infinity.drive.core.security.HiddenContentAccess?>{null}
