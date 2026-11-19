package com.infinity.drive.presentation.sync

internal enum class SyncDirection { UPLOAD, DOWNLOAD, BOTH }
internal enum class SyncAction { UPLOAD, DOWNLOAD, MATCH, CONFLICT, WAITING, SKIP }

internal fun syncAction(
    direction: SyncDirection,
    local: String?,
    remote: String?,
    lastMatch: String?,
    remotePending: Boolean = false,
    blocked: Boolean = false
): SyncAction {
    if (blocked) return SyncAction.CONFLICT
    if (remotePending) return SyncAction.WAITING
    if (local == null && remote == null) return SyncAction.SKIP
    if (local != null && local == remote) return SyncAction.MATCH

    val change = when {
        local == null -> SyncAction.DOWNLOAD
        remote == null -> SyncAction.UPLOAD
        remote == lastMatch -> SyncAction.UPLOAD
        local == lastMatch -> SyncAction.DOWNLOAD
        else -> SyncAction.CONFLICT
    }
    if (change == SyncAction.UPLOAD && direction == SyncDirection.DOWNLOAD ||
        change == SyncAction.DOWNLOAD && direction == SyncDirection.UPLOAD
    ) return SyncAction.SKIP
    return change
}
