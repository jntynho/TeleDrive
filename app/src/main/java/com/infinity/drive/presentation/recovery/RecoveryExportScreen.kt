package com.infinity.drive.presentation.recovery

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.core.crypto.LoginRecoveryFile
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.presentation.common.AppBackHandler
import com.infinity.drive.presentation.components.PageHeader
import com.infinity.drive.presentation.hidden.SensitiveScreen
import com.infinity.drive.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.coroutines.coroutineContext

private enum class RecoveryAvailability { Checking, Available, Unavailable, Failed }

@Composable
internal fun RecoveryExportScreen(onBack: () -> Unit) {
    SensitiveScreen()
    val telegram = koinInject<TelegramClient>()
    val appLock = koinInject<AppLockManager>()
    val resolver = LocalContext.current.contentResolver
    val auth by telegram.authState.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var pendingFile by remember { mutableStateOf<ByteArray?>(null) }
    var pickerTicket by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var availability by remember { mutableStateOf(RecoveryAvailability.Checking) }
    var checkAttempt by remember { mutableIntStateOf(0) }

    fun clearPassphrases() {
        passphrase = ""
        confirmation = ""
    }
    fun finishPicker() {
        val ticket = pickerTicket
        pickerTicket = null
        if (ticket != null) appLock.finishDeviceAuthentication(ticket, authenticated = false)
    }
    DisposableEffect(appLock) { onDispose { finishPicker() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { clearPassphrases() }
    LaunchedEffect(auth, checkAttempt) {
        if (auth != TelegramAuthState.Ready) {
            availability = RecoveryAvailability.Checking
            pendingFile = null
            clearPassphrases()
        } else if (!saved) {
            availability = try {
                if (telegram.getLoginRecovery() == null) RecoveryAvailability.Unavailable
                else RecoveryAvailability.Available
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                RecoveryAvailability.Failed
            }
        }
    }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingFile
        pendingFile = null
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        var written = false
                        try {
                            check(bytes != null && telegram.authState.value == TelegramAuthState.Ready)
                            val output = resolver.openOutputStream(uri, "wt") ?: error("Unavailable destination")
                            output.use { it.write(bytes) }
                            coroutineContext.ensureActive()
                            written = true
                        } finally {
                            if (!written) runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                        }
                    }
                    saved = true
                    message = Res.string.recovery_export_saved
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    message = Res.string.recovery_export_save_error
                } finally {
                    busy = false
                    finishPicker()
                }
            }
        } else {
            finishPicker()
        }
    }

    val canEnterPassword = auth == TelegramAuthState.Ready && availability == RecoveryAvailability.Available
    val editable = !busy && pendingFile == null && !saved && canEnterPassword
    val match = passphrase == confirmation
    AppBackHandler(busy || pendingFile != null) {}
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            PageHeader(
                title = { Text(stringResource(Res.string.recovery_export_title)) },
                navigationIcon = {
                    IconButton(onBack, enabled = !busy && pendingFile == null) {
                        Icon(DriveIcons.ArrowBack, stringResource(Res.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!saved && canEnterPassword) {
                Text(
                    stringResource(Res.string.recovery_export_instructions, LoginRecoveryFile.MIN_PASSPHRASE_LENGTH),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RecoveryPassphraseField(passphrase, { passphrase = it }, Res.string.recovery_export_passphrase, editable)
                RecoveryPassphraseField(confirmation, { confirmation = it }, Res.string.recovery_export_confirmation, editable)
                if (confirmation.isNotEmpty() && !match) {
                    Text(stringResource(Res.string.recovery_export_mismatch), color = MaterialTheme.colorScheme.error)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editable && match && LoginRecoveryFile.isValidPassphrase(passphrase),
                    onClick = {
                        if (!busy && pendingFile == null) {
                            busy = true
                            message = null
                            val secret = passphrase.toCharArray()
                            clearPassphrases()
                            focus.clearFocus()
                            scope.launch {
                                try {
                                    val recovery = telegram.getLoginRecovery()
                                    if (recovery == null) {
                                        availability = RecoveryAvailability.Unavailable
                                    } else {
                                        val bytes = withContext(Dispatchers.Default) {
                                            LoginRecoveryFile.encrypt(recovery, secret)
                                        }
                                        check(telegram.authState.value == TelegramAuthState.Ready)
                                        pendingFile = bytes
                                        // Let the pending Android picker result finish before the app lock takes over.
                                        pickerTicket = appLock.beginDeviceAuthentication()
                                        saveFile.launch(LoginRecoveryFile.FILE_NAME)
                                    }
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) {
                                    pendingFile = null
                                    finishPicker()
                                    message = Res.string.recovery_export_prepare_error
                                } finally {
                                    secret.fill('\u0000')
                                    busy = false
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(if (busy) Res.string.recovery_export_working else Res.string.recovery_export_next))
                }
            }
            val status = when {
                auth != TelegramAuthState.Ready -> Res.string.recovery_export_sign_in
                saved -> message
                availability == RecoveryAvailability.Checking -> Res.string.recovery_export_checking
                availability == RecoveryAvailability.Unavailable -> Res.string.recovery_export_no_token
                availability == RecoveryAvailability.Failed -> Res.string.recovery_export_check_error
                else -> message
            }
            if (status != null) {
                Text(
                    stringResource(status),
                    color = when (status) {
                        Res.string.recovery_export_saved -> MaterialTheme.colorScheme.primary
                        Res.string.recovery_export_checking -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
            if (status == Res.string.recovery_export_checking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (status == Res.string.recovery_export_no_token || status == Res.string.recovery_export_check_error) {
                TextButton(onClick = {
                    availability = RecoveryAvailability.Checking
                    message = null
                    checkAttempt++
                }, enabled = !busy) {
                    Text(stringResource(Res.string.recovery_export_check_again))
                }
            }
        }
    }
}

@Composable
internal fun RecoveryPassphraseField(
    value: String,
    onChange: (String) -> Unit,
    label: StringResource,
    enabled: Boolean
) {
    var revealed by remember(enabled) { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { revealed = false }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(LoginRecoveryFile.MAX_PASSPHRASE_LENGTH)) },
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }, enabled = enabled) {
                Icon(
                    if (revealed) DriveIcons.VisibilityOff else DriveIcons.Visibility,
                    stringResource(if (revealed) Res.string.recovery_hide_password else Res.string.recovery_show_password)
                )
            }
        }
    )
}
