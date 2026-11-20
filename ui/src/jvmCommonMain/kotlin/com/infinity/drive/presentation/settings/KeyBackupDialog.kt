package com.infinity.drive.presentation.settings

import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.settings_back_encryption_key
import com.infinity.drive.resources.settings_hint_optional
import com.infinity.drive.resources.settings_passphrase
import com.infinity.drive.resources.settings_passphrase_min_length
import com.infinity.drive.resources.settings_passphrases_do_not_match
import com.infinity.drive.resources.settings_repeat_passphrase
import com.infinity.drive.resources.settings_required_encryption_key_wrapped
import com.infinity.drive.resources.settings_save_backup
import com.infinity.drive.resources.settings_stored_unencrypted_never_put
import org.jetbrains.compose.resources.stringResource

private const val MIN_LENGTH = 8

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeyBackupDialog(
    working: Boolean,
    onConfirm: (passphrase: String, hint: String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    val mismatch = repeated.isNotEmpty() && passphrase != repeated
    val tooShort = passphrase.isNotEmpty() && passphrase.length < MIN_LENGTH

    AlertDialog(
        onDismissRequest = {if(!working)onDismiss()},
        title = { Text(stringResource(Res.string.settings_back_encryption_key)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(Res.string.settings_required_encryption_key_wrapped),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = passphrase,
                    enabled = !working,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(Res.string.settings_passphrase)) },
                    singleLine = true,
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        {
                            Text(
                                stringResource(
                                    Res.string.settings_passphrase_min_length,
                                    MIN_LENGTH
                                )
                            )
                        }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repeated,
                    enabled = !working,
                    onValueChange = { repeated = it },
                    label = { Text(stringResource(Res.string.settings_repeat_passphrase)) },
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(Res.string.settings_passphrases_do_not_match)) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hint,
                    enabled = !working,
                    onValueChange = { hint = it },
                    label = { Text(stringResource(Res.string.settings_hint_optional)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(Res.string.settings_stored_unencrypted_never_put)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase, hint.trim()) },
                shapes = ButtonDefaults.shapes(),
                enabled = !working && passphrase.length >= MIN_LENGTH && passphrase == repeated
            ) { Text(stringResource(Res.string.settings_save_backup)) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !working,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}
