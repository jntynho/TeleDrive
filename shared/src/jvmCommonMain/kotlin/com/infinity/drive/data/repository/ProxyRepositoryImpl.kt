package com.infinity.drive.data.repository

import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.common.SafeLog
import com.infinity.drive.core.proxy.ProxyProbe
import com.infinity.drive.core.proxy.ProxyProbeResult
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.core.telegram.TelegramProxy
import com.infinity.drive.core.telegram.hasValidFields
import com.infinity.drive.data.local.dao.ProxyDao
import com.infinity.drive.data.local.entity.ProxyEntity
import com.infinity.drive.domain.model.ProxyServer
import com.infinity.drive.domain.repository.ProxyRepository
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Saved routes to Telegram, for networks where it is blocked.
 *
 * The list lives here rather than in TDLib because the session database is
 * deleted on an auth reset, and a user who reaches Telegram only through a
 * proxy would otherwise lose the way back in.
 */
class ProxyRepositoryImpl(
    private val proxyDao: ProxyDao,
    private val settingsRepository: SettingsRepository,
    private val telegramClient: TelegramClient,
    private val proxyProbe: ProxyProbe
) : ProxyRepository {

    override fun observeProxies(): Flow<List<ProxyServer>> = combine(
        proxyDao.observeAll(),
        settingsRepository.preferences.map { it.activeProxyId }
    ) { proxies, activeId ->
        proxies.map { it.toDomain(isActive = it.id == activeId) }
    }

    private val mutations = Mutex()
    private var appliedProxy: TelegramProxy? = null
    private var routeApplied = false

    override suspend fun save(proxy: ProxyServer): AppResult<Unit> = mutate {
        val normalized = proxy.copy(host = proxy.host.trim().removeSurrounding("[", "]"))
        val target = normalized.toTelegram()
        if (!target.hasValidFields()) return@mutate AppResult.Failure(invalidProxy())
        val previous = proxyDao.byId(proxy.id)
        val prefs = settingsRepository.preferences.first()
        val activeEdit = prefs.proxyEnabled && prefs.activeProxyId == proxy.id
        // TDLib receives only valid inputs. A failed active edit keeps the old row and route.
        if (activeEdit) applyRoute(target)
        try {
            proxyDao.upsert(normalized.toEntity(previous?.addedAt ?: System.currentTimeMillis()))
        } catch (error: Exception) {
            if (activeEdit && previous != null && error !is CancellationException) applyRoute(previous.toDomain(false).toTelegram())
            throw error
        }
        if (prefs.activeProxyId.isBlank()) settingsRepository.update { it.copy(activeProxyId = proxy.id) }
        AppResult.Success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> = mutate {
        val prefs = settingsRepository.preferences.first()
        if (prefs.activeProxyId == id) {
            val fallback = proxyDao.all().firstOrNull { it.id != id }
            if (prefs.proxyEnabled) applyRoute(fallback?.toDomain(false)?.toTelegram())
            settingsRepository.update { it.copy(activeProxyId = fallback?.id.orEmpty(), proxyEnabled = it.proxyEnabled && fallback != null) }
        }
        proxyDao.delete(id)
        AppResult.Success(Unit)
    }

    override suspend fun select(id: String): AppResult<Unit> = mutate {
        val proxy = proxyDao.byId(id) ?: return@mutate AppResult.Failure(AppError.NotFound)
        val prefs = settingsRepository.preferences.first()
        if (prefs.proxyEnabled) applyRoute(proxy.toDomain(false).toTelegram())
        if (prefs.activeProxyId != id) settingsRepository.update { it.copy(activeProxyId = id) }
        AppResult.Success(Unit)
    }

    override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> = mutate {
        val prefs = settingsRepository.preferences.first()
        val selected = proxyDao.byId(prefs.activeProxyId) ?: if (enabled) proxyDao.all().firstOrNull() else null
        if (enabled && selected == null) return@mutate AppResult.Failure(AppError.NotFound)
        applyRoute(selected?.toDomain(false)?.toTelegram().takeIf { enabled })
        settingsRepository.update { it.copy(proxyEnabled = enabled, activeProxyId = selected?.id ?: it.activeProxyId) }
        AppResult.Success(Unit)
    }

    override suspend fun rotate(expectedId: String, excludedIds: Set<String>): AppResult<String?> = mutate {
        val prefs = settingsRepository.preferences.first()
        if (!prefs.proxyEnabled || prefs.activeProxyId != expectedId) return@mutate AppResult.Success(null)
        val saved = proxyDao.all()
        val index = saved.indexOfFirst { it.id == expectedId }
        val candidates = saved.drop(index + 1) + saved.take((index + 1).coerceAtLeast(0))
        for (candidate in candidates) {
            if (candidate.id == expectedId || candidate.id in excludedIds) continue
            try {
                applyRoute(candidate.toDomain(false).toTelegram())
            } catch (_: TelegramException) {
                continue
            }
            settingsRepository.update { it.copy(activeProxyId = candidate.id) }
            return@mutate AppResult.Success(candidate.id)
        }
        AppResult.Success(null)
    }

    override suspend fun applyActive() {
        val result = mutate {
            val prefs = settingsRepository.preferences.first()
            val target = if (prefs.proxyEnabled) proxyDao.byId(prefs.activeProxyId)?.toDomain(false)?.toTelegram() else null
            applyRoute(target, force = true)
            AppResult.Success(Unit)
        }
        if (result is AppResult.Failure) SafeLog.w(TAG, "Could not initialize the saved proxy route")
    }

    private suspend fun <T> mutate(action: suspend () -> AppResult<T>): AppResult<T> = mutations.withLock {
        // The connection may become ready while we apply a route. Persist the
        // applied choice even if that state change cancels a failover caller.
        withContext(NonCancellable) {
            try {
                action()
            } catch (_: TelegramException) {
                AppResult.Failure(AppError.UnsupportedOperation("Telegram could not apply the proxy settings."))
            } catch (_: java.io.IOException) {
                AppResult.Failure(AppError.Unknown())
            }
        }
    }

    private suspend fun applyRoute(target: TelegramProxy?, force: Boolean = false) {
        if (!force && routeApplied && target == appliedProxy) return
        telegramClient.applyProxy(target)
        val changed = !routeApplied || target != appliedProxy
        appliedProxy = target
        routeApplied = true
        if (changed && !force) telegramClient.reconnect()
    }

    private fun invalidProxy() = AppError.UnsupportedOperation("Check the proxy address, port and credentials.")

    override suspend fun test(proxy: ProxyServer): AppResult<ProxyProbeResult> = try {
        val target = proxy.toTelegram()
        if (!target.hasValidFields()) AppResult.Failure(invalidProxy()) else {
            telegramClient.testProxy(target)
            AppResult.Success(ProxyProbeResult.ANSWERED)
        }
    } catch (e: TelegramException) {
        if (e.code == UNAUTHORIZED) {
            probe(proxy)
        } else {
            AppResult.Failure(AppError.NetworkUnavailable)
        }
    }

    private suspend fun probe(proxy: ProxyServer): AppResult<ProxyProbeResult> {
        val result = proxyProbe.probe(proxy.toTelegram())
        return if (result == ProxyProbeResult.UNREACHABLE) {
            AppResult.Failure(AppError.NetworkUnavailable)
        } else {
            AppResult.Success(ProxyProbeResult.REACHABLE)
        }
    }

    private fun ProxyEntity.toDomain(isActive: Boolean) = ProxyServer(
        id = id,
        label = label,
        type = type,
        host = host,
        port = port,
        username = username,
        password = password,
        secret = secret,
        isActive = isActive
    )

    private fun ProxyServer.toEntity(addedAt: Long) = ProxyEntity(
        id = id,
        label = label,
        type = type,
        host = host,
        port = port,
        username = username?.takeIf { it.isNotBlank() },
        password = password?.takeIf { it.isNotBlank() },
        secret = secret?.takeIf { it.isNotBlank() },
        addedAt = addedAt
    )

    private fun ProxyServer.toTelegram() = TelegramProxy(
        type = type,
        host = host,
        port = port,
        username = username,
        password = password,
        secret = secret
    )

    private companion object {
        const val TAG = "ProxyRepository"
        const val UNAUTHORIZED = 401
    }
}
