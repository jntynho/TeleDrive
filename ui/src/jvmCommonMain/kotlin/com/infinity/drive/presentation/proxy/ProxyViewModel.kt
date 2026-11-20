package com.infinity.drive.presentation.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.proxy_add_one_first
import com.infinity.drive.resources.proxy_link_imported
import com.infinity.drive.resources.proxy_link_not_recognised
import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.telegram.ProxyLink
import com.infinity.drive.core.proxy.ProxyProbeResult
import com.infinity.drive.core.telegram.TelegramProxy
import com.infinity.drive.domain.model.ProxyServer
import com.infinity.drive.domain.repository.ProxyRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.repository.TelegramAuthRepository
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class ProxyViewModel(
    private val proxyRepository: ProxyRepository,
    settingsRepository: SettingsRepository,
    authRepository: TelegramAuthRepository
) : ViewModel() {

    private val applying = MutableStateFlow(false)
    private val reachability = MutableStateFlow<Map<String, ProxyReachability>>(emptyMap())
    private val testJobs = mutableMapOf<String, Job>()

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val uiState: StateFlow<ProxyUiState> = combine(
        proxyRepository.observeProxies(),
        settingsRepository.preferences.map { it.proxyEnabled }.distinctUntilChanged(),
        reachability, applying, authRepository.connectionState
    ) { proxies, enabled, results, changing, connection ->
        ProxyUiState(
            proxies = proxies,
            enabled = enabled,
            loading = false,
            applying = changing,
            connection = connection,
            reachability = results
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProxyUiState())

    fun save(proxy: ProxyServer) = change {
        testJobs.remove(proxy.id)?.cancel()
        reachability.update { it - proxy.id }
        report(proxyRepository.save(proxy))
    }

    fun delete(id: String) = change {
        testJobs.remove(id)?.cancel()
        reachability.update { it - id }
        report(proxyRepository.delete(id))
    }

    fun select(id: String) = change { report(proxyRepository.select(id)) }

    fun setEnabled(enabled: Boolean) = change {
        val result = proxyRepository.setEnabled(enabled)
        if (result is AppResult.Failure && result.error == AppError.NotFound) {
            _messages.tryEmit(UiText.Resource(Res.string.proxy_add_one_first))
        } else report(result)
    }

    private fun change(action: suspend () -> Unit) {
        if (!applying.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                action()
            } finally {
                applying.value = false
            }
        }
    }

    fun test(proxy: ProxyServer) {
        testJobs[proxy.id]?.cancel()
        testJobs[proxy.id] = viewModelScope.launch {
            if (reachability.value.containsKey(proxy.id)) {
                reachability.update { it - proxy.id }
                delay(CLEAR_DELAY_MS.milliseconds)
            }
            reachability.update { it + (proxy.id to ProxyReachability.TESTING) }
            val result = proxyRepository.test(proxy)
            reachability.update { it + (proxy.id to result.toReachability()) }
            if (result is AppResult.Failure) report(result)
        }
    }

    /** Accepts a shared `tg://proxy` or `t.me` link and saves what it describes. */
    fun importLink(link: String) {
        val parsed = ProxyLink.parse(link)
        if (parsed == null) {
            _messages.tryEmit(UiText.Resource(Res.string.proxy_link_not_recognised))
            return
        }
        change {
            when (val result = proxyRepository.save(parsed.toServer())) {
                is AppResult.Success -> _messages.tryEmit(UiText.Resource(Res.string.proxy_link_imported, parsed.host))
                is AppResult.Failure -> report(result)
            }
        }
    }

    private fun AppResult<ProxyProbeResult>.toReachability(): ProxyReachability = when (this) {
        is AppResult.Success -> when (value) {
            ProxyProbeResult.ANSWERED -> ProxyReachability.ANSWERED
            else -> ProxyReachability.REACHABLE
        }

        is AppResult.Failure -> ProxyReachability.UNREACHABLE
    }

    private fun report(result: AppResult<Unit>) {
        if (result is AppResult.Failure) {
            _messages.tryEmit(result.error.toUiText())
        }
    }

    private fun TelegramProxy.toServer() = ProxyServer(
        id = UUID.randomUUID().toString(),
        label = host,
        type = type,
        host = host,
        port = port,
        username = username,
        password = password,
        secret = secret
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val CLEAR_DELAY_MS = 220L
    }
}
