package com.infinity.drive.domain.model

/** The groups the transfers screen shows, each read and counted on its own. */
enum class TransferSection(val states: List<TransferState>) {
    ACTIVE(listOf(TransferState.QUEUED, TransferState.RUNNING)),
    PAUSED(listOf(TransferState.PAUSED)),
    FAILED(listOf(TransferState.FAILED)),
    COMPLETED(listOf(TransferState.COMPLETED))
}
