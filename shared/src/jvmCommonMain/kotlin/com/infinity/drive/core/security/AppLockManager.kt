package com.infinity.drive.core.security

import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Session lock state. The app locks on launch and after the configured
 * inactivity timeout in the background. Biometric verification itself happens
 * in the UI layer; this class only owns the locked/unlocked state.
 */
class AppLockManager(
    private val settingsRepository: SettingsRepository
) {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null
    private var initialized = false
    private var authenticationSerial = 0L
    private val deviceAuthentications = mutableSetOf<Long>()
    private var deferredForegroundLock = false

    /** Native credential screens may background the host; defer a second app-wide prompt. */
    fun beginDeviceAuthentication():Long = (++authenticationSerial).also{deviceAuthentications.add(it)}
    fun finishDeviceAuthentication(ticket:Long,authenticated:Boolean):Boolean {
        if(!deviceAuthentications.remove(ticket))return false
        if(authenticated){unlock()}
        else if(deviceAuthentications.isEmpty()&&deferredForegroundLock){
            deferredForegroundLock=false;_locked.value=true
        }
        return true
    }

    suspend fun onAppStarted() {
        val prefs = settingsRepository.preferences.first()
        if (!prefs.appLockEnabled) {
            deferredForegroundLock = false
            _locked.value = false
            return
        }
        val elapsedMinutes = backgroundedAt?.let {
            (System.nanoTime() / 1_000_000 - it) / 60_000
        }
        val shouldLock = !initialized || (elapsedMinutes != null && elapsedMinutes >= prefs.autoLockTimeoutMinutes)
        initialized = true
        if (shouldLock) {
            if(deviceAuthentications.isNotEmpty())deferredForegroundLock=true
            else _locked.value=true
        }
    }

    fun onAppStopped() {
        backgroundedAt = System.nanoTime() / 1_000_000
    }

    fun unlock() {
        _locked.value = false
        backgroundedAt = null
        deferredForegroundLock = false
    }

    fun lockNow() {
        deviceAuthentications.clear();deferredForegroundLock=false
        _locked.value = true
    }
}
