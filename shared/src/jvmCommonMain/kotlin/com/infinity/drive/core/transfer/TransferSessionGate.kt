package com.infinity.drive.core.transfer

import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.domain.repository.TelegramAuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** A worker started by Android must restore TDLib itself, without relying on an open Activity. */
suspend fun awaitTransferSession(auth:TelegramAuthRepository,timeoutMillis:Long=45_000):Boolean {
    if(auth.startFromStoredCredentials().getOrNull()!=true)return false
    return withTimeoutOrNull(timeoutMillis){
        auth.authState.first{it!=TelegramAuthState.Initializing}==TelegramAuthState.Ready
    }?:false
}
