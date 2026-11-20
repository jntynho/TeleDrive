package com.infinity.drive.core.proxy

import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.common.SafeLog
import com.infinity.drive.core.telegram.TelegramConnectionState
import com.infinity.drive.domain.repository.ProxyRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.repository.TelegramAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A blocked proxy looks exactly like a slow one: Telegram simply never
 * connects. When the connection stays down long enough to rule out a slow
 * start, the next saved proxy is tried, and then the one after that, until
 * either one answers or the list runs out.
 */
class ProxyFailover(
    private val authRepository: TelegramAuthRepository,
    private val proxyRepository: ProxyRepository,
    private val settingsRepository: SettingsRepository
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(authRepository.connectionState, settingsRepository.preferences) { connection, prefs ->
                connection == TelegramConnectionState.CONNECTING && prefs.proxyEnabled
            }.distinctUntilChanged().collectLatest { shouldTry ->
                if (!shouldTry) return@collectLatest
                var activeId = settingsRepository.preferences.first().activeProxyId
                val tried = mutableSetOf(activeId)
                while (isActive) {
                    delay(SETTLE_MS)
                    val prefs = settingsRepository.preferences.first()
                    if (!prefs.proxyEnabled) return@collectLatest
                    if (activeId != prefs.activeProxyId) {
                        activeId = prefs.activeProxyId
                        tried.clear()
                        tried.add(activeId)
                        continue
                    }
                    val rotated = proxyRepository.rotate(activeId, tried)
                    val next = (rotated as? AppResult.Success)?.value ?: return@collectLatest
                    tried.add(next)
                    activeId = next
                    SafeLog.d(TAG, "Tried another saved proxy")
                }
            }
        }
    }

    private companion object {
        const val TAG = "ProxyFailover"
        const val SETTLE_MS = 20_000L
    }
}
