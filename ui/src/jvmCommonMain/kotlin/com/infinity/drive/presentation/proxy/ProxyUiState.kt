package com.infinity.drive.presentation.proxy

import com.infinity.drive.domain.model.ProxyServer
import com.infinity.drive.core.telegram.TelegramConnectionState

data class ProxyUiState(
    val proxies: List<ProxyServer> = emptyList(),
    val enabled: Boolean = false,
    val loading: Boolean = true,
    val applying: Boolean = false,
    val connection: TelegramConnectionState = TelegramConnectionState.WAITING_FOR_NETWORK,
    val reachability: Map<String, ProxyReachability> = emptyMap()
)
