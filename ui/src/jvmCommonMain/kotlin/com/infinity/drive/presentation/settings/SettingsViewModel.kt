// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.drive.resources.settings_verified_cleanup_result
import com.infinity.drive.core.files.LocalCleanup
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.about_no_update
import com.infinity.drive.resources.backup_preview_started
import com.infinity.drive.resources.home_backup_nothing_to_back_up
import com.infinity.drive.resources.message_backing_up_new_folder
import com.infinity.drive.resources.message_cache_cleared
import com.infinity.drive.resources.message_key_backup_saved
import com.infinity.drive.resources.message_thumbnails_cleared
import com.infinity.drive.core.files.DeleteConsentRequest
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.permissions.PermissionChecker
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramConnectionState
import com.infinity.drive.core.telegram.TelegramUser
import com.infinity.drive.core.transfer.MaintenanceScheduler
import com.infinity.drive.core.transfer.TransferScheduler
import com.infinity.drive.data.repository.LocalDataWiper
import com.infinity.drive.domain.model.BackupTrigger
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.domain.repository.BackupRepository
import com.infinity.drive.domain.repository.CacheRepository
import com.infinity.drive.domain.repository.ChannelRepository
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.KeyBackupRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.repository.TelegramAuthRepository
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import com.infinity.drive.core.update.AppRelease
import com.infinity.drive.core.update.UpdateChecker

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: AppRelease) : UpdateState
}

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val user: TelegramUser? = null,
    val connection: TelegramConnectionState = TelegramConnectionState.CONNECTING,
    val cacheStats: CacheRepository.CacheStats = CacheRepository.CacheStats(0, 0, 0, 0, 0),
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val telegramAuthRepository: TelegramAuthRepository,
    private val telegramClient: TelegramClient,
    private val cacheRepository: CacheRepository,
    private val fileRepository: FileRepository,
    private val keyBackupRepository: KeyBackupRepository,
    private val localDataWiper: LocalDataWiper,
    private val channelRepository: ChannelRepository,
    private val backupRepository: BackupRepository,
    private val appLockManager: AppLockManager,
    private val maintenanceScheduler: MaintenanceScheduler,
    private val transferScheduler: TransferScheduler,
    val permissionChecker: PermissionChecker,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    val authorization = telegramAuthRepository.authState
    val storageChannels = channelRepository.observeChannels().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val user = MutableStateFlow<TelegramUser?>(null)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _keyBackupWorking = MutableStateFlow(false)
    val keyBackupWorking: StateFlow<Boolean> = _keyBackupWorking.asStateFlow()

    private val editablePreferences=MutableStateFlow<UserPreferences?>(null)
    private val preferenceWrites=Channel<(UserPreferences)->UserPreferences>(Channel.UNLIMITED)
    private var pendingWrites=0

    val uiState: StateFlow<SettingsUiState> = combine(
        editablePreferences,
        user,
        telegramAuthRepository.connectionState,
        cacheRepository.observeStats()
    ) { prefs, currentUser, connection, cacheStats ->
        SettingsUiState(
            preferences = prefs ?: UserPreferences(),
            user = currentUser,
            connection = connection,
            cacheStats = cacheStats,
            loading = prefs==null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collect { if(pendingWrites==0)editablePreferences.value=it }
        }
        viewModelScope.launch {
            for(transform in preferenceWrites) {
                try { persistPreferenceChange(transform) }
                catch(e:CancellationException) { throw e }
                catch(e:Exception) { _messages.tryEmit(UiText.Plain("Could not save this setting. Please try again.")) }
                finally {
                    pendingWrites--
                    if(pendingWrites==0)runCatching { settingsRepository.preferences.first() }.getOrNull()?.let { editablePreferences.value=it }
                }
            }
        }
        viewModelScope.launch {runCatching{cacheRepository.refreshStats()}}
        viewModelScope.launch {
            telegramAuthRepository.authState.collectLatest { authorization ->
                when(authorization){
                    com.infinity.drive.core.telegram.TelegramAuthState.Ready -> {
                        when(val result=telegramAuthRepository.getCurrentUser()){
                            is AppResult.Success->user.value=result.value
                            is AppResult.Failure->Unit
                        }
                    }
                    com.infinity.drive.core.telegram.TelegramAuthState.Closed,
                    com.infinity.drive.core.telegram.TelegramAuthState.Uninitialized -> user.value=null
                    else->Unit
                }
            }
        }
    }

    fun update(transform: (UserPreferences) -> UserPreferences) {
        editablePreferences.value=transform(editablePreferences.value ?: uiState.value.preferences)
        pendingWrites++
        preferenceWrites.trySend(transform)
    }

    private suspend fun persistPreferenceChange(transform:(UserPreferences)->UserPreferences) {
            val previous = settingsRepository.preferences.first()
            settingsRepository.update(transform)
            val current = settingsRepository.preferences.first()
            if (current.linkPreviews != previous.linkPreviews) {
                cacheRepository.clearLinkThumbnails()
            }
            if (current.allowMeteredTransfers != previous.allowMeteredTransfers) {
                transferScheduler.rekick(current.allowMeteredTransfers)
            }
            if (current.updateCheckEnabled != previous.updateCheckEnabled) {
                maintenanceScheduler.scheduleUpdateCheck(current.updateCheckEnabled)
            }
            if(current.storageChatId!=previous.storageChatId)backupRepository.syncActiveSessionWithSelection()
            fun scheduleKey(p:UserPreferences)=listOf(p.autoBackupEnabled,p.backupIntervalHours,p.backupWifiOnly,p.backupChargingOnly,p.instantBackupEnabled,p.updateCheckEnabled)
            if(scheduleKey(current)!=scheduleKey(previous))rescheduleWork()
    }

    private val _backupPreview = MutableStateFlow(BackupPreviewState())
    internal val backupPreview = _backupPreview.asStateFlow()
    private var backupPreviewJob: Job? = null

    fun previewBackup() {
        if (backupPreviewJob?.isActive == true) return
        _backupPreview.value = BackupPreviewState(running = true)
        backupPreviewJob = viewModelScope.launch {
            try {
                _backupPreview.value = when (val result = backupRepository.previewBackup()) {
                    is AppResult.Success -> BackupPreviewState(report = result.value)
                    is AppResult.Failure -> BackupPreviewState(error = result.error.toUiText())
                }
            } finally {
                _backupPreview.value = _backupPreview.value.copy(running = false)
            }
        }
    }

    fun cancelBackupPreview() {
        backupPreviewJob?.cancel()
    }

    fun startBackupAfterPreview() {
        if (_backupPreview.value.report?.uploads?.let { it > 0 } != true) return
        _backupPreview.value = BackupPreviewState()
        viewModelScope.launch {
            val message = when (val result = backupRepository.startBackup(BackupTrigger.MANUAL)) {
                is AppResult.Success -> UiText.Resource(
                    if (result.value == null) Res.string.home_backup_nothing_to_back_up
                    else Res.string.backup_preview_started
                )
                is AppResult.Failure -> result.error.toUiText()
            }
            _messages.tryEmit(message)
        }
    }

    private suspend fun scanForNewBackupWork() {
        when (val result = backupRepository.startBackup(BackupTrigger.MANUAL)) {
            is AppResult.Success -> result.value?.let {
                _messages.tryEmit(UiText.Resource(Res.string.message_backing_up_new_folder))
            }

            is AppResult.Failure -> Unit
        }
    }

    private suspend fun rescheduleWork() {
        val prefs = settingsRepository.preferences.first()
        maintenanceScheduler.scheduleAll(
            backupEnabled = prefs.autoBackupEnabled && prefs.backupIntervalHours > 0,
            backupIntervalHours = prefs.backupIntervalHours,
            wifiOnly = prefs.backupWifiOnly,
            chargingOnly = prefs.backupChargingOnly,
            instantBackup = prefs.instantBackupEnabled,
            updateChecks = prefs.updateCheckEnabled
        )
    }

    /** Backup folders belong to the active drive, not to the device. */
    val backupFolders: StateFlow<Set<String>> = settingsRepository.preferences
        .map { it.storageChatId }
        .distinctUntilChanged()
        .flatMapLatest { chatId ->
            channelRepository.observeChannels().map { channels ->
                channels.firstOrNull { it.chatId == chatId }?.backupFolders.orEmpty()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun saveBackupFolderRule(rule: com.infinity.drive.domain.model.BackupFolderRule) {
        viewModelScope.launch {
            if (rule.path !in channelRepository.backupFolders(rule.chatId, migrateLegacy = false)) return@launch
            settingsRepository.update { prefs ->
                if (prefs.storageChatId != rule.chatId) prefs
                else prefs.copy(backupFolderRules = prefs.backupFolderRules.filterNot {
                    it.chatId == rule.chatId && it.path == rule.path
                } + rule)
            }
        }
    }

    fun addBackupFolder(path: String) {
        editBackupFolders { it + path }
    }

    fun removeBackupFolder(path: String) {
        editBackupFolders { it - path }
    }

    private fun editBackupFolders(transform: (Set<String>) -> Set<String>) {
        viewModelScope.launch {
            val chatId = settingsRepository.preferences.first().storageChatId ?: return@launch
            val before = channelRepository.backupFolders(chatId)
            val after = transform(before)
            if (after == before) return@launch
            channelRepository.setBackupFolders(chatId, after)
            settingsRepository.update { prefs -> prefs.copy(backupFolderRules = prefs.backupFolderRules.filterNot {
                it.chatId == chatId && it.path !in after
            }) }
            backupRepository.syncActiveSessionWithSelection()
            rescheduleWork()
            if ((after - before).isNotEmpty()) scanForNewBackupWork()
        }
    }

    fun notify(message: UiText) {
        _messages.tryEmit(message)
    }

    fun backUpEncryptionKey(passphrase: String, hint: String) {
        _keyBackupWorking.value = true
        viewModelScope.launch {
            val result = keyBackupRepository.createBackup(
                passphrase.toCharArray(),
                hint.takeIf { it.isNotBlank() }
            )
            _keyBackupWorking.value = false
            when (result) {
                is AppResult.Success -> {
                    settingsRepository.update {
                        it.copy(keyBackupCreated = true)
                    }
                    _messages.tryEmit(UiText.Resource(Res.string.message_key_backup_saved))
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }

    val reclaimableBytes: StateFlow<Long> = fileRepository.observeReclaimableBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _deleteConsentRequests = MutableSharedFlow<DeleteConsentRequest>(extraBufferCapacity = 1)
    val deleteConsentRequests = _deleteConsentRequests.asSharedFlow()

    private val _cleaningLocal = MutableStateFlow(false)
    val cleaningLocal = _cleaningLocal.asStateFlow()
    private var pendingCleanup: LocalCleanup? = null

    fun freeUpSpace() {
        if (_cleaningLocal.value || pendingCleanup != null) return
        _cleaningLocal.value = true
        viewModelScope.launch {
            try {
                when (val result = fileRepository.freeUpSpace()) {
                    is AppResult.Success -> {
                        val cleanup = result.value
                        val consent = cleanup.consentRequest
                        if (consent == null) {
                            showCleanupResult(cleanup.deletedCount, cleanup.keptCount)
                        } else {
                            pendingCleanup = cleanup
                            _deleteConsentRequests.emit(consent)
                        }
                    }
                    is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
                }
            } finally {
                _cleaningLocal.value = false
            }
        }
    }

    fun onVerifiedCleanupConsent(granted: Boolean) {
        val cleanup = pendingCleanup ?: return
        pendingCleanup = null
        if (!granted) return
        viewModelScope.launch {
            val removed = fileRepository.reconcileVerifiedCleanup(cleanup.consentCopies)
            showCleanupResult(removed, cleanup.keptCount + cleanup.consentCopies.size - removed)
        }
    }

    private fun showCleanupResult(removed: Int, kept: Int) {
        _messages.tryEmit(UiText.Resource(Res.string.settings_verified_cleanup_result, removed, kept))
    }

    fun clearCache() {
        viewModelScope.launch {
            cacheRepository.clearAll()
            _messages.tryEmit(UiText.Resource(Res.string.message_cache_cleared))
        }
    }

    fun clearThumbnails() {
        viewModelScope.launch {
            cacheRepository.clearThumbnails()
            _messages.tryEmit(UiText.Resource(Res.string.message_thumbnails_cleared))
        }
    }

    fun checkForUpdates() {
        if (_updateState.value is UpdateState.Checking) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val release = try{updateChecker.newerRelease(strict=true)}catch(e:Exception){
                if(e is CancellationException)throw e
                _messages.tryEmit(UiText.Plain("Couldn't check for updates. Check your connection and try again."))
                _updateState.value=UpdateState.Idle
                return@launch
            }
            settingsRepository.update { it.copy(notifiedUpdateVersion = release?.version ?: "") }
            settingsRepository.update { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }
            _updateState.value = if (release == null) {
                _messages.tryEmit(UiText.Resource(Res.string.about_no_update))
                UpdateState.Idle
            } else {
                UpdateState.Available(release)
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            when (val result = telegramAuthRepository.logout()) {
                is AppResult.Success -> {
                    withContext(NonCancellable) {
                        localDataWiper.wipe()
                        settingsRepository.clearTelegramCredentials()
                        settingsRepository.update {
                            it.copy(
                                onboardingComplete = false,
                                storageChatId = null,
                                backupFolders = emptySet(),
                                backupFolderRules = emptyList(),
                                linkedTelegramSources = emptyList(),
                                smartFolders = emptyList()
                            )
                        }
                    }
                    onLoggedOut()
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }
}
