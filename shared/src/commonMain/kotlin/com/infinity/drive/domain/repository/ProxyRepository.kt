package com.infinity.drive.domain.repository

import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.proxy.ProxyProbeResult
import com.infinity.drive.domain.model.ProxyServer
import kotlinx.coroutines.flow.Flow

interface ProxyRepository {

    fun observeProxies(): Flow<List<ProxyServer>>

    suspend fun save(proxy: ProxyServer): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>

    /** Picks which saved proxy to route through, and applies it now. */
    suspend fun select(id: String): AppResult<Unit>

    /** Turns routing on or off without forgetting the saved list. */
    suspend fun setEnabled(enabled: Boolean): AppResult<Unit>

    /** Reapplies the current choice, for a client that has just started. */
    suspend fun applyActive()

    /**
     * Tries remaining saved routes only if routing still uses the expected
     * proxy. Returns the newly selected route ID, or null if none was applied.
     */
    suspend fun rotate(expectedId: String, excludedIds: Set<String>): AppResult<String?>

    /** Reports how far a check through [proxy] got. */
    suspend fun test(proxy: ProxyServer): AppResult<ProxyProbeResult>
}
