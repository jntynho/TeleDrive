package com.infinity.drive.presentation.transfers

import com.infinity.drive.data.repository.ActiveChannel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.drive.domain.model.TransferSection
import com.infinity.drive.domain.model.TransferTask
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.transfers_retry_batch_error
import com.infinity.drive.resources.transfers_retry_batch_none
import com.infinity.drive.resources.transfers_retry_batch_queued
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransfersUiState(
    val chatId: Long? = null,
    val active: List<TransferTask> = emptyList(),
    val paused: List<TransferTask> = emptyList(),
    val failed: List<TransferTask> = emptyList(),
    val completed: List<TransferTask> = emptyList(),
    val activeTotal: Int = 0,
    val pausedTotal: Int = 0,
    val failedTotal: Int = 0,
    val completedTotal: Int = 0,
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransfersViewModel(
    private val transferRepository: TransferRepository,
    private val activeChannel: ActiveChannel,
    private val hidden:com.infinity.drive.core.security.HiddenContentAccess
) : ViewModel() {

    private fun rows(chatId: Long) = combine(
        transferRepository.observeSection(chatId, TransferSection.ACTIVE, SECTION_LIMIT),
        transferRepository.observeSection(chatId, TransferSection.PAUSED, SECTION_LIMIT),
        transferRepository.observeSection(chatId, TransferSection.FAILED, SECTION_LIMIT),
        transferRepository.observeSection(chatId, TransferSection.COMPLETED, SECTION_LIMIT)
    ) { active, paused, failed, completed ->
        val seen = mutableSetOf<String>()
        listOf(
            active.sortedWith(TRANSFER_ORDER).filterUnseen(seen),
            paused.sortedWith(TRANSFER_ORDER).filterUnseen(seen),
            failed.sortedWith(TRANSFER_ORDER).filterUnseen(seen),
            completed.sortedWith(FINISHED_ORDER).filterUnseen(seen)
        )
    }

    private fun List<TransferTask>.filterUnseen(seen: MutableSet<String>): List<TransferTask> =
        filter { seen.add(it.id) }

    private fun totals(chatId: Long) = combine(
        transferRepository.observeSectionCount(chatId, TransferSection.ACTIVE),
        transferRepository.observeSectionCount(chatId, TransferSection.PAUSED),
        transferRepository.observeSectionCount(chatId, TransferSection.FAILED),
        transferRepository.observeSectionCount(chatId, TransferSection.COMPLETED)
    ) { counts -> counts.toList() }

    private fun channelState(chatId: Long) = combine(rows(chatId), totals(chatId), hidden.lock.state, hidden.protectedFolders, hidden.notesRestricted) { rawSections, counts, _, _, _ ->
        val sections=rawSections.map{section->section.map{task->task.copy(displayName=hidden.notificationName(task.fileId,task.displayName),errorMessage=if(task.fileId?.let{!hidden.canRead(it)}==true)null else task.errorMessage)}}
        TransfersUiState(
            chatId = chatId,
            active = sections[0],
            paused = sections[1],
            failed = sections[2],
            completed = sections[3],
            activeTotal = counts[0],
            pausedTotal = counts[1],
            failedTotal = counts[2],
            completedTotal = counts[3],
            loading = false
        )
    }.onStart { emit(TransfersUiState(chatId = chatId)) }

    val uiState: StateFlow<TransfersUiState> = activeChannel.observe().flatMapLatest { chatId ->
        if (chatId == null) flowOf(TransfersUiState(loading = false)) else channelState(chatId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), TransfersUiState())

    fun pause(id: String) = launch { transferRepository.pause(id) }

    fun resume(id: String) = launch { transferRepository.resume(id) }

    fun cancel(id: String, dismissFinished: Boolean = false) = launch { transferRepository.cancel(id, dismissFinished) }

    fun retry(id: String) = launch { transferRepository.retry(id) }

    private val retryResults = Channel<UiText>(Channel.CONFLATED)
    val retryMessages = retryResults.receiveAsFlow()

    fun retryFailedBatch(chatId: Long?, batchId: String? = null) = launch {
        if (chatId == null || chatId != activeChannel.id()) return@launch
        val message = try {
            val count = transferRepository.retryFailedBatch(chatId, batchId)
            if (count == 0) UiText.Resource(Res.string.transfers_retry_batch_none)
            else UiText.Resource(Res.string.transfers_retry_batch_queued, count)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            UiText.Resource(Res.string.transfers_retry_batch_error)
        }
        retryResults.trySend(message)
    }

    fun pauseAll(chatId: Long?) = inChannel(chatId, transferRepository::pauseAll)

    fun resumeAll(chatId: Long?) = inChannel(chatId, transferRepository::resumeAll)

    fun cancelAll(chatId: Long?) = inChannel(chatId, transferRepository::cancelAll)

    fun clearFinished(chatId: Long?) = inChannel(chatId, transferRepository::clearFinished)

    private fun inChannel(chatId: Long?, action: suspend (Long) -> Unit) = launch {
        if (chatId != null && chatId == activeChannel.id()) action(chatId)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        /**
         * A backup can queue tens of thousands of rows. Reading them all into
         * one cursor overflows its window while the workers are still writing
         * progress into the same table, so each section is capped.
         */
        const val SECTION_LIMIT = 200

        /**
         * One order for every section, so a bulk pause, resume or cancel only
         * moves rows between sections instead of reshuffling them.
         */
        val TRANSFER_ORDER: Comparator<TransferTask> =
            compareBy<TransferTask> { it.createdAt }.thenBy { it.id }

        /** Finished transfers read as a history, so the latest one sits on top. */
        val FINISHED_ORDER: Comparator<TransferTask> =
            compareByDescending<TransferTask> { it.completedAt ?: it.updatedAt }
    }
}
