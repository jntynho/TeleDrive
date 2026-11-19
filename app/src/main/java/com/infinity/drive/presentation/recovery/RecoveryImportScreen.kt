package com.infinity.drive.presentation.recovery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.core.crypto.LoginRecoveryFile
import com.infinity.drive.core.telegram.TelegramLoginRecovery
import com.infinity.drive.presentation.components.PageHeader
import com.infinity.drive.presentation.hidden.SensitiveScreen
import com.infinity.drive.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RecoveryImportScreen(onBack: () -> Unit, onImport: (TelegramLoginRecovery) -> Unit) {
    SensitiveScreen()
    val resolver = LocalContext.current.contentResolver
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf<android.net.Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<TelegramLoginRecovery?>(null) }
    var busy by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var foregroundGeneration by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        foregroundGeneration++
        password = ""
        account = null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) document = uri
        invalid = false
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            PageHeader(
                title = { Text(stringResource(Res.string.recovery_import_title)) },
                navigationIcon = { IconButton(onBack) { Icon(DriveIcons.ArrowBack, stringResource(Res.string.common_back)) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val selected = account
            if (selected != null) {
                Text(stringResource(Res.string.recovery_import_confirm, "••••${selected.phoneNumber.takeLast(4)}"))
                Button(onClick = { account = null; onImport(selected) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.recovery_export_next))
                }
            } else {
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Text(stringResource(Res.string.recovery_import_choose))
                }
                if (document != null) Text(stringResource(Res.string.recovery_import_chosen))
                RecoveryPassphraseField(
                    password, { password = it; invalid = false }, Res.string.recovery_export_passphrase, !busy
                )
                if (invalid) {
                    Text(stringResource(Res.string.recovery_import_invalid), color = MaterialTheme.colorScheme.error)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(
                    enabled = document != null && !busy && LoginRecoveryFile.isValidPassphrase(password),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val uri = document ?: return@Button
                        val secret = password.toCharArray()
                        val generation = foregroundGeneration
                        password = ""
                        focus.clearFocus()
                        busy = true
                        invalid = false
                        scope.launch {
                            try {
                                val contents = withContext(Dispatchers.IO) {
                                    val buffer = ByteArray(LoginRecoveryFile.MAX_FILE_SIZE + 1)
                                    val stream = resolver.openInputStream(uri) ?: error("File unavailable")
                                    stream.use {
                                        var size = 0
                                        while (size < buffer.size) {
                                            val count = it.read(buffer, size, buffer.size - size)
                                            if (count <= 0) break
                                            size += count
                                        }
                                        require(size <= LoginRecoveryFile.MAX_FILE_SIZE)
                                        buffer.copyOf(size)
                                    }
                                }
                                val recovered = withContext(Dispatchers.Default) { LoginRecoveryFile.decrypt(contents, secret) }
                                if (generation == foregroundGeneration) account = recovered
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Exception) {
                                if (generation == foregroundGeneration) invalid = true
                            } finally {
                                secret.fill('\u0000')
                                busy = false
                            }
                        }
                    }
                ) { Text(stringResource(Res.string.recovery_import_open)) }
            }
        }
    }
}
